package com.androidstudiomobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// DeviceSkinOverlay.kt
//
// Composable que desenha a moldura completa de um dispositivo Android
// usando Canvas vetorial puro (sem PNG externos).
// Cada skin inclui: corpo, bezel, punch-hole/notch, status bar, nav bar,
// botões laterais e borda da câmera.
//
// Uso:
//   DeviceSkinOverlay(DeviceProfiles.PIXEL_8, isLandscape = false) {
//       MyScreen()
//   }
// ─────────────────────────────────────────────────────────────────────────────

data class DeviceSkin(
    val name: String,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val cornerRadiusDp: Int   = 32,
    val bezelTopDp: Int       = 48,
    val bezelBottomDp: Int    = 56,
    val bezelSideDp: Int      = 10,
    val hasPunchHole: Boolean = true,
    val hasNotch: Boolean     = false,
    val hasSideButtons: Boolean = true,
    val bodyColor: Color      = Color(0xFF1A1A2E),
    val frameColor: Color     = Color(0xFF3A3A5E),
    val screenColor: Color    = Color(0xFF000000),
    val category: String      = "Phone"
)

object DeviceProfiles {
    val PIXEL_6          = DeviceSkin("Pixel 6",        411, 914, 32, 44, 54, 10, bodyColor = Color(0xFF1B5E20), frameColor = Color(0xFF2E7D32))
    val PIXEL_7          = DeviceSkin("Pixel 7",        412, 892, 34, 46, 54, 10)
    val PIXEL_8          = DeviceSkin("Pixel 8",        412, 892, 36, 48, 56, 10, bodyColor = Color(0xFF0D47A1), frameColor = Color(0xFF1565C0))
    val PIXEL_8_PRO      = DeviceSkin("Pixel 8 Pro",    412, 924, 38, 48, 60, 11, bodyColor = Color(0xFF4A148C), frameColor = Color(0xFF6A1B9A))
    val PIXEL_9          = DeviceSkin("Pixel 9",        412, 910, 38, 48, 58, 10, bodyColor = Color(0xFF1A237E), frameColor = Color(0xFF283593))
    val SAMSUNG_S24      = DeviceSkin("Samsung S24",    384, 832, 38, 44, 52,  9, bodyColor = Color(0xFF263238), frameColor = Color(0xFF37474F))
    val SAMSUNG_S24_ULTRA= DeviceSkin("S24 Ultra",      384, 870,  8, 42, 54,  9)
    val PIXEL_FOLD       = DeviceSkin("Pixel Fold",     616, 462, 20, 36, 44, 10, category = "Foldable")
    val PIXEL_TABLET     = DeviceSkin("Pixel Tablet",  1280, 800, 20, 24, 24, 20, hasPunchHole = false, category = "Tablet")
    val NEXUS_10         = DeviceSkin("Nexus 10",      1280, 800, 16, 28, 28, 24, hasPunchHole = false, category = "Tablet")
    val WEAROS_ROUND     = DeviceSkin("WearOS Round",   384, 384,192,  8,  8,  8, hasPunchHole = false, hasSideButtons = false, category = "Watch")
    val ANDROID_TV       = DeviceSkin("Android TV",    1920,1080,  8,  8,  8,  8, hasPunchHole = false, hasSideButtons = false, category = "TV")

    val all = listOf(PIXEL_6, PIXEL_7, PIXEL_8, PIXEL_8_PRO, PIXEL_9,
        SAMSUNG_S24, SAMSUNG_S24_ULTRA, PIXEL_FOLD, PIXEL_TABLET, NEXUS_10,
        WEAROS_ROUND, ANDROID_TV)
}

@Composable
fun DeviceSkinOverlay(
    profile: DeviceSkin,
    isLandscape: Boolean   = false,
    isDarkMode: Boolean    = false,
    modifier: Modifier     = Modifier,
    content: @Composable () -> Unit
) {
    val sw      = if (isLandscape) profile.screenHeightDp  else profile.screenWidthDp
    val sh      = if (isLandscape) profile.screenWidthDp   else profile.screenHeightDp
    val bTop    = if (isLandscape) profile.bezelSideDp     else profile.bezelTopDp
    val bBot    = if (isLandscape) profile.bezelSideDp     else profile.bezelBottomDp
    val bSide   = if (isLandscape) profile.bezelTopDp      else profile.bezelSideDp
    val cr      = if (profile.category == "Watch") sw / 2  else profile.cornerRadiusDp

    Box(modifier, contentAlignment = Alignment.Center) {

        Canvas(Modifier.width((sw + bSide * 2).dp).height((sh + bTop + bBot).dp)) {
            drawBody(profile, sw, sh, bTop, bBot, bSide, cr)
            if (profile.hasPunchHole) drawPunchHole(sw, bSide, bTop)
            if (profile.hasNotch)     drawNotch(sw, bSide, bTop)
            if (profile.hasSideButtons) drawSideButtons(sh, bSide, bTop)
            drawStatusBar(sw, bSide, bTop)
            drawNavBar(sw, sh, bSide, bTop, bBot)
        }

        // Conteúdo do usuário, recortado para a área de tela
        Box(
            Modifier
                .width(sw.dp)
                .height(sh.dp)
                .clip(RoundedCornerShape(cr.dp))
        ) { content() }
    }
}

// ── draw helpers ──────────────────────────────────────────────────────────────

