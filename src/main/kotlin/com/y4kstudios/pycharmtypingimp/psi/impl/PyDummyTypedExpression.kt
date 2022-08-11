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
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import javax.swing.Icon

/**
 * A dummy PyExpression exhibiting all properties of `anchor`, but with a static `type`
 */
class PyDummyTypedExpression(val anchor: PyElement, val type: PyType?) : PyExpression {
    override fun getType(context: TypeEvalContext, key: TypeEvalContext.Key) = type

    override fun <T : Any?> getUserData(key: Key<T>): T? = anchor.getUserData(key)
    override fun <T : Any?> putUserData(key: Key<T>, value: T?) { anchor.putUserData(key, value) }
    override fun getIcon(flags: Int): Icon = anchor.getIcon(flags)
    override fun getProject(): Project = anchor.project
    override fun getLanguage(): Language = anchor.language
    override fun getManager(): PsiManager = anchor.manager
    override fun getChildren(): Array<PsiElement> = anchor.children
    override fun getParent(): PsiElement = anchor.parent
    override fun getFirstChild(): PsiElement = anchor.firstChild
    override fun getLastChild(): PsiElement = anchor.lastChild
    override fun getNextSibling(): PsiElement = anchor.nextSibling
    override fun getPrevSibling(): PsiElement = anchor.prevSibling
    override fun getContainingFile(): PsiFile = anchor.containingFile
    override fun getTextRange(): TextRange = anchor.textRange
    override fun getStartOffsetInParent(): Int = anchor.startOffsetInParent
    override fun getTextLength(): Int = anchor.textLength
    override fun findElementAt(offset: Int): PsiElement? = anchor.findElementAt(offset)
    override fun findReferenceAt(offset: Int): PsiReference? = anchor.findReferenceAt(offset)
    override fun getTextOffset(): Int = anchor.textOffset
    override fun getText(): String = anchor.text
    override fun textToCharArray(): CharArray = anchor.textToCharArray()
    override fun getNavigationElement(): PsiElement = anchor.navigationElement
    override fun getOriginalElement(): PsiElement = anchor.originalElement
    override fun textMatches(text: CharSequence): Boolean = anchor.textMatches(text)
    override fun textMatches(element: PsiElement): Boolean = anchor.textMatches(element)
    override fun textContains(c: Char): Boolean = anchor.textContains(c)
    override fun accept(visitor: PsiElementVisitor) { anchor.accept(visitor) }
    override fun acceptChildren(visitor: PsiElementVisitor) { anchor.acceptChildren(visitor) }
    override fun copy(): PsiElement = anchor.copy()
    override fun add(element: PsiElement): PsiElement = anchor.add(element)
    override fun addBefore(element: PsiElement, anchor: PsiElement?): PsiElement = this.anchor.addBefore(element, anchor)
    override fun addAfter(element: PsiElement, anchor: PsiElement?): PsiElement = this.anchor.addAfter(element, anchor)
    @Deprecated("Deprecated in Java")
    override fun checkAdd(element: PsiElement) { anchor.checkAdd(element) }
    override fun addRange(first: PsiElement?, last: PsiElement?): PsiElement { return anchor.addRange(first, last) }
    override fun addRangeBefore(first: PsiElement, last: PsiElement, anchor: PsiElement?): PsiElement = this.anchor.addRangeBefore(first, last, anchor)
    override fun addRangeAfter(first: PsiElement?, last: PsiElement?, anchor: PsiElement?): PsiElement = this.anchor.addRangeAfter(first, last, anchor)
    override fun delete() { anchor.delete() }
    @Deprecated("Deprecated in Java")
    override fun checkDelete() { anchor.checkDelete() }
    override fun deleteChildRange(first: PsiElement?, last: PsiElement?) { anchor.deleteChildRange(first, last) }
    override fun replace(newElement: PsiElement): PsiElement = anchor.replace(newElement)
    override fun isValid(): Boolean = anchor.isValid
    override fun isWritable(): Boolean = anchor.isWritable
    override fun getReference(): PsiReference? = anchor.reference
    override fun getReferences(): Array<PsiReference> = anchor.references
    override fun <T : Any?> getCopyableUserData(key: Key<T>): T? = anchor.getCopyableUserData(key)
    override fun <T : Any?> putCopyableUserData(key: Key<T>, value: T?) { anchor.putCopyableUserData(key, value) }
    override fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement?, place: PsiElement): Boolean = anchor.processDeclarations(processor, state, lastParent, place)
    override fun getContext(): PsiElement? = anchor.context
    override fun isPhysical(): Boolean = anchor.isPhysical
    override fun getResolveScope(): GlobalSearchScope = anchor.resolveScope
    override fun getUseScope(): SearchScope = anchor.useScope
    override fun getNode(): ASTNode = anchor.node
    override fun isEquivalentTo(another: PsiElement?): Boolean = anchor.isEquivalentTo(another)
    override fun navigate(requestFocus: Boolean) { anchor.navigate(requestFocus) }
    override fun canNavigate(): Boolean = anchor.canNavigate()
    override fun canNavigateToSource(): Boolean = anchor.canNavigateToSource()
    override fun getName(): String? = anchor.name
    override fun getPresentation(): ItemPresentation? = anchor.presentation
}
