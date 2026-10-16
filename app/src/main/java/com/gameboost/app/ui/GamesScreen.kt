package com.gameboost.app.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gameboost.app.core.AppItem
import com.gameboost.app.core.Apps
import com.gameboost.app.core.GameSession
import com.gameboost.app.core.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GamesScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(ctx) }
    var apps by remember { mutableStateOf<List<AppItem>?>(null) }
    var manual by remember { mutableStateOf(prefs.manualGames) }
    var hidden by remember { mutableStateOf(prefs.hiddenGames) }
    var showPicker by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { Apps.launchable(ctx) }
    }

    val all = apps
    val library = all?.filter { (it.isGame && it.pkg !in hidden) || it.pkg in manual } ?: emptyList()

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Perpustakaan Game",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        brush = Brush.horizontalGradient(listOf(Neon, Violet)),
                        fontWeight = FontWeight.ExtraBold,
                    ),
                )
                Text("Main dari sini: boost + mode fokus otomatis.", color = TextLo, fontSize = 13.sp)
            }
            GradientButton("Tambah", icon = Icons.Rounded.Add) { showPicker = true }
        }
        Spacer(Modifier.height(16.dp))

        when {
            all == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Neon)
            }
            library.isEmpty() -> GlassCard(Modifier.fillMaxWidth()) {
                Text("Belum ada game", color = TextHi, fontWeight = FontWeight.Bold)
                Text(
                    "Ketuk Tambah untuk memilih game dari aplikasi yang terpasang.",
                    color = TextLo, fontSize = 13.sp,
                )
            }
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(library, key = { it.pkg }) { item ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                bitmap = item.icon, contentDescription = null,
                                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.label, color = TextHi, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text("Boost + mode fokus", color = TextLo, fontSize = 12.sp)
                            }
                            IconButton(onClick = {
                                manual = manual - item.pkg
                                hidden = hidden + item.pkg
                                prefs.manualGames = manual
                                prefs.hiddenGames = hidden
                            }) {
                                Icon(Icons.Rounded.Close, "Hapus dari daftar", tint = TextLo)
                            }
                            GradientButton(
                                text = if (busy == item.pkg) "…" else "Main",
                                icon = Icons.Rounded.PlayArrow,
                                enabled = busy == null,
                            ) {
                                scope.launch {
                                    busy = item.pkg
                                    val r = GameSession.launch(ctx, item.pkg)
                                    busy = null
                                    if (r != null) {
                                        Toast.makeText(
                                            ctx, "Game mode aktif • ${r.freedMb} MB dibebaskan",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPicker && all != null) {
        var query by remember { mutableStateOf("") }
        val candidates = all.filter {
            it.pkg !in library.map { l -> l.pkg } && it.label.contains(query, ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            containerColor = Surface1,
            title = { Text("Tambah game", color = TextHi) },
            text = {
                Column {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        singleLine = true, placeholder = { Text("Cari aplikasi") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.height(320.dp)) {
                        items(candidates, key = { it.pkg }) { item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        manual = manual + item.pkg
                                        hidden = hidden - item.pkg
                                        prefs.manualGames = manual
                                        prefs.hiddenGames = hidden
                                        showPicker = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Image(
                                    bitmap = item.icon, contentDescription = null,
                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    item.label, color = TextHi,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text("Tutup", color = Neon) }
            },
        )
    }
}
