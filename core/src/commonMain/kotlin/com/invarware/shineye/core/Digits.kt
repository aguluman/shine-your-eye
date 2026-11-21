package com.invarware.shineye.core

internal class DigitRun(val start: Int, val end: Int, val digits: String, val isAmount: Boolean)

private const val JOINERS = " -./\u00A0"
private const val NAIRA = '\u20A6'

internal fun digitRuns(text: String): List<DigitRun> {
    val runs = mutableListOf<DigitRun>()
    var i = 0
    while (i < text.length) {
        if (!text[i].isDigit()) {
            i++
            continue
        }
        val run = (if (hasCurrencyBefore(text, i)) amountAt(text, i) else null) ?: plainRunAt(text, i)
        runs += run
        i = run.end
    }
    return runs
}

internal fun exposedDigits(text: String): List<DigitRun> =
    digitRuns(text).filter { !it.isAmount && it.digits.length >= 4 }

internal fun redactDigits(text: String): String {
    val out = StringBuilder()
    var last = 0
    for (run in exposedDigits(text)) {
        out.appendRange(text, last, run.start).append(classify(run.digits).tag)
        last = run.end
    }
    return out.appendRange(text, last, text.length).toString()
}

internal fun isPhone(digits: String): Boolean =
    (digits.length == 11 && digits[0] == '0' && digits[1] in "789") ||
        (digits.length == 13 && digits.startsWith("234") && digits[3] in "789")

private fun classify(digits: String): Removed = when {
    isPhone(digits) -> Removed.PHONE
    digits.length == 10 -> Removed.ACCOUNT
    digits.length == 11 -> Removed.ID
    digits.length in 13..19 -> Removed.CARD
    digits.length in 4..8 -> Removed.CODE
    else -> Removed.NUMBER
}

private fun plainRunAt(text: String, start: Int): DigitRun {
    val digits = StringBuilder()
    var end = start
    var j = start
    while (j < text.length) {
        val c = text[j]
        when {
            c.isDigit() -> {
                digits.append(c)
                j++
                end = j
            }
            c in JOINERS && j + 1 < text.length && text[j + 1].isDigit() -> j++
            else -> break
        }
    }
    return DigitRun(start, end, digits.toString(), isAmount = false)
}

private fun amountAt(text: String, start: Int): DigitRun? {
    var j = start
    val digits = StringBuilder()
    fun digitAt(k: Int) = k < text.length && text[k].isDigit()
    fun take() {
        digits.append(text[j])
        j++
    }
    while (digitAt(j)) take()
    val lead = digits.length
    var grouped = false
    while (j < text.length && text[j] == ',' && digitAt(j + 1) && digitAt(j + 2) && digitAt(j + 3) && !digitAt(j + 4)) {
        j++
        repeat(3) { take() }
        grouped = true
    }
    if (j < text.length && text[j] == '.' && digitAt(j + 1) && !digitAt(j + 3)) {
        j++
        take()
        if (digitAt(j)) take()
    }
    val wellFormed = if (grouped) lead in 1..3 else lead <= 7
    return if (wellFormed) DigitRun(start, j, digits.toString(), isAmount = true) else null
}

private fun hasCurrencyBefore(text: String, i: Int): Boolean {
    var k = i - 1
    if (k >= 0 && text[k] == ' ') k--
    if (k < 0) return false
    fun wordStartsAt(p: Int) = p == 0 || !text[p - 1].isLetter()
    return when {
        text[k] == NAIRA || text[k] == '#' -> true
        k >= 2 && text.substring(k - 2, k + 1).equals("NGN", ignoreCase = true) -> wordStartsAt(k - 2)
        text[k] == 'N' -> wordStartsAt(k)
        else -> false
    }
}
