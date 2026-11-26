package org.magnetitebrowser.android.ui

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.magnetitebrowser.android.BuildConfig
import org.magnetitebrowser.android.model.Address
import org.magnetitebrowser.android.model.Browser

@Composable
fun SettingsView(browser: Browser, onJoin: () -> Unit, onArchive: () -> Unit, onDone: () -> Unit) {
    val sync = browser.sync
    var search by remember {
        mutableStateOf(browser.searchTemplate)
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

        DefaultBrowser()

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
            OutlinedButton(onClick = onArchive) { Text("Archive…") }
            Hint("The tabs closed on your devices, and what you removed from the sidebar here. A tab can be brought back from it.")
            Row {
                OutlinedButton(onClick = browser::syncSoon, enabled = !sync.busy) { Text("Sync Now") }
                TextButton(onClick = { leaving = true }, Modifier.padding(start = 8.dp)) {
                    Text("Leave on This Device…", color = MaterialTheme.colorScheme.error)
                }
            }
            val error = sync.lastError
            Hint(
                error ?: "What you change in the sidebar here reaches your other devices when they sync. They can list the tabs open here and take one over; tabs of private spaces are never listed.",
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

/**
 * Whether links of other apps open here. The system keeps that choice: the
 * button asks it (Android 10 and later), or leads to its default apps. Read
 * again whenever the screen comes back, as from the system's settings.
 */
@Composable
private fun DefaultBrowser() {
    val context = LocalContext.current
    val roles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) context.getSystemService(RoleManager::class.java) else null
    fun held(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roles != null) return roles.isRoleHeld(RoleManager.ROLE_BROWSER)
        // Before roles: whose app the system opens a web link with.
        val link = Intent(Intent.ACTION_VIEW, "https://example.org/".toUri())
        val handler = context.packageManager.resolveActivity(link, PackageManager.MATCH_DEFAULT_ONLY)
        return handler?.activityInfo?.packageName == context.packageName
    }
    var isDefault by remember { mutableStateOf(held()) }
    LifecycleResumeEffect(Unit) {
        isDefault = held()
        onPauseOrDispose {}
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isDefault = held() }

    Title("Default Browser")
    if (isDefault) {
        Hint("Magnetite is your default browser: links from other apps open here, as new tabs.")
    } else {
        OutlinedButton(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roles?.isRoleAvailable(RoleManager.ROLE_BROWSER) == true) {
                ask.launch(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
            } else {
                // A device without that screen gets the settings' start.
                val apps = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                runCatching { context.startActivity(apps) }
                    .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
            }
        }) { Text("Make Default Browser…") }
        Hint("Links from other apps then open here, as new tabs.")
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
