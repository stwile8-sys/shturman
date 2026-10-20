package ru.shturman.routing

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

/** Клан: название и кто глава. */
class ClanInfo(val id: String, val name: String, val ownerUid: String, val ownerName: String)

/** Участник клана: где он, с какой скоростью и куда едет (если сам разрешил показывать). */
class ClanMember(
    val uid: String,
    val name: String,
    val position: LatLon?,
    val speedKmh: Int,
    val updated: Long,
    val route: List<LatLon>?,
    val destTitle: String?,
    val sharing: Boolean,
    /** Цвет участника на карте, например "#FF8A3D". */
    val color: String
)

/** Цвета участников на карте: у каждого свой, по его uid. */
private val CLAN_COLORS = listOf("#FF8A3D", "#3DDCFF", "#7CFF6B", "#FF5C8A", "#B08CFF", "#FFD400", "#4DFFD2", "#FF6B4D")

fun clanColor(uid: String): String = CLAN_COLORS[Math.floorMod(uid.hashCode(), CLAN_COLORS.size)]

/** Положение свежее (участник сейчас на связи): не старше двух минут. */
const val CLAN_FRESH_MS = 2 * 60_000L

/** Дольше суток участник на карте не показывается. */
const val CLAN_SHOW_MS = 24 * 3600_000L

/** Идентификатор клана из названия: без регистра и лишних пробелов («Апекс  Псков» → «апекс_псков»). */
fun clanId(name: String): String =
    name.trim().lowercase().filter { it.isLetterOrDigit() || it == ' ' || it == '-' }
        .split(' ').filter { it.isNotEmpty() }.joinToString("_")

fun clanPassHash(salt: String, password: String): String =
    MessageDigest.getInstance("SHA-256").digest("$salt:$password".toByteArray())
        .joinToString("") { "%02x".format(it) }

/** Что сейчас с участником, коротко: «62 км/ч · в Псков», «стоит», «был 15 мин назад», «скрыл себя». */
fun clanStatus(m: ClanMember, now: Long): String {
    if (!m.sharing) return "скрыл себя"
    if (m.position == null || m.updated <= 0L) return "ещё не выходил на связь"
    val age = now - m.updated
    if (age > CLAN_FRESH_MS) {
        val min = age / 60_000L
        return when {
            min < 60 -> "был $min мин назад"
            min < 24 * 60 -> "был ${min / 60} ч назад"
            else -> "давно не был на связи"
        }
    }
    val dest = m.destTitle?.takeIf { it.isNotBlank() }?.let { " · едет: $it" } ?: ""
    return (if (m.speedKmh >= 5) "${m.speedKmh} км/ч" else "стоит") + dest
}

/** Маршрут для отправки: не больше 300 точек, чтобы документ был лёгким (первая и последняя — всегда). */
fun thinRoute(points: List<LatLon>, max: Int = 300): List<LatLon> {
    if (points.size <= max) return points
    val step = (points.size - 1).toDouble() / (max - 1)
    return List(max) { i -> points[minOf(points.size - 1, Math.round(i * step).toInt())] }
}

/**
 * Кланы: постоянная группа друзей. Каждый участник видит на карте остальных — где они, с какой
 * скоростью едут и по какому маршруту. Вступить можно только по названию и паролю. Глава — создатель
 * клана; он может передать главенство другому участнику, исключить участника и сменить пароль.
 *
 * Firestore: clans/{id} — название, глава, соль и хеш пароля; clans/{id}/members/{uid} — имя,
 * положение, скорость, маршрут. Пароль в открытом виде нигде не хранится.
 */
