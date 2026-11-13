# Publishing Seca on F-Droid

Where each app stands, what this repository has to keep satisfying for F-Droid to rebuild it,
and what a new release has to do in what order.

| App | F-Droid | IzzyOnDroid |
|---|---|---|
| Seca Contacts | **published** ([!49024](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/49024)), reproducible | ready to request |
| Seca Phone | **published** ([!49025](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/49025)), reproducible | ready to request |
| Seca Messages | **published** ([!49106](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/49106)), reproducible, both native libraries built from source | ready to request |

The reproducible build is not a claim. For 0.1.0-beta10, the APK f-droid.org serves has the
same SHA-256 as the one attached to the GitHub release, for all three:

```text
46e0cdf43878edb8f0ce16a01f8acebd57bb2ead76dd68d64ef057fa8d190760  seca-contacts
202f3e114bb13a9c6686e72aa89ca72d20368142ab4fa7c28ee0bf2485c9688d  seca-phone
22cd33add05402d6f93c8af112586bb278080eb66e00a745e773f9e4b358baf9  seca-messages
```

Their server rebuilt each one, found the same bytes, and distributes ours with this project's
signature.

All three follow new tags by themselves (`AutoUpdateMode: Version`), so a release here reaches
F-Droid without a merge request — as long as the APK published here stays reproducible, which
means building it the way their server does. Their queue means F-Droid trails the release here
by a few days, and `fdroid/metadata/` in this repository is a copy of what `fdroiddata` carries,
not the other way round.

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
them — `...successfully verified`. The whole procedure is: build in Linux, sign with the key kept
off the build machine, attach the APKs to the GitHub release, and only then push the tag.

## Seca Messages: what the second review asked for

A second reviewer went through 0.1.0-beta6 in detail on 3 October, and the merge request was
closed on 1 November for having had no answer in between. It was reopened with the three points
below answered, and merged: Seca Messages is published.

1. **The licence had to agree with itself.** The metadata said AGPL-3.0-or-later, because the app
   links libsignal, while the repository said GPL. Seca Messages now carries its own
   `apps/messages/LICENSE`, the AGPL, and the README says which app is under which.
2. **Picture messages are supported, both ways.** This was the reviewer's sharpest point. As the
   default SMS app, Seca Messages is the only app the phone hands a WAP push to, so for a while
   adopting it cost a user their picture messages without a word. They arrive and are shown
   since beta 10. Sending was built in beta 11 but no message reached anybody: the recipient's
   address was written the way the sender's is, so the carrier accepted every message and had
   nowhere to send it, while the sending phone showed it as sent. Fixed in beta 12, the first
   release where a picture, a video, a file, a contact card or a voice message actually arrives
   through the carrier. Beta 13 fixed the other half: a part that names its type in plain words,
   which is the form Seca's own composer writes, made the reader give up, so a picture sent by
   Seca was stored by Seca with no parts at all. Group conversations over MMS are still to come,
   and the listing says so rather than claiming otherwise.
3. **secp256k1 was a prebuilt binary, and is not any more.**
   `fr.acinq.secp256k1:secp256k1-kmp-jni-android` ships `libsecp256k1-jni.so`, which F-Droid does
   not accept any more than libsignal's. The recipe builds it from the `Secp256k1Kmp` srclib, the
   way `com.gorunjinian.metrovault` already does: the SDK's own cmake, the submodule checkout, and
   the same `-Wl,--build-id=none` the linker needs for the library to come out the same on two
   machines. The APK published here carries that same from-source library, built in WSL by
   `scratchpad/wsl-secp256k1.sh`, or their reproducibility check would fail.

   The patched repository block is an `exclusiveContent` naming `org.signal` and the module
   `fr.acinq.secp256k1:secp256k1-kmp-jni-android`, so Maven Central cannot answer for either and
   no prebuilt library can slip back in. It names that one module rather than the whole group on
   purpose: `secp256k1-kmp-jni-common` is a Kotlin wrapper with no binary in it, and it is not
   published locally. A bare `mavenLocal()` among the other repositories, as the first draft had,
   would have let Maven Central answer first and ship the prebuilt library after all — their
   reviewer caught that before the build did.

**The check that proves it, before publishing:** every native library in the APK, stripped of its
build id and of any absolute path. `strings -a` and `readelf -n` over `lib/*/*.so` must come back
empty for `/home/`, `/opt/`, `/builds/` and `Build ID` on the two libraries built here. The three
AndroidX libraries keep their build ids, which is harmless: both sides download the same file.

## Publishing a new version

### F-Droid

The three merge requests against <https://gitlab.com/fdroid/fdroiddata> are merged, so nothing
needs an account any more: their bot opens the merge request for each new tag by itself, and the
recipes in `fdroid/metadata/` are kept here only as a copy of what it maintains there.

**Attach the APKs to the GitHub release before pushing the tag, not after.** `Binaries:` points
at a file named after the tag, and their build runs as soon as the tag exists. Beta 11 was tagged
seventeen hours before its APKs went up, their build landed in that window with nothing to fetch,
and someone opened [issue #45](https://github.com/arditore/Seca/issues/45) about it.

Should a recipe ever need changing by hand, the three fields that move are `versionName`,
`versionCode` and `commit` (the full hash, never the tag name), plus `CurrentVersion` and
`CurrentVersionCode`. While a merge request is waiting, a new release goes into that same merge
request rather than a new one: push the updated recipe to its branch. Their reviewer asked for
exactly that, and a recipe left pointing at an older commit would have them test a version
nobody runs any more.

### IzzyOnDroid, not asked for yet

This one still needs an account and a request, for all three apps. Requirements already met: APKs signed with the release key, attached to the latest GitHub release,
under 30 MB, no debuggable or testOnly flag, fastlane metadata in the repository.

Open a request at <https://codeberg.org/IzzyOnDroid/repo/issues> with the link to
<https://github.com/arditore/Seca>. Expect a question about the permissions Seca asks for; the
answer is in each app's full description: SMS and call log are what a messaging app and a dialer
do, Seca Contacts and Seca Phone have no `INTERNET` permission at all, and Seca Messages uses it
only for Seca Link, which is off by default.
