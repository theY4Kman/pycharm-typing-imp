package com.y4kstudios.pycharmtypingimp.psi.types

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.types.*
import com.y4kstudios.pycharmtypingimp.jetbrains.python.psi.impl.getIterationTargetAndSourceType

/**
 * Provides proper typing for dict.values() iteration
 *
 * PY-52656: https://youtrack.jetbrains.com/issue/PY-52656/Incorrect-dictvalues-return-type
 */
class DictViewsTypeProvider : PyTypeProviderBase() {
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

