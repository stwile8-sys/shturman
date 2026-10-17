# Фоновый сбор данных о дорогах

Решения владельца (17.10): данные обезличенные, как сейчас (скорости по квадратам + ожидание на светофорах, без имени и маршрутов);
писать всегда, когда едет, даже при закрытом приложении; выключатель «Делиться данными» убрать.

Сделано (ветка wip-roaddata, ещё не собиралось): service/DriveWatch.kt (служба DriveWatchService: дремлет → проверка GPS 2 мин → запись;
объект DriveWatch: start/poke/hasBackground/batteryFree/text; BootReceiver), SpeedStore.shared(), NavViewModel и SyncWorker на shared,
DriveWatchWorker раз в 15 мин (Workers.kt), SharedSync.share = всегда true.

Сделано 17.10 после сброса лимита (пункты 1–7), выпуск — пункт 8:
1. NavViewModel: удалить setShareData (share теперь val); в onFix: lastCarSpeedAt (скорость ≥ 6 м/с) и stopRecorder.add в навигации
   ИЛИ если быстрее 6 м/с ехали меньше 5 мин назад.
2. SettingsSheet: подпись раздела «Сбор данных о дорогах · отчёт разработчику»; в PrivacyPage вместо SwitchRow — RoadDataCard():
   текст что собирается и что нет, статус (DriveWatch), кнопки «Разрешить положение «Всегда»» и «Снять ограничение батареи»,
   подсказка для Huawei/Honor (Батарея → Запуск приложений → APEX → вручную, все три пункта).
3. Manifest: ACCESS_BACKGROUND_LOCATION, RECEIVE_BOOT_COMPLETED, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS; <service .service.DriveWatchService
   exported=false foregroundServiceType=location>; <receiver .service.BootReceiver exported=true> BOOT_COMPLETED, MY_PACKAGE_REPLACED,
   android.intent.action.QUICKBOOT_POWERON.
4. MainActivity.onCreate: DriveWatch.start(applicationContext). MapView: после выдачи разрешения — DriveWatch.start; в DisposableEffect
   с GpsTracker: DriveWatch.uiTracking = true, в onDispose = false.
5. Одноразовый диалог после разрешения на местоположение (pref "bg_asked"): объяснение + запрос ACCESS_BACKGROUND_LOCATION, затем
   ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.
6. AutoStartReceiver ACL_CONNECTED: DriveWatch.start(ctx, poke = true) для выбранной машины или Bluetooth-класса «автомобиль/громкая связь».
7. Report: строка DriveWatch.text(ctx).
8. Собрать в CI (ветка night), исправить ошибки, влить в main, короткий отчёт владельцу по-русски + шаги для Huawei.
