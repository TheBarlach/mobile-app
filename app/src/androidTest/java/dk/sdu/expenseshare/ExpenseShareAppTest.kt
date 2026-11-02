package dk.sdu.expenseshare

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dk.sdu.expenseshare.data.remote.AuthApi
import dk.sdu.expenseshare.data.remote.AuthRepository
import dk.sdu.expenseshare.data.remote.SessionState
import dk.sdu.expenseshare.data.remote.SignUpResponse
import dk.sdu.expenseshare.domain.Profile
import dk.sdu.expenseshare.ui.navigation.ExpenseShareApp
import dk.sdu.expenseshare.ui.theme.ExpenseShareTheme
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

@RunWith(AndroidJUnit4::class)
class ExpenseShareAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun launch(initial: SessionState) {
        val repository = AuthRepository(FakeAuthApi(initial))
        composeRule.setContent { ExpenseShareTheme { ExpenseShareApp(authRepository = repository) } }
    }

    @Test
    fun signedInUserSeesProfileAndSplitAndCanNavigateToExplore() {
        launch(SessionState.SignedIn(userId = "anna", email = "anna@example.com"))

        composeRule.onNodeWithText("Welcome, Anna Hansen").assertIsDisplayed()
        composeRule.onNodeWithText("@anna_h").assertIsDisplayed()
        composeRule.onNodeWithText("33.34 DKK").assertIsDisplayed()

        composeRule.onNodeWithText("Explore", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("This screen is a placeholder", substring = true).assertIsDisplayed()
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
