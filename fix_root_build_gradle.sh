sed -i 's/alias(libs.plugins.kotlin.compose) apply false/alias(libs.plugins.kotlin.compose) apply false\n  alias(libs.plugins.kotlin.serialization) apply false/g' build.gradle.kts
