package com.y4kstudios.pycharmtypingimp

import org.junit.Test

class DictViewsTypeProviderTest : PyTypingTestCase() {
    @Test
    fun testDictViewIterType() {
        doTest("float", """
            d: dict[str, float]
            for value in d.values():
                print(va<caret>lue)
        """.trimIndent())
    }

    @Test
    fun testDictViewIterComprehensionType() {
        doTest("float", """
            d: dict[str, float]
            l = [v for v in d.values()]
            it<caret>em = l[0]
        """.trimIndent())
    }
}
