package com.y4kstudios.pycharmtypingimp.psi.types

import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyCallable
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.TypeEvalContext
import com.y4kstudios.pycharmtypingimp.jetbrains.python.psi.impl.getPyCharmType

/**
 * Provides typing info for dynamic class attributes served by __getattr__ or __getattribute__
 *
 * PY-49262 (unfixed as of 2024/11/30): https://youtrack.jetbrains.com/issue/PY-49262/Autocomplete-on-overloaded-getattr-does-not-pick-up-methods-or-attributes-of-type-hinted-class
 * PY-40586 (unfixed as of 2024/11/30): https://youtrack.jetbrains.com/issue/PY-40586/Provide-autocompletions-based-on-the-return-value-of-getattr-on-missing-reference-for-a-class-that-implements-getattr
 * PY-21069 (unfixed as of 2024/11/30): https://youtrack.jetbrains.com/issue/PY-21069/Annotated-return-types-for-getattr-and-getattribute-methods-are-not-taken-into-account-by-type-checker
 */
class GetattrTypeProvider : PyTypeProviderBase() {
    override fun getReferenceExpressionType(
        referenceExpression: PyReferenceExpression,
        context: TypeEvalContext
    ): PyType? {
        // Quick escape for non-qualified expressions
        if (!referenceExpression.isQualified) return null

        // Escape quickly if qualifier isn't typed (idk why that would ever happen)
        val qualifier = referenceExpression.qualifier as? PyTypedElement ?: return null

        // Defer to other typing providers if they successfully provide typing
        // (This allows, e.g., instance attribute and descriptor types to be returned)
        val knownType = referenceExpression.getPyCharmType(context)
        if (knownType != null) return null

        val qualifierType = context.getType(qualifier) ?: return null
        if (qualifierType !is PyClassType) return null

        val pyResolveContext = PyResolveContext.defaultContext(context)
        val getattr =
            sequenceOf(PyNames.GETATTR, PyNames.GETATTRIBUTE)
                .mapNotNull { name ->
                    qualifierType.resolveMember(name, null, AccessDirection.READ, pyResolveContext)
                        ?.firstOrNull()
                        ?.element
                }
                .firstOrNull()
                ?: return null

        if (getattr !is PyCallable) return null
        return context.getReturnType(getattr)
    }
}

