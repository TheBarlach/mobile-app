package dk.sdu.expenseshare.feature.profile

import dk.sdu.expenseshare.MainDispatcherRule
import dk.sdu.expenseshare.data.remote.AuthErrorReason
import dk.sdu.expenseshare.data.remote.AuthException
import dk.sdu.expenseshare.data.remote.AuthRepository
import dk.sdu.expenseshare.data.remote.FakeAuthApi
import dk.sdu.expenseshare.domain.Profile
import dk.sdu.expenseshare.domain.ProfileUpdate
import dk.sdu.expenseshare.feature.auth.FieldError
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = FakeAuthApi()
    private val repository = AuthRepository(api)
    private val savedProfiles = mutableListOf<Profile>()

    private fun viewModel(profile: Profile = Profile(id = "u1", fullName = "John Doe", phone = "+4512345678")) =
        ProfileViewModel(repository, profile, email = "johndoe@test.com", onSaved = { savedProfiles += it })

    @Test
    fun `form starts with the stored profile`() {
        val state = viewModel().state.value

        assertEquals(ProfileForm("John Doe", "+4512345678", notificationsEnabled = true), state.form)
        assertEquals("johndoe@test.com", state.email)
        assertEquals(SaveStatus.Idle, state.saveStatus)
    }

    @Test
    fun `invalid input shows field errors and sends no request`() {
        val viewModel = viewModel()
        viewModel.onFullNameChange(" ")
        viewModel.onPhoneChange("12")

        viewModel.save()

        assertEquals(FieldError.MissingName, viewModel.state.value.errors.fullName)
        assertEquals(FieldError.InvalidPhone, viewModel.state.value.errors.phone)
        assertTrue(api.profileUpdates.isEmpty())
    }

    @Test
    fun `saving sends normalised values and reports success`() {
        val viewModel = viewModel()
        viewModel.onFullNameChange("  Jane Doe ")
        viewModel.onPhoneChange("+45 87 65 43 21")
        viewModel.onNotificationsChange(false)

        viewModel.save()

        assertEquals(
            listOf("u1" to ProfileUpdate(fullName = "Jane Doe", phone = "+4587654321", notificationsEnabled = false)),
            api.profileUpdates
        )
        assertEquals(SaveStatus.Successful, viewModel.state.value.saveStatus)
        assertEquals("Jane Doe", savedProfiles.single().fullName)
    }

    @Test
    fun `status is pending while saving and repeated taps do not write twice`() {
        val gate = CompletableDeferred<Unit>()
        val default = api.updateProfileResult
        api.updateProfileResult = { id, update ->
            gate.await()
            default(id, update)
        }
        val viewModel = viewModel()

        viewModel.save()
        assertEquals(SaveStatus.Pending, viewModel.state.value.saveStatus)
        viewModel.save()
        gate.complete(Unit)

        assertEquals(1, api.profileUpdates.size)
        assertEquals(SaveStatus.Successful, viewModel.state.value.saveStatus)
    }

    @Test
    fun `network failure keeps the input and a retry succeeds`() {
        api.updateProfileResult = { _, _ -> throw AuthException(AuthErrorReason.Network) }
        val viewModel = viewModel()
        viewModel.onFullNameChange("Jane Doe")

        viewModel.save()

        assertEquals(SaveStatus.Failed(AuthErrorReason.Network), viewModel.state.value.saveStatus)
        assertEquals("Jane Doe", viewModel.state.value.form.fullName)
        assertTrue(savedProfiles.isEmpty())

        api.updateProfileResult = { id, update -> Profile(id = id, fullName = update.fullName) }
        viewModel.save()

        assertEquals(SaveStatus.Successful, viewModel.state.value.saveStatus)
        assertEquals(2, api.profileUpdates.size)
    }

    @Test
    fun `missing profile row is reported as a failure`() {
        api.updateProfileResult = { _, _ -> null }
        val viewModel = viewModel()

        viewModel.save()

        assertEquals(SaveStatus.Failed(AuthErrorReason.ProfileNotFound), viewModel.state.value.saveStatus)
    }

    @Test
    fun `editing after a save clears the old result`() {
        val viewModel = viewModel()
        viewModel.save()

        viewModel.onPhoneChange("+4511111111")

        assertEquals(SaveStatus.Idle, viewModel.state.value.saveStatus)
    }
}
