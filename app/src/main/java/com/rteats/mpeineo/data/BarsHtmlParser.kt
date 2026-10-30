package com.rteats.mpeineo.data

import com.rteats.mpeineo.ui.BarsActivity
import com.rteats.mpeineo.ui.BarsActivityType
import com.rteats.mpeineo.ui.BarsDiscipline
import com.rteats.mpeineo.ui.BarsExtractionPayload
import com.rteats.mpeineo.ui.BarsStudentDetail
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * BARS renders its study endpoints as server HTML, even for AJAX fragments.
 * This parser is independent of Android WebView and executes no page scripts.
 * Never log or persist the original HTML (it contains personal student data).
 */
internal object BarsHtmlParser {
    private fun clean(text: String): String = text.replace(Regex("\\s+"), " ").trim()

    private fun row(row: Element): BarsActivity {
        val columns = row.select("td").map { clean(it.text()) }
        if (columns.isEmpty()) return BarsActivity()
        if (columns.size == 4) return BarsActivity(
            type = BarsActivityType.CONTROL_ACTIVITY,
            name = columns[0].replace(Regex("\\(критерии\\)\\s*$", RegexOption.IGNORE_CASE), "").trim(),
            weight = columns[1].ifBlank { null },
            weekNum = columns[2].ifBlank { null },
            markAndDate = columns[3].ifBlank { null },
        )
        val name = columns[0]
        val type = when {
            name.startsWith("Балл текущего") -> BarsActivityType.CURRENT_SCORE
            name.startsWith("Контрольная неделя №") -> BarsActivityType.CONTROL_WEEK
            name.startsWith("Промежуточная") -> BarsActivityType.INTERMEDIATE_MARK
            name.startsWith("Итоговая") -> BarsActivityType.FINAL_MARK
            else -> BarsActivityType.UNDEFINED
        }
        return BarsActivity(
            type = type,
            name = name,
            weekNum = if (type == BarsActivityType.CONTROL_WEEK) columns.getOrNull(1) else null,
            markAndDate = columns.getOrNull(if (type == BarsActivityType.CONTROL_WEEK) 2 else 1),
        )
    }

    fun parseMain(html: String): BarsExtractionPayload {
        val document = Jsoup.parse(html)
        val container = document.getElementById("div-Student_SemesterSheet__Mark")
            ?: error("BARS marks container missing")
        val disciplines = container.select("div.collapse[id^=s_ss_]").mapNotNull { block ->
            val heading = block.previousElementSibling() ?: return@mapNotNull null
            val parts = clean(heading.text()).split(",").map(::clean).filter(String::isNotEmpty)
            if (parts.isEmpty()) return@mapNotNull null
            val assessment = parts.indexOfLast {
                Regex("экзамен|зач[её]т|аттестаци|дифференц", RegexOption.IGNORE_CASE)
                    .containsMatchIn(it)
            }
            val name: String
            val person: String
            val type: String
            if (assessment > 0) {
                name = parts.take((assessment - 1).coerceAtLeast(1)).joinToString(", ")
                person = parts[assessment - 1]
                type = parts[assessment]
            } else if (parts.size >= 4) {
                name = parts.dropLast(3).joinToString(", ")
                person = parts[parts.size - 3]
                type = parts[parts.size - 2]
            } else {
                name = parts[0]
                person = parts.getOrElse(1) { "" }
                type = parts.getOrElse(2) { "" }
            }
            if (name.isBlank()) return@mapNotNull null
            BarsDiscipline(
                disciplineName = name,
                personName = person,
                assessmentType = type,
                activities = block.select("tr")
                    .filterNot { it.hasClass("collapse") }
                    .map(::row),
            )
        }
        check(disciplines.isNotEmpty()) { "BARS returned no disciplines" }

        val metadata = document.selectFirst("#div-FormHeader .row .col-sm")
            ?: document.selectFirst("#div-FormHeader .form-row .col-sm")
        val lines = metadata?.children()?.map { clean(it.text()) }
            ?.filter(String::isNotBlank).orEmpty()
        val first = lines.firstOrNull().orEmpty()
        val match = Regex("^(.*?)\\s*\\((.*?)\\)\\s*$").find(first)
        val name = match?.groupValues?.get(1)?.let(::clean)
            ?: first.replace(Regex("\\(.*\\)$"), "").trim()
        val group = match?.groupValues?.get(2)?.let(::clean)
            ?: lines.getOrElse(1) { "" }

        val details = mutableListOf<BarsStudentDetail>()
        val allowed = Regex(
            "институт|факультет|курс|форма обучения|направление|специальность|" +
                "профиль|кафедра|статус|уровень образования|номер зачетной книжки|" +
                "зач[её]тная книжка|группа|семестр",
            RegexOption.IGNORE_CASE,
        )
        listOf("#div-FormHeader", "#div-StudentInfo", "#div-Student_Info",
            ".student-info", ".student-profile").forEach { selector ->
            val section = document.selectFirst(selector) ?: return@forEach
            fun add(labelText: String, valueText: String) {
                val label = clean(labelText).removeSuffix(":")
                val value = clean(valueText)
                if (allowed.matches(label) && value.isNotBlank() && value.length <= 140 &&
                    details.none { it.label == label }) {
                    details += BarsStudentDetail(label, value)
                }
            }
            section.select("tr").forEach { tr ->
                val cells = tr.select("th, td")
                if (cells.size == 2) add(cells[0].text(), cells[1].text())
            }
            section.select("dt").forEach { dt ->
                val dd = dt.nextElementSibling()
                if (dd?.tagName() == "dd") add(dt.text(), dd.text())
            }
            section.select("label").forEach { label ->
                label.nextElementSibling()?.takeIf {
                    it.tagName() !in setOf("input", "select", "textarea")
                }?.let { add(label.text(), it.text()) }
            }
        }
        val semester = clean(document.selectFirst(".filter-option-inner-inner")?.text().orEmpty())
            .ifBlank {
                document.selectFirst("#ddl_StudyFilterSemester option[selected]")
                    ?.text()?.let(::clean).orEmpty()
            }
        return BarsExtractionPayload(name, group, semester, details.take(18), disciplines)
    }