class Clan(
    private val onInfo: (ClanInfo?) -> Unit,
    private val onMembers: (List<ClanMember>) -> Unit,
    /** Человека исключили или клан распущен. */
    private val onLost: (String) -> Unit
) {
    private val db get() = FirebaseFirestore.getInstance()
    private val user get() = FirebaseAuth.getInstance().currentUser
    private var id: String? = null
    private var info: ClanInfo? = null
    private var membersReg: ListenerRegistration? = null
    private var docReg: ListenerRegistration? = null
    private var seenSelf = false
    private var lastSent = 0L
    private var lastPos: LatLon? = null
    private var lastSpeed = -1
    private var sentRoute: List<LatLon>? = null
    private var routeSent = false

    /** Показывать ли меня клану. */
    var sharing = true
        private set

    val currentId: String? get() = id
    val isOwner: Boolean get() = info?.ownerUid != null && info?.ownerUid == user?.uid

    private fun clans() = db.collection("clans")
    private fun myName() = user?.displayName?.substringBefore(' ')?.takeIf { it.isNotBlank() } ?: "Участник"

    /** Создаёт клан. Создатель — глава. */
    suspend fun create(name: String, password: String, share: Boolean): ClanInfo {
        val me = user ?: throw IllegalStateException("Нужен вход в аккаунт")
        val title = name.trim().replace(Regex("\\s+"), " ")
        val newId = clanId(title)
        if (newId.length < 3) throw IllegalStateException("Название — хотя бы 3 буквы или цифры")
        if (password.length < 4) throw IllegalStateException("Пароль — хотя бы 4 знака")
        val salt = java.util.UUID.randomUUID().toString()
        val ref = clans().document(newId)
        db.runTransaction { tr ->
            if (tr.get(ref).exists()) throw IllegalStateException("Клан с таким названием уже есть")
            tr.set(ref, mapOf(
                "name" to title, "ownerUid" to me.uid, "ownerName" to myName(),
                "salt" to salt, "passHash" to clanPassHash(salt, password), "created" to System.currentTimeMillis()
            ))
            Unit
        }.await()
        addSelf(newId, share)
        attach(newId)
        return ClanInfo(newId, title, me.uid, myName())
    }

    /** Вступает в клан по названию и паролю. */
    suspend fun join(name: String, password: String, share: Boolean): ClanInfo {
        val me = user ?: throw IllegalStateException("Нужен вход в аккаунт")
        val joinId = clanId(name)
        if (joinId.isEmpty()) throw IllegalStateException("Введите название клана")
        val doc = clans().document(joinId).get().await()
        if (!doc.exists()) throw IllegalStateException("Клана с таким названием нет")
        val salt = doc.getString("salt") ?: ""
        if (clanPassHash(salt, password) != doc.getString("passHash")) throw IllegalStateException("Неверный пароль")
        addSelf(joinId, share)
        attach(joinId)
        return ClanInfo(joinId, doc.getString("name") ?: name, doc.getString("ownerUid") ?: "", doc.getString("ownerName") ?: "")
    }

    /** Возвращается в свой клан после перезапуска приложения. false — клана больше нет или нас исключили. */
    suspend fun resume(savedId: String, share: Boolean): Boolean {
        val me = user ?: return true // вход ещё не готов: не считаем, что клан потерян
        val doc = clans().document(savedId).get().await()
        if (!doc.exists()) return false
        val mine = clans().document(savedId).collection("members").document(me.uid).get().await()
        if (!mine.exists()) return false
        sharing = share
        attach(savedId)
        return true
    }

    private suspend fun addSelf(clan: String, share: Boolean) {
        val me = user ?: return
        sharing = share
        clans().document(clan).collection("members").document(me.uid).set(
            mapOf("name" to myName(), "joined" to System.currentTimeMillis(), "share" to share), SetOptions.merge()
        ).await()
    }

    private fun attach(newId: String) {
        detach()
        id = newId
        seenSelf = false
        lastSent = 0L
        routeSent = false
        sentRoute = null
        val ref = clans().document(newId)
        docReg = ref.addSnapshotListener { doc, err ->
            if (err != null || doc == null || id != newId) return@addSnapshotListener
            if (!doc.exists()) {
                if (!doc.metadata.isFromCache) lose("Клан распущен")
                return@addSnapshotListener
            }
            val i = ClanInfo(newId, doc.getString("name") ?: newId, doc.getString("ownerUid") ?: "", doc.getString("ownerName") ?: "")
            info = i
            onInfo(i)
        }
        membersReg = ref.collection("members").addSnapshotListener { snap, err ->
            if (err != null || snap == null || id != newId) return@addSnapshotListener
            val me = user?.uid
            val list = snap.documents.map { d ->
                val lat = d.getDouble("lat")
                val lon = d.getDouble("lon")
                @Suppress("UNCHECKED_CAST")
                val flat = d.get("route") as? List<Number>
                val route = flat?.takeIf { it.size >= 4 }?.chunked(2)?.filter { it.size == 2 }
                    ?.map { LatLon(it[0].toDouble(), it[1].toDouble()) }
                ClanMember(
                    uid = d.id,
                    name = d.getString("name") ?: "Участник",
                    position = if (lat != null && lon != null) LatLon(lat, lon) else null,
                    speedKmh = (d.getLong("speed") ?: 0L).toInt(),
                    updated = d.getLong("updated") ?: 0L,
                    route = route,
                    destTitle = d.getString("dest"),
                    sharing = d.getBoolean("share") != false,
                    color = clanColor(d.id)
                )
            }.sortedBy { it.name.lowercase() }
            val selfThere = list.any { it.uid == me }
            if (selfThere) seenSelf = true
            else if (seenSelf && !snap.metadata.isFromCache && !snap.metadata.hasPendingWrites()) {
                lose("Вас исключили из клана")
                return@addSnapshotListener
            }
            onMembers(list)
        }
    }

    private fun lose(why: String) {
        detach()
        id = null
        info = null
        onInfo(null)
        onMembers(emptyList())
        onLost(why)
    }

    private fun detach() {
        membersReg?.remove(); membersReg = null
        docReg?.remove(); docReg = null
    }

    private fun myDoc() = id?.let { c -> user?.let { me -> clans().document(c).collection("members").document(me.uid) } }

    /** Отправляет своё положение и скорость: на ходу раз в 5 секунд, стоя — раз в минуту. */
    fun sendPosition(here: LatLon, speedKmh: Int) {
        if (!sharing) return
        val doc = myDoc() ?: return
        val now = System.currentTimeMillis()
        val moved = lastPos?.let { distanceM(it, here) > 15.0 } ?: true
        val speedChanged = kotlin.math.abs(speedKmh - lastSpeed) >= 3
        val gap = if (moved || speedChanged) 5_000L else 60_000L
        if (now - lastSent < gap) return
        lastSent = now
        lastPos = here
        lastSpeed = speedKmh
        doc.set(mapOf("name" to myName(), "lat" to here.lat, "lon" to here.lon, "speed" to speedKmh, "updated" to now, "share" to true), SetOptions.merge())
    }

    /** Маршрут, по которому я еду (null — не еду по маршруту). Отправляется только когда сменился. */
    fun updateRoute(points: List<LatLon>?, destTitle: String?) {
        if (!sharing) return
        val doc = myDoc() ?: return
        if (routeSent && points === sentRoute) return
        sentRoute = points
        routeSent = true
        if (points == null || points.size < 2) {
            doc.set(mapOf("route" to FieldValue.delete(), "dest" to FieldValue.delete()), SetOptions.merge())
        } else {
            val flat = ArrayList<Double>(600)
            for (p in thinRoute(points)) {
                flat.add(Math.round(p.lat * 1e5) / 1e5)
                flat.add(Math.round(p.lon * 1e5) / 1e5)
            }
            doc.set(mapOf("route" to flat, "dest" to (destTitle ?: "")), SetOptions.merge())
        }
    }

    /** Показывать меня клану или нет. Выключили — положение и маршрут сразу стираются. */
    fun setSharing(on: Boolean) {
        sharing = on
        val doc = myDoc() ?: return
        lastSent = 0L
        routeSent = false
        sentRoute = null
        if (on) doc.set(mapOf("share" to true), SetOptions.merge())
        else doc.set(
            mapOf("share" to false, "lat" to FieldValue.delete(), "lon" to FieldValue.delete(),
                "speed" to FieldValue.delete(), "route" to FieldValue.delete(), "dest" to FieldValue.delete()),
            SetOptions.merge()
        )
    }

    /** Глава передаёт главенство другому участнику. */
    suspend fun makeOwner(member: ClanMember) {
        val c = id ?: return
        if (!isOwner) throw IllegalStateException("Это может только глава клана")
        clans().document(c).update(mapOf("ownerUid" to member.uid, "ownerName" to member.name)).await()
    }

    /** Глава исключает участника. */
    suspend fun kick(member: ClanMember) {
        val c = id ?: return
        if (!isOwner) throw IllegalStateException("Это может только глава клана")
        if (member.uid == user?.uid) return
        clans().document(c).collection("members").document(member.uid).delete().await()
    }

    /** Глава меняет пароль (старый перестаёт работать для новых участников). */
    suspend fun changePassword(password: String) {
        val c = id ?: return
        if (!isOwner) throw IllegalStateException("Это может только глава клана")
        if (password.length < 4) throw IllegalStateException("Пароль — хотя бы 4 знака")
        val salt = java.util.UUID.randomUUID().toString()
        clans().document(c).update(mapOf("salt" to salt, "passHash" to clanPassHash(salt, password))).await()
    }

    /** Выйти из клана. Главе сначала нужно назначить замену, если в клане остались другие. */
    suspend fun leave(othersCount: Int) {
        if (id == null) return
        if (isOwner && othersCount > 0) throw IllegalStateException("Сначала назначьте нового главу или распустите клан")
        if (isOwner) { disband(); return }
        val doc = myDoc()
        detach()
        id = null
        info = null
        onInfo(null)
        onMembers(emptyList())
        doc?.delete()?.await()
    }

    /** Глава распускает клан: все участники выходят, клан удаляется. */
    suspend fun disband() {
        val c = id ?: return
        if (!isOwner) throw IllegalStateException("Это может только глава клана")
        val ref = clans().document(c)
        detach()
        id = null
        info = null
        onInfo(null)
        onMembers(emptyList())
        val members = ref.collection("members").get().await()
        val batch = db.batch()
        members.documents.forEach { batch.delete(it.reference) }
        batch.delete(ref)
        batch.commit().await()
    }

    /** Приложение закрывается: просто отписываемся, из клана не выходим. */
    fun close() = detach()
}
