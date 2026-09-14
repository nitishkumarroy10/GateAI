# Because the [libraries] block got overwritten by [plugins] previously.
sed -i '/supabase-bom =/i \[libraries]' gradle/libs.versions.toml
