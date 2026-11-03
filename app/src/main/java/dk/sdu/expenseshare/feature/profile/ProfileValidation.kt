package dk.sdu.expenseshare.feature.profile

import dk.sdu.expenseshare.feature.auth.FieldError

// Must match the profiles_phone_format constraint in the database.
private val PHONE_PATTERN = Regex("^\\+?[0-9]{6,15}$")
private val PHONE_SEPARATORS = Regex("[\\s\\-()]")

/** Editable fields on the Profile screen, as typed by the user. */
data class ProfileForm(val fullName: String = "", val phone: String = "", val notificationsEnabled: Boolean = true)

data class ProfileErrors(val fullName: FieldError? = null, val phone: FieldError? = null) {
    val hasErrors: Boolean get() = fullName != null || phone != null
}

/** Removes spaces, dashes and brackets. A blank number is stored as null (no phone). */
fun normalizePhone(phone: String): String? = phone.replace(PHONE_SEPARATORS, "").ifEmpty { null }

fun validateProfile(form: ProfileForm): ProfileErrors = ProfileErrors(
    fullName = if (form.fullName.isBlank()) FieldError.MissingName else null,
    phone = normalizePhone(form.phone)?.let { if (PHONE_PATTERN.matches(it)) null else FieldError.InvalidPhone }
)
