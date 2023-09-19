package com.y4kstudios.pycharmtypingimp.psi.types

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.*
import com.y4kstudios.pycharmtypingimp.jetbrains.python.psi.impl.getIterationTargetAndSourceType
import com.y4kstudios.pycharmtypingimp.openapi.extensions.notApplicableOnlyIfBuild

/**
 * Provides proper typing for dict.values() iteration
 *
 * Addresses:
 *  - [PY-52656](https://youtrack.jetbrains.com/issue/PY-52656/Incorrect-dictvalues-return-type)
 */
class DictValuesViewTypeProvider : PyTypeProviderBase() {
    init {
        notApplicableOnlyIfBuild { baselineVersion, buildNumber ->
            when(baselineVersion) {
                // NOTE: these ranges are pulled from the YT issue's "Included in builds" field
                //  ref: https://youtrack.jetbrains.com/issue/PY-52656/Incorrect-dictvalues-return-type
                221 -> buildNumber >= 5397
                222 -> buildNumber >= 3603
                223 -> buildNumber >= 1613
                else -> baselineVersion > 223
            }
        }
    }

    override fun getReferenceType(
        target: PsiElement,
        context: TypeEvalContext,
        anchor: PsiElement?
    ): Ref<PyType>? {
        if (target !is PyTargetExpression) return null

        val (iterationTarget, iterationSourceType) = target.getIterationTargetAndSourceType(context)
                ?: return null

        if (iterationSourceType !is PyCollectionType) return null

        // XXX(zk): Will this have different QN values for different Python versions?
        if (iterationSourceType.classQName != "_collections_abc.dict_values")
            return null

        val iterationType =
            iterationSourceType.elementTypes.getOrNull(1)
                ?: return null

        if (iterationType is PyTupleType && iterationTarget is PyTupleExpression) {
            return PyTypeChecker.getTargetTypeFromTupleAssignment(target, iterationTarget, iterationType)?.let { Ref.create(it) }
        }

        if (iterationTarget == target) {
            return Ref.create(iterationType)
        }

        return null
    }
}

