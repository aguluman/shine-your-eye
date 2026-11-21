package com.invarware.shineye

import android.util.Log
import com.invarware.shineye.core.AfterDevice
import com.invarware.shineye.core.Analysis
import com.invarware.shineye.core.AskCloud
import com.invarware.shineye.core.AskOnDevice
import com.invarware.shineye.core.CloudConsent
import com.invarware.shineye.core.Environment
import com.invarware.shineye.core.Network
import com.invarware.shineye.core.OfferCloud
import com.invarware.shineye.core.OfficialDomains
import com.invarware.shineye.core.OnDevice
import com.invarware.shineye.core.Opinion
import com.invarware.shineye.core.Raw
import com.invarware.shineye.core.Risk
import com.invarware.shineye.core.Settle
import com.invarware.shineye.core.Source
import com.invarware.shineye.core.Verdict
import com.invarware.shineye.core.afterDevice
import com.invarware.shineye.core.firstStep
import kotlin.coroutines.cancellation.CancellationException

enum class Hop(val label: String) {
    RULES("Checked against known scam patterns on this phone"),
    RULES_ENOUGH("The patterns were clear enough, so no AI was needed"),
    DEVICE("Checked by Gemini Nano on this phone"),
    DEVICE_UNAVAILABLE("Gemini Nano isn't available on this phone"),
    DEVICE_FAILED("Gemini Nano couldn't answer this time"),
    OFFLINE("You're offline, so the cloud check was skipped"),
    CLOUD("Checked by Gemini in the cloud, with personal details removed"),
    CLOUD_DECLINED("You kept this message on your phone"),
    CLOUD_FAILED("The cloud check couldn't be reached"),
}

sealed interface Outcome {
    class Done(val verdict: Verdict, val trail: List<Hop>) : Outcome

    class NeedsConsent(
        val analysis: Analysis,
        val environment: Environment,
        val deviceOpinion: Opinion?,
        val trail: List<Hop>,
    ) : Outcome
}

class Checker(private val device: DeviceModel, private val cloud: CloudModel) {

    suspend fun deviceStatus(): OnDevice = attempt { device.status() } ?: OnDevice.UNAVAILABLE

    suspend fun check(message: Raw, official: OfficialDomains, environment: Environment): Outcome {
        val analysis = Analysis.of(message, official)
        val trail = mutableListOf(Hop.RULES)
        if (analysis.floor == Risk.HIGH) trail += Hop.RULES_ENOUGH
        else {
            if (environment.onDevice == OnDevice.UNAVAILABLE) trail += Hop.DEVICE_UNAVAILABLE
            if (environment.network == Network.OFFLINE) trail += Hop.OFFLINE
        }
        return route(analysis, environment, trail)
    }

    suspend fun accept(pending: Outcome.NeedsConsent): Outcome.Done =
        askCloud(pending.analysis, pending.trail.toMutableList(), pending.deviceOpinion)

    suspend fun decline(pending: Outcome.NeedsConsent): Outcome {
        val trail = pending.trail.toMutableList().apply { add(Hop.CLOUD_DECLINED) }
        return if (pending.deviceOpinion != null) {
            Outcome.Done(pending.analysis.verdict(pending.deviceOpinion), trail)
        } else {
            route(pending.analysis, pending.environment.copy(consent = CloudConsent.NEVER), trail)
        }
    }

    private suspend fun route(analysis: Analysis, environment: Environment, trail: MutableList<Hop>): Outcome =
        when (val step = firstStep(analysis.situation(environment))) {
            AskOnDevice -> askDevice(analysis, environment, trail)
            is AfterDevice -> proceed(step, analysis, environment, trail, deviceOpinion = null)
        }

    private suspend fun askDevice(analysis: Analysis, environment: Environment, trail: MutableList<Hop>): Outcome {
        val opinion = attempt { device.judge(analysis.message) }
        if (opinion == null) {
            trail += Hop.DEVICE_FAILED
            return route(analysis, environment.copy(onDevice = OnDevice.UNAVAILABLE), trail)
        }
        trail += Hop.DEVICE
        return proceed(afterDevice(analysis.situation(environment), opinion), analysis, environment, trail, opinion)
    }

    private suspend fun proceed(
        step: AfterDevice,
        analysis: Analysis,
        environment: Environment,
        trail: MutableList<Hop>,
        deviceOpinion: Opinion?,
    ): Outcome = when (step) {
        Settle -> Outcome.Done(analysis.verdict(deviceOpinion), trail)
        AskCloud -> askCloud(analysis, trail, deviceOpinion)
        OfferCloud -> Outcome.NeedsConsent(analysis, environment, deviceOpinion, trail)
    }

    private suspend fun askCloud(analysis: Analysis, trail: MutableList<Hop>, deviceOpinion: Opinion?): Outcome.Done {
        val opinion = attempt { cloud.judge(analysis.redacted) }
        trail += when {
            opinion == null -> Hop.CLOUD_FAILED
            opinion.source == Source.CLOUD -> Hop.CLOUD
            else -> Hop.DEVICE
        }
        return Outcome.Done(analysis.verdict(opinion ?: deviceOpinion), trail)
    }
}

internal suspend fun <T> attempt(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w("ShineYourEye", "Inference failed", e)
    null
}
