# Seca Link — messages chiffrés de bout en bout entre utilisateurs Seca

> Conception du 2026-09-13, révisée le même jour après les choix du propriétaire du projet : aucun serveur
> à payer ni à héberger. Détaille la « surcouche E2EE Seca-à-Seca » prévue par la spec de la suite
> (`2026-09-09-seca-suite-design.md`). Les SMS classiques restent inchangés : Seca Link ne prend le
> relais qu'entre deux téléphones qui ont tous deux Seca.

## Décisions

| Sujet | Décision |
|---|---|
| Transport | Réseau Nostr : plusieurs relais publics et gratuits, tenus par des bénévoles. Rien à payer ni à héberger. Livraison même quand le destinataire est hors ligne. |
| Secours | SMS de données chiffrés quand il n'y a pas d'Internet : jamais de retour silencieux au SMS en clair. |
| Découverte | Poignée de main discrète : un SMS de données propose une clé au premier échange ; aucun serveur ne connaît le carnet d'adresses. |
| Protocole | Signal Protocol via libsignal (PQXDH résistant au quantique, puis Double Ratchet) à l'intérieur d'enveloppes Nostr « gift wrap » (NIP-59). Aucune cryptographie maison. |
| Accusés et écriture | Accusés de lecture et indicateur « en train d'écrire » actifs par défaut, désactivables. |
| Portée réseau | Seca Messages seul reçoit la permission `INTERNET`. Seca Contacts et Seca Téléphone restent sans accès réseau. |
| Licence | libsignal impose l'AGPL-3.0 au binaire de Seca Messages : accepté, le projet reste libre, publiable et forkable. |

## Pourquoi pas du pair-à-pair direct

Deux téléphones ne peuvent pas se joindre directement sur Internet : les opérateurs les placent derrière un
partage d'adresse (CGNAT), et Android suspend les apps en arrière-plan. Il faut un intermédiaire pour se
trouver et pour garder un message quand l'autre est hors ligne. Les relais Nostr jouent ce rôle sans que
Seca ait à en exploiter un : ils ne reçoivent que du chiffré, et Seca en utilise plusieurs pour ne dépendre
d'aucun.

## Qui voit quoi

| Acteur | Voit | Ne voit pas |
|---|---|---|
| Opérateur mobile | Qu'un SMS de données a été échangé entre deux numéros, à la poignée de main ou en secours | Le contenu |
| Relais Nostr | Des événements chiffrés, la clé publique Nostr du destinataire, la taille et l'heure, l'adresse IP de connexion | L'expéditeur (masqué par le gift wrap), les numéros, les noms, le contenu |
| Google | Rien : GrapheneOS, sans Firebase ni Play Services | — |
| Voleur du téléphone verrouillé | Rien : chiffrement de fichiers de GrapheneOS, clés liées au Titan M2 | — |

L'adresse IP visible des relais est la principale fuite de métadonnées. Une option « Passer par Tor » (via
Orbot) est prévue pour la masquer.

## Architecture

- **`:core:link`** (bibliothèque Android) : identités et clés, sessions libsignal, client Nostr (WebSocket, NIP-01), enveloppes gift wrap, poignée de main, file d'envoi, secours par SMS.
- **Seca Messages** :
  - stockage local des messages Link ;
  - service de connexion aux relais ;
  - interface : badge « Chiffré de bout en bout », vérification, accusés, écriture.

## Identités

- **Clé Nostr** (secp256k1, signatures Schnorr BIP-340) : adresse de réception sur les relais.
- **Identité libsignal** : chiffrement et authentification des échanges.
- Les deux sont créées à l'installation et gardées dans le stockage privé de l'app, chiffrées par une clé Android Keystore adossée au Titan M2. Il n'y a ni compte, ni numéro, ni e-mail.
- Le paquet de pré-clés libsignal (pré-clé signée, pré-clés à usage unique, pré-clé Kyber) est publié comme événement remplaçable sur les relais de réception de l'utilisateur.

## Poignée de main discrète

1. **Canal** : un SMS de données binaire sur un port dédié (`SmsManager.sendDataMessage`). Un téléphone sans Seca l'ignore silencieusement : aucun texte parasite n'apparaît chez un contact sans Seca.
2. **Contenu** : version, clé publique Nostr, empreinte de l'identité libsignal, relais de réception (référencés par index dans la liste par défaut, ou par nom de domaine court). Il est fragmenté si besoin, 140 octets par segment.
3. **Déclenchement** : la première fois qu'une conversation SMS existe avec un numéro, en envoi ou en réception. Une seule invitation, relancée au plus tous les 30 jours sans réponse. Désactivable.
4. **Réponse** : le Seca du destinataire récupère le paquet de pré-clés sur les relais indiqués, ouvre la session et renvoie sa propre invitation. La conversation passe en « Chiffré de bout en bout ».
5. **Limite assumée** : la clé est liée au numéro par le canal SMS. Un attaquant capable d'intercepter les SMS (échange de SIM, SS7) pourrait s'interposer. Deux parades :
   - vérification en personne par QR code (numéro de sécurité), qui affiche « Vérifié » ;
   - alerte visible dès que la clé d'un contact change.

