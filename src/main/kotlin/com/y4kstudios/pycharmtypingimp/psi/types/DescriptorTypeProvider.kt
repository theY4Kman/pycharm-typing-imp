package com.y4kstudios.pycharmtypingimp.psi.types

import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.types.PyNoneType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.TypeEvalContext
import com.y4kstudios.pycharmtypingimp.jetbrains.python.psi.impl.getDescriptorType
import com.y4kstudios.pycharmtypingimp.jetbrains.python.psi.impl.getTypeFromTargets

/**
 * Provides typing info for descriptor-based class attributes served by __get__
 *
 * PY-55531 (unfixed as of 2023/09/19): https://youtrack.jetbrains.com/issue/PY-55531/Pycharm-cant-handle-typing-of-get-method-of-descriptors
 */
class DescriptorTypeProvider : PyTypeProviderBase() {
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

