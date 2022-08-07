package com.y4kstudios.pycharmtypingimp

import org.junit.Test

class DescriptorTypeProviderTest : PyTypingTestCase() {
    @Test
    fun testDescriptorGet() {
        doTest("float", """
            class Attr:
                def __get__(self, instance, owner) -> float: ...

            class Foo:
                my_attr = Attr()

            foo = Foo()
            foo.my_<caret>attr
        """.trimIndent())
    }

    @Test
    fun testSubclassedDescriptorGet() {
        doTest("int", """
            class Attr:
                def __get__(self, instance, owner) -> float: ...

            class AttrChild(Attr):
                def __get__(self, instance, owner) -> int: ...

            class Foo:
                my_attr = AttrChild()

            foo = Foo()
            foo.my_<caret>attr
        """.trimIndent())
    }

    @Test
    fun testSelfTypedDescriptorOwnerGet() {
        doTest("Attr", """
            from typing import TypeVar

            AttrT = TypeVar('AttrT', bound='Attr')

            class Attr:
                def __get__(self: AttrT, instance, owner) -> AttrT: ...

            class Foo:
                my_attr = Attr()

            Foo.my_<caret>attr
        """.trimIndent())
    }

    @Test
    fun testSelfTypedDescriptorGet() {
        doTest("Attr", """
            from typing import TypeVar

            AttrT = TypeVar('AttrT', bound='Attr')

            class Attr:
                def __get__(self: AttrT, instance, owner) -> AttrT: ...

            class Foo:
                my_attr = Attr()

            foo = Foo()
            foo.my_<caret>attr
        """.trimIndent())
    }

    @Test
    fun testSelfTypedSubclassedDescriptorGet() {
        doTest("AttrChild", """
            from typing import TypeVar

            AttrT = TypeVar('AttrT', bound='Attr')

            class Attr:
                def __get__(self: AttrT, instance, owner) -> AttrT: ...

            class AttrChild(Attr):
                pass

            class Foo:
                my_attr = AttrChild()

            foo = Foo()
            foo.my_<caret>attr
        """.trimIndent())
    }

    @Test
    fun testSelfTypedFunctionCall() {
        doTest("Foo", """
            from typing import TypeVar

            T = TypeVar('T')

            class Foo:
                def my_fun(self: T) -> T: ...

            foo = Foo()
            rval = foo.my_fun()
            rv<caret>al
        """.trimIndent())
    }
}
