sed -i 's/System.getenv(\\"SUPABASE_URL\\") ?: \\"\\"/System.getenv(\\"SUPABASE_URL\\") != null ? System.getenv(\\"SUPABASE_URL\\") : \\"\\"/g' app/build.gradle.kts
sed -i 's/System.getenv(\\"SUPABASE_ANON_KEY\\") ?: \\"\\"/System.getenv(\\"SUPABASE_ANON_KEY\\") != null ? System.getenv(\\"SUPABASE_ANON_KEY\\") : \\"\\"/g' app/build.gradle.kts
