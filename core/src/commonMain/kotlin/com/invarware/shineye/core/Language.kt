package com.invarware.shineye.core

enum class Language { ENGLISH, OTHER }

private val nigerianDiacritics = "ẹọṣịụṅƙɗɓƴ"

private val markers = setOf(
    "abeg", "wetin", "una", "dey", "sabi", "wahala", "pikin", "sef", "shey", "abi", "oya", "wey", "naim",
    "biko", "nna", "jowo", "ejo", "kedu", "daalu", "nagode", "allah", "kwana", "ina",
)

internal fun languageOf(words: Words): Language {
    val accented = words.original.lowercase().any { it in nigerianDiacritics }
    val markerCount = words.tokens.count { it in markers }
    return if (accented || markerCount >= 2) Language.OTHER else Language.ENGLISH
}
