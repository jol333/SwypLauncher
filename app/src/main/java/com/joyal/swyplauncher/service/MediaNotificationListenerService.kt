package com.joyal.swyplauncher.service

import android.service.notification.NotificationListenerService

/**
 * Intentionally empty. The platform only hands out media-session access
 * ([android.media.session.MediaSessionManager.getActiveSessions]) to packages with an
 * enabled notification listener, so this exists purely as the grant target for the
 * Control Center media card. Notifications themselves are never read or stored.
 */
class MediaNotificationListenerService : NotificationListenerService()
