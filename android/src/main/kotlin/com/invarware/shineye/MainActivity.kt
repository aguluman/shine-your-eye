package com.invarware.shineye

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    private val shared = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        shared.value = sharedText(intent)
        setContent {
            ShineTheme { CheckScreen(app = application as ShineEyeApp, shared = shared.value) }
        }
    }

    // A second share or "Check for scam" while the app is already open must
    // replace the message instead of keeping the first one on screen.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shared.value = sharedText(intent)
    }
}

private fun sharedText(intent: Intent?): String? = when (intent?.action) {
    Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
    Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
    else -> null
}
