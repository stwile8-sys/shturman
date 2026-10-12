package ru.shturman.media

import android.service.notification.NotificationListenerService

/**
 * Пустая служба: само её наличие и разрешение «Доступ к уведомлениям» позволяют приложению видеть,
 * что играет в плеере (Яндекс Музыка и другие), и управлять им.
 */
class MusicListener : NotificationListenerService()
