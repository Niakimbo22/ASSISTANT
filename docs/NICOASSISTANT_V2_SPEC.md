# NicoAssistant V2 — Moteur d'automatisation générique

Spécification technique complète. Cible : Nothing Phone (4a) Pro, Android 15.
Repo : `Niakimbo22/ASSISTANT` · Kotlin / Jetpack Compose · build via GitHub Actions.

---

## 1. Vision

### Le problème

La V1 est bridée par construction. Le parsing est une cascade de `if (texte.contains("lance"))`, chaque commande est une branche de code dédiée, et ajouter une commande impose de rouvrir Android Studio, recompiler via GitHub Actions et retélécharger l'APK. Trois cas d'usage en dur : musique, appels, lancement d'apps.

### La V2

L'app ne connaît plus *de commandes*, elle connaît *des briques*. Une automatisation devient une donnée en base, créée depuis le téléphone en 30 secondes, sans compilation :

> phrase(s) déclenchante(s) → condition(s) optionnelle(s) → chaîne d'actions

| Avant | Après |
| --- | --- |
| `"lance Spotify"` codé en dur | N'importe quelle phrase → n'importe quelle app |
| 3 types d'actions | ~20 actions combinables |
| 1 action par commande | Chaînes : « je pars » → wifi off + BT casque + itinéraire + SMS |
| Match exact fragile | Match flou tolérant aux erreurs du STT |
| Pas de paramètres | Slots variables : « timer de **15 minutes** » |
| Modifier = recompiler | Modifier = 3 taps dans l'app |

### Principes non négociables

1. **100 % local, 100 % gratuit** — aucune API payante, aucun cloud, STT Android natif.
2. **Zéro hardcode** — si une commande est écrite dans le code Kotlin, c'est un bug d'architecture. Les commandes V1 (musique / appel / app) sont migrées en automatisations *seed*, pas conservées en branches spéciales.
3. **Échec gracieux** — une action qui plante n'interrompt pas la chaîne sauf si marquée critique. Feedback TTS systématique.
4. **Extensible par données, pas par code** — ajouter une action au catalogue = 1 classe ; ajouter une automatisation = 0 ligne.

---

## 2. Architecture globale

### Pipeline, du micro à l'action

```
DÉCLENCHEMENT   double-appui power · bouton in-app · widget · tuile Quick Settings
      ↓
CAPTURE         SpeechRecognizer (fr-FR, offline si dispo) → texte + alternatives
      ↓
NORMALISATION   minuscules · accents retirés · ponctuation · nombres en lettres → digits
      ↓
MATCHING        compare aux N automatisations en base · extrait les slots · score
      ↓
CONDITIONS      heure · wifi · batterie · app au premier plan · jour de semaine
      ↓
EXECUTOR        déroule la chaîne · résout les slots · backends Intent/Accessibility/Shizuku
      ↓
FEEDBACK        TTS · overlay · vibration · log d'exécution
```

### Découpage en packages

```
com.nico.assistant/
├── core/
│   ├── trigger/        AccessibilityService, TileService, receivers
│   ├── stt/            SpeechManager (wrapper SpeechRecognizer)
│   ├── matching/       TextNormalizer, FuzzyMatcher, SlotExtractor, MatchEngine
│   ├── condition/      Condition, ConditionEvaluator
│   └── executor/       ActionExecutor, ExecutionContext, ExecutionLog
├── action/
│   ├── Action.kt              interface commune
│   ├── ActionRegistry.kt      table type → implémentation
│   ├── ActionType.kt          enum + métadonnées UI
│   └── impl/                  une classe par action
├── shizuku/            ShizukuManager, ShizukuShell, ShizukuState
├── data/
│   ├── db/             Room : entités, DAO, converters, migrations
│   ├── repo/           AutomationRepository
│   └── seed/           automatisations livrées par défaut
├── ui/
│   ├── list/           écran liste
│   ├── editor/         éditeur d'automatisation
│   ├── actionpicker/   sélection + paramètres d'action
│   ├── settings/       réglages, Shizuku, logs
│   └── theme/          couleurs, typo, composants style Nothing
└── util/               TTS, permissions, extensions
```

### Les 3 backends d'exécution

Chaque action déclare le backend dont elle a besoin. L'`ActionExecutor` vérifie sa disponibilité avant de lancer et bascule sur le fallback si prévu.

