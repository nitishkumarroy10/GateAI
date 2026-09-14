# Remove the duplicated [plugins] section from the end of libs.versions.toml
sed -i 's/\[plugins\]//g' gradle/libs.versions.toml
# Then add it manually under the correct existing block
sed -i '/google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }/a \kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }' gradle/libs.versions.toml
