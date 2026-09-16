# Publishing Seca on F-Droid

Where each app stands, what this repository already satisfies, and the steps that need a
person with an account somewhere.

| App | F-Droid | IzzyOnDroid |
|---|---|---|
| Seca Contacts | ready to submit | ready |
| Seca Phone | ready to submit | ready |
| Seca Messages | recipe written, to be tried with the maintainers | ready |

## Decisions taken

- **Beta tags ship.** `UpdateCheckMode: Tags` follows every tag, so `v0.1.0-beta3` and the ones
  after it are published as updates. To publish only stable versions later, narrow it to
  `Tags ^v[0-9.]+$`.
- **F-Droid signs first.** Their key is the default, so an APK from F-Droid will not install over
  one from GitHub. Once the build is reproducible, F-Droid can publish the APKs signed here
  instead — the fields for that are at the end of this file.

## What F-Droid asks for, and where it stands here

- **A FOSS license.** `LICENSE`, GPL-3.0-or-later. Seca Messages links libsignal (AGPL-3.0), so
  that binary is AGPL-3.0-or-later; its metadata says so.
- **Public source code, kept up to date.** github.com/arditore/Seca.
- **A git tag per release.** `v0.1.0-beta3` and the ones before it, each matching its versionName.
- **Listing texts in the repository.** `apps/<app>/fastlane/metadata/android/en-US/` carries the
  title, the short description (under 80 characters), the full description, the icon, the
  screenshots and one changelog per versionCode (under 500 characters). French is there too.
- **No proprietary dependency.** The `verifyReleaseNoProprietaryDependencies` task fails the build
  if a Google artifact reaches an app's runtime classpath; it runs as part of `check`.
- **Dependencies built from source or taken from the repositories F-Droid allows** (Maven Central,
  Google Maven, Sonatype, JFrog, JitPack, Clojars). Seca Contacts and Seca Phone take everything
  from Maven Central and Google Maven. Seca Messages needs libsignal, which is neither: see below.

## Seca Messages: libsignal built from source

`org.signal:libsignal-android` is published on Signal's own Maven repository, which F-Droid does
not take, and it ships prebuilt native libraries, which F-Droid does not accept either. So the
recipe in `fdroid/metadata/com.seca.messages.yml` compiles it:

1. `sudo` installs the Rust toolchain libsignal pins (`rust-toolchain`: 1.98.1).
2. `srclibs` checks out `libsignal@v0.102.2`, defined by `fdroid/srclibs/libsignal.yml`.
3. `prebuild` adds the two Android targets, runs libsignal's own `java/build_jni.sh` for
   `android-aarch64` and `android-arm` — the two ABIs Seca Messages ships — then publishes its
   Android artifact into the local Maven folder.
4. `gradleprops` tells this build to take libsignal from there: `-Pseca.libsignal.repo=mavenLocal`,
   which `settings.gradle.kts` understands, plus the NDK version to use.

This repository is ready for it: `settings.gradle.kts` takes libsignal from wherever
`seca.libsignal.repo` points (a folder, or `mavenLocal`), and falls back to Signal's repository
when nothing is said. `apps/messages/build.gradle.kts` takes its NDK version from
`seca.ndkVersion` the same way.

**What has been proven, and where.** The recipe was run on Ubuntu 24.04, the family F-Droid builds
on. libsignal 0.102.2 compiled from its Rust sources for `arm64-v8a` and `armeabi-v7a`, both
`libsignal-android` and `libsignal-client` published into the local Maven folder, and Seca Messages
then built against them: a 23 MB APK, from sources all the way down.

Getting there taught the recipe what it was missing: `protobuf-compiler` and `libprotobuf-dev`,
`cmake` with `ninja-build`, `clang` with `libclang-dev`, the flag that keeps libsignal to the two
ABIs this app ships (`-PandroidArchs=aarch64,arm`), and that publishing the Android artifact alone
is not enough — `libsignal-android` depends on `libsignal-client`, which must be published too. It
needs a JDK 21 and NDK 28, and the Rust version libsignal pins itself.

**What is still unproven:** the same recipe inside F-Droid own build server, where the toolchain
is installed differently. Expect a round or two with the maintainers; that is what the review is
for.

## Steps that need an account

### F-Droid, for Seca Contacts and Seca Phone

1. Create an account on gitlab.com and fork <https://gitlab.com/fdroid/fdroiddata>.
2. In the fork, create a branch named `com.seca.contacts`.
3. Copy `fdroid/metadata/com.seca.contacts.yml` from this repository to
   `metadata/com.seca.contacts.yml` in the fork, and commit it.
4. Open a merge request against `fdroiddata`, titled `New app: Seca Contacts`.
5. Do the same for `com.seca.phone`, on its own branch and its own merge request.
6. Answer the reviewers. Once merged, the app appears within a day or two.

### F-Droid, for Seca Messages

Same steps, plus `fdroid/srclibs/libsignal.yml` copied to `srclibs/libsignal.yml` in the same
merge request, since the recipe refers to it.

### IzzyOnDroid, for the three apps

Requirements already met: APKs signed with the release key, attached to the latest GitHub release,
under 30 MB, no debuggable or testOnly flag, fastlane metadata in the repository.

Open a request at <https://codeberg.org/IzzyOnDroid/repo/issues> with the link to
<https://github.com/arditore/Seca>. Expect a question about the permissions Seca asks for; the
answer is in each app's full description: SMS and call log are what a messaging app and a dialer
do, Seca Contacts and Seca Phone have no `INTERNET` permission at all, and Seca Messages uses it
only for Seca Link, which is off by default.

## Later: publishing the APKs signed here

Once a build here and a build there produce the same bytes, F-Droid can publish this project's own
signed APKs, which then install over the ones from GitHub:

```yaml
AllowedAPKSigningKeys: 2fdea7b46beffe16459bb4dcdd34db5e0cb912274a5eb628e4b0d8a596211790
Binaries: https://github.com/arditore/Seca/releases/download/v%v/seca-contacts-%v.apk
```
