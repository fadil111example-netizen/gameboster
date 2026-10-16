package com.gameboost.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.gameboost.app.core.Booster
import com.gameboost.app.core.BoostResult
import com.gameboost.app.core.GameSession
import com.gameboost.app.core.Prefs
import com.gameboost.app.core.SystemStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun thermalText(s: Int) = when (s) {
    0 -> "Normal"
    1 -> "Hangat"
    2 -> "Sedang"
    3 -> "Panas"
    4 -> "Kritis"
    5 -> "Darurat"
    else -> "Sangat kritis"
}

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf(SystemStats.snapshot(ctx)) }
    var boosting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BoostResult?>(null) }
    var session by remember { mutableStateOf(Prefs(ctx).sessionActive) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                stats = SystemStats.snapshot(ctx)
                session = Prefs(ctx).sessionActive
                delay(2000)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(
                "Game Boost",
                style = MaterialTheme.typography.headlineLarge.copy(
                    brush = Brush.horizontalGradient(listOf(Neon, Violet)),
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
            Text("Bersihkan RAM, pantau suhu, lalu main.", color = TextLo, fontSize = 13.sp)
        }

        Spacer(Modifier.height(8.dp))
        BoostHero(stats.ramPercent, boosting) {
            scope.launch {
                boosting = true
                result = Booster.boost(ctx)
                stats = SystemStats.snapshot(ctx)
                boosting = false
            }
        }

        AnimatedVisibility(result != null) {
            result?.let { r ->
                GlassCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Text(
                        if (r.freedMb > 0) "${r.freedMb} MB dibebaskan" else "RAM sudah optimal",
                        color = Lime, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${r.cleaned} aplikasi latar belakang dihentikan • RAM ${r.ramBefore}% → ${r.ramAfter}%",
                        color = TextLo, fontSize = 13.sp,
                    )
                }
            }
        }

        AnimatedVisibility(session) {
            GlassCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sesi game aktif", color = TextHi, fontWeight = FontWeight.Bold)
                        Text("Akhiri untuk memulihkan notifikasi.", color = TextLo, fontSize = 12.sp)
                    }
                    GradientButton("Akhiri") {
                        GameSession.end(ctx)
                        session = false
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniGauge(
                "RAM", "%.1f GB".format(stats.ramUsedMb / 1024.0),
                stats.ramPercent / 100f, Neon, Icons.Rounded.Memory, Modifier.weight(1f),
            )
            MiniGauge(
                "Penyimpanan", "%.0f GB bebas".format(stats.storageFreeGb),
                stats.storagePercent / 100f, Violet, Icons.Rounded.Storage, Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniGauge(
                "Suhu baterai", "%.1f°C".format(stats.tempC),
                (stats.tempC / 60f), if (stats.tempC >= 42f) Pink else Amber,
                Icons.Rounded.Thermostat, Modifier.weight(1f),
            )
            MiniGauge(
                "Baterai", "${stats.batteryLevel}%" + if (stats.charging) " ⚡" else "",
                stats.batteryLevel / 100f, Lime, Icons.Rounded.BatteryChargingFull,
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        MiniGauge(
            "Prosesor", "${stats.cores} inti • termal ${thermalText(stats.thermal)}",
            stats.thermal / 6f, Neon, Icons.Rounded.Speed, Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Cara kerja Boost", color = TextHi, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Boost menghentikan proses latar belakang aplikasi pengguna agar RAM lebih lega. " +
                    "Android modern membatasi hal ini, jadi hasilnya paling terasa saat banyak aplikasi terbuka. " +
                    "Aplikasi sistem dan game di perpustakaanmu tidak disentuh.",
                color = TextLo, fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
