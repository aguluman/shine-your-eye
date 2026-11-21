package com.invarware.shineye.core

private const val ON_DEVICE_CHARACTER_BUDGET = 8_000

class Analysis private constructor(
    val message: Raw,
    val signals: Set<Signal>,
    val language: Language,
    val redacted: Redacted,
) {
    val floor: Risk get() = floorOf(signals)

    val fitsOnDevice: Boolean get() = message.text.length <= ON_DEVICE_CHARACTER_BUDGET

    fun situation(environment: Environment): Situation = Situation(floor, language, fitsOnDevice, environment)

    fun verdict(opinion: Opinion?): Verdict = decide(signals, opinion)

    companion object {
        fun of(message: Raw, official: OfficialDomains = OfficialDomains.seed): Analysis {
            val words = Words(message.text)
            return Analysis(message, signalsIn(message, words, official), languageOf(words), Redacted.of(message))
        }
    }
}
