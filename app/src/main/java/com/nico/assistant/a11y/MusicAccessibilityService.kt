package com.nico.assistant.a11y

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nico.assistant.util.TextUtils as NicoText

/**
 * STRATÉGIE B (repli robuste) : automatise l'interface de l'appli musique via
 * l'API d'accessibilité, quand le lancement par intent (stratégie A) ne suffit pas.
 *
 * Déroulé :
 *   1. (l'appli est déjà lancée par MusicController)
 *   2. trouver et toucher l'icône de recherche (heuristique texte/description)
 *   3. saisir la requête dans le champ EditText focalisé
 *   4. valider (IME action / bouton de recherche)
 *   5. attendre le chargement (asynchrone, RETRY avec délais) puis toucher le
 *      premier résultat lisible.
 *
 * Les identifiants de vues exacts de RVX étant inconnus, la recherche de nœuds
 * est DÉFENSIVE (texte / content-description / classe) et chaque étape est
 * journalisée sous le tag "NICO_A11Y" pour calibrer sur l'UI réelle.
 */
class MusicAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    // État courant de l'automate et horodatages pour les RETRY.
    private enum class Step { OPEN_SEARCH, TYPE_QUERY, SUBMIT, TAP_RESULT, DONE, FAILED }

    private var step: Step = Step.DONE
    private var query: String = ""
    private var requestStartMs: Long = 0L
    private var stepStartMs: Long = 0L
    private var running = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Service connecté. Requête en attente ? ${pendingQuery != null}")
        // Si une requête a été posée avant la connexion du service, on démarre.
        if (pendingQuery != null) startAutomation()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // On relance un tick sur les changements de fenêtre du paquet ciblé :
        // cela accélère la détection sans dépendre uniquement du timer.
        if (!running) return
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == targetPackage) {
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "onInterrupt")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        handler.removeCallbacks(tick)
    }

    /** Démarre (ou redémarre) l'automate pour la requête en attente. */
    fun startAutomation() {
        val q = pendingQuery
        if (q.isNullOrBlank()) {
            Log.w(TAG, "startAutomation sans requête, abandon")
            return
        }
        query = q
        pendingQuery = null
        step = Step.OPEN_SEARCH
        requestStartMs = System.currentTimeMillis()
        stepStartMs = requestStartMs
        running = true
        Log.i(TAG, "=== Automatisation démarrée : query=\"$query\" pkg=$targetPackage ===")
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, FIRST_DELAY_MS)
    }

    /** Un « tick » : tente l'étape courante, journalise, avance ou réessaie. */
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return

            // Garde-fou global : au-delà du délai total, on abandonne proprement.
            val elapsed = System.currentTimeMillis() - requestStartMs
            if (elapsed > GLOBAL_TIMEOUT_MS && step != Step.DONE) {
                Log.e(TAG, "Délai global dépassé (${elapsed}ms) à l'étape $step -> ÉCHEC")
                step = Step.FAILED
            }

            val root = rootInActiveWindow
            if (root == null && step != Step.DONE && step != Step.FAILED) {
                Log.d(TAG, "root null, nouvel essai...")
                handler.postDelayed(this, RETRY_MS)
                return
            }

            when (step) {
                Step.OPEN_SEARCH -> doOpenSearch(root)
                Step.TYPE_QUERY -> doTypeQuery(root)
                Step.SUBMIT -> doSubmit(root)
                Step.TAP_RESULT -> doTapResult(root)
                Step.DONE -> {
                    Log.i(TAG, "=== Automatisation terminée avec succès ===")
                    running = false
                }
                Step.FAILED -> {
                    Log.e(TAG, "=== Automatisation en échec ===")
                    running = false
                }
            }
        }
    }

    /** Passe à l'étape suivante en réinitialisant le compteur de RETRY. */
    private fun advance(next: Step) {
        Log.i(TAG, "Étape $step -> $next")
        step = next
        stepStartMs = System.currentTimeMillis()
        handler.postDelayed(tick, STEP_GAP_MS)
    }

    /** Réessaie l'étape courante, ou échoue si sa fenêtre de temps est dépassée. */
    private fun retryOrFail(reason: String) {
        val inStep = System.currentTimeMillis() - stepStartMs
        if (inStep > STEP_TIMEOUT_MS) {
            Log.e(TAG, "Étape $step : abandon après ${inStep}ms ($reason)")
            step = Step.FAILED
            handler.post(tick)
        } else {
            Log.d(TAG, "Étape $step : nouvel essai ($reason)")
            handler.postDelayed(tick, RETRY_MS)
        }
    }

    // --- Étapes ---------------------------------------------------------------

    private fun doOpenSearch(root: AccessibilityNodeInfo?) {
        root ?: return retryOrFail("root null")

        // Si un champ de saisie est déjà présent, la barre de recherche est
        // probablement déjà ouverte : on saute directement à la saisie.
        findEditable(root)?.let {
            Log.i(TAG, "Champ de saisie déjà présent, saut vers TYPE_QUERY")
            return advance(Step.TYPE_QUERY)
        }

        val searchNode = findByKeywords(root, SEARCH_KEYWORDS)
        if (searchNode != null) {
            val clickable = climbToClickable(searchNode)
            Log.i(TAG, "Icône recherche trouvée: ${describe(searchNode)} -> clic sur ${describe(clickable)}")
            if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                return advance(Step.TYPE_QUERY)
            }
        }
        retryOrFail("icône recherche introuvable")
    }

    private fun doTypeQuery(root: AccessibilityNodeInfo?) {
        root ?: return retryOrFail("root null")

        val edit = findEditable(root)
        if (edit != null) {
            Log.i(TAG, "Champ éditable trouvé: ${describe(edit)}")
            edit.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    query,
                )
            }
            if (edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                Log.i(TAG, "Texte saisi: \"$query\"")
                return advance(Step.SUBMIT)
            }
        }
        retryOrFail("champ éditable introuvable")
    }

    private fun doSubmit(root: AccessibilityNodeInfo?) {
        root ?: return retryOrFail("root null")

        // 1) Tentative IME « Entrée » (API 30+), l'approche la plus fiable.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            findEditable(root)?.let { edit ->
                val ok = edit.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
                )
                if (ok) {
                    Log.i(TAG, "Validation via IME_ENTER")
                    return advance(Step.TAP_RESULT)
                }
            }
        }

        // 2) Repli : bouton de validation/recherche cliquable.
        val submit = findByKeywords(root, SUBMIT_KEYWORDS)?.let { climbToClickable(it) }
        if (submit != null) {
            Log.i(TAG, "Validation via bouton: ${describe(submit)}")
            submit.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return advance(Step.TAP_RESULT)
        }

        // 3) Beaucoup d'UI affichent les résultats en direct pendant la frappe :
        //    on avance quand même vers la sélection du résultat.
        Log.i(TAG, "Pas de bouton de validation ; résultats probablement en direct")
        advance(Step.TAP_RESULT)
    }

    private fun doTapResult(root: AccessibilityNodeInfo?) {
        root ?: return retryOrFail("root null")

        val result = findFirstResult(root)
        if (result != null) {
            Log.i(TAG, "Premier résultat: ${describe(result)}")
            val clickable = climbToClickable(result)
            if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                Log.i(TAG, "Lecture lancée sur le premier résultat")
                return advance(Step.DONE)
            }
        }
        // Les résultats sont asynchrones : on laisse le temps de charger (RETRY).
        retryOrFail("résultats pas encore chargés")
    }

    // --- Recherche de nœuds (défensive & heuristique) -------------------------

    /** Premier nœud dont texte/description contient l'un des mots-clés. */
    private fun findByKeywords(
        root: AccessibilityNodeInfo,
        keywords: List<String>,
    ): AccessibilityNodeInfo? {
        val found = mutableListOf<AccessibilityNodeInfo>()
        traverse(root) { node ->
            val hay = buildString {
                append(node.text ?: "")
                append(' ')
                append(node.contentDescription ?: "")
                append(' ')
                append(node.viewIdResourceName ?: "")
            }
            val norm = NicoText.normalize(hay)
            if (keywords.any { norm.contains(it) }) found.add(node)
        }
        return found.firstOrNull()
    }

    /** Premier nœud éditable (EditText / isEditable). */
    private fun findEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var result: AccessibilityNodeInfo? = null
        traverse(root) { node ->
            if (result == null) {
                val cls = node.className?.toString() ?: ""
                if (node.isEditable || cls.contains("EditText")) result = node
            }
        }
        return result
    }

    /**
     * Heuristique du « premier résultat » : premier nœud cliquable porteur de
     * texte, en excluant la zone de recherche elle-même.
     */
    private fun findFirstResult(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        traverse(root) { node ->
            val label = "${node.text ?: ""} ${node.contentDescription ?: ""}".trim()
            if (label.isBlank()) return@traverse
            val norm = NicoText.normalize(label)
            val isSearchUi = SEARCH_KEYWORDS.any { norm.contains(it) } ||
                node.isEditable
            if (isSearchUi) return@traverse
            if (node.isClickable || climbToClickable(node) != null) {
                candidates.add(node)
            }
        }
        // On loggue les premiers candidats pour calibrer sur l'UI réelle de RVX.
        candidates.take(5).forEachIndexed { i, n ->
            Log.d(TAG, "Candidat résultat #$i: ${describe(n)}")
        }
        return candidates.firstOrNull()
    }

    /** Remonte jusqu'au premier ancêtre cliquable (ou le nœud lui-même). */
    private fun climbToClickable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        var depth = 0
        while (current != null && depth < 8) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return node
    }

    /** Parcours en profondeur, applique [action] à chaque nœud non nul. */
    private fun traverse(node: AccessibilityNodeInfo?, action: (AccessibilityNodeInfo) -> Unit) {
        node ?: return
        action(node)
        for (i in 0 until node.childCount) {
            traverse(node.getChild(i), action)
        }
    }

    /** Représentation courte d'un nœud pour les logs. */
    private fun describe(node: AccessibilityNodeInfo?): String {
        node ?: return "null"
        val text = node.text?.toString()?.take(30)
        val desc = node.contentDescription?.toString()?.take(30)
        val id = node.viewIdResourceName
        val cls = node.className
        return "[$cls id=$id text=${if (TextUtils.isEmpty(text)) "-" else text} " +
            "desc=${if (TextUtils.isEmpty(desc)) "-" else desc} " +
            "click=${node.isClickable} edit=${node.isEditable}]"
    }

    companion object {
        private const val TAG = "NICO_A11Y"

        // Fenêtres de temps (ms) des RETRY.
        private const val FIRST_DELAY_MS = 900L
        private const val RETRY_MS = 600L
        private const val STEP_GAP_MS = 500L
        private const val STEP_TIMEOUT_MS = 5000L   // ~5s par étape (résultats async)
        private const val GLOBAL_TIMEOUT_MS = 15000L

        private val SEARCH_KEYWORDS = listOf("search", "recherche", "rechercher", "chercher")
        private val SUBMIT_KEYWORDS = listOf("search", "recherche", "rechercher", "ok", "valider", "go")

        // Requête et paquet en attente, posés par MusicController.
        @Volatile
        var pendingQuery: String? = null

        @Volatile
        var targetPackage: String? = null

        @Volatile
        private var instance: MusicAccessibilityService? = null

        /**
         * Point d'entrée depuis MusicController : mémorise la requête et déclenche
         * l'automate si le service est déjà connecté.
         */
        fun enqueue(pkg: String, q: String) {
            targetPackage = pkg
            pendingQuery = q
            instance?.startAutomation()
        }

        /** Le service est-il actuellement actif (connecté) ? */
        fun isRunning(): Boolean = instance != null

        /** L'utilisateur a-t-il activé ce service dans les réglages d'accessibilité ? */
        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${MusicAccessibilityService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
