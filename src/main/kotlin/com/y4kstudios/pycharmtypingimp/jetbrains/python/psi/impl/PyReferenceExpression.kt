package com.y4kstudios.pycharmtypingimp.jetbrains.python.psi.impl

import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiInvalidElementAccessException
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyCallExpressionNavigator
import com.jetbrains.python.psi.impl.PyImportedModule
import com.jetbrains.python.psi.impl.PyReferenceExpressionImpl
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.*
import com.jetbrains.python.refactoring.PyDefUseUtil
import com.y4kstudios.pycharmtypingimp.psi.impl.PyDescriptorGetExpression
import one.util.streamex.StreamEx
import java.util.stream.Collector
import java.util.stream.Collectors

/* !!!
 * NOTE: the below methods are lifted directly from com.jetbrains.python.psi.impl.PyReferenceExpressionImpl.
 *
 * These are private/package-private methods that must be reproduced in full, because PyReferenceExpression
 * does not expose a `getType()` method that doesn't invoke all PyTypeProvider extensions, nor does PyCharm
 * expose an extension point allowing the customization of types *after* they've been determined by other
 * extensions or PyCharm.
 *
 * One of these routes (and, preferably, the latter) would be required to cleanly implement this plugin's
 * TypeProviders without overriding typing info for non-dynamic attributes, or at all. Instead, we perform
 * our typing lookups after every other extension, then essentially replace PyCharm's own typing lookups.
 * THIS WILL VERY LIKELY CAUSE DEGRADATION IF PYCHARM'S TYPING IMPLEMENTATION DRIFTS FROM THIS PLUGIN —
 * if that happens, using this plugin will result in *worse* typing in some cases than if not installed.
*/

internal fun PyReferenceExpression.getPyCharmType(context: TypeEvalContext): PyType? {
    val qualified = isQualified
    if (qualified) {
        val qualifiedReferenceType = getQualifiedReferenceType(context)
        if (qualifiedReferenceType != null) {
            return qualifiedReferenceType.get()
        }
    }
    val typeFromTargets = getTypeFromTargets(context)
    if (qualified && typeFromTargets is PyNoneType) {
        return null
    }
    val descriptorType = getDescriptorType(typeFromTargets, context)
    if (descriptorType != null) {
        return descriptorType.get()
    }
    val callableType = getCallableType(context)
    return callableType ?: typeFromTargets
}

internal fun PyReferenceExpression.getCallableType(context: TypeEvalContext): PyType? {
    val callExpression = PyCallExpressionNavigator.getPyCallExpressionByCallee(this) ?: return null
    return PyCallExpressionHelper.getCalleeType(callExpression, PyResolveContext.defaultContext(context))
}

internal fun PyReferenceExpression.getDescriptorType(
    typeFromTargets: PyType?, context: TypeEvalContext, substituteGenerics: Boolean = false
): Ref<PyType>? {
    if (!isQualified) return null
    val targetType = PyUtil.`as`(
        typeFromTargets,
        PyClassLikeType::class.java
    )
    if (targetType == null || targetType.isDefinition) return null
    val resolveContext = PyResolveContext.noProperties(context)
    val members = targetType.resolveMember(
        PyNames.GET, this, AccessDirection.READ,
        resolveContext
    )
    if (members == null || members.isEmpty()) return null

    val getCallableType =
        if (substituteGenerics) {
            val pyDescriptorGetExpression = PyDescriptorGetExpression(this);
            { callable: PyCallable ->
                callable.getCallType(context, pyDescriptorGetExpression)
            }
        } else
            { callable: PyCallable ->
                context.getReturnType(callable)
            }

    val type = members
        .map { obj: RatedResolveResult -> obj.element }
        .filterIsInstance<PyCallable>()
        .map(getCallableType)
        .let { PyUnionType.union(it) }
    return Ref.create(type)
}

internal fun PyReferenceExpression.getQualifiedReferenceType(context: TypeEvalContext): Ref<PyType>? {
    if (!context.maySwitchToAST(this)) {
        return null
    }

    return PyUtil.getSpecialAttributeType(this, context)?.let { Ref.create(it) }
        ?: getTypeOfProperty(context)
        ?: getQualifiedReferenceTypeByControlFlow(context)?.let { Ref.create(it) }
}

