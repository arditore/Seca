# Seca — A private communication suite for GrapheneOS

> Design approved on 2026-09-09. Covers phase 1. Seca Phone and Seca Messages each get their own spec.

## Context

Three Android apps sharing a unified Material design, privacy-focused and free of Google, for a Pixel 9 running GrapheneOS, published as open source on F-Droid: **Seca Contacts**, **Seca Phone**, **Seca Messages**.

Three checks made up front reshaped the scope of the original request (which included "RCS support" and "Wi-Fi calling support"):

1. **RCS is out of reach for a third-party app.** Google keeps its RCS API to a closed allowlist. Android's RCS is split between the OS, Google Messages and Play Services. GrapheneOS, with system privileges and dedicated staff, announced on 6 September 2026 that it aims for native RCS with E2EE through MLS — **with no timeline**, and its first version would still depend on sandboxed Play Services for activation. A user-installed APK cannot do what an OS project cannot yet do.
2. **Wi-Fi calling is not a dialer feature.** VoWiFi is IMS (an IPsec tunnel to the carrier's ePDG), handled by the modem and the system stack. It already works on a Pixel 9 with GrapheneOS whatever dialer is installed. A third-party dialer can *show* the VoWiFi/HD state and manage `PhoneAccount`s — it does not implement it.
3. **GrapheneOS is rewriting its Messages app in Compose**, with RCS planned. Seca stands apart through a design unified across the three apps and the Seca-to-Seca E2EE layer, not by chasing RCS.

**Expected outcome of phase 1:** a working Gradle monorepo, a shared Material 3 Expressive design system, and a complete, installable **Seca Contacts** — the technical foundation (the other two apps read the address book) and the proving ground of the visual language.

## Decisions

| Topic | Decision |
|---|---|
| Modern messaging | SMS/MMS plus a Seca-to-Seca E2EE layer. No RCS. A `Transport` interface to plug RCS in later if it ever opens. |
| E2EE transport | A minimal relay (encrypted mailboxes, blind to content) plus UnifiedPush/ntfy. Self-hostable. |
| Audience | Open source, F-Droid → reproducible builds, zero proprietary dependency. |
| Design | Material 3 Expressive alpha, accepted, confined to the `:core:design` module. |
| Structure | Multi-module Gradle monorepo, 3 APKs, shared design. |
| Contact sync | None. DAVx⁵ already syncs into `ContactsContract`; the apps read the system provider. |
| First deliverable | `:core:design` + Seca Contacts. |
| Light/dark theme | Follows the system theme, full stop. No switch in the app. |
| Color | Material You by default (wallpaper colors). As an option, in settings: one of a few Seca palettes, from which each app derives its own distinct variant. |
| Cross-app navigation | A bottom bar in each of the three apps, cross-linking by intent to the two siblings. The three APKs stay separate. |
| Icons | Drawn by hand as `ImageVector`s in `:core:design`. `material-icons-core` and `-extended` are frozen at 1.7.8 while Compose is at 1.12.0: dead libraries, set aside. |

## Toolchain

The starting environment has **JDK 25 only**, with no Android SDK, no Gradle and no adb. AGP 9.4 (September 2026) requires:

- **JDK 17** — the minimum *and* the default in AGP 9.4's compatibility table. The JDK 25 present does not fit; install Temurin 17 and target it through `JAVA_HOME` plus the Gradle toolchain, without uninstalling 25.
- **Gradle 9.7.1** (through the wrapper, no system install; AGP 9.4 requires at least 9.6.0)
- **Android SDK**: cmdline-tools, platform-tools (adb), Build Tools 36.0.0, platform API 37
- `compileSdk 37` (the maximum AGP 9.4 supports). The `targetSdk` is to be confirmed against what GrapheneOS ships on the Pixel 9 — to check on the device, not to guess.

> **Correction of 2026-09-09, after checking the device and the artifacts.**
> The Pixel 9 (`tokay`) runs **Android 17, API 37**. The matching SDK platform is named
> `platforms;android-37.0` — `platforms;android-37` does not exist, the SDK having moved to minor versions.
> `android-37.0` is released, not a preview (`PreviewSdkInt=0`, empty `BetaVersion`).
>
> **`compileSdk = 37` is mandatory.** The AAR metadata of `material3:1.5.0-alpha27`, and also of the
> **stable 1.12.0** Compose `ui` and `foundation`, all declare `minCompileSdk=37`. No Compose version
> this project uses builds with `compileSdk 36`: AGP fails the `checkDebugAarMetadata` check, with no
> flag to bypass it.
>
> `targetSdk` stays at **36** — the level tested against, independent of `compileSdk`.
> `buildToolsVersion` stays `36.0.0`, `minSdk` stays 34.
>
> *A first version of this note set `compileSdk 36` to avoid the supposed risk of minor SDK versions in
> AGP 9.4's DSL. That was a mistake: the risk does not exist — `compileSdk = 37` resolves `android-37.0`
> with no extra setting — and the choice made the project impossible to build. Task 4 exposed the
> failure by getting stuck on it.*

The exact versions of Kotlin, the Compose BOM and the `material3` alpha are pinned in the first task by querying the repositories. `material3` must be ≥ `1.5.0-alpha04` for the Expressive APIs (`1.5.0-alpha24` in July 2026).

## Repository layout

The three initial folders (`Seca Contacts`, `Seca Messages`, `Seca Phone`) contain spaces, which makes Gradle paths and F-Droid build recipes fragile. They are empty — renaming them loses nothing:

```
Seca/
├── settings.gradle.kts
├── gradle/libs.versions.toml        # version catalog, every version pinned
├── build-logic/                     # convention plugins (shared configuration)
├── core/
│   ├── design/                      # :core:design — M3E theme, tokens, components
│   ├── model/                       # domain types (SecaContact, PhoneNumber…)
│   └── contacts/                    # ContactsContract access, shared by the 3 apps
└── apps/
    ├── contacts/                    # Seca Contacts  (phase 1)
    ├── phone/                       # Seca Phone     (phase 2)
    └── messages/                    # Seca Messages  (phase 3)
```

`build-logic` carries the common configuration (compileSdk, toolchain, Compose options, lint rules) so the three apps do not drift apart.

## `:core:design` — the heart of the visual unity

The module that answers the "unified, very beautiful design" requirement. It holds **everything** visual; no app defines its own color, shape or typography.

- Seca palette as M3 Expressive tokens, light and dark themes following the system
- **Material You by default, decision corrected on 2026-09-10.** A first reading had set dynamic color aside; the owner wants it by default, with the Seca palettes as a simple option in settings. With dynamic color, the three apps share the wallpaper's colors, like system apps; choosing a palette is what makes them "distinguishable at a glance", each app deriving its own variant.
- An expressive type scale and set of shapes
- Motion specs — M3 Expressive puts the emphasis on movement, which is where most of the perceived quality lies
- Shared components: contact avatar, contact row, search bar, empty states, action sheets
- An **identity per app** derived from the same base: each app gets a distinct accent hue while keeping identical tokens, shapes, type and motion. Recognisable as a family, distinguishable at a glance.

Every experimental API is opted into **here only**, never in the app modules: when the alpha breaks, a single module is affected.

## Seca Contacts — phase 1 scope

Reads and writes the system `ContactsContract`. Must work properly under GrapheneOS's **Contact Scopes**: the OS may present only a subset of contacts, or none. That is a normal state, not an error.

**Storage on the device only** — the owner's requirement, 2026-09-10. Seca Contacts reads every contact on the device, including those created by another app, and saves its own in the device's local account (null `ACCOUNT_TYPE` and `ACCOUNT_NAME`), never in a synced account.

Features:
- A list with fast scrolling, search, sorting and grouping
- Contact card, creation and editing
- Favorites, groups/labels
- vCard (VCF) import and export
- Quick actions: call → Seca Phone, write → Seca Messages, falling back cleanly to the system apps while those do not exist (implicit intents, never a hard dependency)

**A strong, verifiable privacy property: no `INTERNET` permission.** The app structurally cannot exfiltrate an address book. A concrete argument for F-Droid, to document in the README and to enforce with a test that fails if the permission appears.

No analytics, no crash reporting, no Google dependency.

## Implementation approach

TDD: tests first for the domain logic and the repositories.

- **Unit / Robolectric tests** — logic, repositories, `ContactsContract` through a fake provider. They run without a device.
- **Compose UI tests** — design system components and screens.
- **Guard test** — an assertion on the merged manifest: no `INTERNET`.
- **Instrumented** — need an emulator or the Pixel 9 over USB. Unavailable at design time (no adb, no device connected): to set up before verification, otherwise the integration tests cannot run.

## Verification

1. `./gradlew :apps:contacts:assembleDebug` produces an APK
2. `./gradlew test` — unit and Robolectric tests green
3. The `INTERNET` guard test passes, and does fail if the permission is added
4. Install on the Pixel 9: list, search, creation, editing, favorites, VCF import/export
5. **Under Contact Scopes**: explicitly check the behavior with partial access and with empty access
6. Light/dark switch and dynamic color — a visual check of token consistency
7. `./gradlew lint` clean

## Out of scope for phase 1

A project this size does not fit in a single spec. Planned order, each with its own spec and plan:

- **Phase 2** — Seca Phone: `ROLE_DIALER`, `InCallService`, call log, screening, VoWiFi/HD indicators, multiple `PhoneAccount`s. Possibly a self-managed `ConnectionService` for encrypted VoIP. This is where `:core:contacts`, written in phase 1 for Seca Contacts, is generalised into a base a second client can use.
- **Phase 3** — Seca Messages: SMS/MMS, then the E2EE layer plus UnifiedPush relay. By far the heaviest.

## Identified risks

- **`material3` alpha** — API breaks with every version bump. Mitigated by confinement to `:core:design`, not removed.
- **MMS (phase 3)** — APN configuration, MMSC, PDU encoding. Notoriously painful and poorly documented. Not to be underestimated when planning that phase.
- **F-Droid reproducible builds** — a constraint to build in from the start of the build setup, not to catch up on later.
- **GrapheneOS ships competing apps** — their Compose Messages is due "in weeks". Seca's value must remain the design unified across three apps and the E2EE layer, not the race for RCS.
- **Workload** — three complete system apps represent several months of work. Phase 1 is deliberately sized to deliver something usable and beautiful quickly.

## References

- [XDA — Google's RCS API is limited to an allowlist](https://www.xda-developers.com/google-messages-rcs-api-third-party-apps/)
- [heise — GrapheneOS plans RCS and E2EE through MLS](https://www.heise.de/en/news/GrapheneOS-plans-its-own-messenger-solution-with-RCS-and-E2EE-11445618.html)
- [AlternativeTo — GrapheneOS overhauls its AOSP apps](https://alternativeto.net/news/2026/9/grapheneos-plans-to-overhaul-the-bundled-aosp-apps-including-messaging-with-rcs-support/)
- [AGP 9.4 — release notes and compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
- [Compose Material 3 — releases](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- [GrapheneOS — usage guide, Contact Scopes](https://grapheneos.org/usage)