## Messages

- **Envoi** : le contenu est chiffré par libsignal, scellé dans un rumor puis un seal (NIP-59), et enveloppé dans un gift wrap signé par une clé jetable. Il est publié sur trois relais de réception du destinataire.
- **Réception** : un service au premier plan garde une connexion WebSocket légère aux relais de réception, avec une notification discrète et silencieuse. Au retour de connexion, l'app relève tout ce qui est arrivé depuis la dernière fois.
- **Hors ligne** : les relais gardent les événements. La confidentialité persistante du Double Ratchet protège les anciens messages même si une clé fuit plus tard.
- **Accusés et écriture** : des événements chiffrés, eux aussi gift wrap. L'indicateur d'écriture est un événement éphémère (types 20000-29999), jamais stocké par les relais.
- **Secours** : sans Internet, le message chiffré part en SMS de données fragmentés. Si rien n'est possible, l'app propose explicitement « Envoyer en SMS non chiffré ».
- **Stockage local** : une base Room propre à Seca Messages, séparée des SMS d'Android, protégée par le chiffrement de fichiers du système et par le verrou de l'app.

## Relais

- Une liste par défaut de relais publics gratuits qui acceptent les messages chiffrés, modifiable dans les paramètres.
- Publication vers plusieurs relais, pour qu'un relais qui disparaît ou refuse un événement ne fasse rien perdre.
- Aucun relais exploité par le projet : rien à payer, rien à héberger.

## Le « RCS de Seca », par étapes

| Phase | Contenu |
|---|---|
| 1 | `:core:link` : identités, client Nostr, publication du paquet de pré-clés, liste de relais dans les paramètres |
| 2 | Poignée de main par SMS de données, ouverture de session, badge « Chiffré », vérification par QR code, alerte de changement de clé |
| 3 | Messages texte chiffrés, accusés de remise et de lecture, indicateur d'écriture, service de connexion, secours par SMS chiffré |
| 4 | Photos et vidéos chiffrées (clé AES aléatoire par pièce, stockage Blossom gratuit), réactions, réponses citées, messages éphémères, option Tor |
| 5 | Conversations de groupe |

## Dépendances et F-Droid

- **libsignal** (AGPL-3.0), compilée depuis les sources Rust pour F-Droid ; environ 10 Mo de code natif.
  - Prise sur le dépôt Maven de Signal (Maven Central s'arrête à la 0.86.5), limité au seul groupe `org.signal`.
  - Seul l'ABI `arm64-v8a` est embarqué, sans la bibliothèque `libsignal_jni_testing`. Le NDK retire les symboles de débogage à l'empaquetage.
  - Elle exige le desugaring de la bibliothèque Java (`desugar_jdk_libs`, Apache-2.0).
- **secp256k1-kmp** d'ACINQ (Apache-2.0) pour les signatures Schnorr de Nostr.
- **OkHttp** (Apache-2.0) pour les WebSocket vers les relais.
- **Room** (Apache-2.0), à partir de la phase 3.
- Aucune dépendance Google Play : le garde-fou Gradle existant s'applique toujours.

## Phase 1 réalisée

- Seca Link est désactivé par défaut. L'activer crée les clés et publie la partie publique. Rien n'est créé ni envoyé avant.
- Le paquet de pré-clés est un événement NIP-78 (type 30078, étiquette `d` = `seca-link/prekeys`) : chaque relais le remplace au lieu de l'empiler.
  - Il contient la clé d'identité, une pré-clé signée et une pré-clé Kyber de dernier recours, sans pré-clé à usage unique.
  - Il est republié chaque semaine.
- L'identité est scellée en AES-256-GCM par une clé Android Keystore, StrongBox quand la puce existe. Elle est gardée dans `noBackupFilesDir`.
- Relais : connexions chiffrées (`wss`) uniquement, liste modifiable, réponse de chaque relais affichée.

## Ajouts de la suite, par priorité décidée

1. **Anti-démarchage** (Seca Téléphone) : blocage des préfixes réservés au démarchage en France, inconnus en silencieux, sur le téléphone via `CallScreeningService`.
2. **Verrou et écran privé** (les trois apps) : empreinte ou code du téléphone à l'ouverture, captures bloquées, aperçu masqué dans les apps récentes.
3. **Codes de vérification** (Seca Messages) : copie en un geste depuis la notification ou la bulle, marqués sensibles dans le presse-papiers.
4. **Sauvegarde chiffrée** : un fichier protégé par mot de passe avec contacts, profils et SMS.
5. **Confort SMS** : conversations épinglées et archivées, recherche dans le texte des messages, SMS programmés.

### En attente

- Numérotation rapide, fusion des doublons, sélection multiple et ordre du nom.
- MMS dans Seca Messages.
- Partage de « Ma fiche » par QR code, sans réseau.
- Notes après un appel.
- Traduction anglaise, pour la publication sur GitHub et F-Droid.
- Teinte d'une conversation selon le profil du contact, animation d'envoi expressive.
