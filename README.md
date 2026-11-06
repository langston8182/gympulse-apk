# GymPulse — suivi de musculation pour Android

GymPulse est une application Android native pour **planifier**, **exécuter** et **suivre** ses entraînements de musculation.
L'application est **local-first** : Room alimente l'interface et l'application reste utilisable sans réseau ; les
modifications sont mises en file d'attente puis synchronisées avec l'API GymPulse (`gympulse-api`).

> Parcours principal : créer un exercice → composer une séance → lancer la séance → valider les séries (le repos se lance
> tout seul) → terminer et enregistrer → consulter sa progression.

---

## Sommaire

1. [Fonctionnalités](#fonctionnalités)
2. [Lancer le projet](#lancer-le-projet)
3. [Stack technique](#stack-technique)
4. [Architecture](#architecture)
5. [Moteur d'entraînement](#moteur-dentraînement)
6. [Données, Room et synchronisation API](#données-room-et-synchronisation-api)
7. [Statistiques et records](#statistiques-et-records)
8. [Géolocalisation](#géolocalisation)
9. [Design system](#design-system)
10. [Tests](#tests)
11. [Choix techniques](#choix-techniques)
12. [Limitations et suites possibles](#limitations-et-suites-possibles)

---

## Fonctionnalités

| Domaine | Ce qui est disponible |
|---|---|
| **Accueil** | Séance suggérée du jour (suite du programme, lieu, matériel) avec bouton « Démarrer l'entraînement », séance en cours, dernière séance, objectif hebdomadaire, durée et volume de la semaine (comparés à la semaine précédente), évolution du volume sur 7 j / 30 j / 3 mois / 6 mois / 1 an / tout, records récents, fréquence, progression des principaux exercices. |
| **Exercices** | Bibliothèque (35 exercices fournis), recherche insensible aux accents, filtres par groupe musculaire, matériel et type. Création, modification et suppression, avec deux types : répétitions (charge × reps) ou durée. Photo depuis la galerie (copiée et redimensionnée dans le stockage privé) ou illustration prédéfinie. Convention de charge « totale » ou « par haltère ». |
| **Séances** | Création, modification, duplication et suppression. Objectif (hypertrophie, force-hypertrophie, force, endurance, personnalisé), lieux compatibles, exercices ordonnés par glisser-déposer, objectifs par série (reps, charge, durée), repos par exercice **et** surchargeable par série, notes spécifiques. Valeurs par défaut selon l'objectif (hypertrophie 3 × 12, repos 1 min 30 ; force-hypertrophie 4 × 8, repos 1 min 30 ; force 4 × 5, repos 3 min) ; dernières charges proposées automatiquement. Favoris. Ordre des séances choisi par l'utilisateur (« Réorganiser mes séances », par glisser-déposer), synchronisé et repris dans le carrousel de l'accueil. |
| **Programmes** | Regroupent des séances réutilisées sans duplication, avec réordonnancement par glisser-déposer, lieux compatibles et « prochaine séance » du cycle. |
| **Mode entraînement** | Chronomètre global, exercice et série courants (n / total), objectif, charge de la dernière séance comparable, progression globale, exercices précédent et suivant. Steppers géants pour les reps et la charge, et gros bouton « Terminer la série ». Le repos démarre automatiquement (+15 s, +30 s, « Passer »), avec le prochain effort affiché. Chronomètre ou compte à rebours pour les exercices en durée. Pause et reprise, retour sur une série (correction par appui long), passer un exercice, ajouter une série, réorganiser les exercices restants, terminer, abandonner (avec confirmation). Chaque action est sauvegardée immédiatement. |
| **Fin de séance** | Récapitulatif : durée, exercices, séries, volume, comparaison avec la séance précédente, nouveaux records, répartition, notes, puis « Terminer et enregistrer ». |
| **Arrière-plan** | Service de premier plan avec notification en direct (chronomètre, compte à rebours du repos, actions *Passer* / *+30 s* / *Pause*). Retour haptique et son en fin de repos, même écran verrouillé. Reprise proposée d'une séance interrompue après fermeture de l'app ou redémarrage. |
| **Progression** | Vue d'ensemble (KPI et tendances, graphiques interactifs, répartition musculaire, records), historique groupé par mois, détail d'une séance, comparaison de deux séances, statistiques par exercice (1RM estimé, charge max, charge à reps égales, volume, reps, records par nombre de répétitions, comparaison de périodes). |
| **Lieux** | Maison, salle, travail, autres : position GPS, rayon, matériel disponible, séances et programmes associés. Détection ponctuelle au lancement et à l'ouverture des séances, choix manuel à tout moment. |
| **Profil et paramètres** | Thème sombre (par défaut), clair ou système, couleurs dynamiques Material You, kg ou lb, repos par défaut, son, vibrations, écran allumé pendant la séance, objectif hebdomadaire, gestion des données de démonstration. |
| **Première utilisation** | Onboarding en 4 écrans : prénom, unité, données de démonstration (activées par défaut). |

## Lancer le projet

Prérequis :

- **Android Studio** récent, compatible AGP 9.5. Le JDK fourni avec Android Studio (JBR 21) convient.
- **SDK Android 37**, téléchargé automatiquement par Gradle si les licences sont acceptées.
- Un appareil ou un émulateur **Android 8.0+ (API 26)**.

```bash
# Compiler l'APK de debug
./gradlew assembleDebug

# Installer sur l'appareil connecté
./gradlew installDebug

# Tests unitaires et UI JVM (Robolectric) ; les rapports sont dans app/build/reports/tests/
./gradlew testDebugUnitTest

# Tests instrumentés (appareil ou émulateur requis)
./gradlew connectedDebugAndroidTest
```

En ligne de commande, `JAVA_HOME` doit pointer vers un JDK 17 ou plus récent, par exemple
`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` sur macOS.

Au premier lancement, l'onboarding installe la bibliothèque d'exercices. Il installe aussi, si l'option reste activée,
des **données de démonstration** marquées d'un badge « Démo » :

- 8 séances (Push, Pull et Legs en Hypertrophie et en Force, Full body maison, Gainage express) ;
- 2 programmes ;
- 3 lieux ;
- environ 5 mois d'historique généré avec une surcharge progressive réaliste.

Ces données sont supprimables ou rechargeables dans *Paramètres → Données de démonstration*.

## Stack technique

Toutes les versions sont les dernières versions stables publiées au moment du développement, vérifiées sur Maven Central
et le dépôt Google. Elles sont centralisées dans [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

| Composant | Version |
|---|---|
| Gradle / Android Gradle Plugin | 9.8.2 / 9.5.0 (Kotlin intégré à AGP 9) |
| Kotlin / KSP | 2.4.21 / 2.3.13 |
| Compose BOM | 2026.09.00 (Compose 1.12, Material 3 1.4) |
| Navigation Compose | 2.10.2 (routes typées `@Serializable`) |
| Hilt / AndroidX Hilt | 2.60.1 / 1.4.0 |
| Room / DataStore | 2.8.5 / 1.3.0 |
| Lifecycle / Activity / Core | 2.11.0 / 1.13.0 / 1.19.1 |
| Coil 3 | 3.6.3 |
| Play Services Location | 21.4.0 |
| Coroutines / Serialization | 1.11.0 / 1.12.0 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 26 |

WorkManager n'est pas utilisé. Aucune tâche différée ou périodique n'est nécessaire dans cette version : le chronomètre
d'une séance active relève d'un service de premier plan, pas d'une tâche différée. Les graphiques sont dessinés en
Compose `Canvas`, sans bibliothèque tierce.

## Architecture

MVVM + Clean Architecture pragmatique, dans un seul module `app` organisé par couches :

```
com.gympulse.app
├── core/            Thème et design system, graphiques, navigation (routes), formatage, horloge, qualifiers
├── domain/          Cœur métier en Kotlin pur, sans dépendance Android
│   ├── model/         Exercise, WorkoutSession, SessionExercise, TrainingProgram, WorkoutExecution, TrainingLocation…
│   ├── repository/    Interfaces : Exercise, Session, Program, Workout, Progress, Location, Preferences, DemoData
│   ├── workout/       WorkoutEngine (machine à états pure) et ActiveWorkoutManager (orchestration et persistance)
│   ├── stats/         TrainingMath (volume, 1RM), RecordDetector, StatsCalculator
│   └── location/      LocationMatcher (haversine), SessionRecommender (compatibilité et séance suggérée)
├── data/            Implémentations
│   ├── local/         Room : entités, DAO, convertisseurs, mappers
│   ├── repository/    Local*Repository (Room + file d'envoi) et RemoteDemoDataRepository
│   ├── remote/        Retrofit : GymPulseApi, DTO, authentification
│   ├── sync/          SyncOutbox (file d'envoi), SyncEngine (push puis pull), SyncWorker (WorkManager), mappers
│   ├── preferences/   DataStore
│   ├── location/      Fused Location Provider
│   └── image/         Copie et redimensionnement des images de la galerie
├── di/              Modules Hilt (toutes les liaisons interface → implémentation)
├── service/         WorkoutService (premier plan), notifications, actions, alertes de fin de repos
└── presentation/    Écrans Compose, ViewModels et états d'UI, par fonctionnalité
```

Principes appliqués :

- **Flux unidirectionnel.** Chaque ViewModel expose un `StateFlow` d'état immuable (`stateIn(WhileSubscribed)`) et reçoit
  des intentions. Les écrans sont des fonctions de cet état.
- **Domaine indépendant.** Le moteur d'entraînement, les statistiques et la recommandation sont du Kotlin pur, donc
  entièrement testables sur la JVM.
- **Accès aux données par interfaces.** Toutes les liaisons se trouvent dans [`di/Modules.kt`](app/src/main/java/com/gympulse/app/di/Modules.kt).
- **Rien de bloquant sur le thread principal.** Room et DataStore sont suspendants, et les calculs de statistiques
  tournent sur `Dispatchers.Default`. L'historique est partagé (`shareIn`) entre les écrans pour n'être transformé
  qu'une fois.

## Moteur d'entraînement

C'est le cœur de l'application. Il se compose de trois pièces.

1. **`WorkoutEngine`**, une machine à états pure : chaque action est une fonction `(WorkoutExecution, now) → WorkoutExecution`.
   - Valider une série enregistre les valeurs, passe à la série ou à l'exercice suivant, puis démarre le repos configuré
     pour cette série.
   - Le repos réel est enregistré à la fin du repos (passé, terminé ou interrompu par la série suivante).
   - Mettre en pause fige le chronomètre global, le repos et le chronomètre de série. La reprise les décale de la durée
     de la pause.
   - Revenir sur une série terminée permet de la corriger sans relancer de repos.
   - Terminer marque les séries restantes comme passées.
2. **`ActiveWorkoutManager`** (singleton) :
   - possède la séance active et sérialise les actions avec un `Mutex` ;
   - publie le nouvel état puis **le persiste dans Room avant l'action suivante**, pour qu'une série validée ne soit
     jamais perdue, même si le processus meurt ;
   - restaure la séance au démarrage, ce qui déclenche la proposition de reprise ;
   - programme l'alerte de fin de repos.
3. **`WorkoutService`**, un service de premier plan démarré uniquement par une action explicite de l'utilisateur :
   - affiche la notification de suivi, dont le chronomètre est rendu par le système sans réveiller l'application ;
   - prend un **wake lock partiel limité à la durée du repos**, pour que l'alerte (vibration, son, notification)
     tombe à l'heure, même téléphone verrouillé.

**Temps.** Chaque séance est un instantané : nom, type et convention de charge des exercices, séries prévues au moment du
lancement. L'historique reste exact si la séance ou l'exercice est modifié ou supprimé par la suite.

Les horodatages viennent de `MonotonicTimeProvider`. L'heure murale est ancrée une seule fois au lancement du processus,
puis avancée avec `SystemClock.elapsedRealtime()`, qui est monotone et continue pendant la veille. Un changement d'heure
système ne peut donc pas fausser les chronomètres. Les valeurs persistées restent des millisecondes epoch, interprétables
après un redémarrage.

## Données, Room et synchronisation API

- **Room** est la source de vérité de l'interface (schéma v2, migration `1→2` dans `data/local/db/Migrations.kt`).
  Les exécutions n'ont **pas de clé étrangère** vers les modèles : c'est ce qui garantit l'historique décrit plus haut.
- **Environnements** (`BuildConfig.API_BASE_URL`) : *debug* → preprod `https://gympulse-api-preprod.cyrilmarchive.com/v1`,
  *release* → prod `https://gympulse-api.cyrilmarchive.com/v1`.
- **Authentification.** Deux modes, choisis à la compilation :
  - *Compte Google via Amazon Cognito* (builds **release**, ou debug avec `gympulse.useCognito=true` dans
    `local.properties`) : écran « Continuer avec Google », connexion OAuth (code + PKCE) dans un onglet de navigateur
    (AppAuth, redirection `gympulse://auth/callback`), jetons stockés chiffrés (Android Keystore) et rafraîchis
    automatiquement ; l'API reçoit `Authorization: Bearer <jeton d'accès>` (`CognitoAuth`). « Se déconnecter »
    (Réglages → Compte) efface les données du téléphone : elles reviennent à la prochaine connexion.
    « Supprimer mon compte » (même écran) appelle `DELETE /v1/account` : toutes les données du serveur et le compte
    Cognito sont supprimés définitivement, puis le téléphone est effacé (exigé par Google Play).
  - *En-tête `x-user-id`* (builds **debug** par défaut, preprod uniquement) : `DevUserHeaderAuth`, voir plus bas.

  Pool et client Cognito : `COGNITO_DOMAIN` et `COGNITO_CLIENT_ID` dans `app/build.gradle.kts` (preprod renseignés ;
  **prod à renseigner** : sans eux, une build release affiche « La connexion n'est pas encore configurée »).
  Côté Cognito, le client doit autoriser l'URL de rappel `gympulse://auth/callback` et l'URL de déconnexion
  `gympulse://auth/signout`, le fournisseur Google et le flux « Authorization code grant ».
- **Identité en debug (retrouver ses données).** Sur la preprod il n'y a pas de compte : chaque installation a son
  propre utilisateur (UUID aléatoire). Téléphone et émulateur ne partagent donc pas leurs données, et désinstaller puis
  réinstaller l'app crée un utilisateur vide (les anciennes données restent sur le serveur, mais ne sont plus rattachées).
  Pour utiliser le même identifiant partout, ajoutez dans `local.properties` (non versionné) :

  ```properties
  gympulse.devUserId=mon-identifiant
  ```

  puis *Sync Project* et relancez l'app. Priorité : surcharge des tests, puis `gympulse.devUserId`, puis l'UUID généré.
  Pour reprendre les données d'une installation existante, lisez son identifiant avant de la désinstaller :
  `adb shell run-as com.gympulse.app cat shared_prefs/gympulse_identity.xml`. Cet identifiant n'est pas un secret
  (quiconque le connaît lit les données) : ne l'utilisez que pour la preprod. Les builds *release* l'ignorent.
- **Écritures.** Chaque modification est écrite dans Room et dans la table `sync_ops` **dans la même transaction**
  (`SyncOutbox`), puis un `SyncWorker` (WorkManager, réseau requis, reprise avec backoff) envoie la file dans l'ordre : un
  exercice avant la séance qui l'utilise, une séance avant le programme. Un 4xx met la modification de côté (« refusée »)
  sans bloquer la suite ; 401, 5xx et coupures réseau arrêtent l'envoi pour réessayer plus tard.
- **Lectures.** Après l'envoi, `SyncEngine` récupère bibliothèque, exercices, lieux, séances, programmes, historique,
  séance en cours et préférences, sans jamais écraser un élément qui a encore une modification en attente, et supprime
  localement ce qui a disparu du serveur. Le tout se déclenche au lancement, après chaque modification et via
  « Synchroniser maintenant » (Réglages).
- **Bibliothèque d'exercices** : fournie par le serveur, en lecture seule. Modifier un exercice de la bibliothèque crée
  votre propre copie.
- **Photos personnelles** : l'API n'a pas d'endpoint d'upload, elles restent sur l'appareil (seules les illustrations
  prédéfinies sont synchronisées).
- **Démo** : générée par le serveur (`POST /demo`), puis récupérée. Le premier lancement a besoin du réseau pour la
  bibliothèque (« Continuer hors ligne » possible).
- **Statistiques** : calculées localement sur l'historique Room (les endpoints de stats ne sont pas utilisés).
- **DataStore** stocke les préférences (synchronisées aussi) : thème, unité, son, vibrations, repos par défaut,
  objectif, lieu manuel, détection automatique.

## Statistiques et records

Toutes les statistiques sont **calculées à partir de l'historique réel** enregistré dans Room.

- **Volume** = répétitions × charge effective. En « charge par haltère », la charge saisie est celle d'un haltère et le
  volume compte les deux. Les exercices en durée et au poids du corps sans lest n'ajoutent pas de volume.
- **1RM estimé.** Formule d'Epley, calculée uniquement de 1 à 12 répétitions et toujours **affichée comme une
  estimation**.
- **Records**, détectés par rapport aux performances antérieures : charge max, 1RM estimé, répétitions max, durée max,
  volume. Une première performance n'est jamais un record.
- **Comparaison équitable.** Une hausse de charge réalisée avec beaucoup moins de répétitions reste un record de charge,
  mais pas un record de 1RM. Le graphique « Charge à reps égales » et le tableau des records par nombre de répétitions
  ne comparent que des séries équivalentes.

## Géolocalisation

- Permission demandée uniquement quand l'utilisateur active la détection. Le refus ne bloque jamais un entraînement :
  le choix manuel du lieu reste possible.
- **Position ponctuelle** (`getCurrentLocation`) au lancement et à l'ouverture de l'onglet Séances. Aucun suivi en
  arrière-plan, aucune permission `ACCESS_BACKGROUND_LOCATION`.
- **Correspondance** : lieu le plus proche dont le rayon (augmenté d'une tolérance bornée à la précision GPS) contient la
  position.
- **Compatibilité des séances** :
  - *associée* au lieu ;
  - *matériel disponible* : tout le matériel requis est présent ;
  - *matériel manquant*, avec la liste de ce qui manque.
- **Séance suggérée** : suite du programme en cours en sautant les séances irréalisables sur place, sinon début de
  programme, sinon séance réalisable la moins récente.
- `DeviceLocationProvider` et `LocationRepository` isolent la plateforme, pour pouvoir ajouter plus tard du geofencing.

## Design system

- Identité « Volt » : vert-jaune électrique sur fond nuit, cyan pour le repos, corail pour la force et or pour les
  records. Le thème sombre est soigné et activé par défaut ; le thème clair et les couleurs dynamiques sont disponibles.
- Composants réutilisables : cartes, badges, tuiles de statistiques avec tendance, steppers avec répétition à l'appui
  long, sélecteurs, états vides, squelettes de chargement, dialogues, glisser-déposer pour `LazyColumn`, anneau de
  progression.
- Graphiques interactifs en courbe, barres et sparkline : sélection au toucher ou au glisser, info-bulle, animation
  d'entrée.
- Interface pensée pour la salle :
  - zones tactiles de 56 à 64 dp ;
  - écran maintenu allumé pendant la séance ;
  - retours haptiques (désactivables avec les vibrations) ;
  - chiffres tabulaires pour des chronomètres stables.
- Accessibilité : écrans edge-to-edge, descriptions de contenu, actions alternatives au glisser-déposer
  (monter/descendre), contraste élevé.

## Tests

`./gradlew testDebugUnitTest` exécute les tests sur la JVM, Robolectric compris pour Room et l'UI Compose :

| Suite | Couverture |
|---|---|
| `WorkoutEngineTest` | Instantané, passage de série en série puis d'exercice en exercice, déclenchement et durée du repos, repos réel, prolongation, pause et reprise, correction d'une série, exercice passé, ajout de série, réordonnancement, exercice chronométré, fin de séance |
| `ActiveWorkoutManagerTest` | Persistance immédiate, alerte de fin de repos au bon moment (temps virtuel), annulation de l'alerte, pause pendant le repos, **restauration d'une séance interrompue**, fin, abandon |
| `TrainingMathTest`, `StatsCalculatorTest` | Volume (convention par haltère), 1RM, totaux, semaines, périodes, regroupement par jour, semaine ou mois, charge à reps égales, records par répétitions, comparaison de périodes |
| `RecordDetectorTest` | Détection et non-détection des records, séries non équivalentes |
| `LocationMatcherTest`, `SessionRecommenderTest` | Distance, rayon et tolérance GPS, compatibilité lieu/matériel, séance suggérée |
| `WorkoutPersistenceTest` (Room) | Aller-retour des modèles, séance en cours restaurable, historique conservé après suppression des modèles, abandon exclu de l'historique |
| `DemoDataSeederTest` (Room) | Cohérence des données de démonstration et suppression |
| `ComponentsUiTest` (Compose) | Panneau de repos, stepper, carte de séance |
| `ChartAxisTest` | Graduations « rondes » des graphiques (entières pour les comptes) |

`MainJourneyTest` (androidTest) couvre le parcours complet sur appareil : onboarding, séance suggérée, série, repos,
fin et enregistrement.

État vérifié pour cette version :

- 73 tests JVM au vert (dont le moteur de synchronisation contre un faux serveur HTTP) ;
- `MainJourneyTest` au vert sur un émulateur Android 16 (API 36) ;
- `assembleRelease` avec R8 réussi (APK d'environ 2,5 Mo) ;
- lint sans erreur. Les 2 avertissements restants sont volontaires : la cible API 36 (voir les choix techniques) et
  le dossier `mipmap-anydpi-v26`.

## Choix techniques

- **Service de premier plan `specialUse`.** Le type `health` exigerait une permission capteurs ou activité physique
  sans rapport avec un chronomètre. Le sous-type est justifié dans le manifeste. Sur Google Play, ce choix devra être
  déclaré dans la console ; la bascule vers `health` reste possible.
- **Wake lock limité au repos** plutôt qu'une alarme exacte : `SCHEDULE_EXACT_ALARM` est refusée par défaut depuis
  Android 13, et le service de premier plan est déjà actif pendant la séance.
- **Sauvegarde complète de l'exécution à chaque action** (transaction de quelques dizaines de lignes) plutôt que des
  mises à jour partielles : plus simple, atomique et rapide à cette échelle.
- **Graphiques maison** en `Canvas`, pour la légèreté, le contrôle du style et l'absence de dépendance.
- **Un seul module Gradle.** Les couches sont séparées par paquets, sans sur-modularisation pour cette taille de projet.
- **Cibles SDK.** `targetSdk` 36 et `compileSdk` 37 : les dernières bibliothèques AndroidX exigent la compilation avec
  l'API 37. Le passage de `targetSdk` à 37 est à faire après avoir testé les changements de comportement d'Android 17.

## Limitations et suites possibles

- Textes d'interface en français, écrits directement dans le code. L'extraction dans `strings.xml` pour la
  traduction reste à faire.
- Pas de backend, de compte ni de synchronisation, comme prévu pour cette version. Les données restent sur l'appareil
  et sont incluses dans la sauvegarde Android.
- Détection de lieu ponctuelle seulement ; le geofencing en arrière-plan est une évolution possible.
- Les exercices unilatéraux (par exemple le rowing unilatéral) utilisent la convention « charge totale » : le volume
  n'est pas doublé par côté.
- Les coordonnées des lieux de démonstration sont fictives (Paris). Utilisez « Utiliser ma position actuelle » pour
  enregistrer vos vrais lieux.
- Pas d'export ou d'import de données, ni de widget ou tuile de séance en cours : ce sont de bons candidats pour la suite.
