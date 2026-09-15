# Seca Link — end-to-end encrypted messages between Seca users

> Design of 2026-09-13, revised the same day after the project owner's choices: no server to pay for or
> host. It details the "Seca-to-Seca E2EE layer" planned by the suite's spec
> (`2026-09-09-seca-suite-design.md`). Ordinary SMS stay unchanged: Seca Link only takes over between two
> phones that both have Seca.

## Decisions

| Topic | Decision |
|---|---|
| Transport | The Nostr network: several public, free relays run by volunteers. Nothing to pay for or host. Delivery even when the recipient is offline. |
| Fallback | Encrypted data SMS when there is no Internet: never a silent fallback to plain SMS. |
| Discovery | A discreet handshake: a data SMS offers a key at the first exchange; no server knows the address book. |
| Protocol | The Signal Protocol through libsignal (quantum-resistant PQXDH, then Double Ratchet) inside Nostr "gift wrap" envelopes (NIP-59). No home-made cryptography. |
| Receipts and typing | Read receipts and the "typing" indicator on by default, can be turned off. |
| Network reach | Only Seca Messages gets the `INTERNET` permission. Seca Contacts and Seca Phone stay without network access. |
| License | libsignal imposes the AGPL-3.0 on the Seca Messages binary: accepted, the project stays free, publishable and forkable. |

## Why not direct peer-to-peer

Two phones cannot reach each other directly over the Internet: carriers put them behind shared
addresses (CGNAT), and Android suspends apps in the background. An intermediary is needed to find each
other and to keep a message while the other side is offline. Nostr relays play that role without Seca
having to run one: they only receive ciphertext, and Seca uses several so as to depend on none.

## Who sees what

| Party | Sees | Does not see |
|---|---|---|
| Mobile carrier | That a data SMS was exchanged between two numbers, at the handshake or as fallback | The content |
| Nostr relay | Encrypted events, the recipient's Nostr public key, size and time, the connecting IP address | The sender (hidden by the gift wrap), numbers, names, content |
| Google | Nothing: GrapheneOS, without Firebase or Play Services | — |
| Thief of the locked phone | Nothing: GrapheneOS file encryption, keys bound to the Titan M2 | — |

The IP address the relays see is the main metadata leak. A "Go through Tor" option (with Orbot) is
planned to hide it.

## Architecture

- **`:core:link`** (Android library): identities and keys, libsignal sessions, Nostr client (WebSocket, NIP-01), gift wrap envelopes, handshake, send queue, SMS fallback.
- **Seca Messages**:
  - local storage of Link messages;
  - the service connected to the relays;
  - interface: "End-to-end encrypted" badge, verification, receipts, typing.

## Identities

- **Nostr key** (secp256k1, BIP-340 Schnorr signatures): the receiving address on the relays.
- **libsignal identity**: encryption and authentication of the exchanges.
- Both are created at install and kept in the app's private storage, encrypted by an Android Keystore key backed by the Titan M2. There is no account, no number, no email.
- The libsignal pre-key bundle (signed pre-key, one-time pre-keys, Kyber pre-key) is published as a replaceable event on the user's receiving relays.

## Discreet handshake

1. **Channel**: a binary data SMS on a dedicated port (`SmsManager.sendDataMessage`). A phone without Seca silently ignores it: no stray text appears for a contact without Seca.
2. **Content**: version, Nostr public key, fingerprint of the libsignal identity, receiving relays (referenced by index in the default list, or by short domain name). Fragmented if needed, 140 bytes per segment.
3. **Trigger**: the first time an SMS conversation exists with a number, sending or receiving. A single invitation, sent again at most every 30 days without an answer. Can be turned off.
4. **Answer**: the recipient's Seca fetches the pre-key bundle from the named relays, opens the session and sends back its own invitation. The conversation becomes "End-to-end encrypted".
5. **Accepted limit**: the key is tied to the number by the SMS channel. An attacker able to intercept SMS (SIM swap, SS7) could sit in between. Two countermeasures:
   - in-person verification by QR code (safety number), which shows "Verified";
   - a visible alert as soon as a contact's key changes.

## Messages

- **Sending**: the content is encrypted by libsignal, sealed in a rumor then a seal (NIP-59), and wrapped in a gift wrap signed by a throwaway key. It is published on three of the recipient's receiving relays.
- **Receiving**: a foreground service keeps a light WebSocket connection to the receiving relays, with a discreet, silent notification. When the connection comes back, the app picks up everything that arrived since last time.
- **Offline**: relays keep the events. The Double Ratchet's forward secrecy protects old messages even if a key leaks later.
- **Receipts and typing**: encrypted events too, gift wrapped. The typing indicator is an ephemeral event (kinds 20000-29999), never stored by relays.
- **Fallback**: without Internet, the encrypted message leaves as fragmented data SMS. If nothing is possible, the app explicitly offers "Send as unencrypted SMS".
- **Local storage**: a Room database of Seca Messages' own, separate from Android's SMS, protected by the system's file encryption and by the app lock.

## Relays

- A default list of public, free relays that accept encrypted messages, editable in settings.
- Publishing to several relays, so that a relay that disappears or refuses an event loses nothing.
- No relay run by the project: nothing to pay, nothing to host.

## "Seca's RCS", step by step

