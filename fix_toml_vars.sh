# Move versions back to the top where they belong!
sed -i '/supabaseBom = "3.1.1"/d' gradle/libs.versions.toml
sed -i '/ktor = "3.1.0"/d' gradle/libs.versions.toml
sed -i '/workManager = "2.10.0"/d' gradle/libs.versions.toml
sed -i '/serialization = "1.8.0"/d' gradle/libs.versions.toml

sed -i '2i supabaseBom = "3.1.1"\nktor = "3.1.0"\nworkManager = "2.10.0"\nserialization = "1.8.0"' gradle/libs.versions.toml
