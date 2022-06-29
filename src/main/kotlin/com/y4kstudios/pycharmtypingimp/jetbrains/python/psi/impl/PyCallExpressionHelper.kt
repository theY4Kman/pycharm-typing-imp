// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.y4kstudios.pycharmtypingimp.jetbrains.python.psi.impl

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveResult
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonRuntimeService
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.PyCallExpression.PyArgumentsMapping
import com.jetbrains.python.psi.impl.*
import com.jetbrains.python.psi.impl.references.PyReferenceImpl
import com.jetbrains.python.psi.resolve.*
import com.jetbrains.python.psi.types.*
import com.jetbrains.python.pyi.PyiUtil
import com.jetbrains.python.toolbox.Maybe
import one.util.streamex.StreamEx
import org.jetbrains.annotations.Contract
import java.util.*
import java.util.function.Function
import java.util.stream.Collectors
import java.util.stream.Stream


/**
 * Functions common to different implementors of PyCallExpression, with different base classes.
 * User: dcheryasov
 */
object PyCallExpressionHelper {
    /**
     * Tries to interpret a call as a call to built-in `classmethod` or `staticmethod`.
     *
     * @param redefiningCall the possible call, generally a result of chasing a chain of assignments
     * @return a pair of wrapper name and wrapped function; for `staticmethod(foo)` it would be ("staticmethod", foo).
     */
    fun interpretAsModifierWrappingCall(redefiningCall: PyCallExpression): Pair<String, PyFunction>? {
        val redefiningCallee = redefiningCall.callee
        if (redefiningCall.isCalleeText(PyNames.CLASSMETHOD, PyNames.STATICMETHOD)) {
            val referenceExpr = redefiningCallee as PyReferenceExpression?
            if (referenceExpr != null) {
                val refName = referenceExpr.referencedName
                if (
                    PyNames.CLASSMETHOD == refName
                    || PyNames.STATICMETHOD == refName
                    && PyBuiltinCache.isInBuiltins(referenceExpr)
                ) {
                    // yes, really a case of "foo = classmethod(foo)"
                    val argumentList = redefiningCall.argumentList
                    if (argumentList != null) { // really can't be any other way
                        val args = argumentList.arguments
                        if (args.size == 1) {
                            val possible_original_ref = args[0]
                            if (possible_original_ref is PyReferenceExpression) {
                                val original = possible_original_ref.reference.resolve()
                                if (original is PyFunction) {
                                    // pinned down the original; replace our resolved callee with it and add flags.
                                    return Pair.create(refName, original)
                                }
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    fun resolveCalleeClass(us: PyCallExpression): PyClass? {
        val callee = us.callee
        val resolved: PsiElement?
        val resolveResult: QualifiedResolveResult
        if (callee is PyReferenceExpression) {
            // dereference
            val ref = callee
            resolveResult = ref.followAssignmentsChain(
                PyResolveContext.defaultContext(
                    TypeEvalContext.codeInsightFallback(ref.project)
                )
            )
            resolved = resolveResult.element
        } else {
            resolved = callee
        }
        // analyze
        if (resolved is PyClass) {
            return resolved
        } else if (resolved is PyFunction) {
            return resolved.containingClass
        }
        return null
    }

    /**
     * This method should not be called directly,
     * please obtain its result via [TypeEvalContext.getType] with `call.getCallee()` as an argument.
     */
    fun getCalleeType(
        call: PyCallExpression,
        resolveContext: PyResolveContext
    ): PyType? {
        val callableTypes: MutableList<PyType?> = ArrayList()
        val context = resolveContext.typeEvalContext
        val results = PyUtil.filterTopPriorityResults(
            forEveryScopeTakeOverloadsOtherwiseImplementations(
                multiResolveCallee(call.callee, resolveContext),
                { obj: QualifiedRatedResolveResult -> obj.element },
                context
            )
        )
        for (resolveResult in results) {
            val element = resolveResult.element
            if (element != null) {
                val typeFromProviders = Ref.deref(
                    PyReferenceExpressionImpl.getReferenceTypeFromProviders(
                        element,
                        resolveContext.typeEvalContext,
                        call
                    )
                )
                if (PyTypeUtil.toStream(typeFromProviders).allMatch { it is PyCallableType }) {
                    PyTypeUtil.toStream(typeFromProviders).forEachOrdered { e ->
                        callableTypes.add(e)
                    }
                    continue
                }
            }
            for (clarifiedResolveResult in clarifyResolveResult(resolveResult, resolveContext)) {
                ContainerUtil.addIfNotNull(
                    callableTypes,
                    toCallableType(call, clarifiedResolveResult, context)
                )
            }
        }
        return PyUnionType.union(callableTypes)
    }

    fun getReceiver(call: PyCallExpression, resolvedCallee: PyCallable?): PyExpression? {
        if (resolvedCallee is PyFunction) {
            val function = resolvedCallee
            if (PyNames.NEW != function.name && function.modifier == PyFunction.Modifier.STATICMETHOD) {
                return null
            }
        }
        val callee = call.callee
        if (callee != null && isImplicitlyInvokedMethod(resolvedCallee) && resolvedCallee!!.name != callee.name) {
            return callee
        }
        return if (callee is PyQualifiedExpression) {
            callee.qualifier
        } else null
    }

    @Contract("null -> false")
    private fun isImplicitlyInvokedMethod(resolvedCallee: PyCallable?): Boolean {
        if (PyUtil.isInitOrNewMethod(resolvedCallee)) return true
        if (resolvedCallee is PyFunction) {
            val function = resolvedCallee
            return PyNames.CALL == function.name && function.containingClass != null
        }
        return false
    }

    /**
     * It is not the same as [PyCallExpressionHelper.getCalleeType] since
     * this method returns callable types that would be actually called, the mentioned method returns type of underlying callee.
     * Compare:
     * <pre>
     * `class A:
     * pass
     * a = A()
     * b = a()  # callee type is A, resolved callee is A.__call__
    ` *
    </pre> *
     */
    fun multiResolveCallee(
        call: PyCallExpression,
        resolveContext: PyResolveContext
    ): List<PyCallableType> {
        return PyUtil.getParameterizedCachedValue(
            call,
            resolveContext
        ) { it: PyResolveContext ->
            ContainerUtil.concat(
                getExplicitResolveResults(
                    call,
                    it
                ),
                getImplicitResolveResults(
                    call,
                    it
                ),
                getRemoteResolveResults(
                    call,
                    it
                )
            )
        }
    }

    private fun multiResolveCallee(
        subscription: PySubscriptionExpression,
        resolveContext: PyResolveContext
    ): List<PyCallableType> {
        val context = resolveContext.typeEvalContext
        val results = forEveryScopeTakeOverloadsOtherwiseImplementations(
            Arrays.asList(*subscription.getReference(resolveContext).multiResolve(false)),
            context
        )
        return selectCallableTypes(results, context)
    }

    private fun getExplicitResolveResults(
        call: PyCallExpression,
        resolveContext: PyResolveContext
    ): List<PyCallableType> {
        val callee = call.callee ?: return emptyList()
        val context = resolveContext.typeEvalContext
        val calleeType = context.getType(callee)
        val provided =
            PyTypeProvider.EP_NAME.extensionList
                .mapNotNull {
                    it.prepareCalleeTypeForCall(
                        calleeType,
                        call,
                        context
                    )
                }

        if (provided.isNotEmpty()) {
            return provided.mapNotNull { Ref.deref(it) }
        }

        val result: MutableList<PyCallableType> = ArrayList()
        for (type in PyTypeUtil.toStream(calleeType)) {
            if (type is PyClassType) {
                val implicitlyInvokedMethods = forEveryScopeTakeOverloadsOtherwiseImplementations(
                    resolveImplicitlyInvokedMethods(type, call, resolveContext),
                    context
                )
                if (implicitlyInvokedMethods.isEmpty()) {
                    result.add(type)
                } else {
                    result.addAll(
                        changeToImplicitlyInvokedMethods(
                            type,
                            implicitlyInvokedMethods,
                            call,
                            context
                        )
                    )
                }
            } else if (type is PyCallableType) {
                result.add(type)
            }
        }
        return result
    }

    private fun getImplicitResolveResults(
        call: PyCallExpression,
        resolveContext: PyResolveContext
    ): List<PyCallableType> {
        if (!resolveContext.allowImplicits()) return emptyList()
        val callee = call.callee
        val context = resolveContext.typeEvalContext
        if (callee is PyQualifiedExpression) {
            val qualifiedCallee = callee
            val referencedName = qualifiedCallee.referencedName ?: return emptyList()
            val qualifier = qualifiedCallee.qualifier
            if (qualifier == null || !canQualifyAnImplicitName(qualifier)) return emptyList()

            val qualifierType = context.getType(qualifier)
            if (PyTypeChecker.isUnknown(qualifierType, context) ||
                qualifierType is PyStructuralType && qualifierType.isInferredFromUsages
            ) {
                val resolveResults = ResolveResultList()
                PyResolveUtil.addImplicitResolveResults(
                    referencedName,
                    resolveResults,
                    qualifiedCallee
                )
                return selectCallableTypes(
                    forEveryScopeTakeOverloadsOtherwiseImplementations(
                        resolveResults,
                        context
                    ), context
                )
            }
        }
        return emptyList()
    }

    private fun getRemoteResolveResults(
        call: PyCallExpression,
        resolveContext: PyResolveContext
    ): List<PyCallableType> {
        if (!resolveContext.allowRemote()) return emptyList()
        val file = call.containingFile
        if (file == null || !PythonRuntimeService.getInstance()
                .isInPydevConsole(file)
        ) return emptyList()
        val calleeType = getCalleeType(call, resolveContext)
        return PyTypeUtil.toStream(calleeType).select(PyCallableType::class.java).toList()
    }

    private fun selectCallableTypes(
        resolveResults: List<PsiElement>,
        context: TypeEvalContext
    ): List<PyCallableType> {
        return StreamEx
            .of(resolveResults)
            .select(PyTypedElement::class.java)
            .map { element: PyTypedElement? ->
                context.getType(
                    element!!
                )
            }
            .flatMap { type: PyType? ->
                PyTypeUtil.toStream(
                    type
                )
            }
            .select(PyCallableType::class.java)
            .toList()
    }

    private fun multiResolveCallee(
        callee: PyExpression?,
        resolveContext: PyResolveContext
    ): List<QualifiedRatedResolveResult> {
        if (callee is PyReferenceExpression) {
            return callee.multiFollowAssignmentsChain(resolveContext)
        } else if (callee is PyLambdaExpression) {
            return listOf(
                QualifiedRatedResolveResult(
                    callee,
                    emptyList(),
                    RatedResolveResult.RATE_NORMAL,
                    false
                )
            )
        }
        return emptyList()
    }

    private fun clarifyResolveResult(
        resolveResult: QualifiedRatedResolveResult,
        resolveContext: PyResolveContext
    ): List<ClarifiedResolveResult> {
        val resolved = resolveResult.element
        if (resolved is PyCallExpression) { // foo = classmethod(foo)
            val wrapperInfo = interpretAsModifierWrappingCall(
                resolved
            )
            if (wrapperInfo != null) {
                val wrapperName = wrapperInfo.getFirst()
                val wrappedModifier =
                    if (PyNames.CLASSMETHOD == wrapperName) PyFunction.Modifier.CLASSMETHOD else (if (PyNames.STATICMETHOD == wrapperName) PyFunction.Modifier.STATICMETHOD else null)!!
                val result = ClarifiedResolveResult(
                    resolveResult,
                    wrapperInfo.getSecond(),
                    wrappedModifier,
                    false
                )
                return listOf(result)
            }
        } else if (resolved is PyFunction) {
            val function = resolved
            val context = resolveContext.typeEvalContext
            if (function.property != null && isQualifiedByInstance(
                    function,
                    resolveResult.qualifiers,
                    context
                )
            ) {
                val type = context.getReturnType(function)
                return if (type is PyFunctionType) listOf(
                    ClarifiedResolveResult(
                        resolveResult,
                        type.callable,
                        null,
                        false
                    )
                ) else emptyList()
            }
        }
        return if (resolved != null) listOf(
            ClarifiedResolveResult(
                resolveResult,
                resolved,
                null,
                resolved is PyClass
            )
        ) else emptyList()
    }

    private fun toCallableType(
        callSite: PyCallSiteExpression,
        resolveResult: ClarifiedResolveResult,
        context: TypeEvalContext
    ): PyCallableType? {
        val clarifiedResolved = resolveResult.myClarifiedResolved as? PyTypedElement ?: return null
        val callableType = PyUtil.`as`(
            context.getType(clarifiedResolved),
            PyCallableType::class.java
        ) ?: return null
        if (clarifiedResolved is PyCallable) {
            val originalModifier =
                if (clarifiedResolved is PyFunction) clarifiedResolved.modifier else null
            val resolvedModifier =
                ObjectUtils.chooseNotNull(originalModifier, resolveResult.myWrappedModifier)
            val isConstructorCall = resolveResult.myIsConstructor
            val qualifiers = resolveResult.myOriginalResolveResult.qualifiers
            val isByInstance = (isConstructorCall
                    || isQualifiedByInstance(clarifiedResolved, qualifiers, context)
                    || clarifiedResolved is PyBoundFunction)
            val lastQualifier = ContainerUtil.getLastItem(qualifiers)
            val isByClass =
                lastQualifier != null && isQualifiedByClass(
                    clarifiedResolved,
                    lastQualifier,
                    context
                )
            val resolvedImplicitOffset = getImplicitArgumentCount(
                clarifiedResolved,
                resolvedModifier,
                isConstructorCall,
                isByInstance,
                isByClass
            )
            val clarifiedConstructorCallType =
                if (PyUtil.isInitOrNewMethod(clarifiedResolved)) clarifyConstructorCallType(
                    resolveResult,
                    callSite,
                    context
                ) else null
            return if (callableType.modifier == resolvedModifier && callableType.implicitOffset == resolvedImplicitOffset && clarifiedConstructorCallType == null
            ) {
                callableType
            } else PyCallableTypeImpl(
                callableType.getParameters(context),
                ObjectUtils.chooseNotNull(
                    clarifiedConstructorCallType,
                    callableType.getCallType(context, callSite)
                ),
                clarifiedResolved,
                resolvedModifier,
                Math.max(0, resolvedImplicitOffset)
            )
            // wrong source can trigger strange behaviour
        }
        return callableType
    }

    /**
     * Calls the [.getImplicitArgumentCount] (full version)
     * with null flags and with isByInstance inferred directly from call site (won't work with reassigned bound methods).
     *
     * @param callReference the call site, where arguments are given.
     * @param function      resolved method which is being called; plain functions are OK but make little sense.
     * @return a non-negative number of parameters that are implicit to this call.
     */
    fun getImplicitArgumentCount(
        callReference: PyReferenceExpression, function: PyFunction,
        resolveContext: PyResolveContext
    ): Int {
        val followed = callReference.followAssignmentsChain(resolveContext)
        val qualifiers = followed.qualifiers
        val firstQualifier = ContainerUtil.getFirstItem(qualifiers)
        val isByInstance =
            isQualifiedByInstance(function, qualifiers, resolveContext.typeEvalContext)
        val name = callReference.name
        val isConstructorCall = PyUtil.isInitOrNewMethod(function) &&
                (!callReference.isQualified || PyNames.INIT != name && PyNames.NEW != name)
        val isByClass = firstQualifier != null && isQualifiedByClass(
            function,
            firstQualifier,
            resolveContext.typeEvalContext
        )
        return getImplicitArgumentCount(
            function,
            function.modifier,
            isConstructorCall,
            isByInstance,
            isByClass
        )
    }

    /**
     * Finds how many arguments are implicit in a given call.
     *
     * @param callable     resolved method which is being called; non-methods immediately return 0.
     * @param isByInstance true if the call is known to be by instance (not by class).
     * @return a non-negative number of parameters that are implicit to this call. E.g. for a typical method call 1 is returned
     * because one parameter ('self') is implicit.
     */
    private fun getImplicitArgumentCount(
        callable: PyCallable,
        modifier: PyFunction.Modifier?,
        isConstructorCall: Boolean,
        isByInstance: Boolean,
        isByClass: Boolean
    ): Int {
        var implicit_offset = 0
        var firstIsArgsOrKwargs = false
        val parameters = callable.parameterList.parameters
        if (parameters.size > 0) {
            val first = parameters[0]
            val named = first.asNamed
            if (named != null && (named.isPositionalContainer || named.isKeywordContainer)) {
                firstIsArgsOrKwargs = true
            }
        }
        if (!firstIsArgsOrKwargs && (isByInstance || isConstructorCall)) {
            implicit_offset += 1
        }
        val method = callable.asMethod() ?: return implicit_offset
        if (PyUtil.isNewMethod(method)) {
            return if (isConstructorCall) 1 else 0
        }
        if (!isByInstance && !isByClass && PyUtil.isInitMethod(method)) {
            return 1
        }

        // decorators?
        if (modifier == PyFunction.Modifier.STATICMETHOD) {
            if (isByInstance && implicit_offset > 0) implicit_offset -= 1 // might have marked it as implicit 'self'
        } else if (modifier == PyFunction.Modifier.CLASSMETHOD) {
            if (!isByInstance) implicit_offset += 1 // Both Foo.method() and foo.method() have implicit the first arg
        }
        return implicit_offset
    }

    private fun isQualifiedByInstance(
        resolved: PyCallable?,
        qualifiers: List<PyExpression?>,
        context: TypeEvalContext,
    ): Boolean {
        if (PsiTreeUtil.getStubOrPsiParentOfType(resolved, PyDocStringOwner::class.java) !is PyClass) {
            return false
        }
        // true = call by instance
        if (qualifiers.isEmpty()) {
            return true // unqualified + method = implicit constructor call
        }
        for (qualifier in qualifiers) {
            if (qualifier != null && isQualifiedByInstance(resolved, qualifier, context)) {
                return true
            }
        }
        return false
    }

    private fun isQualifiedByInstance(
        resolved: PyCallable?,
        qualifier: PyExpression,
        context: TypeEvalContext
    ): Boolean {
        if (isQualifiedByClass(resolved, qualifier, context)) {
            return false
        }
        val qualifierType = context.getType(qualifier)
        if (qualifierType != null) {
            // TODO: handle UnionType
            if (qualifierType is PyModuleType) return false // qualified by module, not instance.
        }
        return true // NOTE. best guess: unknown qualifier is more probably an instance.
    }

    private fun isQualifiedByClass(
        resolved: PyCallable?,
        qualifier: PyExpression,
        context: TypeEvalContext
    ): Boolean {
        val qualifierType = context.getType(qualifier)
        if (qualifierType is PyClassType) {
            return qualifierType.isDefinition && belongsToSpecifiedClassHierarchy(
                resolved,
                qualifierType.pyClass,
                context
            )
        } else if (qualifierType is PyClassLikeType) {
            return qualifierType.isDefinition // Any definition means callable is classmethod
        } else if (qualifierType is PyUnionType) {
            val members = qualifierType.members
            if (ContainerUtil.all(
                    members
                ) { type: PyType? -> type == null || type is PyNoneType || type is PyClassType }
            ) {
                return StreamEx
                    .of(members)
                    .select(PyClassType::class.java)
                    .filter { type: PyClassType ->
                        belongsToSpecifiedClassHierarchy(
                            resolved,
                            type.pyClass,
                            context
                        )
                    }
                    .allMatch { obj: PyClassType -> obj.isDefinition }
            }
        }
        return false
    }

    private fun belongsToSpecifiedClassHierarchy(
        element: PsiElement?,
        cls: PyClass,
        context: TypeEvalContext
    ): Boolean {
        val parent = PsiTreeUtil.getStubOrPsiParentOfType(
            element,
            PyClass::class.java
        )
        return parent != null && (cls.isSubclass(parent, context) || parent.isSubclass(
            cls,
            context
        ))
    }

    /**
     * This method should not be called directly,
     * please obtain its result via [TypeEvalContext.getType] with `call` as an argument.
     */
    fun getCallType(
        call: PyCallExpression,
        context: TypeEvalContext,
        key: TypeEvalContext.Key
    ): PyType? {
        val callee = call.callee
        if (callee is PyReferenceExpression) {
            // hardwired special cases
            if (PyNames.SUPER == callee.getText()) {
                val superCallType = getSuperCallType(call, context)
                if (superCallType.isDefined) {
                    return superCallType.value()
                }
            }
            if ("type" == callee.getText()) {
                val args = call.arguments
                if (args.size == 1) {
                    val arg = args[0]
                    val argType = context.getType(arg)
                    if (argType is PyClassType) {
                        val classType = argType
                        if (!classType.isDefinition) {
                            val cls = classType.pyClass
                            return context.getType(cls)
                        }
                    } else {
                        return null
                    }
                }
            }
        }
        if (callee is PySubscriptionExpression) {
            val parametrizedType = Ref.deref(PyTypingTypeProvider.getType(callee, context))
            if (parametrizedType != null) {
                return parametrizedType
            }
        }
        val resolveContext = PyResolveContext.defaultContext(context)
        return getCallType(multiResolveCallee(call, resolveContext), call, context)
    }

    /**
     * This method should not be called directly,
     * please obtain its result via [TypeEvalContext.getType] with `subscription` as an argument.
     */
    fun getCallType(
        subscription: PySubscriptionExpression,
        context: TypeEvalContext,
        key: TypeEvalContext.Key
    ): PyType? {
        val resolveContext = PyResolveContext.defaultContext(context)
        return getCallType(multiResolveCallee(subscription, resolveContext), subscription, context)
    }

    private fun getCallType(
        callableTypes: List<PyCallableType>,
        callSite: PyCallSiteExpression,
        context: TypeEvalContext
    ): PyType? {
        return PyUnionType.union(
            ContainerUtil.map(
                dropNotMatchedOverloadsOrLeaveAsIs(
                    ContainerUtil.filter(
                        callableTypes
                    ) { obj: PyCallableType -> obj.isCallable },
                    { obj: PyCallableType -> obj.callable },
                    callSite,
                    context
                )
            ) { it: PyCallableType ->
                it.getCallType(
                    context,
                    callSite
                )
            }
        )
    }

    private fun clarifyConstructorCallType(
        initOrNew: ClarifiedResolveResult,
        callSite: PyCallSiteExpression,
        context: TypeEvalContext
    ): PyType? {
        val initOrNewMethod = initOrNew.myClarifiedResolved as PyFunction
        val initOrNewClass = initOrNewMethod.containingClass
        val receiverClass = ObjectUtils.notNull(
            PyUtil.`as`(initOrNew.myOriginalResolveResult.element, PyClass::class.java),
            initOrNewClass!!
        )
        val initOrNewCallType = initOrNewMethod.getCallType(context, callSite)
        if (receiverClass !== initOrNewClass) {
            if (initOrNewCallType is PyTupleType) {
                return PyTupleType(
                    receiverClass,
                    initOrNewCallType.elementTypes,
                    initOrNewCallType.isHomogeneous
                )
            }
            if (initOrNewCallType is PyCollectionType) {
                val elementTypes = initOrNewCallType.elementTypes
                return PyCollectionTypeImpl(receiverClass, false, elementTypes)
            }
            return PyClassTypeImpl(receiverClass, false)
        }
        return if (initOrNewCallType == null) {
            PyUnionType.createWeakType(PyClassTypeImpl(receiverClass, false))
        } else null
    }

    private fun getSuperCallType(call: PyCallExpression, context: TypeEvalContext): Maybe<PyType?> {
        val callee = call.callee
        if (callee is PyReferenceExpression) {
            val must_be_super = callee.reference.resolve()
            if (must_be_super === PyBuiltinCache.getInstance(call).getClass(PyNames.SUPER)) {
                val argumentList = call.argumentList
                if (argumentList != null) {
                    val containingClass = PsiTreeUtil.getParentOfType(
                        call,
                        PyClass::class.java
                    )
                    val args = argumentList.arguments
                    if (containingClass != null && args.size > 1) {
                        val first_arg = args[0]
                        if (first_arg is PyReferenceExpression) {
                            val firstArgRef = first_arg
                            val qualifier = firstArgRef.qualifier
                            if (qualifier != null && PyNames.__CLASS__ == firstArgRef.referencedName) {
                                val qRef = qualifier.reference
                                val element = qRef?.resolve()
                                if (element is PyParameter) {
                                    val parameterList = PsiTreeUtil.getParentOfType(
                                        element,
                                        PyParameterList::class.java
                                    )
                                    if (parameterList != null && element === parameterList.parameters[0]) {
                                        return Maybe(
                                            getSuperCallTypeForArguments(
                                                context, containingClass,
                                                args[1]
                                            )
                                        )
                                    }
                                }
                            }
                            val possible_class = firstArgRef.reference.resolve()
                            if (possible_class is PyClass && possible_class.isNewStyleClass(context)) {
                                return Maybe(
                                    getSuperCallTypeForArguments(
                                        context,
                                        possible_class,
                                        args[1]
                                    )
                                )
                            }
                        }
                    } else if (call.containingFile is PyFile &&
                        (call.containingFile as PyFile).languageLevel.isPy3K && containingClass != null
                    ) {
                        return Maybe(getSuperClassUnionType(containingClass, context))
                    }
                }
            }
        }
        return Maybe()
    }

    private fun getSuperCallTypeForArguments(
        context: TypeEvalContext,
        firstClass: PyClass,
        second_arg: PyExpression?
    ): PyType? {
        // check 2nd argument, too; it should be an instance
        if (second_arg != null) {
            val second_type = context.getType(second_arg)
            if (second_type is PyClassType) {
                // imitate isinstance(second_arg, possible_class)
                val secondClass = second_type.pyClass
                if (CompletionUtilCoreImpl.getOriginalOrSelf(firstClass) === secondClass) {
                    return getSuperClassUnionType(firstClass, context)
                }
                if (secondClass.isSubclass(firstClass, context)) {
                    val nextAfterFirstInMro = StreamEx
                        .of(secondClass.getAncestorClasses(context))
                        .dropWhile { it: PyClass? -> it !== firstClass }
                        .skip(1)
                        .findFirst()
                        .orElse(null)
                    if (nextAfterFirstInMro != null) {
                        return PyClassTypeImpl(nextAfterFirstInMro, false)
                    }
                }
            }
        }
        return null
    }

    private fun getSuperClassUnionType(pyClass: PyClass, context: TypeEvalContext): PyType? {
        // TODO: this is closer to being correct than simply taking first superclass type but still not entirely correct;
        // super can also delegate to sibling types
        // TODO handle __mro__ here
        val supers = pyClass.getSuperClasses(context)
        if (supers.size > 0) {
            if (supers.size == 1) {
                return PyClassTypeImpl(supers[0], false)
            }
            val superTypes: MutableList<PyType?> = ArrayList()
            for (aSuper in supers) {
                superTypes.add(PyClassTypeImpl(aSuper, false))
            }
            return PyUnionType.union(superTypes)
        }
        return null
    }

    /**
     * Gets implicit offset from the `callableType`,
     * should be used with the methods below since they specify correct offset value.
     *
     * @see PyCallExpression.multiResolveCalleeFunction
     * @see PyCallExpression.multiResolveCallee
     */
    fun mapArguments(
        callSite: PyCallSiteExpression,
        callableType: PyCallableType,
        context: TypeEvalContext
    ): PyArgumentsMapping {
        return mapArguments(
            callSite,
            callSite.getArguments(callableType.callable),
            callableType,
            context
        )
    }

    private fun mapArguments(
        callSite: PyCallSiteExpression,
        arguments: List<PyExpression>,
        callableType: PyCallableType,
        context: TypeEvalContext
    ): PyArgumentsMapping {
        val parameters = callableType.getParameters(context)
            ?: return PyArgumentsMapping.empty(callSite)
        val safeImplicitOffset = Math.min(callableType.implicitOffset, parameters.size)
        val explicitParameters: List<PyCallableParameter> =
            parameters.subList(safeImplicitOffset, parameters.size)
        val implicitParameters: List<PyCallableParameter> =
            parameters.subList(0, safeImplicitOffset)
        val mappingResults = analyzeArguments(arguments, explicitParameters, context)
        return PyArgumentsMapping(
            callSite,
            callableType,
            implicitParameters,
            mappingResults.mappedParameters,
            mappingResults.unmappedParameters,
            mappingResults.unmappedArguments,
            mappingResults.parametersMappedToVariadicPositionalArguments,
            mappingResults.parametersMappedToVariadicKeywordArguments,
            mappingResults.mappedTupleParameters
        )
    }

    fun mapArguments(
        callSite: PyCallSiteExpression,
        resolveContext: PyResolveContext
    ): List<PyArgumentsMapping> {
        val context = resolveContext.typeEvalContext
        return ContainerUtil.map(
            multiResolveCalleeFunction(callSite, resolveContext)
        ) { type: PyCallableType ->
            mapArguments(
                callSite,
                type,
                context
            )
        }
    }

    private fun multiResolveCalleeFunction(
        callSite: PyCallSiteExpression,
        resolveContext: PyResolveContext
    ): List<PyCallableType> {
        return if (callSite is PyCallExpression) {
            callSite.multiResolveCallee(resolveContext)
        } else if (callSite is PySubscriptionExpression) {
            multiResolveCallee(callSite, resolveContext)
        } else {
            val results: MutableList<PyCallableType> = ArrayList()
            for (result in PyUtil.multiResolveTopPriority(callSite, resolveContext)) {
                if (result is PyTypedElement) {
                    val resultType = resolveContext.typeEvalContext.getType(result)
                    if (resultType is PyCallableType) {
                        results.add(resultType)
                        continue
                    }
                }
                return emptyList()
            }
            results
        }
    }

    /**
     * Tries to infer implicit offset from the `callSite` and `callable`.
     *
     * @see PyCallExpressionHelper.mapArguments
     */
    fun mapArguments(
        callSite: PyCallSiteExpression,
        callable: PyCallable,
        context: TypeEvalContext
    ): PyArgumentsMapping {
        val callableType = PyUtil.`as`(
            context.getType(callable),
            PyCallableType::class.java
        ) ?: return PyArgumentsMapping.empty(callSite)
        val parameters = callableType.getParameters(context)
            ?: return PyArgumentsMapping.empty(callSite)
        val resolveContext = PyResolveContext.defaultContext(context)
        val arguments = callSite.getArguments(callable)
        val explicitParameters =
            filterExplicitParameters(parameters, callable, callSite, resolveContext)
        val implicitParameters: List<PyCallableParameter> =
            parameters.subList(0, parameters.size - explicitParameters.size)
        val mappingResults = analyzeArguments(arguments, explicitParameters, context)
        return PyArgumentsMapping(
            callSite,
            callableType,
            implicitParameters,
            mappingResults.mappedParameters,
            mappingResults.unmappedParameters,
            mappingResults.unmappedArguments,
            mappingResults.parametersMappedToVariadicPositionalArguments,
            mappingResults.parametersMappedToVariadicKeywordArguments,
            mappingResults.mappedTupleParameters
        )
    }

    fun getArgumentsMappedToPositionalContainer(mapping: Map<PyExpression, PyCallableParameter>): List<PyExpression> {
        return StreamEx.ofKeys(
            mapping
        ) { obj: PyCallableParameter -> obj.isPositionalContainer }.toList()
    }

    fun getArgumentsMappedToKeywordContainer(mapping: Map<PyExpression, PyCallableParameter>): List<PyExpression> {
        return StreamEx.ofKeys(
            mapping
        ) { obj: PyCallableParameter -> obj.isKeywordContainer }.toList()
    }

    fun getRegularMappedParameters(mapping: Map<PyExpression, PyCallableParameter>): Map<PyExpression, PyCallableParameter> {
        val result: MutableMap<PyExpression, PyCallableParameter> = java.util.LinkedHashMap()
        for ((argument, parameter) in mapping.entries) {
            if (!parameter.isPositionalContainer && !parameter.isKeywordContainer) {
                result[argument] = parameter
            }
        }
        return result
    }

    fun getMappedPositionalContainer(mapping: Map<PyExpression?, PyCallableParameter>): PyCallableParameter? {
        return ContainerUtil.find(
            mapping.values
        ) { p: PyCallableParameter -> p.isPositionalContainer }
    }

    fun getMappedKeywordContainer(mapping: Map<PyExpression?, PyCallableParameter>): PyCallableParameter? {
        return ContainerUtil.find(
            mapping.values
        ) { p: PyCallableParameter -> p.isKeywordContainer }
    }

    fun resolveImplicitlyInvokedMethods(
        type: PyClassType,
        callSite: PyCallSiteExpression?,
        resolveContext: PyResolveContext
    ): List<RatedResolveResult> {
        return if (type.isDefinition) resolveConstructors(
            type,
            callSite,
            resolveContext
        ) else resolveDunderCall(type, callSite, resolveContext)
    }

    private fun changeToImplicitlyInvokedMethods(
        classType: PyClassType,
        implicitlyInvokedMethods: List<PsiElement>,
        call: PyCallExpression,
        context: TypeEvalContext
    ): List<PyCallableType> {
        val cls = classType.pyClass
        return (
                implicitlyInvokedMethods
                    .map {
                        ClarifiedResolveResult(
                            QualifiedRatedResolveResult(
                                cls,
                                emptyList(),
                                RatedResolveResult.RATE_NORMAL,
                                false
                            ),
                            it,
                            null,
                            PyUtil.isInitOrNewMethod(it)
                        )
                    }
                    .mapNotNull {
                        toCallableType(call, it, context)
                    }
                )
    }

    private fun resolveConstructors(
        type: PyClassType,
        location: PyExpression?,
        resolveContext: PyResolveContext
    ): List<RatedResolveResult> {
        val metaclassDunderCall = resolveMetaclassDunderCall(type, location, resolveContext)
        if (!metaclassDunderCall.isEmpty()) {
            return metaclassDunderCall
        }
        val context = resolveContext.typeEvalContext
        val initAndNew = type.pyClass.multiFindInitOrNew(true, context)
        return ContainerUtil.map(preferInitOverNew(initAndNew)) { e: PyFunction? ->
            RatedResolveResult(
                PyReferenceImpl.getRate(e, context),
                e
            )
        }
    }

    private fun preferInitOverNew(initAndNew: List<PyFunction>): Collection<PyFunction> {
        val functions = ContainerUtil.groupBy(
            initAndNew
        ) { obj: PyFunction -> obj.name }
        return if (functions.containsKey(PyNames.INIT)) functions[PyNames.INIT] else functions.values()
    }

    private fun resolveMetaclassDunderCall(
        type: PyClassType,
        location: PyExpression?,
        resolveContext: PyResolveContext
    ): List<RatedResolveResult> {
        val context = resolveContext.typeEvalContext
        val metaClassType = type.getMetaClassType(context, true)
            ?: return emptyList()
        val typeType = PyBuiltinCache.getInstance(type.pyClass).typeType
        if (metaClassType === typeType) return emptyList()
        val results = resolveDunderCall(metaClassType, location, resolveContext)
        if (results.isEmpty()) return emptyList()
        val typeDunderCall =
            if (typeType == null) emptySet<PsiElement>() else ContainerUtil.map2SetNotNull(
                resolveDunderCall(typeType, null, resolveContext)
            ) { obj: RatedResolveResult -> obj.element }
        return ContainerUtil.filter(
            results
        ) { it: RatedResolveResult ->
            val element = it.element
            !typeDunderCall.contains(element) && !ParamHelper.isSelfArgsKwargsCallable(
                element,
                context
            )
        }
    }

    private fun resolveDunderCall(
        type: PyClassLikeType,
        location: PyExpression?,
        resolveContext: PyResolveContext
    ): List<RatedResolveResult> {
        return ContainerUtil.notNullize(
            type.resolveMember(
                PyNames.CALL,
                location,
                AccessDirection.READ,
                resolveContext
            )
        )
    }

    fun analyzeArguments(
        arguments: List<PyExpression>,
        parameters: List<PyCallableParameter>,
        context: TypeEvalContext
    ): ArgumentMappingResults {
        var positionalOnlyMode = ContainerUtil.exists(
            parameters
        ) { p: PyCallableParameter -> p.parameter is PySlashParameter }
        var seenSingleStar = false
        var mappedVariadicArgumentsToParameters = false
        val mappedParameters: MutableMap<PyExpression, PyCallableParameter> =
            java.util.LinkedHashMap()
        val unmappedParameters: MutableList<PyCallableParameter> = ArrayList()
        val unmappedArguments: MutableList<PyExpression> = ArrayList()
        val parametersMappedToVariadicKeywordArguments: MutableList<PyCallableParameter> =
            ArrayList()
        val parametersMappedToVariadicPositionalArguments: MutableList<PyCallableParameter> =
            ArrayList()
        val tupleMappedParameters: MutableMap<PyExpression, PyCallableParameter> =
            java.util.LinkedHashMap()
        val positionalResults = filterPositionalAndVariadicArguments(arguments)
        val keywordArguments = filterKeywordArguments(arguments)
        val variadicPositionalArguments = positionalResults.variadicPositionalArguments
        val positionalComponentsOfVariadicArguments: Set<PyExpression> =
            LinkedHashSet(positionalResults.componentsOfVariadicPositionalArguments)
        val variadicKeywordArguments = filterVariadicKeywordArguments(arguments)
        val allPositionalArguments = positionalResults.allPositionalArguments
        for (parameter in parameters) {
            val psi = parameter.parameter
            if (psi is PyNamedParameter || psi == null) {
                val parameterName = parameter.name
                if (parameter.isPositionalContainer) {
                    for (argument in allPositionalArguments) {
                        if (argument != null) {
                            mappedParameters[argument] = parameter
                        }
                    }
                    if (variadicPositionalArguments.size == 1) {
                        mappedParameters[variadicPositionalArguments[0]] = parameter
                    }
                    allPositionalArguments.clear()
                    variadicPositionalArguments.clear()
                } else if (parameter.isKeywordContainer) {
                    for (argument in keywordArguments) {
                        mappedParameters[argument] = parameter
                    }
                    for (variadicKeywordArg in variadicKeywordArguments) {
                        mappedParameters[variadicKeywordArg] = parameter
                    }
                    keywordArguments.clear()
                    variadicKeywordArguments.clear()
                } else if (seenSingleStar) {
                    val keywordArgument: PyExpression? =
                        removeKeywordArgument(keywordArguments, parameterName)
                    if (keywordArgument != null) {
                        mappedParameters[keywordArgument] = parameter
                    } else if (variadicKeywordArguments.isEmpty()) {
                        if (!parameter.hasDefaultValue()) {
                            unmappedParameters.add(parameter)
                        }
                    } else {
                        parametersMappedToVariadicKeywordArguments.add(parameter)
                        mappedVariadicArgumentsToParameters = true
                    }
                } else if (isParamSpecOrConcatenate(parameter, context)) {
                    for (argument in arguments) {
                        mappedParameters[argument] = parameter
                    }
                    allPositionalArguments.clear()
                    keywordArguments.clear()
                    variadicPositionalArguments.clear()
                    variadicKeywordArguments.clear()
                } else {
                    if (positionalOnlyMode) {
                        val positionalArgument = next(allPositionalArguments)
                        if (positionalArgument != null) {
                            mappedParameters[positionalArgument] = parameter
                        } else if (!parameter.hasDefaultValue()) {
                            unmappedParameters.add(parameter)
                        }
                    } else if (allPositionalArguments.isEmpty()) {
                        val keywordArgument = removeKeywordArgument(keywordArguments, parameterName)
                        if (keywordArgument != null) {
                            mappedParameters[keywordArgument] = parameter
                        } else if (variadicPositionalArguments.isEmpty() && variadicKeywordArguments.isEmpty() && !parameter.hasDefaultValue()) {
                            unmappedParameters.add(parameter)
                        } else {
                            if (!variadicPositionalArguments.isEmpty()) {
                                parametersMappedToVariadicPositionalArguments.add(parameter)
                            }
                            if (!variadicKeywordArguments.isEmpty()) {
                                parametersMappedToVariadicKeywordArguments.add(parameter)
                            }
                            mappedVariadicArgumentsToParameters = true
                        }
                    } else {
                        val positionalArgument = next(allPositionalArguments)
                        if (positionalArgument != null) {
                            mappedParameters[positionalArgument] = parameter
                            if (positionalComponentsOfVariadicArguments.contains(positionalArgument)) {
                                parametersMappedToVariadicPositionalArguments.add(parameter)
                            }
                        } else if (!parameter.hasDefaultValue()) {
                            unmappedParameters.add(parameter)
                        }
                    }
                }
            } else if (psi is PyTupleParameter) {
                val positionalArgument = next(allPositionalArguments)
                if (positionalArgument != null) {
                    tupleMappedParameters[positionalArgument] = parameter
                    val tupleMappingResults = mapComponentsOfTupleParameter(
                        positionalArgument,
                        psi
                    )
                    mappedParameters.putAll(tupleMappingResults.parameters)
                    unmappedParameters.addAll(tupleMappingResults.unmappedParameters)
                    unmappedArguments.addAll(tupleMappingResults.unmappedArguments)
                } else if (variadicPositionalArguments.isEmpty()) {
                    if (!parameter.hasDefaultValue()) {
                        unmappedParameters.add(parameter)
                    }
                } else {
                    mappedVariadicArgumentsToParameters = true
                }
            } else if (psi is PySlashParameter) {
                positionalOnlyMode = false
            } else if (psi is PySingleStarParameter) {
                seenSingleStar = true
            } else if (!parameter.hasDefaultValue()) {
                unmappedParameters.add(parameter)
            }
        }
        if (mappedVariadicArgumentsToParameters) {
            variadicPositionalArguments.clear()
            variadicKeywordArguments.clear()
        }
        unmappedArguments.addAll(allPositionalArguments)
        unmappedArguments.addAll(keywordArguments)
        unmappedArguments.addAll(variadicPositionalArguments)
        unmappedArguments.addAll(variadicKeywordArguments)
        return ArgumentMappingResults(
            mappedParameters,
            unmappedParameters,
            unmappedArguments,
            parametersMappedToVariadicPositionalArguments,
            parametersMappedToVariadicKeywordArguments,
            tupleMappedParameters
        )
    }

    private fun isParamSpecOrConcatenate(
        parameter: PyCallableParameter,
        context: TypeEvalContext
    ): Boolean {
        val type = parameter.getType(context)
        return type is PyParamSpecType || type is PyConcatenateType
    }

    private fun forEveryScopeTakeOverloadsOtherwiseImplementations(
        results: List<ResolveResult>,
        context: TypeEvalContext
    ): List<PsiElement> {
        return PyUtil.filterTopPriorityElements(
            forEveryScopeTakeOverloadsOtherwiseImplementations(
                results,
                { obj: ResolveResult -> obj.element },
                context
            )
        )
    }

    private fun <E> forEveryScopeTakeOverloadsOtherwiseImplementations(
        elements: List<E>,
        mapper: Function<in E, PsiElement?>,
        context: TypeEvalContext
    ): List<E> {
        return if (!containsOverloadsAndImplementations(elements, mapper, context)) {
            elements
        } else StreamEx
            .of(elements)
            .groupingBy<Optional<ScopeOwner>, List<E>, LinkedHashMap<Optional<ScopeOwner>, List<E>>>(
                { element: E ->
                    Optional.ofNullable(
                        ScopeUtil.getScopeOwner(mapper.apply(element))
                    )
                },
                { LinkedHashMap() }, Collectors.toList()
            )
            .values
            .stream()
            .flatMap { oneScopeElements: List<E> ->
                takeOverloadsOtherwiseImplementations(
                    oneScopeElements,
                    mapper,
                    context
                )
            }
            .collect(Collectors.toList())
    }

    private fun <E> containsOverloadsAndImplementations(
        elements: Collection<E>,
        mapper: Function<in E, PsiElement?>,
        context: TypeEvalContext
    ): Boolean {
        var containsOverloads = false
        var containsImplementations = false
        for (element in elements) {
            val mapped = mapper.apply(element) ?: continue
            val overload = PyiUtil.isOverload(mapped, context)
            containsOverloads = containsOverloads or overload
            containsImplementations = containsImplementations or !overload
            if (containsOverloads && containsImplementations) return true
        }
        return false
    }

    private fun <E> takeOverloadsOtherwiseImplementations(
        elements: List<E>,
        mapper: Function<in E, PsiElement?>,
        context: TypeEvalContext
    ): Stream<E> {
        return if (!containsOverloadsAndImplementations(elements, mapper, context)) {
            elements.stream()
        } else elements
            .stream()
            .filter { element: E ->
                val mapped = mapper.apply(element)
                mapped != null && (PyiUtil.isInsideStub(mapped) || PyiUtil.isOverload(
                    mapped,
                    context
                ))
            }
    }

    private fun <E> dropNotMatchedOverloadsOrLeaveAsIs(
        elements: List<E>,
        mapper: Function<in E, PsiElement?>,
        callSite: PyCallSiteExpression,
        context: TypeEvalContext
    ): List<E> {
        val filtered = ContainerUtil.filter(
            elements
        ) { it: E ->
            !notMatchedOverload(
                mapper.apply(it),
                callSite,
                context
            )
        }
        return if (filtered.isEmpty()) elements else filtered
    }

    private fun notMatchedOverload(
        element: PsiElement?,
        callSite: PyCallSiteExpression,
        context: TypeEvalContext
    ): Boolean {
        if (element == null || !PyiUtil.isOverload(element, context)) return false
        val overload = element as PyFunction
        val fullMapping = mapArguments(callSite, overload, context)
        if (!fullMapping.unmappedArguments.isEmpty() || !fullMapping.unmappedParameters.isEmpty()) {
            return true
        }
        val receiver = callSite.getReceiver(overload)
        val mappedExplicitParameters = fullMapping.mappedParameters
        val allMappedParameters: MutableMap<PyExpression, PyCallableParameter> =
            java.util.LinkedHashMap()
        val firstImplicit = ContainerUtil.getFirstItem(fullMapping.implicitParameters)
        if (receiver != null && firstImplicit != null) {
            allMappedParameters[receiver] = firstImplicit
        }
        allMappedParameters.putAll(mappedExplicitParameters)
        return PyTypeChecker.unifyGenericCallWithParamSpecs(
            receiver,
            allMappedParameters,
            context
        ) == null
    }

    private fun mapComponentsOfTupleParameter(
        argument: PyExpression?,
        parameter: PyTupleParameter
    ): TupleMappingResults {
        var argument = argument
        val unmappedParameters: MutableList<PyCallableParameter> = ArrayList()
        val unmappedArguments: MutableList<PyExpression> = ArrayList()
        val mappedParameters: MutableMap<PyExpression, PyCallableParameter> =
            java.util.LinkedHashMap()
        argument = PyPsiUtils.flattenParens(argument)
        if (argument is PySequenceExpression) {
            val argumentComponents = argument.elements
            val parameterComponents = parameter.contents
            for (i in parameterComponents.indices) {
                val param = parameterComponents[i]
                if (i < argumentComponents.size) {
                    val arg = argumentComponents[i]
                    if (arg != null) {
                        if (param is PyNamedParameter) {
                            mappedParameters[arg] = PyCallableParameterImpl.psi(param)
                        } else if (param is PyTupleParameter) {
                            val nestedResults = mapComponentsOfTupleParameter(
                                arg,
                                param
                            )
                            mappedParameters.putAll(nestedResults.parameters)
                            unmappedParameters.addAll(nestedResults.unmappedParameters)
                            unmappedArguments.addAll(nestedResults.unmappedArguments)
                        } else {
                            unmappedArguments.add(arg)
                        }
                    } else {
                        unmappedParameters.add(PyCallableParameterImpl.psi(param))
                    }
                } else {
                    unmappedParameters.add(PyCallableParameterImpl.psi(param))
                }
            }
            if (argumentComponents.size > parameterComponents.size) {
                for (i in parameterComponents.size until argumentComponents.size) {
                    val arg = argumentComponents[i]
                    if (arg != null) {
                        unmappedArguments.add(arg)
                    }
                }
            }
        }
        return TupleMappingResults(mappedParameters, unmappedParameters, unmappedArguments)
    }

    private fun removeKeywordArgument(
        arguments: MutableList<PyKeywordArgument>,
        name: String?
    ): PyKeywordArgument? {
        var result: PyKeywordArgument? = null
        for (argument in arguments) {
            val keyword = argument.keyword
            if (keyword != null && keyword == name) {
                result = argument
                break
            }
        }
        if (result != null) {
            arguments.remove(result)
        }
        return result
    }

    private fun filterKeywordArguments(arguments: List<PyExpression>): MutableList<PyKeywordArgument> {
        val results: MutableList<PyKeywordArgument> = ArrayList()
        for (argument in arguments) {
            if (argument is PyKeywordArgument) {
                results.add(argument)
            }
        }
        return results
    }

    private fun filterPositionalAndVariadicArguments(arguments: List<PyExpression>): PositionalArgumentsAnalysisResults {
        val variadicArguments: MutableList<PyExpression> = ArrayList()
        val allPositionalArguments: MutableList<PyExpression> = ArrayList()
        val componentsOfVariadicPositionalArguments: MutableList<PyExpression> = ArrayList()
        var seenVariadicPositionalArgument = false
        var seenVariadicKeywordArgument = false
        var seenKeywordArgument = false
        for (argument in arguments) {
            if (argument is PyStarArgument) {
                if (argument.isKeyword) {
                    seenVariadicKeywordArgument = true
                } else {
                    seenVariadicPositionalArgument = true
                    val expr: PsiElement? = PyPsiUtils.flattenParens(
                        PsiTreeUtil.getChildOfType(
                            argument,
                            PyExpression::class.java
                        )
                    )
                    if (expr is PySequenceExpression) {
                        val elements = Arrays.asList(*expr.elements)
                        allPositionalArguments.addAll(elements)
                        componentsOfVariadicPositionalArguments.addAll(elements)
                    } else {
                        variadicArguments.add(argument)
                    }
                }
            } else if (argument is PyKeywordArgument) {
                seenKeywordArgument = true
            } else {
                if (seenKeywordArgument ||
                    seenVariadicKeywordArgument || seenVariadicPositionalArgument && LanguageLevel.forElement(
                        argument
                    ).isOlderThan(
                        LanguageLevel.PYTHON35
                    )
                ) {
                    continue
                }
                allPositionalArguments.add(argument)
            }
        }
        return PositionalArgumentsAnalysisResults(
            allPositionalArguments,
            componentsOfVariadicPositionalArguments,
            variadicArguments
        )
    }

    private fun filterVariadicKeywordArguments(arguments: List<PyExpression>): MutableList<PyExpression> {
        val results: MutableList<PyExpression> = ArrayList()
        for (argument in arguments) {
            if (argument != null && isVariadicKeywordArgument(argument)) {
                results.add(argument)
            }
        }
        return results
    }

    fun isVariadicKeywordArgument(argument: PyExpression): Boolean {
        return argument is PyStarArgument && argument.isKeyword
    }

    fun isVariadicPositionalArgument(argument: PyExpression): Boolean {
        return argument is PyStarArgument && !argument.isKeyword
    }

    private fun <T> next(list: MutableList<T>): T? {
        return if (list.isEmpty()) null else list.removeAt(0)
    }

    private fun filterExplicitParameters(
        parameters: List<PyCallableParameter>,
        callable: PyCallable?,
        callSite: PyCallSiteExpression,
        resolveContext: PyResolveContext
    ): List<PyCallableParameter> {
        val implicitOffset: Int
        implicitOffset = if (callSite is PyCallExpression) {
            val callee = callSite.callee
            if (callee is PyReferenceExpression && callable is PyFunction) {
                getImplicitArgumentCount(
                    callee, callable,
                    resolveContext
                )
            } else {
                0
            }
        } else {
            1
        }
        return parameters.subList(Math.min(implicitOffset, parameters.size), parameters.size)
    }

    fun canQualifyAnImplicitName(qualifier: PyExpression): Boolean {
        if (qualifier is PyCallExpression) {
            val callee = qualifier.callee
            if (callee is PyReferenceExpression && PyNames.SUPER == callee.getName()) {
                val target = (callee as PyReferenceExpression).reference.resolve()
                if (target != null && PyBuiltinCache.getInstance(qualifier)
                        .isBuiltin(target)
                ) return false // super() of unresolved type
            }
        }
        return true
    }

    class ArgumentMappingResults internal constructor(
        val mappedParameters: Map<PyExpression, PyCallableParameter>,
        val unmappedParameters: List<PyCallableParameter>,
        val unmappedArguments: List<PyExpression>,
        val parametersMappedToVariadicPositionalArguments: List<PyCallableParameter>,
        val parametersMappedToVariadicKeywordArguments: List<PyCallableParameter>,
        val mappedTupleParameters: Map<PyExpression, PyCallableParameter>
    )

    private class TupleMappingResults internal constructor(
        val parameters: Map<PyExpression, PyCallableParameter>,
        val unmappedParameters: List<PyCallableParameter>,
        val unmappedArguments: List<PyExpression>
    )

    private class PositionalArgumentsAnalysisResults internal constructor(
        val allPositionalArguments: MutableList<PyExpression>,
        val componentsOfVariadicPositionalArguments: List<PyExpression>,
        val variadicPositionalArguments: MutableList<PyExpression>
    )

    private class ClarifiedResolveResult internal constructor(
        val myOriginalResolveResult: QualifiedRatedResolveResult,
        val myClarifiedResolved: PsiElement,
        val myWrappedModifier: PyFunction.Modifier?,
        val myIsConstructor: Boolean
    )
}
