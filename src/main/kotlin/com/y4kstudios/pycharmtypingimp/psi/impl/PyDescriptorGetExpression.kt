package com.y4kstudios.pycharmtypingimp.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.util.castSafelyTo
import com.jetbrains.python.psi.PyCallSiteExpression
import com.jetbrains.python.psi.PyCallable
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import javax.swing.Icon


/**
 * An implicit PyCallSiteExpression for descriptor __get__ accesses, allowing generic substitutions on the __get__ call
 */
class PyDescriptorGetExpression(private val referenceExpression: PyReferenceExpression) : PyCallSiteExpression {
    override fun getReceiver(resolvedCallee: PyCallable?): PyExpression? {
        val context = TypeEvalContext.codeAnalysis(referenceExpression.project, referenceExpression.containingFile)
        val resolveContext = PyResolveContext.noProperties(context)
        return referenceExpression.followAssignmentsChain(resolveContext).element.castSafelyTo<PyExpression>()
    }

    override fun getArguments(resolvedCallee: PyCallable?): MutableList<PyExpression> {
        return mutableListOf()
    }

    override fun <T : Any?> getUserData(key: Key<T>): T? = referenceExpression.getUserData(key)
    override fun <T : Any?> putUserData(key: Key<T>, value: T?) { referenceExpression.putUserData(key, value) }
    override fun getIcon(flags: Int): Icon = referenceExpression.getIcon(flags)
    override fun getProject(): Project = referenceExpression.project
    override fun getLanguage(): Language = referenceExpression.language
    override fun getManager(): PsiManager = referenceExpression.manager
    override fun getChildren(): Array<PsiElement> = referenceExpression.children
    override fun getParent(): PsiElement = referenceExpression.parent
    override fun getFirstChild(): PsiElement = referenceExpression.firstChild
    override fun getLastChild(): PsiElement = referenceExpression.lastChild
    override fun getNextSibling(): PsiElement = referenceExpression.nextSibling
    override fun getPrevSibling(): PsiElement = referenceExpression.prevSibling
    override fun getContainingFile(): PsiFile = referenceExpression.containingFile
    override fun getTextRange(): TextRange = referenceExpression.textRange
    override fun getStartOffsetInParent(): Int = referenceExpression.startOffsetInParent
    override fun getTextLength(): Int = referenceExpression.textLength
    override fun findElementAt(offset: Int): PsiElement? = referenceExpression.findElementAt(offset)
    override fun findReferenceAt(offset: Int): PsiReference? = referenceExpression.findReferenceAt(offset)
    override fun getTextOffset(): Int = referenceExpression.textOffset
    override fun getText(): String = referenceExpression.text
    override fun textToCharArray(): CharArray = referenceExpression.textToCharArray()
    override fun getNavigationElement(): PsiElement = referenceExpression.navigationElement
    override fun getOriginalElement(): PsiElement = referenceExpression.originalElement
    override fun textMatches(text: CharSequence): Boolean = referenceExpression.textMatches(text)
    override fun textMatches(element: PsiElement): Boolean = referenceExpression.textMatches(element)
    override fun textContains(c: Char): Boolean = referenceExpression.textContains(c)
    override fun accept(visitor: PsiElementVisitor) { referenceExpression.accept(visitor) }
    override fun acceptChildren(visitor: PsiElementVisitor) { referenceExpression.acceptChildren(visitor) }
    override fun copy(): PsiElement = referenceExpression.copy()
    override fun add(element: PsiElement): PsiElement = referenceExpression.add(element)
    override fun addBefore(element: PsiElement, anchor: PsiElement?): PsiElement = referenceExpression.addBefore(element, anchor)
    override fun addAfter(element: PsiElement, anchor: PsiElement?): PsiElement = referenceExpression.addAfter(element, anchor)
    @Deprecated("Deprecated in Java")
    override fun checkAdd(element: PsiElement) { referenceExpression.checkAdd(element) }
    override fun addRange(first: PsiElement?, last: PsiElement?): PsiElement { return referenceExpression.addRange(first, last) }
    override fun addRangeBefore(first: PsiElement, last: PsiElement, anchor: PsiElement?): PsiElement = referenceExpression.addRangeBefore(first, last, anchor)
    override fun addRangeAfter(first: PsiElement?, last: PsiElement?, anchor: PsiElement?): PsiElement = referenceExpression.addRangeAfter(first, last, anchor)
    override fun delete() { referenceExpression.delete() }
    @Deprecated("Deprecated in Java")
    override fun checkDelete() { referenceExpression.checkDelete() }
    override fun deleteChildRange(first: PsiElement?, last: PsiElement?) { referenceExpression.deleteChildRange(first, last) }
    override fun replace(newElement: PsiElement): PsiElement = referenceExpression.replace(newElement)
    override fun isValid(): Boolean = referenceExpression.isValid
    override fun isWritable(): Boolean = referenceExpression.isWritable
    override fun getReference(): PsiReference = referenceExpression.reference
    override fun getReferences(): Array<PsiReference> = referenceExpression.references
    override fun <T : Any?> getCopyableUserData(key: Key<T>): T? = referenceExpression.getCopyableUserData(key)
    override fun <T : Any?> putCopyableUserData(key: Key<T>, value: T?) { referenceExpression.putCopyableUserData(key, value) }
    override fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement?, place: PsiElement): Boolean = referenceExpression.processDeclarations(processor, state, lastParent, place)
    override fun getContext(): PsiElement? = referenceExpression.context
    override fun isPhysical(): Boolean = referenceExpression.isPhysical
    override fun getResolveScope(): GlobalSearchScope = referenceExpression.resolveScope
    override fun getUseScope(): SearchScope = referenceExpression.useScope
    override fun getNode(): ASTNode = referenceExpression.node
    override fun isEquivalentTo(another: PsiElement?): Boolean = referenceExpression.isEquivalentTo(another)
    override fun navigate(requestFocus: Boolean) { referenceExpression.navigate(requestFocus) }
    override fun canNavigate(): Boolean = referenceExpression.canNavigate()
    override fun canNavigateToSource(): Boolean = referenceExpression.canNavigateToSource()
    override fun getName(): String? = referenceExpression.name
    override fun getPresentation(): ItemPresentation? = referenceExpression.presentation
    override fun getType(context: TypeEvalContext, key: TypeEvalContext.Key): PyType? = referenceExpression.getType(context, key)
}
