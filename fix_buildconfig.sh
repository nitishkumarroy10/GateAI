# Check if BuildConfig generation is enabled
sed -i '/buildFeatures {/a \    buildConfig = true' app/build.gradle.kts
# Add default blank strings for Supabase keys to avoid compile errors if missing from env
sed -i '/defaultConfig {/a \    buildConfigField("String", "SUPABASE_URL", "System.getenv(\\"SUPABASE_URL\\") ?: \\"\\"")\n    buildConfigField("String", "SUPABASE_ANON_KEY", "System.getenv(\\"SUPABASE_ANON_KEY\\") ?: \\"\\"")' app/build.gradle.kts
