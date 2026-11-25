package org.magnetitebrowser.android.model

import uniffi.magnetite_mobile_core.Page
import uniffi.magnetite_mobile_core.RowItem
import uniffi.magnetite_mobile_core.Space

/** A row the address field offers while typing. */
sealed interface Suggestion {
    /** The text is an address: go there. */
    data class Go(val url: String) : Suggestion
    /** Search for the text. */
    data class Search(val url: String) : Suggestion
    /** A tab open here: show it. */
    data class Open(val tab: Tab) : Suggestion
    /** A favorite, pinned or Today page of the chain: open it in its space. */
    data class Chain(val page: Page, val space: Space) : Suggestion

    val key: String
        get() = when (this) {
            is Go -> "address $url"
            is Search -> "search"
            is Open -> "tab ${tab.id}"
            is Chain -> "page ${space.id} ${page.id}"
        }
}

/**
 * What to offer for `text`: the address it is or a search for it first (the
 * other one next), then the open tabs and the chain's pages that match, best
 * first. Nothing for an empty text.
 */
fun Browser.suggestions(text: String): List<Suggestion> {
    val query = text.trim().lowercase()
    if (query.isEmpty()) return emptyList()
    val search = Address.search(text, searchTemplate)?.let(Suggestion::Search)
    val address = Address.address(text)?.let(Suggestion::Go)
    val rows = mutableListOf<Suggestion>()
    if (address != null) rows += address
    if (search != null) rows += search

    val open = tabs
        .mapNotNull { tab -> match(query, tab.title, tab.url)?.let { tab to it } }
        .sortedByDescending { it.second }
        .take(3)
        .map { it.first }
    rows += open.map(Suggestion::Open)

    // Each page once: the first space that has it, and none a tab here shows.
    val seen = open.map { it.url }.toMutableSet()
    val pages = mutableListOf<Pair<Suggestion.Chain, Int>>()
    for (space in spaces) {
        val pinned = space.pinned.mapNotNull { (it.item as? RowItem.Page)?.page }
        for (page in space.favorites + pinned + space.today) {
            if (page.url in seen) continue
            val score = match(query, page.title, page.url) ?: continue
            seen += page.url
            pages += Suggestion.Chain(page, space) to score
        }
    }
    rows += pages.sortedByDescending { it.second }.take(6).map { it.first }
    return rows
}

/**
 * How well `query` (lowercased) matches a page: its site starting with it is
 * best, then a word of its title, then any part of either (for three letters
 * or more: fewer are inside nearly everything).
 */
private fun match(query: String, title: String, url: String): Int? {
    val title = title.lowercase()
    return when {
        Address.site(url).lowercase().startsWith(query) -> 3
        title.split(Regex("[^\\p{L}\\p{N}]+")).any { it.startsWith(query) } -> 2
        query.length >= 3 && (query in title || query in url.lowercase()) -> 1
        else -> null
    }
}