| Backend | Ce qu'il permet | Setup |
| --- | --- | --- |
| **Intent** | Lancer apps, appels, URLs, partage, navigation | Aucun |
| **Accessibility** | Piloter l'UI d'autres apps (taps, scroll, lecture d'écran) | Activer le service |
| **Shizuku** | Wifi, BT, DND, volume, rotation, settings globaux | Pairing ADB, 1 fois |

### Stack technique

- Kotlin 2.x, Compose BOM récent, Material 3
- Room 2.6+ (KSP, pas KAPT)
- Hilt pour l'injection (ou manual DI si Hilt alourdit trop le build CI)
- kotlinx.serialization pour les TypeConverters
- `dev.rikka.shizuku:api` + `dev.rikka.shizuku:provider`
- Coroutines + Flow partout, aucun callback exposé hors des wrappers

---

## 3. Modèle de données (Room)

### Entités

```kotlin
@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,                    // "Mode sortie"
    val enabled: Boolean = true,
    val phrases: List<String>,           // variantes acceptées
    val matchMode: MatchMode = MatchMode.FUZZY,
    val priority: Int = 0,               // départage les ex-aequo
    val confirmBeforeRun: Boolean = false,
    val feedbackText: String? = null,    // TTS custom, sinon généré
    val createdAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long? = null,
    val runCount: Int = 0
)

@Entity(
    tableName = "actions",
    foreignKeys = [ForeignKey(
        entity = AutomationEntity::class,
        parentColumns = ["id"],
        childColumns = ["automationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("automationId")]
)
data class ActionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val automationId: String,
    val order: Int,                      // position dans la chaîne
    val type: ActionType,
    val params: Map<String, String>,     // JSON via TypeConverter
    val critical: Boolean = false,       // si échec → stoppe la chaîne
    val delayMsBefore: Long = 0
)

@Entity(
    tableName = "conditions",
    foreignKeys = [ForeignKey(
        entity = AutomationEntity::class,
        parentColumns = ["id"],
        childColumns = ["automationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("automationId")]
)
data class ConditionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val automationId: String,
    val type: ConditionType,
    val params: Map<String, String>,
    val negated: Boolean = false
)

@Entity(tableName = "execution_logs")
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val automationId: String?,
    val heardText: String,               // ce que le STT a compris
    val matchScore: Float,
    val success: Boolean,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
```

### Enums

```kotlin
enum class MatchMode {
    EXACT,      // égalité stricte après normalisation
    CONTAINS,   // la phrase est contenue dans ce qui est dit
    FUZZY,      // distance de Levenshtein sous seuil — DÉFAUT
    REGEX       // cas avancés, saisi en mode expert
}

enum class ConditionType {
    TIME_RANGE,          // start="22:00", end="07:00"
    DAY_OF_WEEK,         // days="MON,TUE,WED"
    WIFI_CONNECTED,      // ssid="Freebox" (optionnel)
    BATTERY_BELOW,       // level="20"
    CHARGING,
    BLUETOOTH_CONNECTED, // device="Nothing Ear (3)"
    APP_FOREGROUND,      // package="..."
    HEADPHONES_PLUGGED
}
```

### Modèle métier (hors DB)

Room renvoie une relation assemblée, l'app manipule un objet unique :

```kotlin
data class Automation(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val phrases: List<String>,
    val matchMode: MatchMode,
    val priority: Int,
    val confirmBeforeRun: Boolean,
    val feedbackText: String?,
    val conditions: List<Condition>,
    val actions: List<ActionSpec>   // triées par order
)

data class ActionSpec(
    val id: String,
    val type: ActionType,
    val params: Map<String, String>,
    val critical: Boolean,
    val delayMsBefore: Long
)
```

### TypeConverters

Un seul converter kotlinx.serialization pour `List<String>` et `Map<String, String>` → stockés en JSON texte. Ne pas utiliser Gson (dépendance lourde, sérialisation réflective).

### DAO

```kotlin
@Dao
interface AutomationDao {
    @Transaction @Query("SELECT * FROM automations WHERE enabled = 1")
    fun observeEnabled(): Flow<List<AutomationWithChildren>>

    @Transaction @Query("SELECT * FROM automations ORDER BY name")
    fun observeAll(): Flow<List<AutomationWithChildren>>

    @Transaction @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun getById(id: String): AutomationWithChildren?

    @Upsert suspend fun upsertAutomation(e: AutomationEntity)
    @Upsert suspend fun upsertActions(list: List<ActionEntity>)
    @Upsert suspend fun upsertConditions(list: List<ConditionEntity>)

    @Query("DELETE FROM actions WHERE automationId = :id")
    suspend fun clearActions(id: String)
    @Query("DELETE FROM conditions WHERE automationId = :id")
    suspend fun clearConditions(id: String)

    @Delete suspend fun delete(e: AutomationEntity)

    @Query("UPDATE automations SET runCount = runCount + 1, lastRunAt = :ts WHERE id = :id")
    suspend fun markRun(id: String, ts: Long)
}
```

La sauvegarde d'une automatisation éditée se fait en transaction : `clearActions()` puis `upsertActions()` — plus simple et plus sûr qu'un diff d'ordre.

### Migration depuis la V1

Aucune donnée à migrer, la V1 n'a pas de base. Au premier lancement, un `RoomDatabase.Callback.onCreate()` insère les automatisations *seed* (section 12), qui reproduisent à l'identique les commandes V1 — rien n'est perdu, et tout devient modifiable.

---

## 4. Moteur de matching vocal

C'est le cœur du débridage. Il remplace intégralement la cascade de `if/contains` de la V1.

### 4.1 Normalisation

Appliquée **au texte entendu et aux phrases stockées**, avec le même code, sinon rien ne matche.

```kotlin
object TextNormalizer {
    fun normalize(input: String): String = input
        .lowercase(Locale.FRENCH)
        .let { stripAccents(it) }          // é→e, ç→c, ù→u
        .replace(Regex("[^a-z0-9 ]"), " ") // ponctuation → espace
        .let { wordsToDigits(it) }         // "quinze" → "15"
        .replace(Regex("\\s+"), " ")
        .trim()
}
```

`wordsToDigits` gère les nombres français jusqu'à 999 (attention aux « quatre-vingt », « soixante-dix »). C'est indispensable : le STT écrit tantôt « 15 », tantôt « quinze ».

Liste de mots vides à retirer **uniquement pour le scoring**, jamais pour l'extraction de slots : `le, la, les, un, une, de, du, des, a, au, aux, et, s il te plait, stp`.

### 4.2 Slots (paramètres variables)

Une phrase stockée peut contenir des placeholders entre accolades :

```
"mets un timer de {duree} minutes"
"envoie un message a {contact}"
"ouvre {app}"
"monte le volume a {niveau}"
```

Au matching, la phrase est compilée en regex :

```kotlin
// "mets un timer de {duree} minutes"
// → ^mets un timer de (?<duree>.+?) minutes$
fun compileToRegex(phrase: String): Regex {
    val pattern = Regex("\\{(\\w+)\\}").replace(Regex.escape(phrase)) { m ->
        "(?<${m.groupValues[1]}>.+?)"
    }
    return Regex("^$pattern$", RegexOption.IGNORE_CASE)
}
```

Les valeurs capturées vont dans `MatchResult.slots: Map<String, String>`. Elles sont ensuite injectées dans les paramètres d'action : un param qui vaut `{duree}` est remplacé par la valeur extraite au moment de l'exécution.

**Slots système toujours disponibles**, même sans capture :

| Slot | Valeur |
| --- | --- |
| `{heure}` | heure courante `HH:mm` |
| `{date}` | date du jour |
| `{batterie}` | niveau de batterie |
| `{texte}` | phrase complète entendue |

### 4.3 Scoring

```kotlin
data class MatchResult(
    val automation: Automation,
    val slots: Map<String, String>,
    val score: Float          // 0.0 → 1.0
)

class MatchEngine(private val repo: AutomationRepository) {

    suspend fun match(heard: String, alternatives: List<String>): MatchOutcome {
        val candidates = mutableListOf<MatchResult>()
        val inputs = (listOf(heard) + alternatives).map(TextNormalizer::normalize)

        for (auto in repo.enabledAutomations()) {
            var best = 0f
            var bestSlots = emptyMap<String, String>()

            for (input in inputs) {
                for (phrase in auto.phrases) {
                    val (s, slots) = scorePhrase(input, phrase, auto.matchMode)
                    if (s > best) { best = s; bestSlots = slots }
                }
            }
            if (best > 0f) candidates += MatchResult(auto, bestSlots, best)
        }

        val sorted = candidates.sortedWith(
            compareByDescending<MatchResult> { it.score }
                .thenByDescending { it.automation.priority }
        )
        return classify(sorted)
    }
}
```

`scorePhrase` selon le `MatchMode` :

- **EXACT** — `1.0` si égalité après normalisation, sinon `0.0`.
- **CONTAINS** — `0.85` si la phrase normalisée est contenue dans l'entrée, pondérée par le ratio de longueur.
- **REGEX** — `0.95` si la regex matche.
- **FUZZY** (défaut) — combinaison pondérée :
  - **60 %** similarité de Levenshtein normalisée : `1 - distance / max(len)`
  - **40 %** recouvrement de tokens : `|mots communs| / |mots de la phrase stockée|`
  - **Bonus +0.1** si le premier mot correspond (le verbe d'action porte l'intention)

Une phrase contenant des slots est **toujours** évaluée par regex, quel que soit le mode, avec un fuzzy appliqué à la partie fixe.

### 4.4 Seuils et désambiguïsation

```kotlin
sealed interface MatchOutcome {
    data class Confident(val result: MatchResult) : MatchOutcome
    data class Ambiguous(val top: List<MatchResult>) : MatchOutcome  // 2-3 choix
    data object NoMatch : MatchOutcome
}
```

Règles, seuils réglables dans les paramètres :

| Situation | Décision |
| --- | --- |
| `score >= 0.75` et l'écart avec le 2ᵉ est `> 0.15` | **Confident** → exécution directe |
| `score >= 0.75` mais écart `<= 0.15` | **Ambiguous** → TTS « Tu veux X ou Y ? » + choix à l'écran |
| `0.45 <= score < 0.75` | **Ambiguous** avec le top 1 seul → « Tu voulais dire X ? » |
| `score < 0.45` | **NoMatch** → « J'ai pas compris » + proposition de créer une automatisation avec cette phrase |

**Détail qui change tout** : sur `NoMatch`, l'app propose un bouton « créer une automatisation pour *"…"* » qui ouvre l'éditeur avec la phrase entendue déjà pré-remplie. C'est comme ça que le catalogue se construit à l'usage.

### 4.5 Performance

Avec moins de 200 automatisations, le scoring brut en mémoire prend quelques millisecondes — pas besoin d'index. Le repository garde la liste active en cache `StateFlow`, rafraîchie par le Flow Room. Matching exécuté sur `Dispatchers.Default`.

---

## 5. Registre d'actions

### 5.1 Interface commune

Toute action implémente ce contrat. **Aucune exception, aucun cas spécial.**

```kotlin
interface Action {
    val type: ActionType
    val backend: Backend
    val paramsSchema: List<ParamSpec>

    suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult
}

enum class Backend { INTENT, ACCESSIBILITY, SHIZUKU, INTERNAL }

sealed interface ActionResult {
    data class Success(val message: String? = null) : ActionResult
    data class Failure(val reason: String, val recoverable: Boolean = true) : ActionResult
}

data class ExecutionContext(
    val context: Context,
    val slots: Map<String, String>,
    val tts: TtsManager,
    val shizuku: ShizukuManager
)
```

### 5.2 Schéma de paramètres — c'est lui qui génère l'UI

L'éditeur ne contient **aucun formulaire écrit à la main**. Il lit `paramsSchema` et génère les champs. Ajouter une action rend donc son formulaire disponible automatiquement.

```kotlin
data class ParamSpec(
    val key: String,
    val label: String,             // "Application à lancer"
    val type: ParamType,
    val required: Boolean = true,
    val default: String? = null,
    val hint: String? = null,
    val options: List<Pair<String, String>> = emptyList() // value → label
)

enum class ParamType {
    TEXT,           // champ libre, accepte les {slots}
    NUMBER,
    APP_PICKER,     // liste des apps installées (label + package)
    CONTACT_PICKER,
    URL,
    DURATION,
    TOGGLE,         // on / off / bascule
    ENUM,           // dropdown alimenté par options
    SOUND_PICKER
}
```

### 5.3 Catalogue complet

#### Applications & navigation — backend INTENT

| Type | Params | Description |
| --- | --- | --- |
| `LAUNCH_APP` | `package` (APP_PICKER) | Lance une app |
| `OPEN_URL` | `url` (URL) | Ouvre un lien |
| `OPEN_SETTINGS` | `screen` (ENUM) | Écran de réglages précis (wifi, BT, batterie, apps…) |
| `SEND_INTENT` | `action`, `data`, `extras` (TEXT) | Intent brut — la porte de sortie pour tout ce qui manque |
| `NAVIGATE_TO` | `destination` (TEXT) | Itinéraire Maps, accepte `{slot}` |
| `SEARCH_WEB` | `query` (TEXT) | Recherche |

#### Communication — INTENT

| Type | Params | Description |
| --- | --- | --- |
| `CALL_NUMBER` | `number` ou `contact` (CONTACT_PICKER) | Appel direct |
| `SEND_SMS` | `contact`, `message` (TEXT) | SMS, envoi direct ou pré-rempli |
| `SHARE_TEXT` | `text` (TEXT) | Feuille de partage |
| `OPEN_WHATSAPP_CHAT` | `contact` | Ouvre une conversation |

#### Média — INTENT + ACCESSIBILITY

| Type | Params | Description |
| --- | --- | --- |
| `PLAY_MUSIC_SEARCH` | `query` (TEXT), `app` (APP_PICKER) | Stratégie A V1 : intent de recherche |
| `MEDIA_CONTROL` | `command` (ENUM : play, pause, next, previous) | Via `MediaSession` |
| `PLAY_MUSIC_UI` | `query`, `app` | Stratégie B V1 : pilotage UI via Accessibility |

> **Reprise de l'existant** : le couple Stratégie A → fallback Stratégie B de la V1 est conservé, mais sous forme d'une **chaîne de deux actions** (`PLAY_MUSIC_SEARCH` non critique, puis `PLAY_MUSIC_UI`), pas d'un `if` en dur. La détection dynamique du package de RVX Music (nom de package inconnu, APK sideloadé) reste obligatoire : le `APP_PICKER` liste les apps réellement installées, jamais de package codé en dur.

#### Système — SHIZUKU

| Type | Params | Description |
| --- | --- | --- |
| `TOGGLE_WIFI` | `state` (TOGGLE) | `svc wifi enable/disable` |
| `TOGGLE_BLUETOOTH` | `state` (TOGGLE) | `svc bluetooth enable/disable` |
| `TOGGLE_DND` | `state` (TOGGLE) | Mode Ne pas déranger |
| `SET_VOLUME` | `stream` (ENUM), `level` (NUMBER 0-100) | Accepte `{niveau}` |
| `TOGGLE_AIRPLANE` | `state` | Mode avion |
| `SET_BRIGHTNESS` | `level` (NUMBER) | Luminosité |
| `TOGGLE_ROTATION` | `state` | Rotation auto |
| `TOGGLE_TORCH` | `state` | Lampe torche (CameraManager, pas besoin de Shizuku) |
| `RUN_SHELL` | `command` (TEXT) | **Mode expert** — commande shell arbitraire via Shizuku |

#### Utilitaires — INTERNAL

| Type | Params | Description |
| --- | --- | --- |
| `SPEAK` | `text` (TEXT) | TTS, accepte les slots : « Il est {heure} » |
| `VIBRATE` | `pattern` (ENUM) | Retour haptique |
| `WAIT` | `ms` (DURATION) | Pause dans la chaîne |
| `SET_TIMER` | `duration` (DURATION) | Minuteur, accepte `{duree}` |
| `SET_ALARM` | `time` (TEXT) | Réveil |
| `CREATE_NOTE` | `text` (TEXT) | Note via intent |
| `COPY_TO_CLIPBOARD` | `text` (TEXT) | Presse-papier |
| `SHOW_TOAST` | `text` (TEXT) | Message à l'écran |
| `RUN_AUTOMATION` | `automationId` (ENUM) | **Appelle une autre automatisation** — composition |

> `RUN_AUTOMATION` est ce qui permet de composer : une automatisation « mode nuit » peut réutiliser « couper le son » sans dupliquer les actions. Protection anti-boucle : profondeur max 5, détection de cycle.

### 5.4 Registre

```kotlin
object ActionRegistry {
    private val actions: Map<ActionType, Action> = listOf(
        LaunchAppAction(), OpenUrlAction(), SendIntentAction(),
        CallNumberAction(), SendSmsAction(),
        PlayMusicSearchAction(), PlayMusicUiAction(), MediaControlAction(),
        ToggleWifiAction(), ToggleBluetoothAction(), ToggleDndAction(),
        SetVolumeAction(), ToggleTorchAction(), RunShellAction(),
        SpeakAction(), VibrateAction(), WaitAction(), SetTimerAction(),
        RunAutomationAction(), /* … */
    ).associateBy { it.type }

    fun get(type: ActionType): Action =
        actions[type] ?: error("Action non enregistrée : $type")

    fun availableFor(shizukuReady: Boolean): List<Action> =
        actions.values.filter { it.backend != Backend.SHIZUKU || shizukuReady }
}
```

**Règle d'or pour ajouter une action** : créer la classe dans `action/impl/`, ajouter l'entrée dans `ActionType`, l'inscrire dans `ActionRegistry`. Rien d'autre. Ni UI, ni parsing, ni migration.

### 5.5 Exécution en chaîne

```kotlin
class ActionExecutor(
    private val registry: ActionRegistry,
    private val shizuku: ShizukuManager,
    private val tts: TtsManager,
    private val logDao: ExecutionLogDao
) {
    suspend fun run(automation: Automation, slots: Map<String, String>): ExecutionReport {
        val results = mutableListOf<ActionResult>()

        for (spec in automation.actions.sortedBy { it.order }) {
            if (spec.delayMsBefore > 0) delay(spec.delayMsBefore)

            val action = registry.get(spec.type)

            if (action.backend == Backend.SHIZUKU && !shizuku.isReady()) {
                results += ActionResult.Failure("Shizuku indisponible", recoverable = true)
                if (spec.critical) break else continue
            }

            val resolved = spec.params.mapValues { (_, v) -> resolveSlots(v, slots) }

            val result = runCatching {
                withTimeout(10_000) { action.execute(buildContext(slots), resolved) }
            }.getOrElse { ActionResult.Failure(it.message ?: "Erreur inconnue") }

            results += result
            if (result is ActionResult.Failure && spec.critical) break
        }

        return ExecutionReport(automation, results).also { logDao.insert(it.toLog()) }
    }

    private fun resolveSlots(value: String, slots: Map<String, String>): String =
        Regex("\\{(\\w+)\\}").replace(value) { m ->
            slots[m.groupValues[1]] ?: systemSlot(m.groupValues[1]) ?: m.value
        }
}
```

Points importants :

- Chaque action a un **timeout de 10 s** — une action bloquée ne fige pas l'app.
- Une action non critique qui échoue est loguée et la chaîne continue.
- Le rapport d'exécution alimente l'écran Logs et le feedback TTS.

---

## 6. Intégration Shizuku

### 6.1 Ce que c'est

Shizuku fournit à une app normale un accès aux API système de niveau ADB, sans root. L'utilisateur lance le service Shizuku une fois par redémarrage ; l'app s'y connecte par Binder.

Dépendances :

```kotlin
implementation("dev.rikka.shizuku:api:13.1.5")
implementation("dev.rikka.shizuku:provider:13.1.5")
```

Provider à déclarer dans le manifest :

```xml
<provider
    android:name="rikka.shizuku.ShizukuProvider"
    android:authorities="${applicationId}.shizuku"
    android:multiprocess="false"
    android:enabled="true"
    android:exported="true"
    android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
```

### 6.2 Manager

```kotlin
class ShizukuManager(private val context: Context) {

    private val _state = MutableStateFlow(ShizukuState.UNKNOWN)
    val state: StateFlow<ShizukuState> = _state

    fun init() {
        Shizuku.addBinderReceivedListener { refresh() }
        Shizuku.addBinderDeadListener { _state.value = ShizukuState.NOT_RUNNING }
        Shizuku.addRequestPermissionResultListener { _, granted ->
            _state.value = if (granted == PackageManager.PERMISSION_GRANTED)
                ShizukuState.READY else ShizukuState.PERMISSION_DENIED
        }
        refresh()
    }

    fun refresh() {
        _state.value = when {
            !Shizuku.pingBinder() -> ShizukuState.NOT_RUNNING
            Shizuku.isPreV11() -> ShizukuState.UNSUPPORTED
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.READY
            else -> ShizukuState.PERMISSION_NEEDED
        }
    }

    fun requestPermission() = Shizuku.requestPermission(REQ_CODE)

    fun isReady() = _state.value == ShizukuState.READY

    /** Exécute une commande shell avec les droits ADB. */
    suspend fun exec(command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val out = process.inputStream.bufferedReader().readText()
            process.waitFor()
            out
        }
    }
}

enum class ShizukuState {
    UNKNOWN, NOT_RUNNING, UNSUPPORTED, PERMISSION_NEEDED, PERMISSION_DENIED, READY
}
```

> `Shizuku.newProcess` est une API cachée — l'appeler par réflexion si la version de la lib l'a retirée de l'API publique. Prévoir ce fallback.

### 6.3 Commandes shell utilisées

| Action | Commande |
| --- | --- |
| Wifi on/off | `svc wifi enable` / `svc wifi disable` |
| Bluetooth on/off | `svc bluetooth enable` / `svc bluetooth disable` |
| Mode avion | `cmd connectivity airplane-mode enable` |
| DND | `cmd notification set_dnd priority` / `off` |
| Volume | `media volume --stream 3 --set <0-15>` |
| Luminosité | `settings put system screen_brightness <0-255>` |
| Rotation auto | `settings put system accelerometer_rotation 0|1` |

### 6.4 Onboarding utilisateur

L'écran Réglages → Shizuku affiche un guide adapté à l'état courant :

1. **NOT_RUNNING** → « Installe Shizuku depuis le Play Store, puis démarre-le. »
   Sur Android 11+, appairage sans PC : Options développeur → Débogage sans fil → coller le code dans Shizuku.
2. **PERMISSION_NEEDED** → bouton « Autoriser NicoAssistant ».
3. **READY** → coche verte + bouton « Tester » qui exécute `echo ok`.

Rappel important à afficher : **Shizuku doit être relancé après chaque redémarrage du téléphone** (sauf si le mode root est actif). L'app détecte la perte de binder et propose un raccourci vers Shizuku.

### 6.5 Dégradation sans Shizuku

Si Shizuku n'est pas prêt, les actions SHIZUKU sont **grisées dans l'éditeur** avec un badge explicatif, et remplacées à l'exécution par un fallback quand il existe :

| Action | Fallback sans Shizuku |
| --- | --- |
| `TOGGLE_WIFI` | Ouvre le panneau Wifi (intent) |
| `TOGGLE_BLUETOOTH` | Ouvre le panneau Bluetooth |
| `TOGGLE_DND` | `NotificationManager` + permission Accès DND |
| `SET_VOLUME` | `AudioManager` — fonctionne sans Shizuku |
| `SET_BRIGHTNESS` | Permission `WRITE_SETTINGS` |

`SET_VOLUME` et `TOGGLE_TORCH` n'ont en réalité pas besoin de Shizuku : les implémenter en `INTERNAL`.

---

## 7. UI (Compose)

### 7.1 Écrans

```
MainScreen (liste)
 ├── FAB "+"  →  EditorScreen (création)
 ├── tap item →  EditorScreen (édition)
 ├── swipe    →  supprimer (avec undo)
 └── menu     →  SettingsScreen
                  ├── Shizuku
                  ├── Accessibility
                  ├── Seuils de matching
                  ├── Logs d'exécution
                  └── Import / export JSON
```

### 7.2 Écran liste

- `LazyColumn` de cartes. Chaque carte : nom, première phrase en petit, chips des actions, switch actif/inactif.
- Barre de recherche filtrant sur nom + phrases.
- État vide : « Aucune automatisation — crée la première » + bouton.
- Le compteur `runCount` s'affiche discrètement, utile pour repérer ce qui ne se déclenche jamais.

### 7.3 Éditeur — l'écran central

```
┌──────────────────────────────────┐
│ ← Nouvelle automatisation    ✓   │
├──────────────────────────────────┤
│ Nom                              │
│ [ Mode sortie                  ] │
│                                  │
│ QUAND JE DIS                     │
│ ┌──────────────────────────────┐ │
│ │ (je pars ×) (je m'en vais ×) │ │
│ │ [+ ajouter une phrase]       │ │
│ └──────────────────────────────┘ │
│ Correspondance : [Flexible ▾]    │
│ 🎤 Tester la reconnaissance      │
│                                  │
│ SEULEMENT SI (optionnel)         │
│ [+ ajouter une condition]        │
│                                  │
│ ALORS                            │
│ ┌──────────────────────────────┐ │
│ │ ⠿ 1. Couper le wifi       ⋮  │ │
│ │ ⠿ 2. Connecter Nothing Ear⋮  │ │
│ │ ⠿ 3. Dire "Bonne route"   ⋮  │ │
│ └──────────────────────────────┘ │
│ [+ ajouter une action]           │
│                                  │
│ ▶ TESTER MAINTENANT              │
└──────────────────────────────────┘
```

Détails d'implémentation :

- **Chips de phrases** — ajout par saisie ou dictée. Un micro à côté du champ remplit la phrase par la voix : c'est le moyen le plus fiable d'enregistrer exactement ce que le STT comprend.
- **Liste d'actions réordonnable** — drag & drop via `Modifier.pointerInput` + animation d'offset, ou `sh.calvin.reorderable` si une lib est acceptée.
- **Menu ⋮ par action** : dupliquer, supprimer, marquer critique, ajouter un délai.
- **Bouton Tester** — exécute la chaîne immédiatement, sans passer par la voix, et affiche le rapport action par action. Indispensable pour déboguer sans hurler sur son téléphone.

### 7.4 Sélecteur d'action

Bottom sheet en deux temps :

1. **Choix du type** — actions groupées par catégorie (Apps, Communication, Média, Système, Utilitaires), avec barre de recherche. Les actions indisponibles (Shizuku éteint) sont grisées avec la raison.
2. **Paramètres** — formulaire **généré depuis `paramsSchema`**. Un `when (paramSpec.type)` mappe chaque `ParamType` vers un composable : `TEXT` → `OutlinedTextField`, `APP_PICKER` → liste d'apps avec icônes, `ENUM` → `DropdownMenu`, etc.

Sous chaque champ TEXT, afficher les slots disponibles sous forme de chips cliquables qui s'insèrent dans le champ — c'est ce qui rend les variables découvrables sans documentation.

### 7.5 Overlay d'écoute

Fenêtre flottante (`TYPE_APPLICATION_OVERLAY`) affichée pendant l'écoute, au-dessus de l'app courante :

- Cercle animé réagissant au niveau sonore (`onRmsChanged`)
- Transcription partielle en direct
- Après match : nom de l'automatisation + coches par action réussie
- Auto-fermeture 2 s après la fin

### 7.6 Direction visuelle

Style Nothing, cohérent avec le reste des projets Nico :

- Fond noir pur `#000000`, surfaces `#0A0A0A`, bordures `#1F1F1F`
- Accent rouge Nothing `#D71921`, utilisé avec parcimonie
- Typo monospace pour les libellés techniques et les compteurs, sans-serif pour le reste
- Coins peu arrondis (4-8 dp), pas de dégradés, pas d'ombres
- Transitions rapides (150-200 ms)

---

## 8. Déclenchement et service d'écoute

### 8.1 Points d'entrée

| Déclencheur | Implémentation | Note |
| --- | --- | --- |
| Double-appui power | `AccessibilityService` détectant `KEYCODE_POWER` | Repris de la V1 |
| Tuile Quick Settings | `TileService` | Le plus fiable, à ajouter |
| Widget écran d'accueil | `GlanceAppWidget` | Un tap = écoute |
| Raccourci d'app | `ShortcutManager` | Appui long sur l'icône |
| Bouton in-app | Compose | Toujours dispo |
| Notification persistante | Action sur la notif du foreground service | |

### 8.2 SpeechManager

```kotlin
class SpeechManager(private val context: Context) {

    fun listen(): Flow<SpeechEvent> = callbackFlow {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                     RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)   // alternatives pour le matching
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        // listener → trySend(SpeechEvent.Partial / Final / Error / Rms)
        recognizer.setRecognitionListener(/* … */)
        recognizer.startListening(intent)
        awaitClose { recognizer.destroy() }
    }
}
```

**`EXTRA_MAX_RESULTS = 5` est essentiel** : le matching teste les 5 hypothèses du STT, pas seulement la première. C'est ce qui règle en grande partie le problème des noms propres mal reconnus rencontré en V1 — sans code spécifique, juste plus de candidats.

### 8.3 Foreground service

`ListeningService` en `foregroundServiceType="microphone"`, notification discrète, démarré à la demande et arrêté après exécution. Ne pas le laisser tourner en permanence : batterie et permission micro continue.

### 8.4 Feedback TTS

`TextToSpeech` en fr-FR. Le texte prononcé suit cet ordre de priorité :

1. `automation.feedbackText` s'il est défini (slots résolus)
2. Sinon un message généré : « OK, mode sortie » en cas de succès, « Mode sortie, 2 actions sur 3 » en cas d'échec partiel
3. Respecter le mode silencieux : si DND actif, vibration seule

---

## 9. Permissions et manifest

```xml
<!-- Base -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- Actions -->
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.SEND_SMS" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.WRITE_SETTINGS"
    tools:ignore="ProtectedPermissions" />
<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />
<uses-permission android:name="android.permission.SET_ALARM" />
<uses-permission android:name="android.permission.CAMERA" />

<!-- MAJ in-app (existant V1) -->
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

<!-- Visibilité des apps installées — Android 11+ -->
<queries>
    <intent><action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" /></intent>
</queries>
```

Le bloc `<queries>` est **obligatoire** pour que l'`APP_PICKER` voie les apps installées, RVX Music sideloadée comprise.

Permissions spéciales, demandées à la première utilisation d'une action qui en a besoin, jamais toutes au lancement :

- `SYSTEM_ALERT_WINDOW` → `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`
- `WRITE_SETTINGS` → `Settings.ACTION_MANAGE_WRITE_SETTINGS`
- Accès DND → `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`
- Accessibility → `Settings.ACTION_ACCESSIBILITY_SETTINGS`

---

## 10. Plan d'implémentation

Ordre imposé. **Chaque lot doit compiler et être testable seul avant de passer au suivant.**

### Lot 1 — Socle de données

- Room : entités, enums, TypeConverters, DAO, `AppDatabase`
- `AutomationRepository` exposant des Flow
- Tests unitaires CRUD

✅ Validé quand : une automatisation créée en code se relit après redémarrage de l'app.

### Lot 2 — Moteur de matching

- `TextNormalizer` (accents, nombres français, ponctuation)
- `FuzzyMatcher` (Levenshtein + recouvrement de tokens)
- `SlotExtractor` (compilation phrase → regex nommée)
- `MatchEngine` + seuils

✅ Validé quand : les tests unitaires passent sur un jeu d'au moins 20 paires phrase/entrée, fautes du STT incluses.

### Lot 3 — Actions, première vague

- `Action`, `ActionResult`, `ExecutionContext`, `ParamSpec`, `ActionRegistry`
- Implémentations INTENT : `LAUNCH_APP`, `OPEN_URL`, `CALL_NUMBER`, `SEND_SMS`, `SEARCH_WEB`
- Implémentations INTERNAL : `SPEAK`, `VIBRATE`, `WAIT`, `SHOW_TOAST`
- `ActionExecutor` avec chaînage, timeout, critique, résolution de slots

✅ Validé quand : une chaîne de 3 actions codée en dur s'exécute de bout en bout.

### Lot 4 — UI liste + éditeur

- Écran liste (LazyColumn, switch, suppression avec undo)
- Éditeur : nom, chips de phrases, mode de correspondance
- Sélecteur d'action avec formulaire **généré depuis `paramsSchema`**
- Réordonnancement par drag & drop
- Bouton « Tester maintenant »

✅ Validé quand : Nico crée « ouvre YouTube » depuis le téléphone, sans code, et ça marche.

### Lot 5 — Voix branchée sur le moteur

- `SpeechManager` avec alternatives (`EXTRA_MAX_RESULTS = 5`)
- `ListeningService` foreground
- Pipeline complet : déclencheur → STT → match → conditions → exécution → TTS
- Overlay d'écoute
- Gestion `Ambiguous` / `NoMatch` + proposition de créer une automatisation

✅ Validé quand : les commandes V1 (musique, appel, lancer app) fonctionnent **via les seeds**, sans aucun code spécifique.

### Lot 6 — Shizuku

- `ShizukuManager` + états + écran d'onboarding
- Actions SHIZUKU : wifi, BT, DND, avion, luminosité, rotation, `RUN_SHELL`
- Grisage et fallbacks quand indisponible

✅ Validé quand : « coupe le wifi » fonctionne écran verrouillé.

### Lot 7 — Conditions et composition

- `ConditionEvaluator` + UI d'ajout de conditions
- `RUN_AUTOMATION` avec anti-boucle
- Actions restantes : `MEDIA_CONTROL`, `SET_TIMER`, `NAVIGATE_TO`, `COPY_TO_CLIPBOARD`, `SEND_INTENT`

✅ Validé quand : une automatisation conditionnée à l'heure ne se déclenche qu'au bon moment.

### Lot 8 — Finitions

- Écran Logs d'exécution
- Import / export JSON des automatisations
- Réglages des seuils de matching
- Tuile Quick Settings, widget Glance, raccourcis
- Thème Nothing complet
- Reprise de l'`UpdateChecker` V1

✅ Validé quand : l'app est utilisable au quotidien sans passer par Android Studio.

---

## 11. Build, CI et mise à jour

### Contrainte matérielle

Compilation locale impossible (RAM insuffisante sur le PC utilisé). **Tout le build passe par GitHub Actions.** Ne jamais proposer une solution qui suppose un build local ou un câble USB.

### Workflow

`.github/workflows/build.yml` existant, à conserver et compléter :

```yaml
- Build debug APK sur push
- Publier l'APK en artifact
- Incrémenter BUILD_NUMBER dans BuildInfo.kt
- Faire tourner les tests unitaires (matching surtout) avant l'assemblage
```

Ajouter une étape `./gradlew test` : le moteur de matching est entièrement testable sans appareil, ce serait du gâchis de ne pas le couvrir.

### Distribution

- Repo **public** obligatoire : Claude Code et l'`UpdateChecker` accèdent au repo et aux artifacts sans authentification.
- `UpdateChecker` V1 conservé : interroge `https://api.github.com/repos/Niakimbo22/ASSISTANT/actions/artifacts`, compare `BUILD_NUMBER`, télécharge, installe via `FileProvider` + `ACTION_VIEW`.
- Transfert manuel de secours : Google Drive (pas de câble, pas de débogage USB).

### Débogage sans câble

- Logcat via débogage sans fil (Android 11+)
- Tags conservés : `NICO_A11Y` pour l'Accessibility, ajouter `NICO_MATCH` (scores de matching) et `NICO_EXEC` (résultats d'actions)
- L'écran Logs in-app évite d'avoir besoin de Logcat dans la majorité des cas — le prioriser

