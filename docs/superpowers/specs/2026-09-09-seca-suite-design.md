# Seca — Suite de communication privée pour GrapheneOS

> Conception validée le 2026-09-09. Couvre la phase 1. Seca Phone et Seca Messages auront chacun leur propre spec.

## Contexte

Trois applications Android partageant un design Material unifié, orientées vie privée, dégooglisées, pour Pixel 9 sous GrapheneOS, publiées en open source sur F-Droid : **Seca Contacts**, **Seca Phone**, **Seca Messages**.

Trois vérifications faites en amont ont redéfini le périmètre par rapport à la demande initiale (qui incluait « support RCS » et « support appels wifi ») :

1. **Le RCS est inaccessible à une app tierce.** Google réserve son API RCS à une allowlist fermée. Le RCS Android est réparti entre l'OS, Google Messages et Play Services. GrapheneOS, avec les privilèges système et des salariés dédiés, a annoncé le 6 septembre 2026 viser un RCS natif avec E2EE via MLS — **sans calendrier**, et leur première version dépendrait encore de Play Services sandboxé pour l'activation. Un APK installé par l'utilisateur ne peut pas faire ce qu'un projet d'OS n'arrive pas encore à faire.
2. **Les appels Wi-Fi ne sont pas une fonctionnalité de dialer.** Le VoWiFi est de l'IMS (tunnel IPsec vers l'ePDG opérateur), géré par le modem et la stack système. Il fonctionne déjà sur Pixel 9/GrapheneOS quel que soit le dialer installé. Un dialer tiers peut *afficher* l'état VoWiFi/HD et gérer les `PhoneAccount` — il ne l'implémente pas.
3. **GrapheneOS réécrit actuellement son app Messages en Compose** avec RCS prévu. Seca se différencie par le design unifié entre les trois apps et la couche E2EE Seca-à-Seca, pas en courant après le RCS.

**Résultat attendu de la phase 1 :** un monorepo Gradle fonctionnel, un design system Material 3 Expressive partagé, et **Seca Contacts** complète et installable — fondation technique (les deux autres apps lisent le répertoire) et terrain de validation du langage visuel.

## Décisions actées

| Sujet | Décision |
|---|---|
| Messagerie moderne | SMS/MMS + surcouche E2EE Seca-à-Seca. Pas de RCS. Interface `Transport` pour brancher le RCS plus tard s'il s'ouvre. |
| Transport E2EE | Relais minimal (boîtes chiffrées, aveugle au contenu) + UnifiedPush/ntfy. Auto-hébergeable. |
| Audience | Open source, F-Droid → builds reproductibles, zéro dépendance propriétaire. |
| Design | Material 3 Expressive alpha assumé, confiné au module `:core:design`. |
| Structure | Monorepo Gradle multi-modules, 3 APKs, design partagé. |
| Sync contacts | Aucune. DAVx⁵ synchronise déjà dans `ContactsContract` ; on lit le fournisseur système. |
| Premier livrable | `:core:design` + Seca Contacts. |
| Thème clair/sombre | Suit le thème système, point. Aucune bascule dans l'app. |
| Couleur | Pas de Material You. L'utilisateur choisit une palette parmi quelques-unes ; chaque app en dérive sa propre variante distincte. |
| Navigation inter-apps | Barre en bas dans chacune des trois apps, liens croisés par intent vers les deux voisines. Les trois APKs restent séparés. |
| Icônes | Dessinées à la main en `ImageVector` dans `:core:design`. `material-icons-core` et `-extended` sont figés en 1.7.8 alors que Compose est en 1.12.0 : bibliothèques mortes, écartées. |

## Toolchain

L'environnement de départ a **JDK 25 uniquement**, sans SDK Android, sans Gradle, sans adb. AGP 9.4 (septembre 2026) exige :