    /** Extract only the visible text needed by the existing native report parser. */
    fun mainReport(html: String): String {
        val doc = Jsoup.parse(html)
        val lines = mutableListOf("Источник: /ST_Study/Main/Main")
        doc.selectFirst("#div-FormHeader")?.let { header ->
            lines += "=== Профиль и сводные показатели ==="
            header.select(".list-inline-item").forEach { item ->
                val v = clean(item.text())
                if (v.isNotEmpty() && v.length <= 350) lines += v
            }
            header.selectFirst(".fw-bold")?.let {
                lines.add(0, "Студент: " + clean(it.text()))
            }
        }
        doc.selectFirst("#div-Student_SemesterSheet__Mark")?.let { container ->
            lines += "=== Дисциплины и контрольные мероприятия ==="
            container.select("div.collapse[id^=s_ss_]").forEach { block ->
                val title = clean(block.previousElementSibling()?.text().orEmpty())
                if (title.isNotBlank()) lines += "Дисциплина: " + title.take(500)
                block.select("table").forEachIndexed { index, table ->
                    lines += "Таблица ${index + 1}"
                    table.select("tr").forEachIndexed { rowIndex, tr ->
                        val cells = tr.select("th, td").map { clean(it.text()).take(700) }
                        if (cells.any(String::isNotBlank)) {
                            lines += "${rowIndex + 1}: " + cells.joinToString(" | ")
                        }
                    }
                }
            }
        }
        return lines.joinToString("\n").take(380_000)
    }

    fun summaryReport(html: String): String {
        val doc = Jsoup.parse(html)
        val grades = doc.getElementById("tableMarkSummary")
        val attendance = doc.getElementById("tableSkipSummary")
        fun hasData(table: Element?): Boolean = table != null &&
            table.select("tr").drop(1).any { it.select("td, th").any { cell ->
                cell.text().isNotBlank()
            } }
        check(hasData(grades) && hasData(attendance)) { "BARS summary is incomplete" }
        val lines = mutableListOf("Источник: /ST_Study/Main/Summary")
        doc.selectFirst("#div-Summary .filter-option-inner-inner")?.let {
            lines += "Семестр: " + clean(it.text())
        }
        fun tableRows(table: Element, label: String) {
            lines += "=== $label ==="
            table.select("tr").filterNot { it.hasClass("summary-header-min") }
                .take(160).forEachIndexed { rowIndex, tr ->
                    val cells = tr.select("th, td").map { cell ->
                        val copy = cell.clone()
                        copy.select(".d-inline.d-sm-none").remove()
                        val value = clean(copy.text()).take(400).ifBlank { "∅" }
                        val meta = buildList {
                            if (cell.attr("colspan").toIntOrNull()?.let { it > 1 } == true)
                                add("colspan=" + cell.attr("colspan"))
                            if (cell.attr("rowspan").toIntOrNull()?.let { it > 1 } == true)
                                add("rowspan=" + cell.attr("rowspan"))
                            if (cell.hasClass("summary-td-current-week")) add("current-week")
                            if (cell.selectFirst(".text-danger, .text-success, .badge") != null)
                                add("mark-highlight")
                        }
                        value + if (meta.isNotEmpty()) " [" + meta.joinToString(",") + "]" else ""
                    }
                    lines += "${rowIndex + 1}: " + cells.joinToString(" | ")
                }
        }
        tableRows(grades!!, "Матрица оценок по неделям (ТБ, ПА, И)")
        tableRows(attendance!!, "Посещаемость по журналу и учебному плану")
        lines += "=== Пояснения и примечания ==="
        doc.select(
            "#div-Student_SemesterSheet__MarkSummary .small, " +
                "#div-Student_SemesterSheet__MarkSummary small, " +
                "#div-AttendanceSheet_Student__SkipSummary .small",
        ).forEach { note ->
            val value = clean(note.text())
            if (value.isNotBlank() && value.length < 1000) lines += value
        }
        return lines.joinToString("\n").take(380_000)
    }

    /** Never infer a student's ID: use only verified links from the authenticated page. */
    fun summaryUrl(html: String, mainUrl: HttpUrl): HttpUrl {
        val doc: Document = Jsoup.parse(html)
        val ids = doc.select("a[href]").mapNotNull {
            mainUrl.resolve(it.attr("href"))?.takeIf { u ->
                u.scheme == "https" && u.host == "bars.mpei.ru" &&
                    u.encodedPath.startsWith("/bars_web/ST_Study/")
            }?.queryParameter("studentID")
        }.filter { it.matches(Regex("[A-Za-z0-9-]{1,128}")) }.distinct()
        val current = mainUrl.queryParameter("studentID")
        val selected = current ?: ids.singleOrNull()
            ?: error("BARS student route is ambiguous")
        check(ids.isEmpty() || selected in ids) { "BARS student route mismatch" }
        return "https://bars.mpei.ru/bars_web/ST_Study/Main/Summary"
            .toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("studentID", selected)
            .build()
    }
}
