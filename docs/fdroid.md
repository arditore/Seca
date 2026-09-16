# Publishing Seca on F-Droid

Where each app stands, what this repository already satisfies, and the steps that need a
person with an account somewhere.

| App | F-Droid | IzzyOnDroid |
|---|---|---|
| Seca Contacts | merge request !49024, green, reproducible build verified | ready to request |
| Seca Phone | merge request !49025, green, reproducible build verified | ready to request |
| Seca Messages | merge request !49106, green, reproducible build verified | ready to request |

## Decisions taken

- **Beta tags ship.** `UpdateCheckMode: Tags` follows every tag, so `v0.1.0-beta6` and the ones
  after it are published as updates. To publish only stable versions later, narrow it to
  `Tags ^v[0-9.]+$`.
- **Our signature everywhere.** F-Droid signs with its own key unless it can rebuild, byte for
  byte, the APK published here; then it distributes ours instead. That choice cannot be undone
  once an app is published, so it is made now: `Binaries` and `AllowedAPKSigningKeys` are in all
  three recipes, and the APKs attached to each GitHub release are built the way their server
  builds them. Anyone can therefore move between F-Droid and GitHub without reinstalling.

## What F-Droid asks for, and where it stands here

- **A FOSS license.** `LICENSE`, GPL-3.0-or-later. Seca Messages links libsignal (AGPL-3.0), so
  that binary is AGPL-3.0-or-later; its metadata says so.
- **Public source code, kept up to date.** github.com/arditore/Seca.
- **A git tag per release**, and the recipe points at the **full commit hash** of that tag, never
  at the tag name: a tag can be moved, a hash cannot.
- **Listing texts in the repository.** `apps/<app>/fastlane/metadata/android/en-US/` carries the
  title, the short description (under 80 characters), the full description, the icon, the
  screenshots and one changelog per versionCode (under 500 characters). French is there too.
- **No proprietary dependency.** The `verifyReleaseNoProprietaryDependencies` task fails the build
  if a Google artifact reaches an app's runtime classpath; it runs as part of `check`.
- **Dependencies built from source or taken from the repositories F-Droid allows** (Maven Central,
  Google Maven, Sonatype, JFrog, JitPack, Clojars). Seca Contacts and Seca Phone take everything
  from Maven Central and Google Maven. Seca Messages needs libsignal, which is neither: see below.
- **Nothing hidden from their scanner.** `scanignore` tells it to look away, and reviewers ask for
  it to be removed. Instead, `settings.gradle.kts` marks the block that names where libsignal
  comes from, and each recipe patches that block in `prebuild`, before the scan runs: Seca
  Contacts and Seca Phone delete it, Seca Messages replaces it with `mavenLocal()`.

## Seca Messages: libsignal built from source

`org.signal:libsignal-android` is published on Signal's own Maven repository, which F-Droid does
not take, and it ships prebuilt native libraries, which F-Droid does not accept either. So the
recipe in `fdroid/metadata/com.seca.messages.yml` compiles it:

1. `sudo` installs the build tools and **Debian's `rustup` package** — their reviewers do not want
   the upstream installer script downloaded and run.
2. `srclibs` checks out `libsignal@v0.102.2`, defined by `fdroid/srclibs/libsignal.yml`.
3. `prebuild` selects the Rust version libsignal pins (1.98.1), adds the two Android targets, and
   runs libsignal's own build for `android-aarch64` and `android-arm` — the two ABIs Seca Messages
   ships — then publishes `libsignal-android` and `libsignal-client` into the local Maven folder.
4. The patched `settings.gradle.kts` takes libsignal from there.

This repository is ready for it: `settings.gradle.kts` takes libsignal from wherever
`seca.libsignal.repo` points (a folder, or `mavenLocal`), and falls back to Signal's repository
when nothing is said. `apps/messages/build.gradle.kts` takes its NDK version from
`seca.ndkVersion` the same way, defaulting to the one F-Droid builds with.

**What has been proven, and where.** The recipe was run on Ubuntu 24.04, the family F-Droid builds
on. libsignal 0.102.2 compiled from its Rust sources for `arm64-v8a` and `armeabi-v7a`, both
artifacts published into the local Maven folder, and Seca Messages then built against them: a
23 MB APK, from sources all the way down. Their own build server did the same, in 31 minutes.

Getting there taught the recipe what it was missing: `protobuf-compiler` and `libprotobuf-dev`,
`cmake` with `ninja-build`, `clang` with `libclang-dev`, the flag that keeps libsignal to the two
ABIs this app ships (`-PandroidArchs=aarch64,arm`), and that publishing the Android artifact alone
is not enough — `libsignal-android` depends on `libsignal-client`, which must be published too.

## Building the APKs that get published

For F-Droid to rebuild what is published here, both builds have to be made the same way. The
release APKs are therefore built on Linux (WSL Ubuntu 24.04 here, Debian there), with the same
build tools 36.0.0 and the same NDK 28.0.13004108, and only signed on the machine that holds the
key — signatures are stripped before the two builds are compared, so signing where the key lives
changes nothing.

Three things had to be fixed for that to hold, all found by comparing their build against ours:

1. **Signing must not touch the archive.** `apksigner` re-aligns an APK as it signs, which moves
   every entry and breaks the signature copy their verification does. `--alignment-preserved`
   leaves the archive exactly as the build produced it, so the signature is all that is added.
2. **BoringSSL writes absolute paths into libsignal's native library**, through the `__FILE__` of
   its assertions, and two machines never share that path. Both builds now compile it with
   `-ffile-prefix-map=<libsignal>=.`, which makes those paths relative.
3. **The GNU build id.** With the paths fixed, their library and ours still differed by exactly 8
   bytes in 8.9 MB: the id the linker computes from debug information that still names local
   folders, and which survives the stripping that removes that information. libsignal asks for it
   itself, in `rust/bridge/jni/build.rs`, after anything passed on the command line, so the recipe
   changes the request rather than the flags around it.

Proven twice over: libsignal built from scratch in two different folders gives byte-identical
libraries for both ABIs, and F-Droid's own server rebuilt all three published APKs and verified
them — `...successfully verified`. The whole procedure is: build in Linux, sign on Windows, attach
to the GitHub release.

## Steps that need an account

### F-Droid

The three merge requests are open against <https://gitlab.com/fdroid/fdroiddata>, one per app,
each on its own branch in a fork, and each carrying the metadata file from `fdroid/metadata/`.
Seca Messages also carries `fdroid/srclibs/libsignal.yml` as `srclibs/libsignal.yml`.

To publish a new version: update `versionName`, `versionCode`, `commit` (the full hash),
`CurrentVersion` and `CurrentVersionCode` in each file, and open one merge request per app —
or let `AutoUpdateMode: Version` do it once the apps are published.

### IzzyOnDroid, for the three apps

Requirements already met: APKs signed with the release key, attached to the latest GitHub release,
under 30 MB, no debuggable or testOnly flag, fastlane metadata in the repository.

Open a request at <https://codeberg.org/IzzyOnDroid/repo/issues> with the link to
<https://github.com/arditore/Seca>. Expect a question about the permissions Seca asks for; the
answer is in each app's full description: SMS and call log are what a messaging app and a dialer
do, Seca Contacts and Seca Phone have no `INTERNET` permission at all, and Seca Messages uses it
only for Seca Link, which is off by default.
