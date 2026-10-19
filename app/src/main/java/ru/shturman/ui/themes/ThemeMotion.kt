package ru.shturman.ui.themes

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import kotlin.math.PI
import kotlin.math.sin

/**
 * Характер движения темы в новом оформлении. Панели, кнопки и стекло у всех тем одинаковые,
 * а движение у каждой своё — спокойное, без «салюта». У APEX свои анимации (Flavor.APEX), сюда она не входит.
 */
enum class Motion {
    /** Спорт-премиум: точные быстрые въезды снизу, тонкая красная линия на старте. */
    PRECISE,
    /** Классика: неоновое «включение» — короткое мерцание и раскрытие по высоте, как экран. */
    POWER_ON,
    /** Принцесса: мягкое всплытие с лёгким увеличением. */
    FLOAT,
    /** NASCAR: рывок сбоку, как машина со смены полосы. */
    SIDE_DASH,
    /** GT Racing: шторка слева направо, как выезд с пит-лейна. */
    WIPE,
    /** Night Rider: проявление из глубины с лёгким уменьшением, синий след на старте. */
    EMERGE,
    /** Street Underground: спуск сверху, янтарный луч фар на старте. */
    DROP,
    /** Rally Gravel: въезд справа с небольшим заносом, пыль на старте. */
    DUST,
    /** Drift Night: медленное туманное проявление. */
    FOG
}

object Motions {
    fun of(themeId: Int): Motion = when (themeId) {
        0 -> Motion.POWER_ON
        1 -> Motion.FLOAT
        2 -> Motion.SIDE_DASH
        3 -> Motion.WIPE
        4 -> Motion.EMERGE
        5 -> Motion.DROP
        6 -> Motion.DUST
        7 -> Motion.FOG
        else -> Motion.PRECISE
    }

    /** Как долго и по какой кривой появляется панель. */
    fun enterSpec(m: Motion): AnimationSpec<Float> = when (m) {
        Motion.PRECISE -> tween(260, easing = FastOutSlowInEasing)
        Motion.POWER_ON -> tween(340, easing = LinearEasing)
        Motion.FLOAT -> spring(dampingRatio = 0.78f, stiffness = 170f)
        Motion.SIDE_DASH -> tween(220, easing = FastOutSlowInEasing)
        Motion.WIPE -> tween(300, easing = FastOutSlowInEasing)
        Motion.EMERGE -> tween(380, easing = FastOutSlowInEasing)
        Motion.DROP -> spring(dampingRatio = 0.72f, stiffness = 420f)
        Motion.DUST -> spring(dampingRatio = 0.62f, stiffness = 260f)
        Motion.FOG -> tween(520, easing = LinearOutSlowInEasing)
    }

    /** Неоновое мерцание при включении: два коротких провала, потом ровный свет. */
    fun flicker(v: Float): Float = when {
        v >= 1f -> 1f
        v < 0.12f -> 0f
        v < 0.24f -> 0.75f
        v < 0.34f -> 0.2f
        v < 0.5f -> 0.9f
        v < 0.58f -> 0.5f
        else -> 1f
    }

    /**
     * Появление панели: v от 0 до 1 (у пружин может чуть перелетать 1). Шторку (WIPE) рисует сам themedEnter,
     * здесь — прозрачность, сдвиг и масштаб.
     */
    fun GraphicsLayerScope.enter(m: Motion, v: Float) {
        val q = 1f - v
        val a = v.coerceIn(0f, 1f)
        when (m) {
            Motion.PRECISE -> { alpha = a; translationY = q * 12f * density }
            Motion.POWER_ON -> {
                alpha = flicker(v)
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                scaleY = 0.9f + 0.1f * a
            }
            Motion.FLOAT -> { alpha = a; translationY = q * 22f * density; val s = 0.96f + 0.04f * v; scaleX = s; scaleY = s }
            Motion.SIDE_DASH -> { alpha = (v * 1.6f).coerceIn(0f, 1f); translationX = -q * 28f * density }
            Motion.WIPE -> { alpha = (v * 2f).coerceIn(0f, 1f) }
            Motion.EMERGE -> { alpha = a; val s = 1.04f - 0.04f * v; scaleX = s; scaleY = s }
            Motion.DROP -> { alpha = a; translationY = -q * 14f * density }
            Motion.DUST -> { alpha = a; translationX = q * 20f * density; rotationZ = q * 1.5f }
            Motion.FOG -> { alpha = a * a; translationY = q * 6f * density }
        }
    }

    /** Смена содержимого (новый поворот): v идёт от 1 к 0. */
    fun GraphicsLayerScope.change(m: Motion, v: Float) {
        val k = v.coerceIn(0f, 1f)
        when (m) {
            Motion.PRECISE -> { alpha = 1f - 0.6f * k; translationY = 6f * density * v }
            Motion.POWER_ON -> alpha = if (k > 0.6f) 0.3f else if (k > 0.3f) 0.85f else 1f
            Motion.FLOAT -> { alpha = 1f - 0.4f * k; val s = 1f - 0.04f * v; scaleX = s; scaleY = s }
            Motion.SIDE_DASH -> { alpha = 1f - 0.6f * k; translationX = 16f * density * v }
            Motion.WIPE -> { alpha = 1f - 0.7f * k; translationX = -10f * density * v }
            Motion.EMERGE -> { alpha = 1f - 0.5f * k; val s = 1f + 0.04f * v; scaleX = s; scaleY = s }
            Motion.DROP -> { alpha = 1f - 0.6f * k; translationY = -8f * density * v }
            Motion.DUST -> { alpha = 1f - 0.3f * k; translationX = sin(v * PI.toFloat() * 6f) * 2.5f * density * v }
            Motion.FOG -> alpha = 1f - 0.7f * k
        }
    }

    /** Отклик на нажатие: p от 0 (отпущено) до 1 (нажато). */
    fun GraphicsLayerScope.press(m: Motion, p: Float) {
        when (m) {
            Motion.FLOAT -> { val s = 1f - 0.06f * p; scaleX = s; scaleY = s }
            Motion.SIDE_DASH -> { translationX = 3f * density * p; val s = 1f - 0.03f * p; scaleX = s; scaleY = s }
            Motion.DROP -> { translationY = 2f * density * p; val s = 1f - 0.03f * p; scaleX = s; scaleY = s }
            Motion.POWER_ON -> { alpha = 1f - 0.25f * p; val s = 1f - 0.03f * p; scaleX = s; scaleY = s }
            else -> { val s = 1f - 0.04f * p; scaleX = s; scaleY = s }
        }
    }
}