internal fun PyReferenceExpression.getTypeFromTargets(context: TypeEvalContext): PyType? {
    val resolveContext = PyResolveContext.defaultContext(context)
    val members: MutableList<PyType?> = ArrayList()
    val realFile = FileContextUtil.getContextFile(this)
    if (
        containingFile !is PyExpressionCodeFragment
        || realFile != null
        && context.maySwitchToAST(realFile)
    ) {
        for (target in PyUtil.multiResolveTopPriority(getReference(resolveContext))) {
            if (target === this || target == null) {
                continue
            }
            if (!target.isValid) {
                throw PsiInvalidElementAccessException(this)
            }
            members.add(getTypeFromTarget(target, context, this))
        }
    }
    return PyUnionType.union(members)
}

internal fun PyReferenceExpression.getQualifiedReferenceTypeByControlFlow(context: TypeEvalContext): PyType? {
    var qualifier: PyExpression? = qualifier ?: return null
    if (context.allowDataFlow(this)) {
        var next = qualifier
        while (next != null) {
            qualifier = next
            next = if (qualifier is PyQualifiedExpression) qualifier.qualifier else null
        }
        val scopeOwner = ScopeUtil.getScopeOwner(this) ?: return null
        val qname = asQualifiedName() ?: return null
        return getTypeByControlFlow(qname.toString(), context, qualifier!!, scopeOwner)
    }
    return null
}

internal fun getTypeByControlFlow(
    name: String,
    context: TypeEvalContext,
    anchor: PyExpression,
    scopeOwner: ScopeOwner
): PyType? {
    val augAssignment = PsiTreeUtil.getParentOfType(
        anchor,
        PyAugAssignmentStatement::class.java
    )
    val element = augAssignment ?: anchor
    try {
        val defs = PyDefUseUtil.getLatestDefs(scopeOwner, name, element, true, false)
        // null means empty set of possible types, Ref(null) means Any
        val combinedType = StreamEx.of(defs)
            .select(ReadWriteInstruction::class.java)
            .map { instr: ReadWriteInstruction ->
                instr.getType(context, anchor)
            }
            .collect(toUnionFromRef())
        return Ref.deref(combinedType)
    } catch (ignored: PyDefUseUtil.InstructionNotFoundException) {
    }
    return null
}

internal fun PyReferenceExpression.getTypeOfProperty(context: TypeEvalContext): Ref<PyType>? {
    val qualifier = qualifier ?: return null
    val name = name ?: return null
    val qualifierType = context.getType(qualifier)
    return getTypeOfProperty(qualifierType, name, context)
}

internal fun PyReferenceExpression.getTypeOfProperty(
    qualifierType: PyType?,
    name: String,
    context: TypeEvalContext
): Ref<PyType>? {
    when (qualifierType) {
        is PyClassType -> {
            val pyClass = qualifierType.pyClass
            val property = pyClass.findProperty(name, true, context)
            if (property != null) {
                if (qualifierType.isDefinition) {
                    return Ref.create(
                        PyBuiltinCache.getInstance(pyClass).getObjectType(PyNames.PROPERTY)
                    )
                }
                if (AccessDirection.of(this) == AccessDirection.READ) {
                    val type = property.getType(qualifier, context)
                    if (type != null) {
                        return Ref.create(type)
                    }
                }
                return Ref.create()
            }
        }
        is PyUnionType -> {
            for (type in qualifierType.members) {
                val result = getTypeOfProperty(type, name, context)
                if (result != null) {
                    return result
                }
            }
        }
    }
    return null
}

