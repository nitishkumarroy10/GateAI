# Fix the sections - we are missing [plugins] because I broke it
sed -i 's/android-application = { id = "com.android.application", version.ref = "agp" }/\[plugins\]\nandroid-application = { id = "com.android.application", version.ref = "agp" }/g' gradle/libs.versions.toml

# We are missing [versions] for the new versions, they are just dangling
sed -i 's/supabaseBom = "3.1.1"/\[versions\]\nsupabaseBom = "3.1.1"/g' gradle/libs.versions.toml

# We are missing [libraries] for the new libs
sed -i 's/supabase-bom = {/\[libraries\]\nsupabase-bom = {/g' gradle/libs.versions.toml
