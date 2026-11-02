package dk.sdu.expenseshare.data.remote

import dk.sdu.expenseshare.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Shared Supabase client. Use this instead of creating new clients in screens.
 *
 * Values come from `supabase.url` / `supabase.publishableKey` in local.properties
 * (see local.properties.example). Only the publishable key may be used in the app;
 * never add a Supabase secret key.
 *
 * The client is created lazily so a missing configuration fails with a clear
 * message on first use instead of crashing the app at startup. Sessions are
 * persisted on the device and refreshed automatically by the Auth plugin.
 */
object SupabaseProvider {
    val client: SupabaseClient by lazy {
        val url = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        check(url.isNotBlank() && key.isNotBlank()) {
            "Missing Supabase configuration. Add supabase.url and supabase.publishableKey to local.properties."
        }
        createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
            install(Auth)
            install(Postgrest)
        }
    }
}
