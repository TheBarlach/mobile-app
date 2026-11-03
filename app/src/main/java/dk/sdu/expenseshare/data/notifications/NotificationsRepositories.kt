package dk.sdu.expenseshare.data.notifications

import dk.sdu.expenseshare.data.UserScoped

/**
 * Holds the [NotificationsRepository] of the signed-in user, so unsent read marks keep being
 * retried while the user moves between screens.
 */
typealias NotificationsRepositories = UserScoped<NotificationsRepository>
