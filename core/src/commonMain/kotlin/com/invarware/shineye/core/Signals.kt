package com.invarware.shineye.core

enum class Risk { LOW, ELEVATED, HIGH }

enum class Signal(val weight: Risk, val explanation: String) {
    ASKS_FOR_SECRET(Risk.HIGH, "It asks you to share a PIN, OTP, password or card detail. Banks never ask for these."),
    IMPERSONATES_BANK(Risk.HIGH, "It links to a website that uses a bank's name but is not that bank's official site."),
    ACCOUNT_THREAT(Risk.ELEVATED, "It says your account, card or BVN will be blocked or restricted. Scammers use fear to rush you."),
    ID_UPDATE(Risk.ELEVATED, "It asks you to update, link or verify your BVN or NIN. Do that only at your bank or in its official app."),
    REFUND_REQUEST(Risk.ELEVATED, "It says money reached you by mistake and asks for it back. Check your balance in your bank app first."),
    PRIZE_OR_GRANT(Risk.ELEVATED, "It offers a prize, grant, bonus or loan you did not apply for."),
    FEE_REQUEST(Risk.ELEVATED, "It asks you to pay a fee before you receive money or a service."),
    SHORTENED_LINK(Risk.ELEVATED, "It uses a shortened link that hides the real website."),
    UNOFFICIAL_LINK(Risk.LOW, "It links to a website that is not on the list of official bank sites."),
    URGENCY(Risk.LOW, "It pushes you to act immediately."),
    CALL_THIS_NUMBER(Risk.LOW, "It asks you to call or WhatsApp a personal phone number."),
}

fun floorOf(signals: Set<Signal>): Risk {
    val high = signals.count { it.weight == Risk.HIGH }
    val elevated = signals.count { it.weight == Risk.ELEVATED }
    val low = signals.count { it.weight == Risk.LOW }
    return when {
        high > 0 || elevated >= 2 -> Risk.HIGH
        elevated == 1 || low >= 2 -> Risk.ELEVATED
        else -> Risk.LOW
    }
}

internal class Words(val original: String) {
    val padded: String = " " + original.lowercase()
        .replace("'", "").replace("\u2019", "")
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .split(' ').filter { it.isNotEmpty() }.joinToString(" ") + " "

    val tokens: List<String> = padded.trim().split(' ').filter { it.isNotEmpty() }

    fun has(phrase: String): Boolean = padded.contains(" $phrase ")

    fun hasAny(phrases: Collection<String>): Boolean = phrases.any(::has)

    fun without(phrases: Collection<String>): Words {
        var text = padded
        for (phrase in phrases) {
            while (text.contains(" $phrase ")) text = text.replace(" $phrase ", "  ")
        }
        return Words(text)
    }
}

private val secrets = setOf(
    "pin", "otp", "password", "passcode", "cvv", "cvv2", "token", "one time password", "security code",
    "verification code", "card details", "card number", "login details", "internet banking details",
)
private val shareVerbs = setOf("send", "share", "provide", "reply", "give", "forward", "submit", "tell")
private val negations = setOf(
    "do not share", "dont share", "never share", "not to share", "not share",
    "do not disclose", "dont disclose", "never disclose", "not disclose",
    "do not give", "dont give", "never give", "not give",
    "do not send", "dont send", "never send", "not send",
    "do not reply", "dont reply", "never reply", "not reply",
    "do not forward", "dont forward", "never forward",
)
private val threats = setOf(
    "block", "blocked", "suspend", "suspended", "restrict", "restricted", "deactivate", "deactivated",
    "freeze", "frozen", "disabled", "barred", "locked", "restriction", "suspension", "deactivation", "closed",
)
private val threatened = setOf("account", "card", "atm", "bvn", "nin", "sim", "wallet")
private val identities = setOf("bvn", "nin")
private val identityVerbs = setOf(
    "update", "upgrade", "link", "linkage", "revalidate", "revalidation", "verify", "verification",
    "validate", "validation", "reactivate", "expired", "expire", "expiring",
)
private val refunds = setOf(
    "sent by mistake", "mistakenly sent", "sent to you by mistake", "wrong transfer", "transferred wrongly",
    "wrongly transferred", "sent wrongly", "by error", "refund", "reverse it", "send it back", "send back",
    "return the money", "mistake transfer",
)
private val prizes = setOf(
    "you have won", "you won", "winner", "grant", "palliative", "lottery", "cash prize", "free money",
    "giveaway", "promo", "reward", "bonus", "empowerment", "loan approved", "pre approved", "preapproved",
)
private val fees = setOf(
    "fee", "fees", "charge", "charges", "processing fee", "activation fee", "registration fee",
    "delivery fee", "clearance fee",
)
private val payVerbs = setOf("pay", "transfer", "send", "deposit", "remit")
private val urgency = setOf(
    "urgent", "urgently", "immediately", "right now", "asap", "as soon as possible", "within 24 hours",
    "within 24hrs", "within 24 hrs", "within 48 hours", "act now", "final notice", "last warning",
    "expires today",
)
private val contactVerbs = setOf("call", "whatsapp", "contact", "text us", "chat")
private val shorteners = setOf(
    "bit.ly", "bitly.com", "tinyurl.com", "cutt.ly", "t.co", "rb.gy", "is.gd", "goo.gl", "shorturl.at",
    "ow.ly", "tiny.cc", "rebrand.ly", "s.id", "t.ly", "short.gy",
)
private val brands = listOf(
    "gtbank", "gtco", "firstbank", "zenith", "accessbank", "uba", "fidelity", "opay", "palmpay", "moniepoint",
    "kuda", "wema", "alat", "sterling", "fcmb", "unionbank", "stanbic", "ecobank", "polaris", "keystone",
    "cbn", "nibss", "nimc", "efcc", "jaiz", "providus", "globus", "titan",
)

