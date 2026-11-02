package dk.sdu.expenseshare.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row in the `public.profiles` table. One per `auth.users` row. */
@Serializable
data class Profile(
    val id: String,
    val username: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

data class SignUpInput(val fullName: String, val username: String, val email: String, val password: String)

data class SignInInput(val email: String, val password: String)
