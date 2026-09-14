cat << 'INNER' >> gradle/libs.versions.toml

supabaseBom = "3.1.1"
ktor = "3.1.0"
workManager = "2.10.0"
serialization = "1.8.0"
INNER

cat << 'INNER' >> gradle/libs.versions.toml

supabase-bom = { group = "io.github.jan-tennert.supabase", name = "bom", version.ref = "supabaseBom" }
supabase-postgrest = { group = "io.github.jan-tennert.supabase", name = "postgrest-kt" }
supabase-gotrue = { group = "io.github.jan-tennert.supabase", name = "gotrue-kt" }
supabase-realtime = { group = "io.github.jan-tennert.supabase", name = "realtime-kt" }
ktor-client-android = { group = "io.ktor", name = "ktor-client-android", version.ref = "ktor" }
ktor-client-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }

[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
INNER
