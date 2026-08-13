# Walkthrough - Fixed KSP "unexpected jvm signature V"

I have resolved the `java.lang.IllegalStateException: unexpected jvm signature V` error occurring during KSP processing.

## Changes

### Dependencies
- Updated **Room** version from `2.5.1` to `2.8.4` in [libs.versions.toml](file:///C:/Users/Powag/AndroidStudioProjects/ProgMob_Progetto_Melluso/gradle/libs.versions.toml).
- Cleaned up [app/build.gradle.kts](file:///C:/Users/Powag/AndroidStudioProjects/ProgMob_Progetto_Melluso/app/build.gradle.kts) by replacing the hardcoded `room-compiler` dependency with the version catalog reference.

## Verification Results

### Automated Tests
- Ran `./gradlew :app:kspDebugKotlin`: **SUCCESS**
- This confirms that the Room annotation processor is now compatible with your Kotlin version and KSP2 configuration.

render_diffs(file:///C:/Users/Powag/AndroidStudioProjects/ProgMob_Progetto_Melluso/gradle/libs.versions.toml)
render_diffs(file:///C:/Users/Powag/AndroidStudioProjects/ProgMob_Progetto_Melluso/app/build.gradle.kts)
