package com.invarware.shineye

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.initialize
import com.google.firebase.remoteconfig.remoteConfig
import com.invarware.shineye.core.CloudConsent
import com.invarware.shineye.core.Environment
import com.invarware.shineye.core.Network
import com.invarware.shineye.core.OfficialDomains
import com.invarware.shineye.core.Raw
import kotlinx.coroutines.tasks.await

private const val DEFAULT_CLOUD_MODEL = "gemini-3.5-flash-lite"

class ShineEyeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Firebase.initialize(this)
        Firebase.appCheck.installAppCheckProviderFactory(appCheckProvider())
        Firebase.remoteConfig.setDefaultsAsync(mapOf("cloud_model" to DEFAULT_CLOUD_MODEL, "official_domains" to ""))
    }

    var consent: CloudConsent
        get() = CloudConsent.valueOf(prefs().getString("consent", null) ?: CloudConsent.ASK.name)
        set(value) = prefs().edit().putString("consent", value.name).apply()

    suspend fun checker(): Checker {
        attempt { Firebase.remoteConfig.fetchAndActivate().await() }
        val name = Firebase.remoteConfig.getString("cloud_model").ifBlank { DEFAULT_CLOUD_MODEL }
        return Checker(DeviceModel(name), CloudModel(name))
    }

    suspend fun check(checker: Checker, text: String): Outcome {
        val environment = Environment(checker.deviceStatus(), network(), consent)
        return checker.check(Raw(text), officialDomains(), environment)
    }

    private fun officialDomains(): OfficialDomains =
        Firebase.remoteConfig.getString("official_domains").let { if (it.isBlank()) OfficialDomains.seed else OfficialDomains.parse(it) }

    private fun network(): Network {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        return if (validated) Network.ONLINE else Network.OFFLINE
    }

    private fun prefs() = getSharedPreferences("shine-your-eye", Context.MODE_PRIVATE)
}
