package ru.shturman.license

import android.content.Context
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

/** Почта владельца: только он видит экран создания кодов (и только ему это разрешают правила Firestore). */
const val ADMIN_EMAIL = "mr1will123@gmail.com"

/** Код доступа в списке владельца. */
class LicenseRow(
    val code: String,
    val note: String,
    val used: Boolean,
    val usedByEmail: String,
    val usedAt: Long,
    val created: Long
)

private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789" // без похожих символов: 0/O, 1/I/L

/** «abcd efgh» и «ABCD-EFGH» — один и тот же код. */
fun normalizeCode(raw: String): String = raw.uppercase().filter { it.isLetterOrDigit() }

fun prettyCode(code: String): String = if (code.length == 8) code.substring(0, 4) + "-" + code.substring(4) else code

private fun deviceId(context: Context): String =
    Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

/**
 * Коды доступа. Владелец создаёт коды (коллекция licenses), друг вводит код при первом входе.
 * Код срабатывает один раз: после ввода он помечается использованным, а аккаунт друга
 * привязывается к его телефону (коллекция users). На другом телефоне понадобится новый код.
 */
object License {
    private val db get() = FirebaseFirestore.getInstance()

    fun isAdmin(email: String?): Boolean = email.equals(ADMIN_EMAIL, ignoreCase = true)

    private fun prefs(context: Context) = context.getSharedPreferences("license", Context.MODE_PRIVATE)

    /**
     * Активирован ли этот аккаунт на этом телефоне. Ответ запоминается, чтобы приложение
     * открывалось и без интернета. Возвращает null, если проверить не удалось и запомненного ответа нет.
     */
    suspend fun isActivated(context: Context): Boolean? {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        if (isAdmin(user.email)) return true
        val me = deviceId(context)
        val cacheKey = "ok_${user.uid}"
        return try {
            val doc = db.collection("users").document(user.uid).get().await()
            val ok = doc.exists() && doc.getString("deviceId") == me
            prefs(context).edit().putBoolean(cacheKey, ok).apply()
            ok
        } catch (e: Exception) {
            if (prefs(context).contains(cacheKey)) prefs(context).getBoolean(cacheKey, false) else null
        }
    }

    /** Активирует аккаунт кодом. Возвращает null при успехе или текст ошибки. */
    suspend fun redeem(context: Context, rawCode: String): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return "Нет входа в аккаунт"
        val code = normalizeCode(rawCode)
        if (code.length != 8) return "Код состоит из 8 символов"
        val me = deviceId(context)
        return try {
            val licenseRef = db.collection("licenses").document(code)
            val userRef = db.collection("users").document(user.uid)
            val result = db.runTransaction { tx ->
                val doc = tx.get(licenseRef)
                when {
                    !doc.exists() -> "Такого кода нет"
                    doc.getBoolean("used") == true -> "Этот код уже использован"
                    else -> {
                        tx.update(
                            licenseRef,
                            mapOf(
                                "used" to true,
                                "usedBy" to user.uid,
                                "usedByEmail" to (user.email ?: ""),
                                "usedAt" to System.currentTimeMillis(),
                                "deviceId" to me
                            )
                        )
                        tx.set(userRef, mapOf("code" to code, "deviceId" to me, "activatedAt" to System.currentTimeMillis()))
                        "ok"
                    }
                }
            }.await()
            if (result == "ok") {
                prefs(context).edit().putBoolean("ok_${user.uid}", true).apply()
                null
            } else result
        } catch (e: Exception) {
            "Не вышло: ${e.message?.take(120) ?: "нет интернета"}"
        }
    }

    // ---------- для владельца ----------

    /** Создаёт count новых кодов с пометкой note (например «Серёга»). Возвращает их список. */
    suspend fun create(count: Int, note: String): List<String> {
        val random = SecureRandom()
        val created = ArrayList<String>()
        repeat(count.coerceIn(1, 20)) {
            val code = (1..8).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
            db.collection("licenses").document(code).set(
                mapOf("used" to false, "note" to note, "created" to System.currentTimeMillis())
            ).await()
            created += code
        }
        return created
    }

    suspend fun list(): List<LicenseRow> {
        val snap = db.collection("licenses").orderBy("created", Query.Direction.DESCENDING).limit(300).get().await()
        return snap.documents.map { d ->
            LicenseRow(
                code = d.id,
                note = d.getString("note") ?: "",
                used = d.getBoolean("used") == true,
                usedByEmail = d.getString("usedByEmail") ?: "",
                usedAt = d.getLong("usedAt") ?: 0L,
                created = d.getLong("created") ?: 0L
            )
        }
    }

    /** Удаляет неиспользованный код. */
    suspend fun delete(code: String) {
        db.collection("licenses").document(code).delete().await()
    }

    /** Сбрасывает привязку друга к телефону (если он сменил телефон): ему потом понадобится новый код. */
    suspend fun unbind(uid: String) {
        db.collection("users").document(uid).delete().await()
    }
}
