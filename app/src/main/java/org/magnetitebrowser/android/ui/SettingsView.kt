package org.magnetitebrowser.android.ui

import androidx.core.content.edit
import androidx.core.net.toUri
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.magnetitebrowser.android.BuildConfig
import org.magnetitebrowser.android.model.Address
import org.magnetitebrowser.android.model.Browser

@Composable
fun SettingsView(browser: Browser, onJoin: () -> Unit, onDone: () -> Unit) {
    val sync = browser.sync
    var search by remember {
        mutableStateOf(sync.prefs.getString(Browser.SEARCH, null) ?: Address.engines[0].second)
    }
    var leaving by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }

    Screen("Settings", onDone) {
        Title("Search")
        for ((label, template) in Address.engines) {
            val pick = {
                search = template
                sync.prefs.edit { putString(Browser.SEARCH, template) }
            }
            Row(Modifier.fillMaxWidth().clickable(onClick = pick), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = search == template, onClick = pick)
                Text(label)
            }
        }

        Title("Sync")
        val server = sync.server
        if (server == null) {
            OutlinedButton(onClick = onJoin) { Text("Join a Sync Chain…") }
            Hint("Shows the spaces, favorites and pinned tabs of Magnetite on your computer.")
        } else {
            Value("Server", server.toUri().host ?: server)
            Value(
                "Last sync",
                when {
                    sync.busy -> "Now…"
                    else -> sync.lastSync?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: "Not yet"
                },
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Show Tabs on Other Devices", Modifier.weight(1f))
                Switch(checked = sync.sharesTabs, onCheckedChange = {
                    sync.shareTabs(it)
                    browser.shareTabsSoon()
                })
            }
            Row {
                OutlinedButton(onClick = browser::syncSoon, enabled = !sync.busy) { Text("Sync Now") }
                TextButton(onClick = { leaving = true }, Modifier.padding(start = 8.dp)) {
                    Text("Leave on This Device…", color = MaterialTheme.colorScheme.error)
                }
            }
            val error = sync.lastError
            Hint(
                error ?: "This device reads the chain's sidebar and does not change it. Your other devices can list the tabs open here and take one over; tabs of private spaces are never listed.",
                error = error != null,
            )
        }

        Title("Website data")
        OutlinedButton(onClick = { clearing = true }) {
            Text("Clear Website Data…", color = MaterialTheme.colorScheme.error)
        }
        Hint("Cookies, logins and site storage of every space on this device.")

        Title("About")
        Value("Version", BuildConfig.VERSION_NAME)
    }

    if (leaving) {
        Confirm(
            "Leave the sync chain on this device?",
            "The chain and your other devices stay as they are. The tabs open here stay too.",
            "Leave", onConfirm = sync::leave, onClose = { leaving = false },
        )
    }
    if (clearing) {
        Confirm(
            "Clear website data of every space?", "You are signed out of the sites you use here.",
            "Clear", onConfirm = browser::clearWebsiteData, onClose = { clearing = false },
        )
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text, Modifier.padding(top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Value(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Ask before something that cannot be taken back. */
@Composable
private fun Confirm(title: String, text: String, action: String, onConfirm: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onClose()
            }) { Text(action, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