| Phase | Content |
|---|---|
| 1 | `:core:link`: identities, Nostr client, publishing the pre-key bundle, relay list in settings |
| 2 | Handshake by data SMS, session opening, "Encrypted" badge, QR code verification, key change alert |
| 3 | Encrypted text messages, delivery and read receipts, typing indicator, connection service, encrypted SMS fallback |
| 4 | Encrypted photos and videos (random AES key per item, free Blossom storage), reactions, quoted replies, disappearing messages, Tor option |
| 5 | Group conversations |

## Dependencies and F-Droid

- **libsignal** (AGPL-3.0), built from the Rust sources for F-Droid; about 10 MB of native code.
  - Taken from Signal's Maven repository (Maven Central stops at 0.86.5), limited to the `org.signal` group only.
  - Only the `arm64-v8a` ABI is shipped, without the `libsignal_jni_testing` library. The NDK strips debug symbols at packaging.
  - It requires Java library desugaring (`desugar_jdk_libs`, Apache-2.0).
- **secp256k1-kmp** by ACINQ (Apache-2.0) for Nostr's Schnorr signatures.
- **OkHttp** (Apache-2.0) for the WebSockets to the relays.
- **Room** (Apache-2.0), from phase 3.
- No Google Play dependency: the existing Gradle guard still applies.

## Phase 1 done

- Seca Link is off by default. Turning it on creates the keys and publishes their public part. Nothing is created or sent before.
- The pre-key bundle is a NIP-78 event (kind 30078, tag `d` = `seca-link/prekeys`): each relay replaces it instead of piling it up.
  - It holds the identity key, a signed pre-key and a last-resort Kyber pre-key, with no one-time pre-key.
  - It is published again every five hours, and read back from each relay to see which ones keep it.
- The identity is sealed with AES-256-GCM by an Android Keystore key, StrongBox when the chip exists. It is kept in `noBackupFilesDir`.
- Relays: encrypted connections (`wss`) only, editable list, each relay's answer shown.

## Phases 2 and 3 done

- **Invitation**: a data SMS on port 19734, 130 bytes at most. It carries the Nostr key, eight bytes of the identity's fingerprint and up to three relays. It leaves on its own when a conversation opens or a message is exchanged, at most once a day per contact: two phones that both have Seca Link connect without anyone asking, and the conversation says so once the session is open.
- **A contact who leaves**: their keys stop being published. Every six hours the listening service looks for the keys of each connected contact; keys untouched for a day, or gone from every relay that answered, end the encrypted conversation — a notification, a notice in the thread, and messages back to SMS. A relay that answers nothing concludes nothing, and a contact whose keys come back is connected again. Opening a conversation looks again at once, at most once an hour.
- **Keys on the relays**: published again every five hours by the listening service, and read back from each relay right after. A relay that takes the keys and keeps nothing is not named in invitations, and a contact's keys are looked for on the relays they named, on this phone's own and on the default ones.
- **Session**:
  - the pre-key bundle is only accepted when signed by the announced Nostr key and carrying the identity whose fingerprint came by SMS;
  - trust on first use, an alert when a contact's key changes;
  - nothing is sent to a verified contact whose key changed until the owner has verified again.
- **Safety number**: 60 digits and a QR code, which the other phone scans with its camera.
- **Envelope**:
  - a Nostr event of kind 1059, signed by a throwaway key; kind 21059 for "typing", which relays do not keep;
  - a timestamp moved back by up to 15 minutes;
  - the inside is sealed with AES-256-GCM, with an HKDF-SHA256 key derived from a secp256k1 ECDH between the throwaway key and the recipient. NIP-44 is not used: only Seca opens these envelopes, and the message is already encrypted by libsignal;
  - the sender signs, inside, the recipient and the message.
- **Content**: text, delivery receipt, read receipt, typing. Each content is padded to a multiple of 128 bytes.
- **Storage**: a private SQLite database of Seca Messages, separate from Android's SMS. Room is not used, to avoid adding a code generator.
- **Receiving**: a `remoteMessaging` foreground service, one connection per relay, reconnection with growing back-off, NIP-42 authentication when a relay asks for it.
- **Fallback**: a message that does not leave offers "Send as unencrypted SMS". The encrypted data SMS fallback remains to be done: a first PQXDH message, with its Kyber key, would take about fifteen SMS.
- **Tests**: format, envelope, and a complete libsignal session in both directions, on the bundle as Seca publishes it. They run on JDK 25, libsignal being compiled for Java 21.

## Additions to the suite, in the order decided

1. **Anti-telemarketing** (Seca Phone): blocking the number ranges reserved for telemarketing in France, unknown callers silenced, on the phone through `CallScreeningService`.
2. **Lock and private screen** (all three apps): fingerprint or phone code on opening, screenshots blocked, preview hidden in recent apps.
3. **Verification codes** (Seca Messages): copied in one gesture from the notification or the bubble, marked sensitive in the clipboard.
4. **Encrypted backup**: a password-protected file with contacts, profiles and SMS.
5. **SMS comfort**: pinned and archived conversations, search in message text, scheduled SMS.

### Pending

- Speed dial, duplicate merging, multiple selection and name order.
- MMS in Seca Messages.
- Sharing "My card" by QR code, without network.
- Notes after a call.
- English translation, for publishing on GitHub and F-Droid.
- A conversation tinted by the contact's profile, an expressive send animation.
