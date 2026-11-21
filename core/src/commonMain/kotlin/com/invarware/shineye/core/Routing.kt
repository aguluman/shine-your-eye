package com.invarware.shineye.core

enum class OnDevice { AVAILABLE, UNAVAILABLE }

enum class Network { ONLINE, OFFLINE }

enum class CloudConsent { ALWAYS, ASK, NEVER }

data class Environment(val onDevice: OnDevice, val network: Network, val consent: CloudConsent)

class Situation(val floor: Risk, val language: Language, val fitsOnDevice: Boolean, val environment: Environment)

sealed interface Step

sealed interface AfterDevice : Step

data object AskOnDevice : Step

data object AskCloud : AfterDevice

data object OfferCloud : AfterDevice

data object Settle : AfterDevice

fun firstStep(situation: Situation): Step {
    val environment = situation.environment
    val device = environment.onDevice == OnDevice.AVAILABLE && situation.fitsOnDevice
    val cloud = cloudStep(environment)
    return when {
        situation.floor == Risk.HIGH -> Settle
        device && situation.language == Language.ENGLISH -> AskOnDevice
        cloud != Settle -> cloud
        device -> AskOnDevice
        else -> Settle
    }
}

fun afterDevice(situation: Situation, opinion: Opinion): AfterDevice =
    if (opinion.confident && situation.language == Language.ENGLISH) Settle else cloudStep(situation.environment)

private fun cloudStep(environment: Environment): AfterDevice = when {
    environment.network == Network.OFFLINE -> Settle
    environment.consent == CloudConsent.ALWAYS -> AskCloud
    environment.consent == CloudConsent.ASK -> OfferCloud
    else -> Settle
}
