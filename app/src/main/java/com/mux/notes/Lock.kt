package com.mux.notes

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 4 digit PIN keypad. [onDone] gets the entered PIN, the pad clears itself. */
@Composable
fun PinPad(title: String, hint: String, error: String, onDone: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T(title, 16f, cMain, true)
        T(hint, 11f, cSub, lines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            for (i in 0 until 4) {
                Box(
                    Modifier.size(16.dp).border(1.5.dp, cMain)
                        .background(if (i < pin.length) cMain else Color.Transparent)
                )
            }
        }
        if (error.isNotEmpty()) T(error, 11f, cBad, true)
        for (row in listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "<").chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in row) {
                    Chip(k, false, Modifier.width(78.dp)) {
                        when (k) {
                            "C" -> pin = ""
                            "<" -> pin = pin.dropLast(1)
                            else -> if (pin.length < 4) {
                                pin += k
                                if (pin.length == 4) {
                                    val entered = pin
                                    pin = ""
                                    onDone(entered)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    var err by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().safeDrawingPadding(), Alignment.Center) {
        Panel("NOTES LOCKED") {
            PinPad("ENTER PIN", "Your notes are protected", err) { p ->
                if (Prefs.checkPin(p)) onUnlock() else err = "WRONG PIN"
            }
        }
    }
}
