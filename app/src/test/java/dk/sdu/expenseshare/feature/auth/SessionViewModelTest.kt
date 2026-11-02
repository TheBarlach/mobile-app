package dk.sdu.expenseshare.feature.auth

import dk.sdu.expenseshare.MainDispatcherRule
import dk.sdu.expenseshare.data.remote.AuthErrorReason
import dk.sdu.expenseshare.data.remote.AuthException
import dk.sdu.expenseshare.data.remote.AuthRepository
import dk.sdu.expenseshare.data.remote.FakeAuthApi
import dk.sdu.expenseshare.data.remote.SessionState
import dk.sdu.expenseshare.domain.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SessionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = FakeAuthApi()
    private val viewModel by lazy { SessionViewModel(AuthRepository(api)) }

    private val anna = Profile(id = "anna", fullName = "Anna Hansen")
    private val bo = Profile(id = "bo", fullName = "Bo Jensen")

    @Test
    fun `stays loading until the stored session has been read`() {
        assertEquals(SessionState.Loading, viewModel.state.value.session)
        assertNull(viewModel.state.value.profile)
    }

    @Test
    fun `loads the profile of the signed in user`() {
        api.profileResult = { anna }
        viewModel

        api.sessionState.value = SessionState.SignedIn(userId = "anna", email = "anna@example.com")

        assertEquals(anna, viewModel.state.value.profile)
        assertEquals(listOf("anna"), api.profileRequests)
    }

    @Test
    fun `never shows the previous user's profile after switching user`() {
        api.profileResult = { if (it == "anna") anna else throw AuthException(AuthErrorReason.Network) }
        viewModel
        api.sessionState.value = SessionState.SignedIn("anna", null)

        api.sessionState.value = SessionState.SignedIn("bo", null)

        assertNull(viewModel.state.value.profile)
        assertEquals(AuthErrorReason.Network, viewModel.state.value.profileError)
    }

    @Test
    fun `a failed profile request can be retried`() {
        var failing = true
        api.profileResult = { if (failing) throw AuthException(AuthErrorReason.Network) else bo }
        viewModel
        api.sessionState.value = SessionState.SignedIn("bo", null)
        assertEquals(AuthErrorReason.Network, viewModel.state.value.profileError)

        failing = false
        viewModel.reloadProfile()

        assertEquals(bo, viewModel.state.value.profile)
        assertNull(viewModel.state.value.profileError)
    }

    @Test
    fun `keeps the loaded profile when a reload fails`() {
        var failing = false
        api.profileResult = { if (failing) throw AuthException(AuthErrorReason.Network) else anna }
        viewModel
        api.sessionState.value = SessionState.SignedIn("anna", null)

        failing = true
        viewModel.reloadProfile()

        assertEquals(anna, viewModel.state.value.profile)
        assertEquals(AuthErrorReason.Network, viewModel.state.value.profileError)
    }

    @Test
    fun `signing out clears the profile`() {
        api.profileResult = { anna }
        viewModel
        api.sessionState.value = SessionState.SignedIn("anna", null)

        viewModel.signOut()

        assertEquals(1, api.signOutCalls)
        assertEquals(SessionState.SignedOut, viewModel.state.value.session)
        assertNull(viewModel.state.value.profile)
    }
}
