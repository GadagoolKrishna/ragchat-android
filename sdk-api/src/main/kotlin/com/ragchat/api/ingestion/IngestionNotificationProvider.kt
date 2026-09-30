package com.ragchat.api.ingestion

import com.ragchat.api.model.IngestionProgress

/**
 * Host-supplied notification descriptor for foreground worker execution.
 *
 * @property notificationId Unique Android notification ID.
 * @property channelId Notification channel identifier.
 * @property channelName User-visible channel name.
 * @property title Notification title.
 * @property contentText User-visible notification progress message.
 * @property isIndeterminate Whether progress bar is indeterminate.
 * @property maxProgress Maximum progress units (e.g. 100).
 * @property currentProgress Current progress units.
 */
public data class IngestionNotificationInfo(
    val notificationId: Int,
    val channelId: String,
    val channelName: String,
    val title: String,
    val contentText: String,
    val isIndeterminate: Boolean = false,
    val maxProgress: Int = 100,
    val currentProgress: Int = 0,
)

/**
 * Service Provider Interface (SPI) for providing foreground service notification metadata.
 *
 * Implemented by host applications to customize background ingestion notifications.
 */
public interface IngestionNotificationProvider {
    /**
     * Constructs notification parameters based on current ingestion progress.
     *
     * @param progress Latest progress event.
     * @return Configured [IngestionNotificationInfo].
     */
    public fun getNotificationInfo(progress: IngestionProgress): IngestionNotificationInfo
}
