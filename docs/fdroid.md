# Publishing Seca on F-Droid

Where each app stands, what this repository already satisfies, and the steps that need a
person with an account somewhere.

| App | F-Droid | IzzyOnDroid |
|---|---|---|
| Seca Contacts | ready to submit | ready |
| Seca Phone | ready to submit | ready |
| Seca Messages | blocked by libsignal, see below | ready |

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
- **Dependencies from the repositories F-Droid allows** (Maven Central, Google Maven, Sonatype,
  JFrog, JitPack, Clojars). Seca Contacts and Seca Phone take everything from Maven Central and
  Google Maven. Seca Messages does not: see below.

## What blocks Seca Messages

`org.signal:libsignal-android` comes from `https://build-artifacts.signal.org/libraries/maven/`,
which is not among the repositories F-Droid allows, and it ships prebuilt native libraries, which
F-Droid expects to be built from source. The build also pins `ndkVersion 30.0.16248370`.

Three ways out, from lightest to heaviest:

1. **IzzyOnDroid first.** That repository takes the APK this project signs, from GitHub Releases,
   as long as it stays under 30 MB — Seca Messages is 22 MB. Nothing to change.
2. **Build libsignal from its Rust sources** inside the F-Droid recipe. It is what the F-Droid
   policy wants and what Molly does; it is also a serious piece of work (Rust toolchain, NDK,
   build time) and it has to keep working at every libsignal update.
3. **Fall back to the libsignal published on Maven Central** (0.86.5 at the time of writing).
   It is older than the one used here and would have to be checked against the code, and the
   prebuilt native code question stays.

## Steps that need an account

### F-Droid, for Seca Contacts and Seca Phone

1. Create an account on gitlab.com and fork <https://gitlab.com/fdroid/fdroiddata>.
2. In the fork, create a branch named `com.seca.contacts`.
3. Copy `fdroid/metadata/com.seca.contacts.yml` from this repository to `metadata/com.seca.contacts.yml`
   in the fork, and commit it.
4. Open a merge request against `fdroiddata`, titled `New app: Seca Contacts`.
5. Do the same for `com.seca.phone`, on its own branch and its own merge request.
6. Answer the reviewers. Once merged, the app appears within a day or two.

Nothing else is needed from this repository: F-Droid builds from the tag, and reads the listing
texts from the fastlane folders.

### IzzyOnDroid, for the three apps

Requirements already met: APKs signed with the release key, attached to the latest GitHub release,
under 30 MB, no debuggable or testOnly flag, fastlane metadata in the repository.

Open a request at <https://codeberg.org/IzzyOnDroid/repo/issues> with the link to
<https://github.com/arditore/Seca>. Expect a question about the permissions Seca asks for; the
answer is in each app's full description: SMS and call log are what a messaging app and a dialer
do, Seca Contacts and Seca Phone have no `INTERNET` permission at all, and Seca Messages uses it
only for Seca Link, which is off by default.

## Two decisions to make before submitting

- **Betas.** F-Droid follows tags: with `UpdateCheckMode: Tags`, every `v0.1.0-betaN` tag ships as
  an update. To publish only stable versions, set `UpdateCheckMode: Tags ^v[0-9.]+$` in the
  metadata, or wait for 1.0 before submitting.
- **Who signs.** By default F-Droid signs with its own key, so the APK from F-Droid cannot be
  installed over one from GitHub. To keep this project's signature, F-Droid can publish the APKs
  built here once it reproduces them byte for byte, with these fields:

  ```yaml
  AllowedAPKSigningKeys: 2fdea7b46beffe16459bb4dcdd34db5e0cb912274a5eb628e4b0d8a596211790
  Binaries: https://github.com/arditore/Seca/releases/download/v%v/seca-contacts-%v.apk
  ```

  It only works if the build is reproducible: same sources, same tools, same output. Worth trying
  after the first inclusion, not before.
