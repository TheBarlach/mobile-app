package dk.sdu.expenseshare

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dk.sdu.expenseshare.data.balances.BalancesRepositories
import dk.sdu.expenseshare.data.balances.BalancesRepository
import dk.sdu.expenseshare.data.local.BalancesCache
import dk.sdu.expenseshare.data.local.NotificationsCache
import dk.sdu.expenseshare.data.local.TextStore
import dk.sdu.expenseshare.data.notifications.NotificationsRepositories
import dk.sdu.expenseshare.data.notifications.NotificationsRepository
import dk.sdu.expenseshare.data.remote.AuthApi
import dk.sdu.expenseshare.data.remote.AuthRepository
import dk.sdu.expenseshare.data.remote.BalancesApi
import dk.sdu.expenseshare.data.remote.ExpenseRow
import dk.sdu.expenseshare.data.remote.ExpenseShareRow
import dk.sdu.expenseshare.data.remote.GroupMemberRow
import dk.sdu.expenseshare.data.remote.GroupRow
import dk.sdu.expenseshare.data.remote.MemberProfileRow
import dk.sdu.expenseshare.data.remote.NotificationRow
import dk.sdu.expenseshare.data.remote.NotificationsApi
import dk.sdu.expenseshare.data.remote.SessionState
import dk.sdu.expenseshare.data.remote.SignUpResponse
import dk.sdu.expenseshare.data.sync.Outbox
import dk.sdu.expenseshare.data.sync.PendingWrite
import dk.sdu.expenseshare.domain.Profile
import dk.sdu.expenseshare.ui.navigation.ExpenseShareApp
import dk.sdu.expenseshare.ui.theme.ExpenseShareTheme
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Backend stand-in so the UI test does not need a Supabase project or network. */
private class FakeAuthApi(initial: SessionState) : AuthApi {
    override val sessionState = MutableStateFlow(initial)

    override suspend fun isUsernameAvailable(username: String) = true

    override suspend fun signUp(email: String, password: String, username: String, fullName: String) =
        SignUpResponse(hasSession = true, identityCount = 1)

    override suspend fun signIn(email: String, password: String) {
        sessionState.value = SessionState.SignedIn(userId = "anna", email = email)
    }

    override suspend fun signOut() {
        sessionState.value = SessionState.SignedOut
    }

    override suspend fun fetchProfile(userId: String) =
        Profile(id = userId, username = "anna_h", fullName = "Anna Hansen")
}

/** Groups backend stand-in: Anna paid 300 DKK for dinner with Alex and Sarah. */
private class FakeBalancesApi : BalancesApi {
    val sent = mutableListOf<PendingWrite>()

    override suspend fun fetchGroups() = listOf(
        GroupRow(
            id = "trip",
            name = "Summer Trip",
            members =
            listOf(
                GroupMemberRow("anna", MemberProfileRow(fullName = "Anna Hansen")),
                GroupMemberRow("alex", MemberProfileRow(fullName = "Alex")),
                GroupMemberRow("sarah", MemberProfileRow(fullName = "Sarah"))
            ),
            expenses =
            listOf(
                ExpenseRow(
                    id = "dinner",
                    description = "Dinner",
                    paidBy = "anna",
                    amount = 30_000,
                    shares =
                    listOf(
                        ExpenseShareRow("anna", 10_000),
                        ExpenseShareRow("alex", 10_000),
                        ExpenseShareRow("sarah", 10_000)
                    )
                )
            )
        )
    )

    override suspend fun send(write: PendingWrite) {
        sent += write
    }
}

/** Notifications backend stand-in: Alex added an expense and John sent a reminder. */
private class FakeNotificationsApi : NotificationsApi {
    val markedRead = mutableListOf<Set<String>>()
    private val now = Instant.now()

    override suspend fun fetchNotifications() = listOf(
        NotificationRow(
            id = "n1",
            type = "expense_added",
            groupName = "Summer Trip",
            actorName = "Alex",
            description = "Dinner",
            amount = 60_000,
            createdAt = now.minusSeconds(10 * 60).toString()
        ),
        NotificationRow(
            id = "n2",
            type = "payment_reminder",
            groupName = "Roommates",
            actorName = "John",
            amount = 10_000,
            createdAt = now.minusSeconds(24 * 60 * 60).toString()
        )
    )

    override suspend fun markRead(ids: Set<String>) {
        markedRead += ids
    }
}

