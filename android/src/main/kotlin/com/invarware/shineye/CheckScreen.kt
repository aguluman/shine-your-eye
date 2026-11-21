package com.invarware.shineye

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.invarware.shineye.core.CloudConsent
import kotlinx.coroutines.launch

private sealed interface Screen {
    data object Idle : Screen
    data object Working : Screen
    class Consent(val checker: Checker, val pending: Outcome.NeedsConsent) : Screen
    class Result(val done: Outcome.Done) : Screen
}

@Composable
fun CheckScreen(app: ShineEyeApp, shared: String?) {
    var text by rememberSaveable { mutableStateOf(shared.orEmpty()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Idle) }
    val scope = rememberCoroutineScope()

    fun show(checker: Checker, outcome: Outcome) {
        screen = when (outcome) {
            is Outcome.Done -> Screen.Result(outcome)
            is Outcome.NeedsConsent -> Screen.Consent(checker, outcome)
        }
    }

    fun check() = scope.launch {
        screen = Screen.Working
        val checker = app.checker()
        show(checker, app.check(checker, text))
    }

    LaunchedEffect(shared) { if (!shared.isNullOrBlank()) check() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Shine your eye", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Paste a message about your bank account. It is checked on your phone first.",
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
            label = { Text("Message") },
        )
        Button(onClick = { check() }, enabled = text.isNotBlank() && screen != Screen.Working) {
            Text("Check message")
        }

        when (val current = screen) {
            Screen.Idle -> Unit
            Screen.Working -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Checking on your phone…", style = MaterialTheme.typography.bodyMedium)
            }
            is Screen.Consent -> ConsentCard(
                pending = current.pending,
                onSend = { always ->
                    if (always) app.consent = CloudConsent.ALWAYS
                    scope.launch {
                        screen = Screen.Working
                        show(current.checker, current.checker.accept(current.pending))
                    }
                },
                onKeep = {
                    scope.launch {
                        screen = Screen.Working
                        show(current.checker, current.checker.decline(current.pending))
                    }
                },
            )
            is Screen.Result -> ResultCard(current.done)
        }
    }
}

@Composable
private fun ConsentCard(pending: Outcome.NeedsConsent, onSend: (Boolean) -> Unit, onKeep: () -> Unit) {
    var always by remember { mutableStateOf(false) }
    val redacted = pending.analysis.redacted
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Get a second opinion from the cloud?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            if (pending.deviceOpinion == null) "A stronger check runs in the cloud. Only this copy would leave your phone:"
            else "Gemini Nano wasn't sure. Only this copy would leave your phone:",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            redacted.text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(12.dp),
        )
        val removed = redacted.describe()
        if (removed.isNotEmpty()) {
            Text("Removed: ${removed.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
        }
        Row {
            Checkbox(checked = always, onCheckedChange = { always = it })
            Text("Always send redacted copies", modifier = Modifier.padding(top = 12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onSend(always) }) { Text("Send redacted copy") }
            OutlinedButton(onClick = onKeep) { Text("Keep it on my phone") }
        }
    }
}

@Composable
private fun ResultCard(done: Outcome.Done) {
    val verdict = done.verdict
    val tone = LocalVerdicts.current.of(verdict.kind)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
    ) {
        Spacer(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(tone, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
        )
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(verdict.kind.headline, style = MaterialTheme.typography.titleLarge, color = tone, fontWeight = FontWeight.Bold)
            Text(verdict.kind.advice, style = MaterialTheme.typography.bodyLarge)
            verdict.signals.forEach { Text("• ${it.explanation}", style = MaterialTheme.typography.bodyMedium) }
            val modelReasons = verdict.opinion?.reasons.orEmpty().distinct()
            if (modelReasons.isNotEmpty()) {
                Text("What the AI noticed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                modelReasons.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
            Text("How this was checked", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            done.trail.forEachIndexed { index, hop ->
                Text("${index + 1}. ${hop.label}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
