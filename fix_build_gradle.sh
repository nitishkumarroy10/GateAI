sed -i 's/alias(libs.plugins.kotlin.compose)/alias(libs.plugins.kotlin.compose)\n  alias(libs.plugins.kotlin.serialization)/g' app/build.gradle.kts

sed -i '/dependencies {/a \
  implementation(platform(libs.supabase.bom))\n  implementation(libs.supabase.postgrest)\n  implementation(libs.supabase.gotrue)\n  implementation(libs.supabase.realtime)\n  implementation(libs.ktor.client.android)\n  implementation(libs.ktor.client.core)\n  implementation(libs.androidx.work.runtime.ktx)\n  implementation(libs.kotlinx.serialization.json)' app/build.gradle.kts

