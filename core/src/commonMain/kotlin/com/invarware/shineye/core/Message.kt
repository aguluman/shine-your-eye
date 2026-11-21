package com.invarware.shineye.core

class Raw(val text: String)

enum class Removed(val tag: String, private val singular: String, private val plural: String) {
    PHONE("[PHONE]", "phone number", "phone numbers"),
    ACCOUNT("[ACCOUNT]", "account number", "account numbers"),
    ID("[ID]", "BVN or NIN", "BVNs or NINs"),
    CARD("[CARD]", "card number", "card numbers"),
    CODE("[CODE]", "code", "codes"),
    NUMBER("[NUMBER]", "long number", "long numbers"),
    EMAIL("[EMAIL]", "email address", "email addresses"),
    LINK_PATH("/…", "link path", "link paths");

    fun describe(count: Int): String = "$count ${if (count == 1) singular else plural}"
}

class Redacted private constructor(val text: String) {

    val removed: Map<Removed, Int> = Removed.entries
        .associateWith { kind -> text.windowed(kind.tag.length).count { it == kind.tag } }
        .filterValues { it > 0 }

    fun describe(): List<String> = Removed.entries.mapNotNull { kind -> removed[kind]?.let(kind::describe) }

    companion object {
        private const val MAX_PASSES = 8

        fun of(message: Raw): Redacted {
            var text = message.text
            repeat(MAX_PASSES) {
                val next = pass(text)
                if (next == text) return Redacted(text)
                text = next
            }
            return Redacted(text)
        }

        private fun pass(text: String): String = redactDigits(
            buildString {
                for (segment in segments(text)) when (segment) {
                    is Segment.Plain -> append(segment.text)
                    is Segment.Email -> append(Removed.EMAIL.tag)
                    is Segment.Link -> {
                        append(segment.host)
                        if (segment.hasPath) append(Removed.LINK_PATH.tag)
                    }
                }
            },
        )
    }
}
