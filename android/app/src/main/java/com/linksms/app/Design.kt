package com.linksms.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LinkSmsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF9DD8BB), onPrimary = Color(0xFF123B2C),
        primaryContainer = Color(0xFF234C3B), onPrimaryContainer = Color(0xFFD3F3DF),
        background = Color(0xFF101914), surface = Color(0xFF15201B),
        surfaceVariant = Color(0xFF24352B), onSurface = Color(0xFFE6EEE6), onSurfaceVariant = Color(0xFFAABCAF),
        outlineVariant = Color(0xFF34473B)
    ) else lightColorScheme(
        primary = Color(0xFF286347), onPrimary = Color.White,
        primaryContainer = Color(0xFFE1EEDC), onPrimaryContainer = Color(0xFF244B35),
        background = Color(0xFFF5F7F1), surface = Color(0xFFFFFEFA),
        surfaceVariant = Color(0xFFEDF1E6), onSurface = Color(0xFF21392C), onSurfaceVariant = Color(0xFF677761),
        outlineVariant = Color(0xFFDDE5D5)
    )
    MaterialTheme(colorScheme = colors, shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(26.dp)), content = content)
}

@Composable
fun BrandHeader(showLock: Boolean, onLock: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Forum, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("LinkSMS", fontWeight = FontWeight.Bold, fontSize = 23.sp, letterSpacing = (-0.7).sp)
            Text("YOUR PRIVATE INBOX", fontSize = 9.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showLock) FilledTonalIconButton(onClick = onLock) { Icon(Icons.Outlined.Lock, contentDescription = "Lock inbox") }
    }
}

@Composable
fun LockedPanel(error: String, onUnlock: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScrollCompat().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(140.dp).background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background)), RoundedCornerShape(70.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Fingerprint, null, modifier = Modifier.size(66.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(28.dp))
        Text("A little privacy.\nA lot of peace of mind.", fontSize = 29.sp, lineHeight = 35.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, letterSpacing = (-0.8).sp)
        Spacer(Modifier.height(14.dp))
        Text("Use your fingerprint, supported face unlock,\nor device PIN to open your inbox.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 23.sp, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(18.dp)) {
            Icon(Icons.Outlined.LockOpen, null, Modifier.size(19.dp)); Spacer(Modifier.width(10.dp)); Text("Unlock inbox")
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(Icons.Outlined.Sync, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Background sync stays on while locked", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsGroup(title: String, subtitle: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Column { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            content()
        }
    }
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier = this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))

@Composable
fun SourceTag(text: String, icon: ImageVector) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(7.dp)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
