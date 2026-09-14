# Remove the custom libraries we added at the bottom
sed -i '/\[libraries\]/,$d' gradle/libs.versions.toml

# Re-append them at the bottom of the original [libraries] section before [plugins]
sed -i '/\[plugins\]/i \
supabase-bom = { group = "io.github.jan-tennert.supabase", name = "bom", version.ref = "supabaseBom" }\
supabase-postgrest = { group = "io.github.jan-tennert.supabase", name = "postgrest-kt" }\
supabase-gotrue = { group = "io.github.jan-tennert.supabase", name = "gotrue-kt" }\
supabase-realtime = { group = "io.github.jan-tennert.supabase", name = "realtime-kt" }\
ktor-client-android = { group = "io.ktor", name = "ktor-client-android", version.ref = "ktor" }\
ktor-client-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }\
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }\
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }' gradle/libs.versions.toml

