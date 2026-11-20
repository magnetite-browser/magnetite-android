package org.magnetitebrowser.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.magnetitebrowser.android.model.Browser
import uniffi.magnetite_mobile_core.defaultServer

/** A screen over the app: its title, a button to leave it, and what it holds. */
@Composable
fun Screen(title: String, onDone: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onDone)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDone) { Icon(Icons.Default.Close, "Done") }
            }
            content()
        }
    }
}

/** A line under a part of a screen that says what it does, or what went wrong. */
@Composable
fun Hint(text: String, error: Boolean = false) {
    Text(
        text, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Join a sync chain: scan the QR code of a computer that syncs, or enter the server and the 24 words. */
@Composable
fun JoinView(browser: Browser, onDone: () -> Unit) {
    val sync = browser.sync
    val scope = rememberCoroutineScope()
    var ownServer by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf("") }
    var words by rememberSaveable { mutableStateOf("") }
    var scanning by rememberSaveable { mutableStateOf(false) }
    // A sync code has 24 words.
    val complete = words.split(Regex("\\s+")).count { it.isNotEmpty() } == 24 && !(ownServer && address.isBlank())
    val plain = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false)

    // One join at a time: a second one would write over the first one's chain.
    var joining by remember { mutableStateOf(false) }
    val join = { server: String, code: String ->
        if (!joining) scope.launch {
            joining = true
            sync.join(server, code)
            joining = false
            if (sync.joined) {
                browser.syncNow()
                onDone()
            }
        }
    }

    Screen("Join a Sync Chain", onDone) {
        OutlinedButton(onClick = { scanning = true }, Modifier.fillMaxWidth().testTag("join.scan"), enabled = !sync.busy) {
            Text("Scan QR Code")
        }
        Hint("On your computer: Settings → Sync → Show sync code. Or enter its server and words below.")
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !ownServer, onClick = { ownServer = false }, label = { Text("Magnetite Sync") })
            FilterChip(selected = ownServer, onClick = { ownServer = true }, label = { Text("Your own server") })
        }
        if (ownServer) {
            OutlinedTextField(
                address, { address = it }, Modifier.fillMaxWidth().padding(top = 8.dp).testTag("join.server"),
                placeholder = { Text("https://sync.example.org") }, singleLine = true,
                keyboardOptions = plain.copy(keyboardType = KeyboardType.Uri),
            )
        }
        OutlinedTextField(
            words, { words = it }, Modifier.fillMaxWidth().padding(top = 16.dp).testTag("join.words"),
            label = { Text("Sync code") }, placeholder = { Text("24 words") }, minLines = 4,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            // Not a password, but nothing the keyboard should learn.
            keyboardOptions = plain.copy(keyboardType = KeyboardType.Password),
        )
        Hint("The words stay on this device, encrypted with a key of its key store.")
        Button(
            onClick = { join(if (ownServer) address.trim() else defaultServer(), words) },
            Modifier.fillMaxWidth().padding(top = 20.dp).testTag("join.go"),
            enabled = complete && !sync.busy,
        ) {
            if (sync.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Join")
        }
        val error = sync.lastError
        Hint(
            error ?: "This device shows the chain's spaces, favorites and pinned tabs, and does not change them. Your other devices see the tabs open here.",
            error = error != null,
        )
    }
    if (scanning) {
        ScannerView(
            onRead = { link ->
                scanning = false
                // Shown in the form too, so a join that fails can be looked at.
                ownServer = link.server != defaultServer()
                address = if (ownServer) link.server else ""
                words = link.words
                join(link.server, link.words)
            },
            onDone = { scanning = false },
        )
    }
}