private fun DrawScope.drawBody(
    p: DeviceSkin, sw: Int, sh: Int,
    bTop: Int, bBot: Int, bSide: Int, cr: Int
) {
    val totalW = (sw + bSide * 2).dp.toPx()
    val totalH = (sh + bTop + bBot).dp.toPx()
    val crPx   = cr.dp.toPx()

    // Corpo externo
    val body = Path().apply {
        addRoundRect(RoundRect(Rect(Offset.Zero, Size(totalW, totalH)),
            CornerRadius(crPx + 6.dp.toPx())))
    }
    drawPath(body, p.bodyColor)
    drawPath(body, p.frameColor, style = Stroke(2.dp.toPx()))

    // Área da tela
    val screen = Path().apply {
        addRoundRect(RoundRect(
            Rect(Offset(bSide.dp.toPx(), bTop.dp.toPx()), Size(sw.dp.toPx(), sh.dp.toPx())),
            CornerRadius(crPx)
        ))
    }
    drawPath(screen, p.screenColor)
}

private fun DrawScope.drawPunchHole(sw: Int, bSide: Int, bTop: Int) {
    val cx = bSide.dp.toPx() + sw.dp.toPx() / 2
    val cy = (bTop / 2).dp.toPx()
    drawCircle(Color(0xFF0A0A0A), 5.5.dp.toPx(), Offset(cx, cy))
    drawCircle(Color(0xFF1A1A1A), 4.dp.toPx(),   Offset(cx, cy))
}

private fun DrawScope.drawNotch(sw: Int, bSide: Int, bTop: Int) {
    val nw = 120.dp.toPx(); val nh = 28.dp.toPx()
    val cx = bSide.dp.toPx() + sw.dp.toPx() / 2
    drawPath(Path().apply {
        moveTo(cx - nw / 2, 0f);      lineTo(cx + nw / 2, 0f)
        lineTo(cx + nw / 2 + 16.dp.toPx(), nh)
        lineTo(cx - nw / 2 - 16.dp.toPx(), nh); close()
    }, Color(0xFF111111))
}

private fun DrawScope.drawSideButtons(sh: Int, bSide: Int, bTop: Int) {
    val x  = 0f
    val y1 = (bTop + 80).dp.toPx()
    val w  = 3.5f.dp.toPx(); val c = CornerRadius(2.dp.toPx())
    drawRoundRect(Color(0xFF555555), Offset(x - w, y1),         Size(w, 40.dp.toPx()), c)
    drawRoundRect(Color(0xFF555555), Offset(x - w, y1 + 52.dp.toPx()), Size(w, 28.dp.toPx()), c)
    // Power button right side
    val rx = size.width; val ry = (bTop + 90).dp.toPx()
    drawRoundRect(Color(0xFF555555), Offset(rx, ry), Size(w, 36.dp.toPx()), c)
}

private fun DrawScope.drawStatusBar(sw: Int, bSide: Int, bTop: Int) {
    val fg  = Color.White.copy(alpha = 0.85f)
    val bx  = bSide.dp.toPx(); val by = bTop.dp.toPx()
    val h   = 22.dp.toPx()
    // Battery
    val bw = 18.dp.toPx(); val bh = 9.dp.toPx()
    val bo = Offset(bx + sw.dp.toPx() - bw - 4.dp.toPx(), by + h / 2 - bh / 2)
    drawRoundRect(fg, bo, Size(bw, bh), CornerRadius(2.dp.toPx()), style = Stroke(1.5.dp.toPx()))
    drawRoundRect(fg, Offset(bo.x + bw, bo.y + bh * 0.3f), Size(2.dp.toPx(), bh * 0.4f), CornerRadius(1.dp.toPx()))
    drawRoundRect(fg, Offset(bo.x + 1.dp.toPx(), bo.y + 1.dp.toPx()),
        Size(bw * 0.7f - 1.dp.toPx(), bh - 2.dp.toPx()), CornerRadius(1.dp.toPx()))
    // Wi-Fi arcs
    repeat(3) { i ->
        val r = (3 + i * 3).dp.toPx()
        drawArc(fg, 225f, 90f, false,
            Offset(bo.x - 24.dp.toPx() - r, by + h / 2 - r / 2), Size(r, r),
            style = Stroke(1.5.dp.toPx()))
    }
}

private fun DrawScope.drawNavBar(sw: Int, sh: Int, bSide: Int, bTop: Int, bBot: Int) {
    val fg  = Color.White.copy(alpha = 0.6f)
    val by  = (bTop + sh).dp.toPx() + (bBot.dp.toPx() - 28.dp.toPx()) / 2 + 14.dp.toPx()
    val cx  = bSide.dp.toPx() + sw.dp.toPx() / 2

    // ◀  Back
    drawPath(Path().apply {
        moveTo(cx - 56.dp.toPx(), by + 6.dp.toPx())
        lineTo(cx - 68.dp.toPx(), by)
        lineTo(cx - 56.dp.toPx(), by - 6.dp.toPx()); close()
    }, fg)
    // ⬤  Home
    drawCircle(fg, 7.dp.toPx(), Offset(cx, by), style = Stroke(1.5.dp.toPx()))
    // ▣  Recents
    drawRoundRect(fg, Offset(cx + 50.dp.toPx(), by - 6.dp.toPx()),
        Size(12.dp.toPx(), 12.dp.toPx()), CornerRadius(2.dp.toPx()), style = Stroke(1.5.dp.toPx()))
}
