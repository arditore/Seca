# Seca Link — messages chiffrés de bout en bout entre utilisateurs Seca

> Conception du 2026-09-13. Détaille la « surcouche E2EE Seca-à-Seca » prévue par la spec de la suite
> (`2026-09-09-seca-suite-design.md`, phase Seca Messages). Les SMS classiques restent inchangés :
> Seca Link ne remplace le SMS qu'entre deux téléphones qui ont tous deux Seca.

## Décisions

| Sujet | Décision |
|---|---|
| Transport | Internet, via un relais qui ne transporte que du chiffré. Instantané, pièces jointes en pleine qualité, accusés, indicateur d'écriture. |
| Hébergement | Relais auto-hébergé : l'utilisateur déploie le sien (image Docker) et chaque installation peut indiquer un autre relais. |
| Découverte | Poignée de main discrète : un SMS de données propose une clé au premier échange ; aucun serveur ne connaît le carnet d'adresses. |
| Protocole | Signal Protocol via libsignal : PQXDH (échange de clés résistant au quantique) puis Double Ratchet. Aucune cryptographie maison. |
| Portée | Seca Messages seul reçoit la permission `INTERNET`. Seca Contacts et Seca Téléphone restent sans accès réseau. |

## Qui voit quoi

| Acteur | Voit | Ne voit pas |
|---|---|---|
| Opérateur mobile | Qu'un SMS de données a été échangé entre deux numéros, une fois, à la poignée de main | Le contenu, et tout ce qui passe ensuite par le relais |
| Relais | Des identifiants de compte aléatoires, la taille et l'heure des enveloppes, l'adresse IP de connexion | Les numéros de téléphone, les noms, le contenu, qui parle à qui en clair |
| Distributeur UnifiedPush | Qu'un réveil a été envoyé à un appareil | Le contenu, l'expéditeur |
| Google | Rien : GrapheneOS, pas de Firebase ni de Play Services | — |
| Voleur du téléphone verrouillé | Rien (chiffrement de fichiers de GrapheneOS, clés liées au Titan M2) | — |

## Architecture

- **`:core:link`** (bibliothèque Android) : identité et clés, sessions libsignal, client du relais, poignée de main, file d'envoi.
- **Seca Messages** : stockage local des messages Link, interface (badge « Chiffré de bout en bout », vérification), réveils UnifiedPush.
- **`server/relay`** : service Go autonome, stockage SQLite, livré en image Docker.

## Identité et compte

- Une paire de clés d'identité libsignal est créée à l'installation, gardée dans le stockage privé de l'app et chiffrée par une clé Android Keystore adossée au Titan M2.
- Le compte sur le relais est anonyme : un identifiant aléatoire de 128 bits. L'authentification se fait par signature Ed25519 d'un défi ; il n'y a ni numéro, ni e-mail, ni mot de passe.
- Pré-clés publiées sur le relais : une pré-clé signée, cent pré-clés à usage unique, une pré-clé Kyber pour PQXDH. Elles sont renouvelées quand le stock baisse.

## Poignée de main discrète

1. **Canal** : un SMS de données binaire sur un port dédié (`SmsManager.sendDataMessage`). Un téléphone sans Seca l'ignore silencieusement : aucun texte parasite n'apparaît chez un contact qui n'a pas Seca.
2. **Contenu** : version du protocole, adresse du relais, identifiant de compte, empreinte de la clé d'identité. Il est fragmenté si besoin, 140 octets par segment.
3. **Déclenchement** : la première fois qu'une conversation SMS existe avec un numéro, en envoi ou en réception. Une seule invitation, relancée au plus tous les 30 jours sans réponse. Désactivable dans les paramètres.
4. **Réponse** : le Seca du destinataire récupère le paquet de pré-clés sur le relais de l'expéditeur, ouvre la session et renvoie sa propre invitation. La conversation affiche alors « Chiffré de bout en bout ».
5. **Limite assumée** : la poignée de main lie une clé à un numéro par le canal SMS. Un attaquant capable d'intercepter les SMS (échange de SIM, SS7) pourrait s'interposer. Deux parades :
   - vérification en personne par QR code (numéro de sécurité), qui affiche « Vérifié » ;
   - alerte visible dès que la clé d'un contact change.

