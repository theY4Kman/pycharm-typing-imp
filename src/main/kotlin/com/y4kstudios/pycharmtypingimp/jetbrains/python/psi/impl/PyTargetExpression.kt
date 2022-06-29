package com.y4kstudios.pycharmtypingimp.jetbrains.python.psi.impl

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyComprehensionElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyForPart
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext

internal fun PyTargetExpression.getIterationTargetAndSourceType(
    context: TypeEvalContext
): Pair<PyExpression, PyType?>? {
    var target: PyExpression? = null
    var source: PyExpression? = null
    val forPart = PsiTreeUtil.getParentOfType(
        this,
        PyForPart::class.java
    )
    if (forPart != null) {
        val expr = forPart.target
        if (PsiTreeUtil.isAncestor(expr, this, false)) {
            target = expr
            source = forPart.source
        }
    }
    val comprh = PsiTreeUtil.getParentOfType(
        this,
        PyComprehensionElement::class.java
    )
    if (comprh != null) {
        for (c in comprh.forComponents) {
            val expr = c.iteratorVariable
            if (PsiTreeUtil.isAncestor(expr, this, false)) {
                target = expr
                source = c.iteratedList
            }
        }
    }
    if (target != null && source != null) {
        return Pair(target, context.getType(source))
    }
    return null
}
