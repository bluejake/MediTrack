package com.medilog.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.medilog.app.ui.theme.SfPro
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.medilog.app.R
import com.medilog.app.ui.components.rememberDrawablePainter
import com.medilog.app.ui.glass.GlassButton
import com.medilog.app.ui.glass.GlassCard
import com.medilog.app.ui.glass.LocalBackdrop
import com.medilog.app.ui.theme.AppTheme
import com.medilog.app.ui.theme.AppThemeMode
import com.medilog.app.ui.theme.ThemePrefs
import com.medilog.app.ui.theme.mediTextStyle
import com.medilog.app.ui.theme.colors

/** 崩溃详情展示页 */
class CrashActivity : ComponentActivity() {

    // 审计 U4：应用内语言（API<33）同步作用于崩溃详情页
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.medilog.app.util.LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 主题模式持久化：崩溃页与主进程一致（决定 MD3 / MIUIX 分支，避免玻璃态误入）
        ThemePrefs.init(this)

        var text = intent.getStringExtra(EXTRA_TEXT) ?: ""
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path != null) text = text + "\n\n" + getString(R.string.crash_log_file, path)

        setContent {
            // 与主界面同一套主题分发（提供 LocalAppThemeMode / LocalMd3Colors）：
            // 此前用 MediTheme 不提供主题模式 → 组件默认走 MATERIAL 分支，
            // MIUIX 用户的崩溃页会拿到 MD3 实心面，与主界面液态玻璃不一致。
            AppTheme(mode = ThemePrefs.mode.value) {
                CrashContent(
                    text = text,
                    onCopy = {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("crash", text))
                            Toast.makeText(this, R.string.crash_copied, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onClose = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_TEXT = "crash_text"
        const val EXTRA_PATH = "crash_path"
    }
}

@Composable
private fun CrashContent(
    text: String,
    onCopy: () -> Unit,
    onClose: () -> Unit
) {
    val c = colors
    val backdrop = rememberLayerBackdrop()
    // MIUIX 保留液态玻璃环境光；MATERIAL 按 MD3 收敛为干净表面（无 iOS 底光）
    val isMaterial = ThemePrefs.mode.value == AppThemeMode.MATERIAL

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        if (!isMaterial) {
            Image(
            painter = rememberDrawablePainter(R.drawable.bg_ambient),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
            contentScale = ContentScale.Crop
        )
        }

        androidx.compose.runtime.CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                BasicText(
                    stringResource(R.string.crash_title),
                    style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold))
                )
                Spacer(Modifier.height(12.dp))
                GlassCard(Modifier.weight(1f).fillMaxWidth(), radius = 18.dp, tintAlpha = 0.2f) {
                    Box(Modifier.fillMaxSize()) {
                        BasicText(
                            text,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp),
                            style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 12.sp))
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton(
                        stringResource(R.string.crash_copy),
                        onClick = onCopy,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        stringResource(R.string.alert_close),
                        onClick = onClose,
                        modifier = Modifier.weight(1f),
                        tint = c.primary,
                        textColor = Color.White
                    )
                }
            }
        }
    }
}
