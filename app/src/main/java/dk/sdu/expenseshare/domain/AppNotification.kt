package dk.sdu.expenseshare.domain

/** What a notification is about. Each type is created by a trigger in the database. */
enum class NotificationType {
    /** [AppNotification.actorName] added the expense [AppNotification.description]. */
    ExpenseAdded,

    /** [AppNotification.actorName] asked the user to pay them [AppNotification.amount], if known. */
    PaymentReminder,

    /** [AppNotification.actorName] paid the user [AppNotification.amount]. */
    PaymentReceived,

    /** [AppNotification.actorName] joined [AppNotification.groupName]. */
    MemberJoined,

    /** [AppNotification.actorName] added the user to [AppNotification.groupName]. */
    AddedToGroup
}

/**
 * One item in the user's notification list. Named to avoid confusion with Android's
 * `android.app.Notification`. Names are null when the person has no name or was deleted.
 */
data class AppNotification(
    val id: String,
    val type: NotificationType,
    val groupName: String?,
    val actorName: String?,
    val description: String?,
    /** In øre. */
    val amount: Long?,
    val createdAtMillis: Long,
    val isRead: Boolean
) {
    init {
        require(amount == null || amount >= 0) { "Notification amount must not be negative, was $amount" }
    }
}