private class MemoryStore : TextStore {
    private var text: String? = null

    override suspend fun read() = text

    override suspend fun write(text: String) {
        this.text = text
    }
}

@RunWith(AndroidJUnit4::class)
class ExpenseShareAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val balancesApi = FakeBalancesApi()
    private val notificationsApi = FakeNotificationsApi()

    private fun launch(initial: SessionState) {
        val repository = AuthRepository(FakeAuthApi(initial))
        val balances =
            BalancesRepositories { _, scope ->
                BalancesRepository(
                    balancesApi,
                    BalancesCache(MemoryStore()),
                    Outbox(MemoryStore(), balancesApi::send, scope),
                    scope
                )
            }
        val notifications =
            NotificationsRepositories { _, scope ->
                NotificationsRepository(notificationsApi, NotificationsCache(MemoryStore()), scope)
            }
        composeRule.setContent {
            ExpenseShareTheme {
                ExpenseShareApp(
                    authRepository = repository,
                    balancesRepositories = balances,
                    notificationsRepositories = notifications
                )
            }
        }
    }

    @Test
    fun balancesShowWhoOwesTheUserAndRemindersAreSent() {
        launch(SessionState.SignedIn(userId = "anna", email = "anna@example.com"))

        composeRule.onNodeWithText("Balances", useUnmergedTree = true).performClick()

        composeRule.onNodeWithText("+ 200 DKK").assertIsDisplayed()
        composeRule.onNodeWithText("Summer Trip").assertIsDisplayed()
        composeRule.onNodeWithText("Alex owes you").assertIsDisplayed()
        composeRule.onNodeWithText("Sarah owes you").assertIsDisplayed()

        composeRule.onAllNodesWithText("Remind")[0].performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { balancesApi.sent.isNotEmpty() }
        composeRule.onNodeWithText("Reminded").assertIsDisplayed()
    }

    @Test
    fun signedInUserSeesProfileAndSplit() {
        launch(SessionState.SignedIn(userId = "anna", email = "anna@example.com"))

        composeRule.onNodeWithText("Welcome, Anna Hansen").assertIsDisplayed()
        composeRule.onNodeWithText("@anna_h").assertIsDisplayed()
        composeRule.onNodeWithText("33.34 DKK").assertIsDisplayed()
    }

    @Test
    fun alertsShowNotificationsAndMarkThemRead() {
        launch(SessionState.SignedIn(userId = "anna", email = "anna@example.com"))

        // The badge is visible on every tab.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("2 unread notifications").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Alerts", useUnmergedTree = true).performClick()

        composeRule.onNodeWithText("Notifications").assertIsDisplayed()
        composeRule.onNodeWithText("New Expense").assertIsDisplayed()
        composeRule.onNodeWithText("Alex added Dinner - 600 DKK").assertIsDisplayed()
        composeRule.onNodeWithText("Payment Reminder").assertIsDisplayed()
        composeRule.onNodeWithText("You owe John 100 DKK").assertIsDisplayed()

        composeRule.onNodeWithText("Alex added Dinner - 600 DKK").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { notificationsApi.markedRead.isNotEmpty() }
        composeRule.onAllNodesWithContentDescription("1 unread notification").assertCountEquals(1)

        composeRule.onNodeWithText("Mark all as read").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { notificationsApi.markedRead.size == 2 }
        composeRule.onNodeWithText("Mark all as read").assertDoesNotExist()
    }

    @Test
    fun signedOutUserSeesLoginAndValidationErrors() {
        launch(SessionState.SignedOut)

        composeRule.onNodeWithText("No account? Create one").assertIsDisplayed()
        composeRule.onNodeWithText("Enter a valid email address.").assertDoesNotExist()

        // The title and the button share the text "Log in"; only the button is clickable.
        composeRule.onNode(hasText("Log in") and hasClickAction()).performClick()

        composeRule.onNodeWithText("Enter a valid email address.").assertIsDisplayed()
        composeRule.onNodeWithText("Enter your password.").assertIsDisplayed()
    }

    @Test
    fun loginLinkOpensRegistration() {
        launch(SessionState.SignedOut)

        composeRule.onNodeWithText("No account? Create one").performClick()

        composeRule.onNodeWithText("Already have an account? Log in").assertIsDisplayed()
        composeRule.onNodeWithText("Full name").assertIsDisplayed()
    }
}
