# Seca

Trois applications de communication pour Android, pensées pour GrapheneOS et les
téléphones sans Google : **Seca Contacts**, **Seca Téléphone** et **Seca Messages**.
Un même design Material 3 Expressive, la vie privée d'abord, aucune dépendance
Google.

| Seca Contacts | Seca Téléphone | Seca Messages |
|---|---|---|
| ![Seca Contacts](apps/contacts/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png) | ![Seca Téléphone](apps/phone/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png) | ![Seca Messages](apps/messages/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png) |

## Les applications

**Seca Contacts** — le répertoire, dans le carnet d'Android.
Profils colorés (Famille, Travail…) partagés avec les deux autres applications,
favoris, recherche, fusion des doublons, fiche personnelle partagée par QR code,
import et export vCard, sauvegarde chiffrée. Sans permission Internet.

**Seca Téléphone** — les appels.
Écran d'appel avec le profil de l'appelant, recherche T9, blocage du démarchage
(numéros réservés en France), inconnus en silencieux, profils bloqués pour une
durée choisie, SIM mémorisée pour chaque contact, refus d'appel avec un message,
sorties audio et Bluetooth nommées. Sans permission Internet.

**Seca Messages** — les SMS, et Seca Link.
Conversations aux couleurs du profil du contact, messages programmés, codes de
vérification effacés après un délai, SMS publicitaires rangés à part,
sauvegarde chiffrée. Et, entre téléphones Seca, **Seca Link**.

Depuis n'importe laquelle des trois, une **sauvegarde commune** réunit contacts
et profils, historique et filtrage des appels, SMS, conversations et réglages
dans un seul fichier chiffré, pour changer de téléphone.

## Seca Link

Une messagerie chiffrée de bout en bout entre téléphones Seca, sans compte et
sans serveur Seca.

- Le chiffrement est celui de Signal : [libsignal](https://github.com/signalapp/libsignal),
  avec l'échange de clés post-quantique PQXDH.
- Les messages chiffrés transitent par des relais [Nostr](https://nostr.com) publics.
  Chaque enveloppe est signée par une clé jetable : un relais voit qui reçoit, quand
  et à peu près quelle taille, jamais qui écrit ni ce qui est écrit.
- Deux téléphones se connectent en échangeant une invitation par SMS, ou en
  scannant le code de l'autre en face à face.
- Accusés de lecture, indicateur d'écriture, réactions, réponses citées, photos et
  messages vocaux chiffrés, messages éphémères, numéro de sécurité à comparer.
- Option Tor avec [Orbot](https://orbot.app) : les relais ne voient plus l'adresse
  du téléphone.
- Désactivé par défaut. Sans Seca Link, Seca Messages n'utilise pas Internet.

## Vie privée

- Aucune analytique, aucun rapport de plantage, aucun pisteur.
- Seca Contacts et Seca Téléphone n'ont pas la permission `INTERNET` : ils ne
  peuvent rien envoyer nulle part.
- Chaque application vérifie à la construction qu'aucun artefact
  `com.google.android.gms`, `com.google.firebase` ou `com.google.android.play`
  n'atteint son exécution : la tâche `verifyReleaseNoProprietaryDependencies`,
  branchée sur `check`, fait échouer le build sinon.
- Les profils de Seca Contacts ne sont lisibles que par les applications signées
  avec la même clé.

## Ce que Seca ne fait pas

- **Pas de RCS.** Google réserve son API RCS à une liste fermée d'applications.
- **Pas d'appels Wi-Fi à proprement parler.** Le VoWiFi relève de la pile IMS du
  système et fonctionne quel que soit le composeur ; Seca Téléphone en affiche l'état.

## Installer

Seca est **en bêta**. En attendant F-Droid, les versions sont publiées dans les
[Releases GitHub](https://github.com/arditore/Seca/releases) : installez les trois
APK, puis choisissez Seca Téléphone comme application Téléphone et Seca Messages
comme application SMS. Android 12 ou plus récent.

Les APK sont signés avec ce certificat (empreinte SHA-256) :

```
2F:DE:A7:B4:6B:EF:FE:16:45:9B:B4:DC:DD:34:DB:5E:0C:B9:12:27:4A:5E:B6:28:E4:B0:D8:A5:96:21:17:90
```

## Construire

Prérequis : JDK 17 et le SDK Android (plateforme `android-37`, build-tools 36.0.0).
Les tests de Seca Link demandent aussi un JDK 25, que Gradle trouve lui-même.

```bash
./gradlew :apps:contacts:assembleDebug :apps:phone:assembleDebug :apps:messages:assembleDebug
./gradlew test
./gradlew check
```

Pour dessiner les écrans principaux avec des données fictives (les captures de
ce README et des fiches F-Droid) :

```bash
SECA_SCREENSHOTS=build/captures ./gradlew testDebugUnitTest --tests "*ScreenshotsTest"
```

## Licence

[GPL-3.0-or-later](LICENSE).
