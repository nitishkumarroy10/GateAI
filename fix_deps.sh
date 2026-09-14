# Check if supabase gotrue-kt was split or renamed.
# Actually let's just use 3.0.0. I am blindly guessing versions which is bad. Let's find out what version works.
sed -i 's/implementation("io.github.jan-tennert.supabase:gotrue-kt:3.1.2")/implementation("io.github.jan-tennert.supabase:auth-kt:3.0.0")/g' app/build.gradle.kts
sed -i 's/implementation("io.github.jan-tennert.supabase:postgrest-kt:3.1.2")/implementation("io.github.jan-tennert.supabase:postgrest-kt:3.0.0")/g' app/build.gradle.kts
sed -i 's/implementation("io.github.jan-tennert.supabase:realtime-kt:3.1.2")/implementation("io.github.jan-tennert.supabase:realtime-kt:3.0.0")/g' app/build.gradle.kts
