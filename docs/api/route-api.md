# Движок маршрутов APEX для приложения «Заказы»

Водитель APEX ничего из этого не видит. Пользоваться может только приложение, подписанное тем же ключом
(разрешение `ru.apex.permission.ROUTE_API`, уровень `signature`). Адреса из запросов не сохраняются.

## 1. Расчёт маршрутов — служба
`bindService(Intent("ru.apex.action.ROUTE_API").setPackage("ru.shturmani"))`, обмен через `Messenger`.
В каждом запросе и ответе `v = 1`. Ответ приходит с тем же `what`; при ошибке в нём строка `error`:
`DATA_NOT_READY`, `NO_LOCATION`, `NO_ROUTE`, `BAD_REQUEST`, `VERSION`, `FAILED`.

| what | запрос | ответ |
|---|---|---|
| 0 PING | — | `dataReady: Boolean`, `apexBuild: Int` |
| 1 GEOCODE | `query: String` (от 3 букв) | `results: ArrayList<Bundle{name, address, lat, lon}>` |
| 2 ROUTE | `departAt: Long` (мс, 0 = сейчас), `fromCurrent: Boolean` (по умолч. true) или `fromLat/fromLon`, `stops: ArrayList<Bundle{lat, lon, name, arriveBy, leaveAt}>` (1–12) | `legs: ArrayList<Bundle{fromIdx, toIdx, polyline, timeSec, lengthM, departBy, late}>` |

- Этап `i` ведёт от точки `i-1` (для первого — от текущего места) к остановке `i`; `fromIdx = -1` — старт.
- Следующий этап выезжает не раньше `leaveAt` предыдущей остановки (конец заказа) и не раньше прибытия.
- Время считается так же, как водителю: свои скорости по часу выезда, погода, сезон.
- `polyline` — Google Encoded Polyline, точность 6. `departBy = arriveBy − время − 10 мин`. `late` — не успеть к `arriveBy`.

## 2. «Поехать» — запуск ведения
`startActivity(Intent("ru.apex.action.NAVIGATE").setPackage("ru.shturmani"))` с extras:
`v=1, lat, lon, name, source="zakazy", orderId: Long, hasNext: Boolean`.
APEX открывается, ждёт вход и дорожные данные, строит маршрут и сам запускает ведение (голос как обычно).

## 3. «Следующий заказ»
По приезде, только для таких поездок и только если `hasNext`, APEX показывает кнопку «Следующий заказ».
Нажатие: `Intent("ru.zakazy.action.NEXT_ORDER").setPackage("ru.zakazy")` с `orderId` заказа, на который приехали.
