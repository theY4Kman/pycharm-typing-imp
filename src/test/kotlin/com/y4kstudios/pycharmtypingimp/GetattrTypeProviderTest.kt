package com.y4kstudios.pycharmtypingimp

import com.intellij.psi.util.parentOfType
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.TypeEvalContext
import org.intellij.lang.annotations.Language
import org.junit.Test

class GetattrTypeProviderTest : PyTestCase() {
    @Test
    fun testRegularAttr() {
        doTest("float", """
            class Foo:
                existent: float

            foo = Foo()
            foo.exist<caret>ent
        """.trimIndent())
    }

    @Test
    fun testGetattr() {
        doTest("int", """
            class Foo:
                def __getattr__(self, item) -> int: ...

            foo = Foo()
            foo.nonexist<caret>ent
        """.trimIndent())
    }

    @Test
    fun testRegularAttrAlongsideGetattr() {
        doTest("float", """
            class Foo:
                existent: float
                def __getattr__(self, item) -> int: ...

            foo = Foo()
            foo.exist<caret>ent
        """.trimIndent())
    }

    @Test
    fun testGetattribute() {
        doTest("int", """
            class Foo:
                def __getattribute__(self, item) -> int: ...

            foo = Foo()
            foo.nonexist<caret>ent
        """.trimIndent())
    }

    @Test
    fun testRegularAttrAlongsideGetattribute() {
        doTest("float", """
            class Foo:
                existent: float
                def __getattribute__(self, item) -> int: ...

            foo = Foo()
            foo.exist<caret>ent
        """.trimIndent())
    }

    private fun doTest(expectedType: String, @Language("Python") testFile: String) {
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
