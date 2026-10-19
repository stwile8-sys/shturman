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

    private fun shot(name: String, theme: Int = Themes.PREMIUM, content: @Composable () -> Unit) {
        ru.shturman.ui.themes.UiTest.instant = true
        ShColors.themeId = theme
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
    fun tripClassic() = shot("10_trip_classic", theme = 0) {
        MainLayout(trip.copy(curveAdviceKmh = 40), listOf(Pill("Возможен гололёд", warn = true)), true, actions) { FakeMap(route = true) }
    }

    @Test
    fun mapNightRider() = shot("11_map_nightrider", theme = 4) {
        MainLayout(NavState(dataReady = true, hud = Hud(weather = Weather("Снег", -5, 0.8))), emptyList(), true, actions) { FakeMap(route = false) }
    }

    @Test
    fun roadData() = shot("12_road_data") {
        // поддельная база: несколько «улиц» вокруг Пскова, разные часы и скорости
        val now = System.currentTimeMillis()
        val rnd = java.util.Random(7)
        val cells = ArrayList<ru.shturman.routing.CellRow>()
        val baseA = (57.81 / 0.0012).toLong()
        val baseB = (28.33 / 0.0022).toLong()
        for (street in 0 until 9) {
            for (i in 0 until 40) {
                val a = baseA + (if (street % 2 == 0) i - 20 else street * 4 - 18)
                val b = baseB + (if (street % 2 == 0) street * 3 - 12 else i - 20)
                repeat(1 + rnd.nextInt(6)) {
                    val day = rnd.nextInt(7)
                    val hour = listOf(7, 8, 8, 9, 13, 17, 18, 18, 19, 22)[rnd.nextInt(10)]
                    cells += ru.shturman.routing.CellRow(a, b, day * 24 + hour, 2.0 + rnd.nextDouble() * 15.0, 1 + rnd.nextInt(6), now - rnd.nextInt(20) * 24L * 3600 * 1000)
                }
            }
        }
        val stops = (0 until 30).map {
            ru.shturman.routing.StopRow(57.79 + rnd.nextDouble() * 0.04, 28.31 + rnd.nextDouble() * 0.06, 32, 20.0 + rnd.nextDouble() * 50, 2, now)
        }
        ru.shturman.ui.RoadDataContent(
            stats = ru.shturman.routing.computeRoadStats(cells, stops, now),
            info = ru.shturman.ui.CollectInfo("25 мин назад", "Отправлено 42, получено 1 380", "Фоновый сбор на этом телефоне: ждёт поездку. Обмен с общей базой — сам раз в 2 часа, когда есть интернет.", 3),
            syncing = false, onSync = {}, onClose = {}
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
