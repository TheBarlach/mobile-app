package dk.sdu.expenseshare

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dk.sdu.expenseshare.ui.navigation.ExpenseShareApp
import dk.sdu.expenseshare.ui.theme.ExpenseShareTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpenseShareAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeShowsDeterministicSplitAndNavigatesToExplore() {
        composeRule.setContent { ExpenseShareTheme { ExpenseShareApp() } }

        composeRule.onNodeWithText("33.34 DKK").assertIsDisplayed()

        composeRule.onNodeWithText("Explore", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("This screen is a placeholder", substring = true).assertIsDisplayed()
    }
}
