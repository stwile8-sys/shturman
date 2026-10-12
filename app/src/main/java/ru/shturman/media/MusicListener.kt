package ru.shturman.media

import android.service.notification.NotificationListenerService

/**
 * Пустая служба: само её наличие и разрешение «Доступ к уведомлениям» позволяют приложению видеть,
 * что играет в плеере (Яндекс Музыка и другие), и работает ли Антирадар.
 */
class MusicListener : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    companion object {
        @Volatile var instance: MusicListener? = null
    }
}
