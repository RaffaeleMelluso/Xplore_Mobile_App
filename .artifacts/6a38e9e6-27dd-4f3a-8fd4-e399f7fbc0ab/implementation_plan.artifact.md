# Fix KSP "unexpected jvm signature V" error

The build error `unexpected jvm signature V` is caused by an incompatibility between **Room 2.5.1** and the **KSP2** processor (which is likely enabled by default or used with Kotlin 2.0+). Specifically, Room 2.5.1's KSP processor does not correctly handle `Unit` (represented as `V` for `void` in JVM signatures) return types in DAOs when using the K2 compiler.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Users/Powag/AndroidStudioProjects/ProgMob_Progetto_Melluso/gradle/libs.versions.toml)
- Update `room` version from `2.5.1` to `2.8.4` (latest stable).

#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/Powag/AndroidStudioProjects/ProgMob_Progetto_Melluso/app/build.gradle.kts)
- Replace the hardcoded `ksp("androidx.room:room-compiler:2.5.1")` with the version catalog reference `ksp(libs.androidx.room.compiler)`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:kspDebugKotlin` to verify that the Room annotation processing completes successfully.
- Run a full build `./gradlew assembleDebug` to ensure no other regressions.
