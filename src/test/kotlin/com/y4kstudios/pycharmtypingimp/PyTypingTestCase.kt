package com.y4kstudios.pycharmtypingimp

import com.intellij.psi.util.parentOfType
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.TypeEvalContext
import org.intellij.lang.annotations.Language

open class PyTypingTestCase : PyTestCase() {
    protected fun doTest(expectedType: String, @Language("Python") testFile: String) {
        myFixture.configureByText(PythonFileType.INSTANCE, testFile.trimIndent())
        val element = elementAtCaret
        val expr = element as? PyTypedElement ?: element.parentOfType()
        assertNotNull("No element at caret", expr)
        expr ?: return

        val codeAnalysis = TypeEvalContext.codeAnalysis(expr.project, expr.containingFile)
        val userInitiated = TypeEvalContext.userInitiated(expr.project, expr.containingFile).withTracing()
        assertType("Failed in code analysis context", expectedType, expr, codeAnalysis)
        assertType("Failed in user initiated context", expectedType, expr, userInitiated)
    }
}
