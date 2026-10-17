package ru.shturman.ui.themes

import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.shturman.R

private fun ease(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
private fun lin(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

/**
 * Видео-заставка APEX (res/raw/apex_intro_v.mp4 — для телефона, apex_intro_h.mp4 — для магнитолы, по ориентации экрана).
 * Звук — из самого видео. Касание пропускает заставку. Уход — как выключение старого кинескопа:
 * кадр схлопывается в светящуюся линию, потом в точку и гаснет, открывая приложение.
 * Если видео не открылось, заставка сразу уходит и не мешает запуску.
 */
@Composable
fun ApexIntro(onFinished: () -> Unit) {
    val conf = LocalConfiguration.current
    val portrait = conf.screenHeightDp >= conf.screenWidthDp
    val res = if (portrait) R.raw.apex_intro_v else R.raw.apex_intro_h
    val ratio = if (portrait) 9f / 16f else 16f / 9f
    val finish by rememberUpdatedState(onFinished)
    val out = remember { Animatable(0f) }
    val flags = remember { booleanArrayOf(false, false) } // [0] уход начат, [1] заставка закрыта
    val scope = rememberCoroutineScope()
    val player = remember { MediaPlayer() }

    fun leave(fast: Boolean) {
        if (flags[0]) return
        flags[0] = true
        SplashGate.covering = false
        scope.launch {
            out.animateTo(1f, tween(if (fast) 480 else 680, easing = LinearEasing)) {
                val v = (1f - value).coerceIn(0f, 1f)
                runCatching { player.setVolume(v, v) }
            }
            runCatching { player.stop() }
            if (!flags[1]) { flags[1] = true; finish() }
        }
    }

    DisposableEffect(Unit) {
        SplashGate.covering = true
        onDispose {
            SplashGate.covering = false
            runCatching { player.release() }
        }
    }
    // Страховка: если видео почему-то не закончилось само, уходим через 12 секунд
    LaunchedEffect(Unit) {
        delay(12_000L)
        leave(false)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val o = out.value
                if (o > 0f) {
                    scaleY = 1f - 0.994f * ease(lin(o, 0f, 0.45f))
                    scaleX = 1f - 0.995f * ease(lin(o, 0.4f, 0.85f))
                    alpha = 1f - lin(o, 0.78f, 1f)
                }
            }
            .drawWithContent {
                drawContent()
                val o = out.value
                if (o > 0f) drawRect(Color(0xFFF3F1EA).copy(alpha = 0.92f * ease(lin(o, 0.08f, 0.42f))))
            }
            .background(Color(0xFF060605)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { c ->
                TextureView(c).apply {
                    isOpaque = false
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                            runCatching {
                                c.resources.openRawResourceFd(res).use { afd ->
                                    player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                }
                                player.setAudioAttributes(
                                    AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                        .build()
                                )
                                player.setSurface(Surface(st))
                                player.setOnPreparedListener { if (!flags[0]) it.start() }
                                player.setOnCompletionListener { leave(false) }
                                player.setOnErrorListener { _, _, _ -> leave(true); true }
                                player.prepareAsync()
                            }.onFailure { leave(true) }
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            runCatching { player.setSurface(null) }
                            return true
                        }

                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                    }
                }
            },
            modifier = Modifier.aspectRatio(ratio)
        )
        // Касание в любом месте пропускает заставку
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { leave(true) } })
    }
}
