# NicoAssistant 🎙️

Assistant vocal personnel pour Android, en **Kotlin / Jetpack Compose**, pensé
pour **remplacer Google Assistant** sur trois tâches :

1. 🎵 **Jouer de la musique** (via une appli type RVX Music / YouTube Music
   sideloadée, au nom de paquet inconnu) ;
2. 📞 **Passer un appel** à un contact ;
3. 📱 **Ouvrir une application**.

Tout se passe **en local, sans réseau** : la reconnaissance vocale utilise le
`SpeechRecognizer` natif d'Android et l'analyse des commandes est basée sur des
règles (aucune API, aucun serveur).

L'appli est prévue pour être déclenchée par le **double-appui sur le bouton
Marche/Arrêt** (geste configuré par vous dans les réglages système, pointant
vers NicoAssistant).

---

## ⚙️ Caractéristiques techniques

| | |
|---|---|
| Langage | Kotlin |
| UI | Jetpack Compose (Material 3) |
| minSdk | 26 (Android 8.0) |
| compileSdk / targetSdk | 35 (Android 15) |
| Modules | Un seul module `:app` |
| Reconnaissance vocale | `SpeechRecognizer` natif, `fr-FR` |
| Synthèse vocale | `TextToSpeech` natif, `fr-FR` |
| Réseau | **Aucun** (100 % hors-ligne) |

---

## 🏗️ Compiler et installer

> ⚠️ Le SDK Android est requis. Le plus simple est **Android Studio**
> (Hedgehog ou plus récent).

### Option A — Android Studio (recommandé)

1. **Ouvrir le projet** : `File > Open` et sélectionner ce dossier.
2. Laisser Gradle se synchroniser (téléchargement de l'AGP et des dépendances
   depuis Google Maven / Maven Central).
3. Brancher le téléphone en **débogage USB** (ou utiliser un émulateur).
4. Cliquer sur **Run ▶**.

### Option B — Ligne de commande

Prérequis : SDK Android installé et variable `ANDROID_HOME` définie (ou un
fichier `local.properties` à la racine contenant `sdk.dir=/chemin/vers/Android/Sdk`).

```bash
# Générer l'APK de debug
./gradlew assembleDebug

# Installer directement sur un appareil branché
./gradlew installDebug
```

L'APK se trouve dans `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🚀 Première configuration (dans l'ordre)

### 1. Accorder les permissions

Au premier lancement, l'appli demande :

- 🎙️ **Micro** (`RECORD_AUDIO`) — pour écouter vos commandes ;
- 👥 **Contacts** (`READ_CONTACTS`) — pour retrouver la personne à appeler ;
- 📞 **Téléphone** (`CALL_PHONE`) — pour lancer l'appel.

Acceptez les trois.

### 2. Choisir l'application de musique (RVX Music)

Comme l'APK RVX est **sideloadé** et peut avoir **n'importe quel nom de
paquet**, rien n'est codé en dur : vous devez le sélectionner **une fois**.

1. Ouvrir NicoAssistant → icône ⚙️ **Réglages** (en haut à droite).
2. Section **« Application de musique cible »** : la liste montre **toutes** les
   applis installées, avec leur **libellé** ET leur **nom de paquet réel**.
3. Toucher votre appli RVX Music : elle est mémorisée (une ✓ apparaît).
4. Le **nom de paquet sélectionné** reste affiché en haut de la section, pour
   débogage. Il est aussi **imprimé dans Logcat** (`NICO_VM: Appli musique
   sélectionnée : … -> <paquet>`).

### 3. Activer le service d'accessibilité (repli robuste)

Pour que la musique se lance **même si l'appli n'accepte aucun intent de
recherche**, NicoAssistant peut **piloter automatiquement l'interface** de
l'appli musique (stratégie B) via un service d'accessibilité.

1. Réglages de NicoAssistant → **« Ouvrir les réglages d'accessibilité »**.
2. Trouver **« NicoAssistant Music »** dans la liste et l'activer.
3. Confirmer la demande système.

> Sans ce service, seule la **stratégie A** (intents de recherche) est utilisée ;
> elle suffit pour beaucoup d'applis mais pas forcément pour un fork custom.

### 4. Configurer le double-appui sur le bouton Marche/Arrêt

C'est un réglage **système**, indépendant de l'appli :

- **Réglages** (du téléphone) → **Système** → **Gestes** →
  **Appuyer deux fois sur la touche Marche/Arrêt** (le libellé varie selon la
  marque : « Ouvrir rapidement l'application », « Raccourci du bouton
  Marche/Arrêt », etc.).
- Choisir **NicoAssistant** comme application à ouvrir.

> Selon le constructeur (Samsung, Xiaomi, Pixel…), l'emplacement exact du
> réglage change, mais le principe reste le même : associer le double-appui
> power à NicoAssistant.

### 5. (Optionnel) Écoute automatique

Dans les réglages de l'appli, l'interrupteur **« Écouter automatiquement à
l'ouverture »** :

- **Activé** (défaut) : le micro démarre **dès l'ouverture** → idéal avec le
  double-appui power (vous appuyez, vous parlez).
