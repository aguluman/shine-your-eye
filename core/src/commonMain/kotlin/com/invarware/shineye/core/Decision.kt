package com.invarware.shineye.core

enum class Source { ON_DEVICE, CLOUD }

class Opinion(val risk: Risk, val confident: Boolean, val reasons: List<String>, val source: Source)

enum class Kind(val headline: String, val advice: String) {
    SCAM(
        "This looks like a scam",
        "Do not reply, click the link or send money. If you are worried about your account, call the number printed on your card.",
    ),
    SUSPICIOUS(
        "Be careful with this message",
        "Do not act on it yet. Contact your bank through its official app or the number printed on your card.",
    ),
    LOOKS_SAFE(
        "No signs of a scam",
        "It does not match common scam patterns. Still, never share your PIN or OTP with anyone.",
    ),
    NOTHING_FOUND(
        "No warning signs found",
        "Only the checks on your device ran. If in doubt, call your bank on the number printed on your card.",
    ),
}

class Verdict(val kind: Kind, val risk: Risk, val signals: List<Signal>, val opinion: Opinion?)

fun decide(signals: Set<Signal>, opinion: Opinion?): Verdict {
    val floor = floorOf(signals)
    val risk = if (opinion == null || opinion.risk <= floor) floor else opinion.risk
    val kind = when (risk) {
        Risk.HIGH -> Kind.SCAM
        Risk.ELEVATED -> Kind.SUSPICIOUS
        Risk.LOW -> if (opinion?.confident == true) Kind.LOOKS_SAFE else Kind.NOTHING_FOUND
    }
    return Verdict(kind, risk, signals.sortedByDescending { it.weight }, opinion)
}
