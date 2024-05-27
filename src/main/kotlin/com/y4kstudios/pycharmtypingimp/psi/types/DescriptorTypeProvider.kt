package com.y4kstudios.pycharmtypingimp.psi.types

import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.types.PyNoneType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.TypeEvalContext
import com.y4kstudios.pycharmtypingimp.jetbrains.python.psi.impl.getDescriptorType
import com.y4kstudios.pycharmtypingimp.jetbrains.python.psi.impl.getTypeFromTargets
import com.y4kstudios.pycharmtypingimp.openapi.extensions.notApplicableOnlyIfBuild

/**
 * Provides typing info for descriptor-based class attributes served by __get__
 *
 * PY-55531 (unfixed as of 2024/05/27): https://youtrack.jetbrains.com/issue/PY-55531/Pycharm-cant-handle-typing-of-get-method-of-descriptors
 *
 * See also https://youtrack.jetbrains.com/issue/PY-26184/Type-hinting-information-for-bound-generics-lost-in-descriptors
 *    Note that (as of 2024/05/27) this issue has "Included in builds" listed, but this class's issue still
 *    persists in these versions
 */
class DescriptorTypeProvider : PyTypeProviderBase() {
    init {
        notApplicableOnlyIfBuild { baselineVersion, buildNumber ->
            when(baselineVersion) {
                // NOTE: these ranges are pulled from the YT issue's "Included in builds" field
                //  ref: https://youtrack.jetbrains.com/issue/PY-26184/Type-hinting-information-for-bound-generics-lost-in-descriptors
                // 241 -> buildNumber >= 17318  // tested not working
                // 242 -> buildNumber >= 7578   // tested not working
                else -> false
            }
        }
    }

    override fun getReferenceExpressionType(
        referenceExpression: PyReferenceExpression,
        context: TypeEvalContext
    ): PyType? {
        // Quick escape for non-qualified expressions
        if (!referenceExpression.isQualified) return null

        val typeFromTargets = referenceExpression.getTypeFromTargets(context)
        if (typeFromTargets is PyNoneType) {
            return null
        }

        val descriptorType = referenceExpression.getDescriptorType(typeFromTargets, context, substituteGenerics = true)
            ?: return null

        return descriptorType.get()
    }
}

