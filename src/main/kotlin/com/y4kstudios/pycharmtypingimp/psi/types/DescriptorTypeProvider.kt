package com.y4kstudios.pycharmtypingimp.psi.types

import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyCallable
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.*
import com.y4kstudios.pycharmtypingimp.jetbrains.python.psi.impl.getDescriptorType
import com.y4kstudios.pycharmtypingimp.jetbrains.python.psi.impl.getPyCharmType
import com.y4kstudios.pycharmtypingimp.jetbrains.python.psi.impl.getTypeFromTargets

/**
 * Provides typing info for descriptor-based class attributes served by __get__
 *
 * PY-55531: https://youtrack.jetbrains.com/issue/PY-55531/Pycharm-cant-handle-typing-of-get-method-of-descriptors
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

