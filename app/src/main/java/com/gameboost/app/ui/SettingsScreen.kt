package com.gameboost.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.gameboost.app.core.GameSession
import com.gameboost.app.core.Prefs

private fun open(ctx: Context, intent: Intent) {
    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var boostOnLaunch by remember { mutableStateOf(prefs.boostOnLaunch) }
    var dnd by remember { mutableStateOf(prefs.dnd) }
    var overlay by remember { mutableStateOf(prefs.overlay) }

    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val overlayOk = remember(tick) { GameSession.overlayGranted(ctx) }
    val dndOk = remember(tick) { GameSession.dndGranted(ctx) }
    val notifOk = remember(tick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Pengaturan",
            style = MaterialTheme.typography.headlineMedium.copy(
                brush = Brush.horizontalGradient(listOf(Neon, Violet)),
                fontWeight = FontWeight.ExtraBold,
            ),
        )
        Spacer(Modifier.height(16.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Text("Saat membuka game", color = TextHi, fontWeight = FontWeight.Bold)
            SwitchRow("Boost otomatis", "Bersihkan RAM sebelum game dibuka.", boostOnLaunch) {
                boostOnLaunch = it; prefs.boostOnLaunch = it
            }
            SwitchRow("Mode fokus", "Hanya prioritas yang boleh berbunyi. Dipulihkan saat sesi diakhiri.", dnd) {
                dnd = it; prefs.dnd = it
            }
            SwitchRow("Overlay monitor", "Pil kecil berisi RAM, suhu, dan baterai di atas game.", overlay) {
                overlay = it; prefs.overlay = it
            }
        }

        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Izin", color = TextHi, fontWeight = FontWeight.Bold)
            PermRow("Tampil di atas aplikasi lain", "Dibutuhkan untuk overlay monitor.", overlayOk) {
                open(
                    ctx,
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                )
            }
            PermRow("Akses Jangan Ganggu", "Dibutuhkan untuk mode fokus.", dndOk) {
                open(ctx, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
            PermRow("Notifikasi", "Menampilkan tombol Akhiri sesi di bilah notifikasi.", notifOk) {
                open(
                    ctx,
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Yang tidak bisa dilakukan", color = TextHi, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tanpa root, aplikasi biasa tidak bisa menaikkan clock CPU/GPU atau mengukur FPS game lain. " +
                    "Game Boost fokus pada hal yang benar-benar diizinkan Android: membebaskan RAM, " +
                    "menahan gangguan notifikasi, dan memantau suhu.",
                color = TextLo, fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = TextHi, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(desc, color = TextLo, fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Neon,
                checkedTrackColor = Neon.copy(alpha = 0.35f),
            ),
        )
    }
}

@Composable
private fun PermRow(title: String, desc: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clickable(enabled = !granted, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = TextHi, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(desc, color = TextLo, fontSize = 12.sp)
        }
        Text(
            if (granted) "Aktif" else "Beri izin",
            color = if (granted) Lime else Color.White,
            fontWeight = FontWeight.Bold, fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (granted) Color(0x2269F0AE) else Violet)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
