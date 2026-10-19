package ru.shturman.shots

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.shturman.Hud
import ru.shturman.NavState
import ru.shturman.routing.Lane
import ru.shturman.routing.LatLon
import ru.shturman.routing.NextTurn
import ru.shturman.routing.PaceNote
import ru.shturman.routing.Place
import ru.shturman.routing.RouteOption
import ru.shturman.routing.RoutePosition
import ru.shturman.routing.Weather
import ru.shturman.ui.MainActions
import ru.shturman.ui.MainLayout
import ru.shturman.ui.Pill
import ru.shturman.ui.SearchScreen
import ru.shturman.ui.WindshieldHud
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShturmanTheme
import ru.shturman.ui.themes.Themes

/**
 * Снимки экранов нового оформления на поддельной карте. Запуск — только в workflow shots.yml (-Pshots),
 * картинки попадают в ветку shots. Проверяем глазами вёрстку: размеры, отступы, что ничего не налезает.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi", application = android.app.Application::class)
class Shots {

    private val route = RouteOption(
        timeSec = 18 * 60.0, lengthM = 12_400.0,
        points = (0..40).map { LatLon(57.80 + it * 0.0028, 28.33 + (it % 7) * 0.0004) },
        maneuvers = emptyList(), label = "Самый быстрый"
    )
    private val slow = RouteOption(21 * 60.0, 13_100.0, route.points, emptyList(), "Без камер")
    private val calm = RouteOption(22 * 60.0, 12_900.0, route.points, emptyList(), "Спокойный")

    private val actions = MainActions(
        openSettings = {}, openSearch = {}, voiceSearch = {}, pickPlace = {}, clearRoute = {}, toggleVoice = {},
        overview = {}, recenter = {}, setSatellite = {}, setMap3d = {}, openMusic = {}, selectOption = {},
        setDeparture = {}, start = {}
    )

    private val trip = NavState(
        dataReady = true, navigating = true, options = listOf(route), selected = 0,
        position = RoutePosition(6, 1_700.0),
        turn = NextTurn(3, false, 320.0, "ул. Гоголя", lanes = listOf(Lane(2), Lane(2), Lane(66, active = 64))),
        notes = listOf(PaceNote(3, false, 320.0, 40.0, 45.0), PaceNote(4, true, 1_170.0, 60.0, 70.0)),
        speedKmh = 72, limitKmh = 60, destination = Place("ул. Гоголя, 12", "Псков", route.points.last())
    )

    private fun shot(name: String, content: @Composable () -> Unit) {
        ShColors.themeId = Themes.PREMIUM
        captureRoboImage("build/shots/$name.png") { ShturmanTheme { content() } }
    }

    @Test
    fun trip() = shot("01_trip") {
        MainLayout(trip, emptyList(), true, actions) { FakeMap(route = true) }
    }

    @Test
    fun tripAdvice() = shot("02_trip_advice") {
        MainLayout(
            trip.copy(curveAdviceKmh = 40, speedKmh = 93, hazard = "Впереди лежачий полицейский"),
            listOf(Pill("Ищу спутники: 4 из 9", warn = false)), false, actions
        ) { FakeMap(route = true) }
    }

    @Test
    fun tripRoundabout() = shot("03_trip_ring") {
        MainLayout(
            trip.copy(turn = NextTurn(3, false, 280.0, "Петровская", roundaboutExit = 2, exitAngle = 30.0), notes = emptyList(), limitKmh = null),
            emptyList(), true, actions
        ) { FakeMap(route = true) }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun tripSmall() = shot("04_trip_small") {
        MainLayout(trip.copy(curveAdviceKmh = 40), emptyList(), true, actions) { FakeMap(route = true) }
    }

    @Test
    @Config(qualifiers = "w852dp-h393dp-land-xxhdpi")
    fun tripLandscape() = shot("05_trip_land") {
        MainLayout(trip, emptyList(), true, actions) { FakeMap(route = true) }
    }

    @Test
    fun map() = shot("06_map") {
        MainLayout(
            NavState(dataReady = true, speedKmh = 48, home = Place("ул. Гоголя, 12", "Псков", route.points.last()),
                hud = Hud(weather = Weather("Ясно", -2, 1.0))),
            emptyList(), true, actions
        ) { FakeMap(route = false) }
    }

    @Test
    fun routes() = shot("07_routes") {
        MainLayout(
            NavState(
                dataReady = true, options = listOf(route, slow, calm), selected = 0,
                destination = Place("ул. Гоголя, 12", "Псков", route.points.last()),
                weather = Weather("Ясно", -2, 1.0)
            ),
            emptyList(), true, actions
        ) { FakeMap(route = true) }
    }

    @Test
    fun search() = shot("08_search") {
        SearchScreen(
            home = Place("ул. Гоголя, 12", "Псков", route.points.last()), work = null, onSetFavorite = { _, _ -> },
            results = listOf(
                Place("ул. Гоголя, 12", "Псков", route.points.last()),
                Place("ул. Гоголя, 41", "Псков", route.points[20]),
                Place("ул. Гоголя", "улица · Псков", route.points[10])
            ),
            searching = false, error = null, onQuery = {}, onPick = {}, onClose = {},
            initialQuery = "Гоголя", here = route.points.first()
        )
    }

    @Test
    fun hud() = shot("09_hud") {
        WindshieldHud(trip, mirror = false, onMirror = {}, onExit = {})
    }
}

/** Поддельная тёмная карта: кварталы, дороги и (по желанию) маршрут со стрелкой. */
@Composable
private fun FakeMap(route: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(Color(0xFF0D0D0E))
        val minor = Color(0xFF1A1A1C)
        val major = Color(0xFF242426)
        for (i in 0..6) drawLine(minor, Offset(w * (0.08f + i * 0.15f), 0f), Offset(w * (0.1f + i * 0.15f), h), 8f * density)
        for (i in 0..8) drawLine(minor, Offset(0f, h * (0.06f + i * 0.12f)), Offset(w, h * (0.07f + i * 0.12f)), 8f * density)
        drawLine(major, Offset(w * 0.5f, 0f), Offset(w * 0.5f, h), 14f * density)
        drawLine(major, Offset(0f, h * 0.5f), Offset(w, h * 0.48f), 14f * density)
        if (route) {
            val p = Path().apply {
                moveTo(w * 0.5f, h * 1.1f); lineTo(w * 0.5f, h * 0.52f); quadraticTo(w * 0.5f, h * 0.48f, w * 0.56f, h * 0.48f); lineTo(w * 1.1f, h * 0.47f)
            }
            drawPath(p, ShColors.Accent, style = Stroke(7f * density, cap = StrokeCap.Round))
            drawCircle(ShColors.Accent.copy(alpha = 0.14f), 22f * density, Offset(w * 0.5f, h * 0.73f))
        }
        val c = Offset(w * 0.5f, h * (if (route) 0.73f else 0.55f))
        val a = Path().apply {
            moveTo(c.x, c.y - 18f * density); lineTo(c.x + 11f * density, c.y + 10f * density)
            lineTo(c.x, c.y + 3f * density); lineTo(c.x - 11f * density, c.y + 10f * density); close()
        }
        drawPath(a, Color.White)
    }
}
