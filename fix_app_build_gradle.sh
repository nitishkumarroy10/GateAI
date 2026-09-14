sed -i 's/compileSdk { version = release(36) { minorApiLevel = 1 } }/compileSdk = 35/g' app/build.gradle.kts
sed -i 's/targetSdk = 36/targetSdk = 35/g' app/build.gradle.kts
