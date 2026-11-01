package com.mux.notes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SysScreen(onUpdateFound: (UpdateInfo) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var dataMsg by remember { mutableStateOf("") }
    // PIN flow: 0 idle, 1 enter new, 2 confirm new, 3 enter current to remove
    var pinStage by remember { mutableIntStateOf(0) }
    var firstPin by remember { mutableStateOf("") }
    var pinErr by remember { mutableStateOf("") }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            dataMsg = try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Store.exportAll().toByteArray()) }
                "BACKUP SAVED"
            } catch (e: Exception) { "BACKUP FAILED" }
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            dataMsg = try {
                val text = ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } ?: ""
                val n = Store.importAll(text)
                if (n < 0) "NOT A VALID BACKUP FILE" else "RESTORED $n NOTES"
            } catch (e: Exception) { "RESTORE FAILED" }
        }
    }

    val active = Store.notes.count { !it.archived && !it.trashed }
    val archived = Store.notes.count { it.archived && !it.trashed }
    val trashed = Store.notes.count { it.trashed }

    Page {
        T("SYSTEM", 16f, cMain, true)
        TwoCol(
            {
                Panel("INTERFACE COLOR") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ACCENTS.forEachIndexed { i, a -> Chip(a.name, Prefs.accent == i, Modifier.weight(1f)) { Prefs.updateAccent(i) } }
                    }
                }
                Panel("SECURITY") {
                    if (pinStage == 0) {
                        KV("PIN LOCK", if (Prefs.hasPin) "ON" else "OFF", if (Prefs.hasPin) cGood else cSub)
                        T(
                            "The app locks when you leave it. If you forget the PIN you must clear the app data, which deletes the notes, so make a backup first.",
                            10f, cSub, lines = 5, mod = Modifier.padding(vertical = 6.dp)
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip(if (Prefs.hasPin) "CHANGE PIN" else "SET PIN", false, Modifier.weight(1f)) { pinErr = ""; pinStage = 1 }
                            if (Prefs.hasPin) Chip("REMOVE PIN", false, Modifier.weight(1f)) { pinErr = ""; pinStage = 3 }
                        }
                    } else {
                        when (pinStage) {
                            1 -> PinPad("NEW PIN", "Choose 4 digits", pinErr) { p -> firstPin = p; pinErr = ""; pinStage = 2 }
                            2 -> PinPad("CONFIRM PIN", "Enter the same 4 digits again", pinErr) { p ->
                                if (p == firstPin) {
                                    Prefs.setPin(p)
                                    pinStage = 0
                                } else {
                                    pinErr = "DID NOT MATCH, TRY AGAIN"
                                    pinStage = 1
                                }
                            }
                            else -> PinPad("REMOVE PIN", "Enter your current PIN", pinErr) { p ->
                                if (Prefs.checkPin(p)) {
                                    Prefs.clearPin()
                                    pinStage = 0
                                } else pinErr = "WRONG PIN"
                            }
                        }
                        Chip("CANCEL", false, Modifier.fillMaxWidth().padding(top = 10.dp)) { pinStage = 0 }
                    }
                }
                Panel("DATA") {
                    KV("NOTES", "$active")
                    KV("ARCHIVED", "$archived")
                    KV("IN TRASH", "$trashed")
                    T("Backup is one JSON file with every note, drawing and sheet. Restore adds notes that are missing or older.", 10f, cSub, lines = 4, mod = Modifier.padding(vertical = 6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("BACKUP", false, Modifier.weight(1f)) { saveLauncher.launch("mux-notes-backup.json") }
                        Chip("RESTORE", false, Modifier.weight(1f)) { openLauncher.launch(arrayOf("*/*")) }
                    }
                    if (dataMsg.isNotEmpty()) T(dataMsg, 11f, cGood, mod = Modifier.padding(top = 6.dp))
                }
            },
            {
                Panel("UPDATES") {
                    KV("INSTALLED VERSION", "v${appVersion(ctx)}")
                    Chip("CHECK FOR UPDATES", false, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        scope.launch {
                            status = "CHECKING..."
                            if (!isOnline(ctx)) {
                                status = "NO INTERNET CONNECTION"
                                return@launch
                            }
                            val (ok, info) = checkUpdate(ctx)
                            status = when {
                                !ok -> "CHECK FAILED"
                                info == null -> "UP TO DATE"
                                else -> "NEW VERSION AVAILABLE: v${info.version}"
                            }
                            if (info != null) onUpdateFound(info)
                        }
                    }
                    if (status.isNotEmpty()) T(status, 11f, cSub, mod = Modifier.padding(top = 6.dp))
                }
                Panel("ABOUT") {
                    T("NOTES", 18f, cMain, true)
                    T("v${appVersion(ctx)}  com.mux.notes", 10f, cSub, mod = Modifier.padding(bottom = 8.dp))
                    T("DEVELOPER", 10f, cSub)
                    T(AppInfo.DEV_NAME, 14f, cText, true, Modifier.padding(bottom = 6.dp))
                    T("EMAIL", 10f, cSub)
                    T(AppInfo.DEV_EMAIL, 13f, cMain, mod = Modifier.clickable { openMail(ctx, AppInfo.DEV_EMAIL) }.padding(bottom = 6.dp))
                    T("GITHUB", 10f, cSub)
                    T("github.com/${AppInfo.GITHUB_USER}", 13f, cMain, mod = Modifier.clickable { openUrl(ctx, "https://github.com/${AppInfo.GITHUB_USER}") }.padding(bottom = 6.dp))
                    T("MORE APPS", 10f, cSub)
                    T("zApps Store", 13f, cMain, mod = Modifier.clickable { openUrl(ctx, AppInfo.MORE_APPS) })
                }
            }
        )
    }
}
