package com.rteats.mpeineo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsHtmlParserTest {
    @Test fun parsesMarksWithoutWebView() {
        val html = """
            <div id="div-Student_SemesterSheet__Mark">
              <div class="my-2">Алгебра, Иванов, экзамен, 2026</div>
              <div id="s_ss_0" class="collapse"><table>
                <tr><td>Лабораторная</td><td>1</td><td>2</td><td>5</td></tr>
                <tr><td>Итоговая оценка</td><td>5</td></tr>
              </table></div>
            </div>
        """.trimIndent()
        val data = BarsHtmlParser.parseMain(html)
        assertEquals(1, data.disciplines.size)
        assertEquals("Алгебра", data.disciplines[0].disciplineName)
        assertEquals("5", data.disciplines[0].finalMark)
        assertTrue(BarsHtmlParser.mainReport(html).contains("Лабораторная"))
    }

    @Test(expected = IllegalStateException::class)
    fun loginPageCannotBeParsedAsGrades() {
        BarsHtmlParser.parseMain("<form><input name='Password'></form>")
    }
}
