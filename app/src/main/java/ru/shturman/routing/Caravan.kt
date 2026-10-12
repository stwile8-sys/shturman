package ru.shturman.routing

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/** Участник каравана на карте. */
class CaravanMember(val uid: String, val name: String, val position: LatLon, val leader: Boolean, val off: Boolean = false)

/** Куда едет ведущий. */
class CaravanDest(val title: String, val position: LatLon)

/**
 * Караван: несколько машин видят друг друга на карте, один (создатель) ведёт и задаёт общий пункт назначения.
 * Обмен идёт через Firestore: caravans/{код} и caravans/{код}/members/{uid}. Участник показывается,
 * пока его положение свежее (не старше двух минут).
 */
class Caravan(
    private val onMembers: (List<CaravanMember>) -> Unit,
    private val onDest: (CaravanDest?) -> Unit
) {
    private val db get() = FirebaseFirestore.getInstance()
    private val user get() = FirebaseAuth.getInstance().currentUser
    private var code: String? = null
    private var leader = false
    private var lastSent = 0L
    private var membersReg: ListenerRegistration? = null
    private var docReg: ListenerRegistration? = null

    val currentCode: String? get() = code
    val isLeader: Boolean get() = leader

    /** Создаёт караван и возвращает его четырёхзначный код. */
    suspend fun create(): String {
        val me = user ?: throw IllegalStateException("Нет входа в аккаунт")
        val newCode = (1000..9999).random().toString()
        db.collection("caravans").document(newCode).set(
            mapOf("leaderUid" to me.uid, "leaderName" to (me.displayName ?: "Ведущий"), "created" to System.currentTimeMillis())
        ).await()
        attach(newCode, true)
        return newCode
    }

    /** Входит в существующий караван по коду. */
    suspend fun join(joinCode: String) {
        val me = user ?: throw IllegalStateException("Нет входа в аккаунт")
        val doc = db.collection("caravans").document(joinCode).get().await()
        if (!doc.exists()) throw IllegalStateException("Каравана с таким кодом нет")
        attach(joinCode, doc.getString("leaderUid") == me.uid)
    }

    private fun attach(newCode: String, asLeader: Boolean) {
        leave()
        code = newCode
        leader = asLeader
        membersReg = db.collection("caravans").document(newCode).collection("members")
            .addSnapshotListener { snap, _ ->
                val me = user?.uid
                val now = System.currentTimeMillis()
                val list = snap?.documents?.mapNotNull { d ->
                    if (d.id == me) return@mapNotNull null
                    val lat = d.getDouble("lat") ?: return@mapNotNull null
                    val lon = d.getDouble("lon") ?: return@mapNotNull null
                    if (now - (d.getLong("updated") ?: 0L) > 120_000L) return@mapNotNull null
                    CaravanMember(d.id, d.getString("name") ?: "Участник", LatLon(lat, lon), d.getBoolean("leader") == true, d.getBoolean("off") == true)
                } ?: emptyList()
                onMembers(list)
            }
        docReg = db.collection("caravans").document(newCode).addSnapshotListener { doc, _ ->
            val lat = doc?.getDouble("destLat")
            val lon = doc?.getDouble("destLon")
            onDest(if (lat != null && lon != null) CaravanDest(doc.getString("destTitle") ?: "Пункт назначения", LatLon(lat, lon)) else null)
        }
    }

    /** Отправляет своё положение (не чаще раза в 3 секунды). */
    fun sendPosition(here: LatLon, off: Boolean = false) {
        val c = code ?: return
        val me = user ?: return
        val now = System.currentTimeMillis()
        if (now - lastSent < 3_000L) return
        lastSent = now
        db.collection("caravans").document(c).collection("members").document(me.uid).set(
            mapOf(
                "name" to (me.displayName?.substringBefore(' ') ?: "Участник"),
                "lat" to here.lat, "lon" to here.lon, "leader" to leader, "off" to off, "updated" to now
            )
        )
    }

    /** Ведущий задаёт общий пункт назначения. */
    fun setDestination(title: String, p: LatLon) {
        val c = code ?: return
        if (!leader) return
        db.collection("caravans").document(c).update(
            mapOf("destTitle" to title, "destLat" to p.lat, "destLon" to p.lon)
        )
    }

    fun leave() {
        membersReg?.remove(); membersReg = null
        docReg?.remove(); docReg = null
        val c = code
        val me = user
        if (c != null && me != null) {
            db.collection("caravans").document(c).collection("members").document(me.uid).delete()
        }
        code = null
        leader = false
        onMembers(emptyList())
        onDest(null)
    }
}
