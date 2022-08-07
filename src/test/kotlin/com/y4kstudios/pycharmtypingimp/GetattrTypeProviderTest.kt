package com.y4kstudios.pycharmtypingimp

import org.junit.Test

class GetattrTypeProviderTest : PyTypingTestCase() {
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
}
