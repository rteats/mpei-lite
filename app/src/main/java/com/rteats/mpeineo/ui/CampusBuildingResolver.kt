package com.rteats.mpeineo.ui

import java.util.Locale

/** A verified campus-building identifier extracted from the room text.
 *  Do not infer buildings from bare numbers, streets or Latin lookalike letters.
 *  If a location mentions more than one building, it is ambiguous.
 */
internal fun campusBuildingForRoom(location: String): String? {
    val normalized = location.trim().uppercase(Locale.ROOT).replace('Ё', 'Е')
    if (normalized.isBlank()) return null
    val symbols = "[АБВГДЕЖЗИКЛМНРСТФХЭ]"
    val explicitRoom = Regex("(?<![\\p{L}\\d])($symbols)\\s*[-–—]\\s*\\d{1,4}")
    val namedBuilding = Regex("(?<![\\p{L}])КОРП(?:УС|\\.)?\\s*($symbols)(?![\\p{L}\\d])")
    val candidates = (explicitRoom.findAll(normalized).map { it.groupValues[1] } +
        namedBuilding.findAll(normalized).map { it.groupValues[1] }).toSet()
    return candidates.singleOrNull()
}

/** One-shot cross-tab map focus, separate from the map's ordinary interaction state. */
internal data class CampusMapFocus(
    val id: Int,
    val building: String,
    val classroom: String,
)

/** Campus letters are a stable offline search index; no map network request. */
internal val campusSearchSymbols = listOf(
    "А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "К",
    "Л", "М", "Н", "Р", "С", "Т", "Ф", "Х", "Э",
)

/** Recognize a named building or an auditorium's building prefix. */
internal fun campusBuildingHits(query: String): List<String> {
    val normalized = query.trim().uppercase(Locale.ROOT).replace('Ё', 'Е')
    if (normalized.isBlank()) return campusSearchSymbols
    val compact = normalized.removePrefix("КОРПУС")
        .removePrefix("КОРП.")
        .removePrefix("КОРП")
        .trim().removePrefix("№").trim()
    val match = Regex("^([АБВГДЕЖЗИКЛМНРСТФХЭ])(?:\\s*[-–—]\\s*\\d{1,4})?$")
        .matchEntire(compact)
    return match?.groupValues?.get(1)?.let(::listOf).orEmpty()
}