---

## 12. Seeds et extensions

### Automatisations livrées par défaut

Insérées au premier lancement. Elles reproduisent la V1 **sans une ligne de code spécifique**, ce qui prouve que l'architecture tient.

| Nom | Phrases | Actions |
| --- | --- | --- |
| Lancer une app | `ouvre {app}`, `lance {app}`, `demarre {app}` | `LAUNCH_APP(package={app})` |
| Musique | `mets {titre}`, `joue {titre}`, `écoute {titre}` | `PLAY_MUSIC_SEARCH(query={titre})` non critique → `PLAY_MUSIC_UI(query={titre})` |
| Appeler | `appelle {contact}`, `téléphone à {contact}` | `CALL_NUMBER(contact={contact})` |
| Quelle heure | `quelle heure il est`, `il est quelle heure` | `SPEAK("Il est {heure}")` |
| Lampe | `allume la lampe`, `lampe torche` | `TOGGLE_TORCH(on)` |
| Pause musique | `pause`, `stop la musique` | `MEDIA_CONTROL(pause)` |
| Mode sortie | `je pars`, `je m'en vais` | `TOGGLE_WIFI(off)` + `SPEAK("Bonne route")` |
| Mode nuit | `bonne nuit`, `je vais dormir` | `TOGGLE_DND(on)` + `SET_VOLUME(0)` + `SET_BRIGHTNESS(10)` + `SPEAK("Bonne nuit")` |

> La ligne « Musique » est la preuve du concept : la stratégie A → B de la V1 devient **deux actions dans une liste**, éditable depuis le téléphone, au lieu d'un `if (true)` corrigé à la main dans le code.

### Pistes d'extension (hors périmètre V2)

- **Déclencheurs non vocaux** — heure, connexion wifi, branchement chargeur, arrivée à un lieu. L'architecture les accepte déjà : seul le point d'entrée change, matching et exécution sont inchangés.
- **Glyph Matrix** — retour visuel des exécutions sur la matrice du Nothing Phone via le Glyph Matrix SDK.
- **Wake word** — détection « Nico » en continu (Porcupine gratuit jusqu'à un certain seuil, ou modèle local). Coûteux en batterie, à évaluer après coup.
- **Partage d'automatisations** — export JSON par QR code ou lien.
- **Variables persistantes** — compteurs, bascules mémorisées entre exécutions.

---

## Rappels de contexte

- APK sideloadé RVX Music : **package name inconnu**, détection dynamique obligatoire, **aucun package codé en dur**.
- Pas de câble USB ni de débogage USB disponible.
- Build local impossible → GitHub Actions systématique.
- Repo `Niakimbo22/ASSISTANT` à garder public.
