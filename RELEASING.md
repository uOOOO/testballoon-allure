# Releasing

testballoon-allure ships once per supported Kotlin version, versioned
`<library-version>-K<kotlin-version>` (e.g. `0.1.0-K2.3.20`). The `-K`
suffix must match the consumer's TestBalloon variant, because the
TestBalloon Gradle plugin injects a compiler plugin built for exactly
that Kotlin version and this library depends on the matching
`testBalloon-framework-core`.

## How variants build

Everything releases from `main`. `-PkotlinVariant=<kotlin-version>`
repins the catalog's `kotlin` and `testballoon` entries (see
`settings.gradle.kts`); without it, builds use the versions in
`gradle/libs.versions.toml`. The supported variant list lives in the
`release.yml` matrix; the README table mirrors it. When bumping the
TestBalloon version in the catalog, also bump `testBalloonBaseVersion`
in `settings.gradle.kts`.

## Cutting a release

1. Make sure CI is green on `main`. Optionally sweep the variants
   locally: for each matrix entry, run
   `./gradlew -PkotlinVariant=<v> :testballoon-allure:jvmTest :testballoon-allure-android:testAndroidHostTest`
2. Tag the base version (no `-K` suffix) and push the tag:
   `git tag v<version> && git push origin v<version>`
3. Run the Release workflow from the tag (Actions UI, or
   `gh workflow run release.yml --ref v<version>`). It lints once, then
   tests and uploads every variant in parallel.
4. Uploading is not publishing: in the Central Portal
   (https://central.sonatype.com/publishing), publish the deployments
   oldest Kotlin first, newest last — indexers that treat the most
   recent publication as "latest" then point at the newest variant.
5. Verify the coordinates resolve, then start the next development
   cycle by bumping `version` in `gradle.properties`.

## Adding or dropping a Kotlin variant

1. Check the variant builds: run the gate with `-PkotlinVariant=<v>`.
   TestBalloon variants are listed in its CHANGELOG
   (https://github.com/infix-de/testBalloon/blob/main/CHANGELOG.md).
2. Edit the `kotlin` matrix list in `.github/workflows/release.yml` and
   the README table. Keep both in sync.
3. A variant that fails the gate (old AGP/KGP floors, newer-API usage)
   stays out — never ship an untested variant. Current floor: Kotlin
   2.2.0 (2.1.20 and older fail to build against their TestBalloon
   variants).
