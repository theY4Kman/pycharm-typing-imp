package com.y4kstudios.pycharmtypingimp

import org.junit.Test

class DictItemsViewTypeProviderTest : PyTypingTestCase() {

    @Test
    fun testDictItemsViewDictifiedType() {
        doTest("dict[str, float]", """
            d: dict[str, float]
            dictified = dict(d.items())
            dict<caret>ified
        """.trimIndent())
    }

    // NOTE: this test is expected to fail, until `PyCollectionTypeImpl.getIteratedItemType()` is fixed
    //       see #com.y4kstudios.pycharmtypingimp.psi.types.DictItemsViewTypeProvider.getCallType for more info
    @Test
    fun testDictItemsViewTuplifiedType() {
        doTest("tuple[tuple[str, float], ...]", """
            d: dict[str, float]
            tuplified = tuple(d.items())
            tupl<caret>ified
        """.trimIndent())
    }

    @Test
    fun testDictItemsViewListifiedType() {
        doTest("list[tuple[str, float]]", """
            d: dict[str, float]
            listified = list(d.items())
            list<caret>ified
        """.trimIndent())
    }

    @Test
    fun testDictKeysViewListifiedType() {
        doTest("list[str]", """
            d: dict[str, float]
            listified = list(d.keys())
            list<caret>ified
        """.trimIndent())
    }

    @Test
    fun testDictValuesViewListifiedType() {
        doTest("list[float]", """
            d: dict[str, float]
            listified = list(d.values())
            list<caret>ified
        """.trimIndent())
    }
}
