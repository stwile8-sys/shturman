# APEX (бывший «Штурман») — навигатор для Android

Владелец — не программист, пишет по-русски с телефона. Отвечать по-русски, простыми словами, итог — коротко:
что изменилось для водителя, номер сборки. Без отчёта о шагах.

## Что это
Kotlin + Jetpack Compose (BOM 2024.09), карта MapLibre 11.5.2, маршруты офлайн через Valhalla 3.9.1
(`io.github.rallista:valhalla-mobile:0.6.4`: `routeRaw` / `traceAttributesRaw`, нативный JSON Valhalla), Firebase (вход Google, Firestore).
minSdk 26, targetSdk 34. Дорожные данные — Псковская область (релизы `roaddata-*`).
Внутренние имена не менять: applicationId `ru.shturmani`, пакет Kotlin `ru.shturman`, ключи настроек.

## Где что
- `NavViewModel.kt` — состояние экрана (`NavState`) и поездка: `onFix` → место на маршруте, подсказки голосом, ограничения, сигнал превышения, съезд с маршрута.
- `routing/` — `Router` (запрос и разбор Valhalla: манёвры, кольца `roundabout_exit_count`, полосы `turn_lanes`), `RouteModel` (`nextTurn`), `PaceNotes` (дорожник, совет скорости), `Voice` + `VoiceClips` (голос — только готовые записи), `Deviation`, `Overspeed`, `Sun`, `GpsTracker`, `SpeedStore`/`SharedSync` (общая база).
- `ui/` — `MainScreen` (состояние, системные окна, шторки) и `MainLayout` (раскладка нового оформления «Спорт-премиум»: поездка, карта,
  выбор маршрута, горизонтальный экран; высоты блоков меряются, чтобы ничего не перекрывалось), `Sport.kt` (панель манёвра, «ЗАТЕМ»,
  кольцо скорости, стеклянные кнопки, карточки), `ApexIcons` (контурные значки), `MapView` (камера, стрелка, обзор маршрута, масштаб от скорости),
  `RoutePanel`, `SearchScreen` (+ `Recents`), `RoadSigns`, `WindshieldHud` (проекция на лобовое), `MiniPlayer`, `SettingsSheet`,
  `themes/` (темы, заставки, звуки; `ThemeMotion` — у каждой темы свой характер анимаций, кроме APEX).
- `service/` — `NavService` (уведомление поездки), `DriveWatch` (фоновый сбор обезличенных данных о дорогах), `BootReceiver`.
- `update/` — `Updater` (релизы `build-N` на GitHub), `AutoUpdate` (фоновое обновление).
- `app/src/test` — юнит-тесты логики (JVM). `docs/voice` — как делать новые фразы тем же голосом. `docs/design` — макеты и план
  нового оформления (`redesign-plan.md`). `app/src/shots` + `.github/workflows/shots.yml` — снимки экранов на поддельной карте
  (Robolectric + Roborazzi), картинки — в ветке `shots`: `git fetch -q origin '+refs/heads/shots:refs/remotes/origin/shots'`.

## Правила владельца (не нарушать)
- Камеры скорости не рисовать на карте и не озвучивать — они только в варианте маршрута «Без камер».
- Голос навигатора, звуки и время заставок не менять. Новые фразы — только записями тем же голосом (Piper irina, `docs/voice`);
  на Huawei нет синтезатора речи, поэтому на каждую фразу, которую говорит навигатор, нужна запись в `VoiceClips`.
- Логику экранов без просьбы не менять. Тема «Классика» — заставка с самолётом и welcome.ogg.
- Данные о поездках — только обезличенно.
- Не делать функций для гонок по дорогам общего пользования и ухода от полиции.
- Превышение до +15 км/ч считается допустимым: сигнал (один раз на превышение) и красная скорость — только выше.

## Сборка и выпуск
Локально Android SDK нет — собирает GitHub Actions (`.github/workflows/build.yml`: `testDebugUnitTest` + `assembleDebug`).
- Работа идёт в ветке `night`: push → сборка; тестовый APK `night-N` видит только владелец.
- Слияние в `main` = релиз `build-N` «APEX, сборка N»; его сразу получают все друзья (окно обновления и фоновое автообновление).
  Выпускать, когда задача готова и сборка `night` зелёная.
- Дождаться сборки (простой `sleep N` запрещён):
  `H=$(git rev-parse HEAD); until gh run list --branch night --limit 3 --json headSha,status -q ".[] | select(.headSha==\"$H\") | .status" | grep -q completed; do sleep 10; done`
- Ошибки упавшей сборки: `git fetch -q origin '+refs/heads/buildlog:refs/remotes/origin/buildlog' && git show origin/buildlog:errors.txt | grep "^e: "`
- Предупреждения: `git fetch -q origin '+refs/heads/buildwarn:refs/remotes/origin/buildwarn' && git show origin/buildwarn:warnings.txt`
  (старые, их не трогаем: unchecked cast в `NavViewModel`, `Music.kt`, `Notification.Builder`).
- Выпуск: `git checkout -q main && git merge -q --ff-only origin/main && git merge -q --no-ff night -m "…" && git push -q origin main && git checkout -q night`,
  дождаться сборки `main`, проверить: `gh api repos/stwile8-sys/shturman/releases --jq '[.[] | select(.tag_name|startswith("build-"))][0] | "\(.tag_name) | \(.name)"'`
- GitHub GraphQL недоступен — только REST (`gh api repos/…`). Создавать и удалять релизы из сеанса нельзя — их делает сборка.

## Стиль кода
- Комментарии и тексты интерфейса — по-русски, понятным языком. Сообщения коммитов — по-русски, что изменилось для водителя.
- Оформление «Спорт-премиум» у всех тем, кроме APEX: шрифт Montserrat (`ShText.Hero/Value/Caps/...`), цвета — из темы (`ShColors`),
  стекло — `Modifier.panel(...)`, кнопки на карте — `GlassButton` 52 dp, значки — `ApexIcons`. Новые экраны — по макетам (холст «Спорт-премиум»).
- В `DrawScope` внутри вложенных лямбд — `this@drawWithContent.drawContent()`.
- Новые настройки — в SharedPreferences `settings`, в `NavState` и в `resetSettings()`.

## Помощники (`.claude/agents`)
`apex-reviewer` — проверка кода перед выпуском; `apex-tester` — юнит-тесты логики; `apex-ui` — интерфейс;
`apex-release` — сборка и выпуск; `apex-voice` — новые голосовые фразы тем же голосом.
