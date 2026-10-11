package ru.shturman.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import ru.shturman.R

/** Данные пользователя из Google-аккаунта: имя, почта и фото подтягиваются автоматически. */
data class UserInfo(val name: String, val email: String, val photoUrl: String?)

fun FirebaseUser.toInfo() = UserInfo(
    name = displayName ?: "Водитель",
    email = email ?: "",
    photoUrl = photoUrl?.toString()
)

fun currentUserInfo(): UserInfo? = FirebaseAuth.getInstance().currentUser?.toInfo()

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Вход через Google. Возвращает null при успехе, иначе короткий текст ошибки по-русски. */
suspend fun signInWithGoogle(context: Context): String? {
    val activity = context.findActivity() ?: return "Не удалось открыть окно входа"
    return try {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(activity.getString(R.string.default_web_client_id))
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activity).getCredential(activity, request)
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            FirebaseAuth.getInstance()
                .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .await()
            null
        } else {
            "Неподдерживаемый тип входа"
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: GetCredentialCancellationException) {
        "Вход отменён"
    } catch (e: Exception) {
        "Не удалось войти: ${e.javaClass.simpleName}${e.message?.let { " — $it" } ?: ""}"
    }
}

suspend fun signOut(context: Context) {
    FirebaseAuth.getInstance().signOut()
    try {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // не критично: из аккаунта в приложении мы уже вышли
    }
}