internal fun PyReferenceExpression.getTypeFromTarget(
    target: PsiElement,
    context: TypeEvalContext,
    anchor: PyReferenceExpression
): PyType? {
    val type = dropSelfForQualifiedMethod(
        getGenericTypeFromTarget(
            target,
            context,
            anchor
        ), context, anchor
    )
    if (context.maySwitchToAST(anchor)) {
        val qualifier = anchor.qualifier
        if (qualifier != null) {
            val qualifierType = context.getType(qualifier)
            val possiblyParameterizedQualifier =
                !(qualifierType is PyModuleType || qualifierType is PyImportedModuleType)
            if (possiblyParameterizedQualifier && PyTypeChecker.hasGenerics(type, context)) {
                val substitutions =
                    PyTypeChecker.unifyGenericCall(qualifier, emptyMap(), context)
                if (substitutions != null) {
                    val substituted = PyTypeChecker.substitute(type, substitutions, context)
                    if (substituted != null) {
                        return substituted
                    }
                }
            }
        }
    }
    return type
}

internal fun dropSelfForQualifiedMethod(
    type: PyType?,
    context: TypeEvalContext,
    anchor: PyReferenceExpression
): PyType? {
    return if (type is PyFunctionType && context.maySwitchToAST(anchor) && anchor.qualifier != null) {
        type.dropSelf(context)
    } else type
}

internal fun PyReferenceExpression.getGenericTypeFromTarget(
    target: PsiElement,
    context: TypeEvalContext,
    anchor: PyReferenceExpression
): PyType? {
    if (target !is PyTargetExpression) {  // PyTargetExpression will ask about its type itself
        val pyType =
            PyReferenceExpressionImpl.getReferenceTypeFromProviders(target, context, anchor)
        if (pyType != null) {
            return pyType.get()
        }
    }
    if (target is PyTargetExpression) {
        val name = target.name!!
        if (PyNames.NONE == name) {
            return PyNoneType.INSTANCE
        }
        if (PyNames.TRUE == name || PyNames.FALSE == name) {
            return PyBuiltinCache.getInstance(target).boolType
        }
    }
    if (target is PyFile) {
        return PyModuleType(target)
    }
    if (target is PyElement && context.allowDataFlow(anchor)) {
        val scopeOwner = ScopeUtil.getScopeOwner(anchor)
        val name = target.name
        if (scopeOwner != null && name != null &&
            !ScopeUtil.getElementsOfAccessType(
                name,
                scopeOwner,
                ReadWriteInstruction.ACCESS.ASSERTTYPE
            ).isEmpty()
        ) {
            val type = getTypeByControlFlow(name, context, anchor, scopeOwner)
            if (type != null) {
                return type
            }
        }
    }
    if (target is PyFunction) {
        val decoratorList = target.decoratorList
        if (decoratorList != null) {
            val propertyDecorator = decoratorList.findDecorator(PyNames.PROPERTY)
            if (propertyDecorator != null) {
                return PyBuiltinCache.getInstance(target).getObjectType(PyNames.PROPERTY)
            }
            for (decorator in decoratorList.decorators) {
                val qName = decorator.qualifiedName
                if (qName != null && (qName.endsWith(PyNames.SETTER) || qName.endsWith(PyNames.DELETER) || qName.endsWith(
                        PyNames.GETTER
                    ))
                ) {
                    return PyBuiltinCache.getInstance(target).getObjectType(PyNames.PROPERTY)
                }
            }
        }
    }
    if (target is PyTypedElement) {
        return context.getType(target)
    }
    if (target is PsiDirectory) {
        val dir = target
        val file = dir.findFile(PyNames.INIT_DOT_PY)
        if (file != null) {
            return getTypeFromTarget(file, context, anchor)
        }
        if (PyUtil.isPackage(dir, anchor)) {
            val containingFile = anchor.containingFile
            if (containingFile is PyFile) {
                val qualifiedName = QualifiedNameFinder.findShortestImportableQName(dir)
                if (qualifiedName != null) {
                    val module = PyImportedModule(
                        null,
                        containingFile, qualifiedName
                    )
                    return PyImportedModuleType(module)
                }
            }
        }
    }
    return null
}


fun toUnionFromRef(): Collector<Ref<PyType?>?, *, Ref<PyType?>?> =
    Collectors.reducing(null) { accType: Ref<PyType?>?, hintType: Ref<PyType?>? ->
        if (hintType == null) {
            accType
        } else if (accType == null) {
            hintType
        } else {
            Ref.create(PyUnionType.union(accType.get(), hintType.get()))
        }
    }
