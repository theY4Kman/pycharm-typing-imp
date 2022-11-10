package com.y4kstudios.pycharmtypingimp

import org.junit.Test

class DictValuesViewTypeProviderTest : PyTypingTestCase() {
    @Test
    fun testDictKeysViewIterType() {
        doTest("str", """
            d: dict[str, float]
            for key in d.keys():
                print(ke<caret>y)
        """.trimIndent())
    }

    @Test
    fun testDictKeysViewIterComprehensionType() {
        doTest("str", """
            d: dict[str, float]
            l = [k for k in d.keys()]
            it<caret>em = l[0]
        """.trimIndent())
    }

    @Test
    fun testDictValuesViewIterType() {
        doTest("float", """
            d: dict[str, float]
            for value in d.values():
                print(va<caret>lue)
        """.trimIndent())
    }

    @Test
    fun testDictValuesViewIterComprehensionType() {
        doTest("float", """
            d: dict[str, float]
            l = [v for v in d.values()]
            it<caret>em = l[0]
        """.trimIndent())
    }

    @Test
    fun testDictItemsViewIterType() {
        doTest("tuple[str, float]", """
            d: dict[str, float]
            for item in d.items():
                print(it<caret>em)
        """.trimIndent())
    }

    @Test
    fun testDictItemsViewIterComprehensionType() {
        doTest("tuple[str, float]", """
            d: dict[str, float]
            l = [item for item in d.items()]
            it<caret>em = l[0]
        """.trimIndent())
    }
}
