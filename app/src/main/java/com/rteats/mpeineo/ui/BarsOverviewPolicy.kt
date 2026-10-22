package com.rteats.mpeineo.ui

/** Single vocabulary shared by BARS report controls and the overview title. */
internal enum class BarsOverviewSection(val key: String, val title: String) {
    SUBJECTS("subjects", "Дисциплины"),
    GRADES("grades", "По неделям"),
    ATTENDANCE("attendance", "Посещаемость"),
    CONTROLS("controls", "Контрольные"),
}

internal fun barsSectionTitle(section: String): String =
    BarsOverviewSection.entries.firstOrNull { it.key == section }?.title ?: "БАРС"
