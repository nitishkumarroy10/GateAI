sed -i '/google()/a \        mavenCentral()' settings.gradle.kts
sed -i 's/implementation("io.github.jan-tennert.supabase:gotrue-kt:3.1.1")/implementation("io.github.jan-tennert.supabase:gotrue-kt:3.1.2")/g' app/build.gradle.kts
sed -i 's/implementation("io.github.jan-tennert.supabase:postgrest-kt:3.1.1")/implementation("io.github.jan-tennert.supabase:postgrest-kt:3.1.2")/g' app/build.gradle.kts
sed -i 's/implementation("io.github.jan-tennert.supabase:realtime-kt:3.1.1")/implementation("io.github.jan-tennert.supabase:realtime-kt:3.1.2")/g' app/build.gradle.kts
