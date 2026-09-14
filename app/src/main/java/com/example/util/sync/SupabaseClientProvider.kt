package com.example.util.sync

import com.example.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseClientProvider {
    val client: SupabaseClient by lazy {
        // Fallback dummy URLs if not provided, usually these come from secrets via BuildConfig
        val url = BuildConfig.SUPABASE_URL.takeIf { it.isNotBlank() } ?: "https://dummy.supabase.co"
        val key = BuildConfig.SUPABASE_ANON_KEY.takeIf { it.isNotBlank() } ?: "dummy_key"
        
        createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key
        ) {
            install(Postgrest)
            install(Auth)
            install(Realtime)
        }
    }
}