- **JDK 17** — min *et* défaut dans la table de compatibilité AGP 9.4. Le JDK 25 présent ne convient pas ; installer Temurin 17 et le cibler via `JAVA_HOME` + toolchain Gradle, sans désinstaller le 25.
- **Gradle 9.7.1** (via wrapper, pas d'installation système ; AGP 9.4 exige au minimum 9.6.0)
- **Android SDK** : cmdline-tools, platform-tools (adb), Build Tools 36.0.0, platform API 37
- `compileSdk 37` (maximum supporté par AGP 9.4). Le `targetSdk` est à confirmer contre ce que GrapheneOS livre sur Pixel 9 — à vérifier sur l'appareil, ne pas deviner.

> **Correction du 2026-09-09, après vérification sur l'appareil et sur les artefacts.**
> Le Pixel 9 (`tokay`) tourne sous **Android 17, API 37**. La plateforme SDK correspondante s'appelle
> `platforms;android-37.0` — `platforms;android-37` n'existe pas, le SDK étant passé aux versions mineures.
> `android-37.0` est publiée, non préversion (`PreviewSdkInt=0`, `BetaVersion` vide).
>
> **`compileSdk = 37` est obligatoire.** Les métadonnées AAR de `material3:1.5.0-alpha27`, mais aussi de
> Compose `ui` et `foundation` **1.12.0 stables**, déclarent toutes `minCompileSdk=37`. Aucune version de
> Compose retenue par ce projet ne se construit en `compileSdk 36` : AGP échoue au contrôle
> `checkDebugAarMetadata`, sans indicateur de contournement.
>
> `targetSdk` reste à **36** — c'est le niveau contre lequel on teste, et il est indépendant de `compileSdk`.
> `buildToolsVersion` reste `36.0.0`, `minSdk` reste 34.
>
> *Une première correction de cette note fixait `compileSdk 36` pour éviter le risque supposé des versions
> mineures de SDK dans le DSL d'AGP 9.4. C'était une erreur : ce risque n'existe pas — `compileSdk = 37`
> résout `android-37.0` sans réglage supplémentaire — et le choix rendait le projet inconstructible.
> L'échec a été révélé par la tâche 4, qui s'est bloquée dessus.*

Les versions exactes de Kotlin, du BOM Compose et de `material3` alpha se pinnent à la première tâche en interrogeant les dépôts. `material3` doit être ≥ `1.5.0-alpha04` pour les APIs Expressive (`1.5.0-alpha24` en juillet 2026).

## Structure du dépôt

Les trois dossiers initiaux (`Seca Contacts`, `Seca Messages`, `Seca Phone`) contiennent des espaces, ce qui fragilise les chemins Gradle et les recettes de build F-Droid. Ils sont vides — les renommer ne perd rien :

```
Seca/
├── settings.gradle.kts
├── gradle/libs.versions.toml        # version catalog, toutes versions pinnées
├── build-logic/                     # convention plugins (config partagée)
├── core/
│   ├── design/                      # :core:design — thème M3E, tokens, composants
│   ├── model/                       # types domaine (SecaContact, PhoneNumber…)
│   └── contacts/                    # accès ContactsContract, partagé par les 3 apps
└── apps/
    ├── contacts/                    # Seca Contacts  (phase 1)
    ├── phone/                       # Seca Phone     (phase 2)
    └── messages/                    # Seca Messages  (phase 3)
```

`build-logic` porte la configuration commune (compileSdk, toolchain, options Compose, règles lint) pour que les trois apps ne divergent pas.

## `:core:design` — le cœur de l'unité visuelle

Le module qui répond à l'exigence « design uni, très joli ». Il contient **tout** ce qui est visuel ; aucune app ne définit sa propre couleur, forme ou typographie.

- Palette Seca en tokens M3 Expressive, thèmes clair/sombre suivant le système
- **Material You écarté, décision du 2026-09-10.** La couleur dynamique dérive tous les rôles du fond d'écran et ignore l'identité : les trois apps devenaient visuellement identiques, ce qui annulait la promesse « distinguables au coup d'œil ». Remplaée par une petite palette au choix de l'utilisateur, dont chaque app dérive une variante propre.
- Échelle typographique et jeu de formes expressifs
- Spécifications de motion — M3 Expressive met l'accent sur le mouvement, c'est là que se joue l'essentiel de la qualité perçue
- Composants partagés : avatar de contact, ligne de contact, barre de recherche, états vides, feuilles d'action
- Une **identité par app** dérivée du même socle : chaque app reçoit une teinte d'accent distincte tout en gardant tokens, formes, typo et motion identiques. Reconnaissables comme une famille, distinguables au coup d'œil.

Toutes les APIs expérimentales sont opt-in **ici uniquement**, jamais dans les modules d'app : quand l'alpha casse, un seul module est touché.

## Seca Contacts — périmètre phase 1

Lit et écrit le `ContactsContract` système. Doit fonctionner correctement sous **Contact Scopes** de GrapheneOS : l'OS peut ne présenter qu'un sous-ensemble de contacts, voire aucun. C'est un état normal, pas une erreur.

Fonctionnalités :
- Liste avec défilement rapide, recherche, tri et regroupement
- Fiche contact, création et édition
- Favoris, groupes/labels
- Import/export vCard (VCF)
- Actions rapides : appeler → Seca Phone, écrire → Seca Messages, avec repli propre sur les apps système tant que celles-ci n'existent pas (intents implicites, jamais de dépendance dure)

**Propriété de vie privée forte et vérifiable : aucune permission `INTERNET`.** L'app ne peut structurellement pas exfiltrer un répertoire. Argument concret pour F-Droid, à documenter dans le README et à faire respecter par un test qui échoue si la permission apparaît.

Aucune analytique, aucun crash reporting, aucune dépendance Google.

## Approche d'implémentation

TDD : tests d'abord pour la logique domaine et les repositories.

- **Tests unitaires / Robolectric** — logique, repositories, `ContactsContract` via provider factice. Tournent sans appareil.
- **Tests Compose UI** — composants du design system et écrans.
- **Test de garde** — assertion sur le manifeste fusionné : `INTERNET` absent.
- **Instrumentés** — nécessitent un émulateur ou le Pixel 9 en USB. Indisponible au moment de la conception (pas d'adb, pas d'appareil connecté) : à mettre en place avant la vérification, sinon les tests d'intégration ne seront pas exécutables.

## Vérification

1. `./gradlew :apps:contacts:assembleDebug` produit un APK
2. `./gradlew test` — unitaires et Robolectric au vert
3. Le test de garde `INTERNET` passe, et échoue bien si on ajoute la permission
4. Installation sur le Pixel 9 : liste, recherche, création, édition, favoris, import/export VCF
5. **Sous Contact Scopes** : vérifier explicitement le comportement avec accès partiel et avec accès vide
6. Bascule clair/sombre et couleur dynamique — contrôle visuel de la cohérence des tokens
7. `./gradlew lint` propre

## Hors périmètre phase 1

Un projet de cette taille ne tient pas dans une spec unique. Ordre prévu, chacun avec sa propre spec et son propre plan :

- **Phase 2** — Seca Phone : `ROLE_DIALER`, `InCallService`, journal d'appels, filtrage, indicateurs VoWiFi/HD, `PhoneAccount` multiples. Éventuellement un `ConnectionService` auto-géré pour de la VoIP chiffrée. C'est ici que `:core:contacts`, écrit en phase 1 pour les besoins de Seca Contacts, est généralisé en socle consommable par un second client.
- **Phase 3** — Seca Messages : SMS/MMS, puis couche E2EE + relais UnifiedPush. La plus lourde de loin.

## Risques identifiés

- **`material3` alpha** — ruptures d'API à chaque montée de version. Atténué par le confinement dans `:core:design`, pas éliminé.
- **MMS (phase 3)** — configuration APN, MMSC, encodage PDU. Notoirement pénible et mal documenté. À ne pas sous-estimer au moment de planifier cette phase.
- **Builds reproductibles F-Droid** — contrainte à intégrer dès la mise en place du build, pas à rattraper après coup.
- **GrapheneOS livre des apps concurrentes** — leur Messages en Compose arrive « en semaines ». La valeur de Seca doit rester le design unifié entre trois apps et la couche E2EE, pas la course au RCS.
- **Charge de travail** — trois applications système complètes représentent plusieurs mois de travail. La phase 1 est délibérément dimensionnée pour livrer quelque chose d'utilisable et de beau rapidement.

## Références

- [XDA — l'API RCS de Google est réservée à une allowlist](https://www.xda-developers.com/google-messages-rcs-api-third-party-apps/)
- [heise — GrapheneOS prévoit RCS et E2EE via MLS](https://www.heise.de/en/news/GrapheneOS-plans-its-own-messenger-solution-with-RCS-and-E2EE-11445618.html)
- [AlternativeTo — refonte des apps AOSP de GrapheneOS](https://alternativeto.net/news/2026/9/grapheneos-plans-to-overhaul-the-bundled-aosp-apps-including-messaging-with-rcs-support/)
- [AGP 9.4 — notes de version et compatibilité](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
- [Compose Material 3 — versions](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- [GrapheneOS — guide d'usage, Contact Scopes](https://grapheneos.org/usage)
