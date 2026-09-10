# Seca

Trois applications de communication pour Android, conçues pour GrapheneOS :
**Seca Contacts**, **Seca Phone** et **Seca Messages**. Un design Material 3
Expressive commun, orienté vie privée, sans aucune dépendance Google.

## État

Fondation et design system en place, avec une application catalogue qui les
présente. Seca Contacts est la prochaine étape. Voir `docs/superpowers/specs/`
pour la conception et `docs/superpowers/plans/` pour les plans d'implémentation.

## Trois applications, une seule famille

Les trois applications seront des APK distincts. Chacune affichera en bas une
barre menant aux deux autres, qu'elle lancera par intent ; l'application
catalogue présente déjà cette barre. Garder trois APK séparés préserve la
séparation des permissions : Seca Contacts n'aura jamais besoin d'`INTERNET`,
Seca Phone détiendra `ROLE_DIALER`, Seca Messages `ROLE_SMS`.

Le thème clair ou sombre suit celui du système, sans réglage dans l'app. La
seule préférence visuelle est la palette — Océan, Forêt, Crépuscule ou Ardoise.
Chaque application décale la teinte de la palette choisie, si bien que les trois
restent reconnaissables quelle que soit la palette.

Material You n'est pas proposé : la couleur dynamique tire toutes les teintes du
fond d'écran et rendait les trois applications identiques.

## Ce que Seca ne fait pas

- **Pas de RCS.** Google réserve son API RCS à une allowlist fermée ;
  aucune application tierce ne peut l'implémenter. Seca Messages proposera
  du SMS/MMS et une couche chiffrée de bout en bout entre utilisateurs Seca.
- **Pas d'implémentation des appels Wi-Fi.** Le VoWiFi relève de la pile IMS
  du système et fonctionne déjà indépendamment du composeur installé.
  Seca Phone en affichera l'état.

## Construire

Prérequis : JDK 17 et le SDK Android (plateforme `android-37.0`, build-tools 36.0.0).

```bash
./gradlew :apps:catalog:assembleDebug
./gradlew test
./gradlew :apps:catalog:check
```

## Vie privée

Aucune analytique, aucun rapport de plantage, aucune dépendance Google. Chaque
application vérifie à la construction qu'aucun artefact `com.google.android.gms`,
`com.google.firebase` ou `com.google.android.play` n'atteint son classpath
d'exécution : la tâche `verifyReleaseNoProprietaryDependencies`, branchée sur
`check`, fait échouer le build dans le cas contraire.

Seca Contacts ne demandera **pas** la permission `INTERNET` : l'application
sera structurellement incapable d'exfiltrer un répertoire.

Les trois icônes de la barre sont dessinées dans le dépôt, en `ImageVector`.
Les bibliothèques `material-icons-core` et `material-icons-extended` de Google
sont figées en 1.7.8 alors que le projet utilise Compose 1.12.0 : trois icônes
ne justifiaient pas d'embarquer une dépendance abandonnée.

## Licence

GPL-3.0-or-later.