## Messages

- **Enveloppe** : identifiant de compte du destinataire, contenu chiffré par libsignal, horodatage. Le relais la garde jusqu'à sa récupération (30 jours au plus) et la supprime dès l'accusé de réception.
- **Réveil** : le relais envoie un réveil **vide** au point UnifiedPush du destinataire (distributeur ntfy ou autre), puis l'app vient chercher ses enveloppes. Sans distributeur, l'app relève périodiquement.
- **Plusieurs relais** : chacun a le sien. Un message part vers le relais du destinataire, appris pendant la poignée de main.
- **Repli** : jamais de retour silencieux au SMS. Si l'envoi chiffré échoue, ou si le contact n'a pas Seca, l'app propose explicitement « Envoyer en SMS non chiffré ».
- **Stockage local** : une base Room propre à Seca Messages, séparée des SMS d'Android, protégée par le chiffrement de fichiers du système, avec une option de verrouillage biométrique de l'app.

## Le « RCS de Seca », par étapes

| Phase | Contenu |
|---|---|
| 1 | Relais, `:core:link` (identité, compte, pré-clés), réglage de l'adresse du relais |
| 2 | Poignée de main, ouverture de session, badge « Chiffré », vérification par QR code, alerte de changement de clé |
| 3 | Messages texte chiffrés, accusé de remise, réveils UnifiedPush, repli explicite vers le SMS |
| 4 | Accusés de lecture et indicateur d'écriture, photos et vidéos chiffrées (clé AES aléatoire par pièce, blob sur le relais), réactions, réponses citées, messages éphémères |
| 5 | Conversations de groupe |

## Relais

- API HTTPS : comptes, pré-clés, dépôt et relève d'enveloppes, accusés, enregistrement UnifiedPush, pièces jointes.
- Journalisation minimale : aucune adresse IP conservée, aucune métadonnée au-delà de la file d'attente.
- Limites de débit et quota de stockage par compte.
- Livraison : image Docker et binaire unique, avec une documentation d'auto-hébergement derrière un proxy TLS (Caddy).

## Dépendances et F-Droid

- **libsignal** (AGPL-3.0), compilée depuis les sources Rust pour F-Droid ; environ 10 Mo de code natif.
- **Connecteur UnifiedPush** (Apache-2.0).
- **Room** (Apache-2.0).
- Aucune dépendance Google Play : le garde-fou Gradle existant s'applique toujours.
- **Licence** : lier libsignal impose l'AGPL-3.0 au binaire de Seca Messages. C'est compatible avec la GPL-3.0 déclarée pour la suite, mais c'est à confirmer par le propriétaire du projet.

## Questions ouvertes

1. Accusés de lecture et indicateur d'écriture : actifs par défaut, ou à activer ?
2. Nom de domaine et hébergement du premier relais.
3. Licence de Seca Messages (AGPL-3.0 induite par libsignal).

## Améliorations de la suite en attente

### Vie privée et sécurité

- Verrouillage biométrique de Seca Messages, en option pour Seca Contacts.
- Option « écran privé » : captures bloquées et aperçu masqué dans les apps récentes.
- Clavier sans apprentissage personnalisé dans le champ de message.
- Filtrage d'appels (`CallScreeningService`) : silence des numéros inconnus, blocage du démarchage par préfixes réservés (0162, 0163, 0270, 0271, 0377, 0378, 0424, 0425, 0568, 0569, 0948, 0949, à revérifier contre la décision de l'ARCEP).
- Codes de vérification reçus par SMS : copie en un geste, effacement automatique après usage en option.
- Notifications sans contenu pour certains contacts ou profils.

### Confort

- Numérotation rapide dans Seca Téléphone.
- SMS programmés, conversations épinglées et archivées, recherche dans le texte des messages.
- Fusion des doublons, sélection multiple et ordre du nom dans Seca Contacts.
- MMS (photos, groupes) dans Seca Messages.

### Esthétique

- Teinte d'une conversation selon le profil du contact.
- Animation d'envoi expressive et retour haptique.