- **Désactivé** : l'appli affiche le gros bouton micro à toucher manuellement.

---

## 🗣️ Utilisation

Parlez naturellement en français ; les noms propres anglais (artistes, applis)
sont acceptés.

| Vous dites | Action |
|---|---|
| « **lance** du Werenoi », « **mets** du Travis Scott », « **joue** … », « **écoute** … », « **balance** … » | 🎵 Musique |
| « **appelle** Luca », « **téléphone à** Maman » | 📞 Appel |
| « **ouvre** Snapchat », « **lance l'application** Instagram », « **démarre** … » | 📱 Ouvrir une appli |

**Cas ambigu « lance »** : si le texte qui suit correspond à une appli
installée, c'est une **ouverture d'appli** ; sinon, c'est de la **musique**.

Chaque action donne un **retour vocal** (TTS : « Je lance Werenoi »,
« J'appelle Luca », « J'ouvre Snapchat ») **et** une **fenêtre** à l'écran avec
le même message et l'intention analysée.

### Mode debug

L'écran principal contient un **champ de saisie** : tapez une commande
(ex. `lance du Werenoi`) et touchez **Exécuter** pour la tester **sans parler**.

---

## 🎵 Comment la musique est lancée (2 stratégies)

**Stratégie A — rapide/propre.** On lance l'appli musique sélectionnée avec un
intent de recherche : `MEDIA_PLAY_FROM_SEARCH`, `ACTION_SEARCH`
(`SearchManager.QUERY` + `Intent.EXTRA_TEXT`), puis une URI de recherche
YouTube Music. On force le **paquet cible** (jamais codé en dur).

**Stratégie B — repli robuste (service d'accessibilité).** Si le service est
activé, NicoAssistant automatise l'UI de l'appli :

1. lance l'appli (par son paquet mémorisé) ;
2. trouve et touche l'icône de **recherche** (heuristique sur le texte /
   description : `search` / `recherche` / `rechercher`) ;
3. **saisit** la requête dans le champ `EditText` ;
4. **valide** (action IME « Entrée » ou bouton de recherche) ;
5. **attend** le chargement des résultats (asynchrone, **RETRY** avec délais,
   jusqu'à ~5 s par étape) puis **touche le premier résultat** jouable.

La recherche de nœuds est **défensive et heuristique** (les identifiants de vues
de RVX sont inconnus). **Chaque étape et chaque nœud trouvé sont journalisés**
sous le tag Logcat **`NICO_A11Y`**, pour calibrer sur l'UI réelle de RVX :

```bash
adb logcat -s NICO_A11Y NICO_MUSIC NICO_VM NICO_SPEECH NICO_TTS
```

---

## 📁 Structure du projet

```
app/src/main/java/com/nico/assistant/
├── MainActivity.kt              # Activité unique (Compose) + permissions + nav
├── ui/
│   ├── AssistantViewModel.kt    # Orchestration : voix -> parsing -> action
│   ├── MainScreen.kt            # Micro, texte reconnu, champ debug, popups
│   ├── SettingsScreen.kt        # Réglages : écoute auto, appli musique, a11y
│   └── Theme.kt                 # Thème Material 3
├── voice/SpeechManager.kt       # SpeechRecognizer natif (fr-FR)
├── parser/CommandParser.kt      # Analyse locale par règles (aucun réseau)
├── apps/AppRepository.kt        # Énumération des applis + matching flou
├── music/MusicController.kt     # Stratégie A + repli automatique vers B
├── a11y/MusicAccessibilityService.kt  # Stratégie B (pilotage de l'UI)
├── call/CallController.kt       # Contacts + ACTION_CALL
├── tts/TtsManager.kt            # Synthèse vocale (fr-FR)
├── prefs/Prefs.kt               # SharedPreferences
└── util/TextUtils.kt            # Normalisation insensible aux accents/casse
```

---

## 🔐 Permissions (dans le manifeste)

- `RECORD_AUDIO`, `READ_CONTACTS`, `CALL_PHONE` — demandées **à l'exécution**.
- `QUERY_ALL_PACKAGES` — indispensable (Android 11+) pour **voir et lancer**
  l'appli RVX sideloadée.
- Service d'accessibilité déclaré avec sa config
  (`res/xml/accessibility_service_config.xml`) et le libellé **« NicoAssistant
  Music »**.

---

## ❓ Dépannage

- **« Aucune appli musique »** → sélectionnez-la dans les réglages.
- **La musique ne se lance pas** → activez le service d'accessibilité
  (stratégie B) puis observez `adb logcat -s NICO_A11Y` pour voir quelle étape
  bloque (les nœuds détectés y sont listés).
- **« Contact introuvable »** → vérifiez l'orthographe / la permission Contacts.
- **« Application introuvable »** → le libellé prononcé doit correspondre (le
  matching est flou et insensible aux accents, mais pas magique).
- **Le micro ne démarre pas tout seul** → vérifiez l'interrupteur « Écouter
  automatiquement » et la permission micro.
