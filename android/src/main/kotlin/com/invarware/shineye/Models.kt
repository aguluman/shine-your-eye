package com.invarware.shineye

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.InferenceSource
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.ai
import com.google.firebase.ai.annotations.Generable
import com.google.firebase.ai.annotations.Guide
import com.google.firebase.ai.ondevice.FirebaseAIOnDevice
import com.google.firebase.ai.ondevice.OnDeviceModelStatus
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.invarware.shineye.core.OnDevice
import com.invarware.shineye.core.Opinion
import com.invarware.shineye.core.Prompt
import com.invarware.shineye.core.Raw
import com.invarware.shineye.core.Redacted
import com.invarware.shineye.core.Risk
import com.invarware.shineye.core.Source
import kotlinx.serialization.Serializable

@Serializable
enum class ModelRisk { SAFE, SUSPICIOUS, SCAM }

@Serializable
@Generable(description = "Whether a message received by a Nigerian bank customer is a scam")
data class Judgement(
    @Guide(description = "SAFE, SUSPICIOUS or SCAM")
    val risk: ModelRisk,
    @Guide(description = "True only when the message clearly matches, or clearly does not match, a known scam pattern")
    val confident: Boolean,
    @Guide(description = "Short reasons for the person who received the message", minItems = 1, maxItems = 3)
    val reasons: List<String>,
) {
    companion object
}

class NoJudgement : Exception("The model returned no judgement")

@OptIn(PublicPreviewAPI::class)
class DeviceModel(cloudModelName: String) {

    private val model = Firebase.ai(backend = GenerativeBackend.googleAI())
        .generativeModel(
            modelName = cloudModelName,
            onDeviceConfig = OnDeviceConfig(mode = InferenceMode.ONLY_ON_DEVICE, maxOutputTokens = 256),
        )

    suspend fun status(): OnDevice {
        val status = FirebaseAIOnDevice.checkStatus()
        val name = when (status) {
            OnDeviceModelStatus.AVAILABLE -> "AVAILABLE"
            OnDeviceModelStatus.DOWNLOADABLE -> "DOWNLOADABLE"
            OnDeviceModelStatus.DOWNLOADING -> "DOWNLOADING"
            else -> "UNAVAILABLE"
        }
        Log.i("ShineYourEye", "on-device model: $name")
        return if (status == OnDeviceModelStatus.AVAILABLE) OnDevice.AVAILABLE else OnDevice.UNAVAILABLE
    }

    suspend fun judge(message: Raw): Opinion {
        val response = model.generateObject(Judgement.firebaseAISchema(), Prompt.onDevice(message))
        val judgement: Judgement = response.getObject() ?: throw NoJudgement()
        return judgement.toOpinion(Source.ON_DEVICE)
    }
}

@OptIn(PublicPreviewAPI::class)
class CloudModel(cloudModelName: String) {

    private val model = Firebase.ai(backend = GenerativeBackend.googleAI())
        .generativeModel(
            modelName = cloudModelName,
            onDeviceConfig = OnDeviceConfig(mode = InferenceMode.PREFER_IN_CLOUD, maxOutputTokens = 256),
        )

    suspend fun judge(message: Redacted): Opinion {
        val response = model.generateObject(Judgement.firebaseAISchema(), Prompt.inCloud(message))
        val judgement: Judgement = response.getObject() ?: throw NoJudgement()
        val source = if (response.response.inferenceSource == InferenceSource.ON_DEVICE) Source.ON_DEVICE else Source.CLOUD
        return judgement.toOpinion(source)
    }
}

private fun Judgement.toOpinion(source: Source) = Opinion(
    risk = when (risk) {
        ModelRisk.SAFE -> Risk.LOW
        ModelRisk.SUSPICIOUS -> Risk.ELEVATED
        ModelRisk.SCAM -> Risk.HIGH
    },
    confident = confident,
    reasons = reasons.take(3),
    source = source,
)
