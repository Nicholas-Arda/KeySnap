package com.example.data

/** Moves a visible item without changing the membership of the filtered list. */
internal fun moveShortcut(order: List<String>, from: String, to: String): List<String> {
    val source = order.indexOf(from)
    val destination = order.indexOf(to)
    if (source < 0 || destination < 0 || source == destination) return order
    return order.toMutableList().apply { add(destination, removeAt(source)) }
}

/**
 * Replaces only the slots belonging to the visible subset. Hidden shortcuts keep their exact
 * slots and relative order. IDs resolve against current data, so a drag cannot overwrite edits
 * or resurrect a shortcut deleted since the gesture began.
 */
internal fun mergeShortcutOrder(scripts: List<ShortcutScript>, orderedIds: List<String>): List<ShortcutScript> {
    val byId = scripts.associateBy { it.id }
    val ids = orderedIds.distinct().filter { it in byId }
    val included = ids.toSet()
    val replacements = ids.iterator()
    return scripts.map { if (it.id in included) byId.getValue(replacements.next()) else it }
}
