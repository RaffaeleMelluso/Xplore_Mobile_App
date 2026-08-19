# Fix KSP Build Error: "unexpected jvm signature V"

The project is experiencing a KSP compilation error: `[ksp] java.lang.IllegalStateException: unexpected jvm signature V`. This error is a known compatibility issue between older versions of Room (like 2.5.1) and newer Kotlin/KSP versions. Room 2.5.1 does not fully support the KSP features required by modern Kotlin compilers (Kotlin 2.0+), leading to failures when resolving JVM signatures for `suspend` functions or `Unit` return types.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Users/Powag/AndroidStudioProjects/ProgMob_Progetto_Melluso/gradle/libs.versions.toml)
- Upgrade `room` version from `2.5.1` to `2.8.4` (or latest stable compatible with the project's futuristic setup).
- Update `lifecycleViewModelKtx` to `2.11.0` for consistency with `lifecycleRuntimeKtx`.

#### [MODIFY] [build.gradle.kts (app)](file:///C:/Users/Powag/AndroidStudioProjects/ProgMob_Progetto_Melluso/app/build.gradle.kts)
- Replace hardcoded `ksp("androidx.room:room-compiler:2.5.1")` with the version-catalog reference `ksp(libs.androidx.room.compiler)`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:kspDebugKotlin` to verify that the KSP processing completes successfully.
- Run `./gradlew :app:assembleDebug` to ensure the entire app builds.

### Manual Verification
- Verify that Room-generated code (DAOs) is correctly created in `build/generated/ksp/debug/kotlin`.
