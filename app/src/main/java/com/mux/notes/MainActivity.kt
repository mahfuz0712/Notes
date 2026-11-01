package com.mux.notes

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Store.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        setContent { App() }
    }

    override fun onStop() {
        Store.flush()
        super.onStop()
    }
}

private val TABS = listOf("NOTES", "ARCHIVE", "TRASH", "SYS")

@Composable
fun App() {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var tab by remember { mutableIntStateOf(0) }
    var openId by remember { mutableStateOf<String?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var locked by remember { mutableStateOf(Prefs.hasPin) }

    LaunchedEffect(Unit) { Store.load() }
    LaunchedEffect(Unit) {
        if (isOnline(ctx)) update = checkUpdate(ctx).second
    }
    // lock again whenever the app goes to the background
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP && Prefs.hasPin) locked = true
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    fun closeEditor() {
        val id = openId
        if (id != null) {
            Store.flush()
            Store.discardIfBlank(id)
        }
        openId = null
    }
    BackHandler(enabled = openId != null && !locked) { closeEditor() }

    update?.let { u ->
        UpdateDialog(
            u, appVersion(ctx),
            onUpdate = { openUrl(ctx, u.apkUrl); update = null },
            onCancel = { update = null }
        )
    }

    HudBackground {
        if (locked) {
            LockScreen { locked = false }
        } else {
            val id = openId
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (id != null) {
                    EditorHost(id) { closeEditor() }
                } else {
                    Header()
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (tab) {
                            0 -> NotesScreen(0) { openId = it }
                            1 -> NotesScreen(1) { openId = it }
                            2 -> NotesScreen(2) { openId = it }
                            else -> SysScreen { update = it }
                        }
                        if (tab == 0) {
                            Chip(
                                "+ NEW NOTE", true,
                                Modifier.align(Alignment.BottomEnd).padding(16.dp).width(140.dp)
                            ) { showNew = true }
                        }
                    }
                    NavBar(tab) { tab = it }
                }
            }
            if (showNew) {
                NewNoteDialog(
                    onPick = { type, budget ->
                        val n = Store.create(type, budget)
                        showNew = false
                        tab = 0
                        openId = n.id
                    },
                    onClose = { showNew = false }
                )
            }
        }
    }
}

@Composable
private fun Header() {
    val count = Store.notes.count { !it.archived && !it.trashed }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("NOTES", 20f, cMain, true, Modifier.weight(1f))
            T("[ $count ]", 11f, cSub, align = TextAlign.End)
        }
        T("// MUX NOTES CORE", 11f, cSub)
        Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(cDim))
    }
}

@Composable
private fun NavBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF050B10)).padding(top = 10.dp, bottom = 6.dp)) {
        TABS.forEachIndexed { i, name ->
            Column(
                Modifier.weight(1f).clickable { onSelect(i) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                T(name, 12f, if (i == selected) cMain else cSub, i == selected)
                Box(
                    Modifier.padding(top = 4.dp).width(28.dp).height(2.dp)
                        .background(if (i == selected) cMain else Color.Transparent)
                )
            }
        }
    }
}
