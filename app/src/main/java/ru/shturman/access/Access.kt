package ru.shturman.access

import android.content.Context
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/** Почта владельца: только он видит раздел «Коды доступа» и может их создавать. */
const val ADMIN_EMAIL = "mr1will123@gmail.com"

/** Что решила проверка доступа. */
sealed class AccessState {
    object Checking : AccessState()
    object Granted : AccessState()
    object NeedCode : AccessState()
    class Blocked(val message: String) : AccessState()
    class Failed(val message: String) : AccessState()
}

/** Одна строка списка кодов в админке. */
class CodeRow(
    val code: String,
    val used: Boolean,
    val usedByUid: String,
    val usedByName: String,
    val usedByEmail: String,
    val usedAt: Long,
    val createdAt: Long,
    val note: String
)

/**
 * Коды доступа. Владелец создаёт коды в админке и отдаёт их друзьям. Код срабатывает один раз:
 * он привязывается к Google-аккаунту и к устройству. Всё хранится в Firestore (codes и users),
 * а настоящую защиту обеспечивают правила базы, а не само приложение.
 */
object Access {
    private val db get() = FirebaseFirestore.getInstance()
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // без похожих символов: 0/O, 1/I

    fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    private fun message(e: Exception): String =
        (e.cause?.message ?: e.message ?: e.javaClass.simpleName).take(160)

    suspend fun check(context: Context): AccessState {
        val user = FirebaseAuth.getInstance().currentUser ?: return AccessState.Failed("Нет входа в аккаунт")
        if (user.email == ADMIN_EMAIL) return AccessState.Granted
        return try {
            val doc = db.collection("users").document(user.uid).get().await()
            if (!doc.exists()) return AccessState.NeedCode
            val bound = doc.getString("deviceId") ?: ""
            val mine = deviceId(context)
            when {
                bound.isEmpty() -> {
                    // Владелец сбросил привязку: закрепляем за этим устройством
                    db.collection("users").document(user.uid).update("deviceId", mine).await()
                    AccessState.Granted
                }
                bound == mine -> AccessState.Granted
                else -> AccessState.Blocked("Доступ привязан к другому устройству. Если вы сменили телефон, напишите тому, кто выдал код.")
            }
        } catch (e: Exception) {
            AccessState.Failed("Не удалось проверить доступ: ${message(e)}")
        }
    }

    /** Активирует код. Возвращает null, если всё получилось, иначе текст ошибки. */
    suspend fun redeem(context: Context, raw: String): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return "Нет входа в аккаунт"
        val code = raw.trim().uppercase()
        if (code.length < 6) return "Введите код целиком"
        val device = deviceId(context)
        return try {
            db.runTransaction { tx ->
                val ref = db.collection("codes").document(code)
                val snap = tx.get(ref)
                if (!snap.exists()) throw IllegalStateException("Такого кода нет")
                if (snap.getBoolean("used") == true) throw IllegalStateException("Этот код уже использован")
                val now = System.currentTimeMillis()
                tx.update(
                    ref,
                    mapOf(
                        "used" to true, "usedBy" to user.uid, "usedByEmail" to (user.email ?: ""),
                        "usedByName" to (user.displayName ?: ""), "usedAt" to now, "deviceId" to device
                    )
                )
                tx.set(
                    db.collection("users").document(user.uid),
                    mapOf(
                        "activated" to true, "code" to code, "deviceId" to device,
                        "email" to (user.email ?: ""), "name" to (user.displayName ?: ""), "activatedAt" to now
                    )
                )
                null
            }.await()
            null
        } catch (e: Exception) {
            message(e)
        }
    }

    // ---------- для владельца ----------

    suspend fun listCodes(): List<CodeRow> =
        db.collection("codes").orderBy("createdAt", Query.Direction.DESCENDING).limit(300).get().await()
            .documents.map { d ->
                CodeRow(
                    code = d.id,
                    used = d.getBoolean("used") == true,
                    usedByUid = d.getString("usedBy") ?: "",
                    usedByName = d.getString("usedByName") ?: "",
                    usedByEmail = d.getString("usedByEmail") ?: "",
                    usedAt = d.getLong("usedAt") ?: 0L,
                    createdAt = d.getLong("createdAt") ?: 0L,
                    note = d.getString("note") ?: ""
                )
            }

    /** Создаёт count новых кодов вида SH-ABCD-EFGH. */
    suspend fun createCodes(count: Int, note: String = "") {
        val batch = db.batch()
        val now = System.currentTimeMillis()
        repeat(count) {
            fun part() = (1..4).map { ALPHABET.random() }.joinToString("")
            val code = "SH-${part()}-${part()}"
            batch.set(
                db.collection("codes").document(code),
                mapOf("used" to false, "createdAt" to now, "note" to note)
            )
        }
        batch.commit().await()
    }

    suspend fun deleteCode(code: String) {
        db.collection("codes").document(code).delete().await()
    }

    /** Сбросить привязку к устройству: при следующем запуске код закрепится за новым телефоном. */
    suspend fun resetDevice(uid: String) {
        db.collection("users").document(uid).update("deviceId", "").await()
    }

    /** Отозвать доступ: у человека снова спросят код (а использованный код остаётся сгоревшим). */
    suspend fun revoke(uid: String) {
        db.collection("users").document(uid).delete().await()
    }
}
