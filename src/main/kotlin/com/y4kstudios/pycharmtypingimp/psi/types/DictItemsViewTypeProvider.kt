package com.y4kstudios.pycharmtypingimp.psi.types

import com.intellij.openapi.util.Ref
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyCallSiteExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.*
import com.y4kstudios.pycharmtypingimp.openapi.extensions.notApplicableOnlyIfBuild

/**
 * Provides proper typing for dict.values() iteration
 *
 * Addresses:
 *  - [PY-30709](https://youtrack.jetbrains.com/issue/PY-30709/Incorrect-type-inference-of-dictitems)
 */
class DictItemsViewTypeProvider : PyTypeProviderBase() {
    init {
        notApplicableOnlyIfBuild { baselineVersion, buildNumber ->
            when(baselineVersion) {
                // NOTE: these ranges are pulled from the YT issue's "Included in builds" field
                //  ref: https://youtrack.jetbrains.com/issue/PY-30709/Incorrect-type-inference-of-dictitems
                // 222 -> buildNumber >= 3603  // unsupported by this plugin
                // 223 -> buildNumber >= 8294  // tested not working
                231 -> buildNumber >= 2977
                else -> false
            }
        }
    }

    companion object {
        private const val DICT_ITEMS = "_dict_items"
        private const val COLLECTIONS_DICT_ITEMS = "_collections_abc.dict_items"
        val DICT_ITEMS_NAMES = setOf(DICT_ITEMS, COLLECTIONS_DICT_ITEMS)

        const val TUPLE_CONSTRUCTOR = "tuple.__new__"
        const val DICT_CONSTRUCTOR: String = "dict.__init__"
        private const val LIST_CONSTRUCTOR = "list.__init__"
        private const val SET_CONSTRUCTOR = "set.__init__"
        private const val RANGE_CONSTRUCTOR = "range"

        fun getCollectionConstructors(languageLevel: LanguageLevel): Set<String> {
            return if (languageLevel.isPython2) {
                setOf(
                    TUPLE_CONSTRUCTOR,
                    LIST_CONSTRUCTOR,
                    DICT_CONSTRUCTOR,
                    SET_CONSTRUCTOR,
                    RANGE_CONSTRUCTOR
                )
            } else {
                setOf(
                    TUPLE_CONSTRUCTOR,
                    LIST_CONSTRUCTOR,
                    DICT_CONSTRUCTOR,
                    SET_CONSTRUCTOR
                )
            }
        }
    }

    override fun getCallType(
        function: PyFunction,
        callSite: PyCallSiteExpression,
        context: TypeEvalContext
    ): Ref<PyType?>? {
        val qualifiedName = function.qualifiedName
            ?: return null
        if (!getCollectionConstructors(LanguageLevel.forElement(callSite)).contains(qualifiedName)) {
            return null
        }

        val arguments = callSite.getArguments(null)
        if (arguments.isEmpty()) {
            return null
        }

        val firstArg = arguments[0]
        val firstArgType = context.getType(firstArg) as? PyCollectionType
            ?: return null

        if (firstArgType.classQName !in DICT_ITEMS_NAMES) {
            return null
        }

        val argumentTypes =
            when (qualifiedName) {
                DICT_CONSTRUCTOR -> {
                    firstArgType.elementTypes
                }

                /**
                 * Tuples initialized from dict_items[K, V] are variable in length, and
                 * should look like: tuple[tuple[K, V], ...]
                 *
                 * XXX(zk): Unfortunately, the PyStdlibTypeProvider takes over before we can handle
                 *   things, and ultimately uses PyCollectionTypeImpl.getIteratedItemType() to get
                 *   the tuple's homogeneous type... which currently simply grabs the first generic
                 *   arg of dict_items[K, V].
                 *
                 *   There is a TODO in getIteratedItemType() to select the proper matching Iterable
                 *   type, but this is not yet implemented. Until that's done, hacking around
                 *   PyStdlibTypeProvider is not something I particularly wish to do at 5AM.
                 */
                TUPLE_CONSTRUCTOR -> {
                    val itemType = PyTupleType.create(firstArg, firstArgType.elementTypes)
                    return Ref.create(PyTupleType.createHomogeneous(firstArg, itemType))
                }

                else -> {
                    listOf(PyTupleType.create(firstArg, firstArgType.elementTypes))
                }
            }

        val cls = function.containingClass
        if (cls != null) {
            return Ref.create(PyCollectionTypeImpl(cls, false, argumentTypes))
        } else {
            val returnType = context.getReturnType(function)
            if (returnType is PyClassType) {
                return Ref.create(PyCollectionTypeImpl(returnType.pyClass, false, argumentTypes))
            }
        }

        return null
    }
}