internal fun signalsIn(message: Raw, words: Words, official: OfficialDomains): Set<Signal> {
    val asked = words.without(negations)
    val hosts = hostsIn(message.text)
    val foreign = hosts.filterNot(official::owns)
    val runs = digitRuns(message.text)

    // Strong, self-contained signals. These establish that the message is about
    // money even when it never says "bank".
    val base = buildSet {
        if (asked.hasAny(secrets) && asked.hasAny(shareVerbs)) add(Signal.ASKS_FOR_SECRET)
        if (foreign.any(::usesBrand)) add(Signal.IMPERSONATES_BANK)
        if (words.hasAny(threats) && words.hasAny(threatened)) add(Signal.ACCOUNT_THREAT)
        if (words.hasAny(identities) && words.hasAny(identityVerbs)) add(Signal.ID_UPDATE)
        if (words.hasAny(refunds)) add(Signal.REFUND_REQUEST)
        if (words.hasAny(prizes)) add(Signal.PRIZE_OR_GRANT)
        if (words.hasAny(fees) && words.hasAny(payVerbs)) add(Signal.FEE_REQUEST)
    }

    // A link, a phone number or urgency only matters if this is a bank message,
    // or if another scam signal already put it in that context. Without this,
    // an ordinary ride-hailing promo with a WhatsApp number and a link is
    // reported as a warning, which makes the app look unreliable.
    val financial = base.isNotEmpty() || bankContext(words, hosts, official)

    return buildSet {
        addAll(base)
        if (financial) {
            if (foreign.any { it in shorteners }) add(Signal.SHORTENED_LINK)
            if (foreign.any { it !in shorteners && !usesBrand(it) }) add(Signal.UNOFFICIAL_LINK)
            if (words.hasAny(urgency)) add(Signal.URGENCY)
            if (words.hasAny(contactVerbs) && runs.any { isPhone(it.digits) }) add(Signal.CALL_THIS_NUMBER)
        }
    }
}

private val bankWords = setOf(
    "bank", "banks", "banking", "account", "accounts", "card", "atm", "bvn", "nin",
    "transfer", "transaction", "transactions", "balance", "debit", "credit", "alert",
    "statement", "loan", "loans", "deposit", "withdraw", "withdrawal", "wallet", "ussd",
    "otp", "pin", "naira", "ngn", "beneficiary", "kyc", "payment", "pay", "paid",
    "sort code", "bank app", "mobile banking", "internet banking",
)

private fun bankContext(words: Words, hosts: List<String>, official: OfficialDomains): Boolean =
    words.hasAny(bankWords) ||
        hosts.any(::usesBrand) ||
        hosts.any(official::owns)

internal fun usesBrand(host: String): Boolean {
    val labels = host.split('.', '-').filter { it.isNotEmpty() }
    return brands.any { brand ->
        labels.any { label ->
            label == brand ||
                (brand.length >= 4 && label.startsWith(brand)) ||
                (brand.length >= 5 && label.contains(brand))
        }
    }
}
