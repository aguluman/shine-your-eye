@file:OptIn(ExperimentalJsExport::class)

package com.invarware.shineye.core

@JsExport
class Inspection internal constructor(internal val analysis: Analysis) {
    val floor: String get() = analysis.floor.name
    val english: Boolean get() = analysis.language == Language.ENGLISH
    val fitsOnDevice: Boolean get() = analysis.fitsOnDevice
    val devicePrompt: String get() = Prompt.onDevice(analysis.message)
    val redacted: RedactedMessage get() = RedactedMessage(analysis.redacted)
}

@JsExport
class RedactedMessage internal constructor(internal val value: Redacted) {
    val text: String get() = value.text
    val removed: Array<String> get() = value.describe().toTypedArray()
    val cloudPrompt: String get() = Prompt.inCloud(value)
}

@JsExport
class Finding(
    val headline: String,
    val advice: String,
    val kind: String,
    val signals: Array<String>,
    val modelReasons: Array<String>,
)

@JsExport
fun inspect(text: String, officialDomains: Array<String>): Inspection =
    Inspection(Analysis.of(Raw(text), if (officialDomains.isEmpty()) OfficialDomains.seed else OfficialDomains(officialDomains.toList())))

@JsExport
fun firstStepFor(inspection: Inspection, deviceAvailable: Boolean, online: Boolean, consent: String): String =
    stepName(firstStep(inspection.analysis.situation(environment(deviceAvailable, online, consent))))

@JsExport
fun stepAfterDevice(inspection: Inspection, online: Boolean, consent: String, risk: String, confident: Boolean): String =
    stepName(
        afterDevice(
            inspection.analysis.situation(environment(true, online, consent)),
            Opinion(riskOf(risk), confident, emptyList(), Source.ON_DEVICE),
        ),
    )

@JsExport
fun findingFor(inspection: Inspection, risk: String?, confident: Boolean, reasons: Array<String>, source: String?): Finding {
    val opinion = risk?.let { Opinion(riskOf(it), confident, reasons.toList(), if (source == "ON_DEVICE") Source.ON_DEVICE else Source.CLOUD) }
    val verdict = inspection.analysis.verdict(opinion)
    return Finding(
        headline = verdict.kind.headline,
        advice = verdict.kind.advice,
        kind = verdict.kind.name,
        signals = verdict.signals.map { it.explanation }.toTypedArray(),
        modelReasons = (verdict.opinion?.reasons ?: emptyList()).distinct().toTypedArray(),
    )
}

private fun environment(deviceAvailable: Boolean, online: Boolean, consent: String) = Environment(
    if (deviceAvailable) OnDevice.AVAILABLE else OnDevice.UNAVAILABLE,
    if (online) Network.ONLINE else Network.OFFLINE,
    CloudConsent.valueOf(consent),
)

private fun riskOf(modelRisk: String): Risk = when (modelRisk) {
    "SCAM" -> Risk.HIGH
    "SUSPICIOUS" -> Risk.ELEVATED
    else -> Risk.LOW
}

private fun stepName(step: Step): String = when (step) {
    AskOnDevice -> "ASK_ON_DEVICE"
    AskCloud -> "ASK_CLOUD"
    OfferCloud -> "OFFER_CLOUD"
    Settle -> "SETTLE"
}
