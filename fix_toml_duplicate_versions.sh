sed -i '/\[versions\]/d' gradle/libs.versions.toml
sed -i '1i [versions]' gradle/libs.versions.toml

sed -i '/\[libraries\]/d' gradle/libs.versions.toml
sed -i '/androidx-core-ktx =/i \[libraries]' gradle/libs.versions.toml

sed -i '/\[plugins\]/d' gradle/libs.versions.toml
sed -i '/android-application = /i \[plugins]' gradle/libs.versions.toml

