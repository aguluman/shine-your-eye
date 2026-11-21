package com.invarware.shineye.core

internal sealed interface Segment {
    class Plain(val text: String) : Segment
    class Email(val text: String) : Segment
    class Link(val text: String, val host: String, val hasPath: Boolean) : Segment
}

private enum class HitKind { EMAIL, LINK }

private class Hit(val start: Int, val end: Int, val kind: HitKind)

private val emailPattern = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")

private val schemedPattern = Regex(
    """\b(?:https?://(?:[^\s/@<>"']+@)?|www\.)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?(?::\d+)?(?:[/?#][^\s<>"']*)?""",
    RegexOption.IGNORE_CASE,
)

private val barePattern = Regex(
    """\b(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+""" +
        """(?:com|ng|net|org|info|xyz|top|online|site|live|link|app|me|co|io|biz|club|shop|icu|cc|ly|gl|gd|ws|""" +
        """vip|pro|store|tech|website|space|click|support|help|services|cloud|page|today|world|africa)\b""" +
        """(?:/[^\s<>"']*)?""",
)

private const val TRAILING = ".,;:!?)]}'\""

internal fun segments(text: String): List<Segment> {
    val hits = (
        emailPattern.findAll(text).map { Hit(it.range.first, it.range.last + 1, HitKind.EMAIL) } +
            schemedPattern.findAll(text).map { Hit(it.range.first, trimmedEnd(text, it.range), HitKind.LINK) } +
            barePattern.findAll(text).map { Hit(it.range.first, trimmedEnd(text, it.range), HitKind.LINK) }
        ).sortedWith(compareBy<Hit> { it.start }.thenByDescending { it.end })

    val out = mutableListOf<Segment>()
    var cursor = 0
    for (hit in hits) {
        if (hit.start < cursor || hit.end <= hit.start) continue
        if (hit.start > cursor) out += Segment.Plain(text.substring(cursor, hit.start))
        val matched = text.substring(hit.start, hit.end)
        out += when (hit.kind) {
            HitKind.EMAIL -> Segment.Email(matched)
            HitKind.LINK -> link(matched)
        }
        cursor = hit.end
    }
    if (cursor < text.length) out += Segment.Plain(text.substring(cursor))
    return out
}

internal fun hostsIn(text: String): List<String> =
    segments(text).filterIsInstance<Segment.Link>().map { it.host }.filter { it.isNotEmpty() }

private fun trimmedEnd(text: String, range: IntRange): Int {
    var end = range.last + 1
    while (end > range.first && text[end - 1] in TRAILING) end--
    return end
}

private fun link(matched: String): Segment.Link {
    val withoutScheme = matched.lowercase().removePrefix("https://").removePrefix("http://")
    val authorityEnd = withoutScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) withoutScheme.length else it }
    val authority = withoutScheme.substring(0, authorityEnd)
    val rest = withoutScheme.substring(authorityEnd)
    val hostAndPort = authority.substringAfterLast('@')
    val host = hostAndPort.substringBefore(':').removePrefix("www.").trimEnd('.')
    val hasPath = (rest.isNotEmpty() && rest != "/") || hostAndPort.contains(':') || authority.contains('@')
    return Segment.Link(matched, host, hasPath)
}
