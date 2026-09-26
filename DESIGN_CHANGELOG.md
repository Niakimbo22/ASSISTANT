# Refonte « liquid glass » — journal de design

Branche : `claude/ui-liquid-glass-agaaeu`. Refonte visuelle complète de NicoAssistant,
faite de nuit en autonomie. La logique métier (matching, executor, Room, Shizuku, seeds)
n'a pas été touchée, à une exception près, voulue : `ActionType` porte désormais son
pictogramme et `ActionCategory` sa couleur (métadonnées d'UI, sans dépendance Compose).

## Ce qui a changé

### Design system — `ui/theme/`

Tout ce qui a une couleur, un rayon, un espacement ou un ressort vient d'ici. Aucun écran
ne déclare de valeur visuelle en dur. Chaque composant a son `@Preview`.

| Fichier | Contenu |
| --- | --- |
| `Tokens.kt` | Couleurs (verre blanc 4–12 %, texte 100/70/50 %), dégradés spéculaires, rayons 14–32 dp, espacements, ressorts partagés |
| `Theme.kt` | Typo embarquée : Inter (contenu), Inter Display (grands titres), Space Mono (libellés de section, compteurs) ; phase partagée du fond animé |
| `Symbols.kt` | 82 icônes **Material Symbols Rounded** reprises des SVG officiels |
| `Backdrop.kt` | Fond vivant : trois halos (rouge Nothing, violet, bleu) qui dérivent en 48 s |
| `Glass.kt` | `Modifier.glass`, `GlassCard`, `pressScale` (0,97 sur ressort) |
| `Buttons.kt` | `GlassButton` (principal / secondaire / fantôme / destructeur), `GlassIconButton` |
| `Inputs.kt` | `GlassTextField`, `GlassSelectField`, `GlassPickerField`, `GlassSwitch`, `GlassToggleRow`, `GlassSegmentedControl`, `GlassSlider` |
| `Chips.kt` | `GlassChip`, `StatusBadge`, `SectionLabel` |
| `Sheets.kt` | `GlassSheet`, `GlassOptionSheet`, `GlassDialog` — avec flou de fenêtre Android 12+ |
| `Scaffold.kt` | `GlassScaffold`, `GlassTopBar`, `GlassBottomBar`, `GlassSnackbar`, `frostedGlass` (Haze) |
| `Mic.kt` | `MicOrb` (halo qui respire, réagit au niveau sonore), `GlassDock`, `VoiceWave` |
| `ActionVisuals.kt` / `ConditionVisuals.kt` | Icône + couleur de chaque action / condition |
| `AppIcon.kt` | Vraie icône d'une app installée, chargée hors du fil principal |
| `Haptics.kt` | Retour haptique système (tick, bascule, confirmation, refus) |
| `SharedElements.kt` | Élément partagé carte → éditeur |

### Écrans

- **Liste** — cartes-phrases (« Quand je dis “bonne nuit” → 🔕 🔉 ☀️ »), 4 icônes max
  puis un compteur rond de même taille (fini le « +1 » qui passe à la ligne), compteur
  d'exécutions en mono, interrupteur en verre. Grand titre + sous-titre mono. Dock flottant
  (journal, micro, +) dont la hauteur est réservée par la liste : plus rien n'est caché
  dessous. Glisser pour supprimer avec annulation. Écran vide avec trois modèles créés en
  un tap.
- **Éditeur** — trois blocs de verre QUAND JE DIS / SEULEMENT SI / ALORS + Réglages. Tous
  les champs ont la même boîte. Phrases en pastilles, dictée au micro. Actions en frise
  verticale réordonnable (appui long + glisser). ✓ rouge quand c'est complet, éteint
  sinon — et un tap dessus révèle ce qui manque. « Tester maintenant » collé en bas,
  au-dessus de la navigation gestuelle.
- **Sélecteur d'action** — feuille plein écran, recherche collée en haut (la grille défile
  dessous, jamais dessus), filtres par catégorie, grille de tuiles icône + nom +
  description. Actions impossibles grisées avec un badge qui dit pourquoi ; actions avec
  repli marquées « Repli ».
- **Formulaires de paramètres** — toujours générés depuis `paramsSchema`, restylés : champ
  de verre, liste d'options en feuille, bascule on/off/toggle en contrôle segmenté, choix
  d'app avec vraies icônes, slots en pastilles cliquables.
- **Réglages, Assistant, Journal** — même langage : grand titre, cartes de verre avec icône
  et badge d'état, boutons du design system.
- **Écoute** — n'est plus un écran : une pastille flottante façon Dynamic Island, au-dessus
  de n'importe quel écran, floutée, avec onde réactive (onRmsChanged) et transcription en
  direct. Elle se déploie en carte pour les choix, l'échec (« Créer une automatisation pour
  ça ») ou le résultat, puis se range seule.
- **Navigation** — pile d'écrans avec transitions sur ressort ; « retour » ferme d'abord la
  pastille d'écoute.

## Choix faits sans toi

1. **Branche** : le process demandait `ui-liquid-glass`, mais cette session ne peut pousser
   que sur `claude/ui-liquid-glass-agaaeu`. C'est la même chose sous un autre nom.
2. **Haze 1.2.2, pas 2.0.0.** La 2.0.0 (dernière stable) exige Compose 1.12 et Kotlin 2.4 :
   toute la chaîne (AGP, Kotlin, BOM) aurait dû migrer, sans build local pour vérifier.
   La 1.2.2 est la dernière alignée sur notre Compose 1.7.6.
3. **Flou réel là où il se voit.** Haze floute les barres, le dock, la snackbar et la pastille
   d'écoute (elles passent sur du contenu net). Les feuilles et dialogues utilisent le flou
   de fenêtre natif d'Android 12+ (tout l'écran derrière se floute, calculé par le système).
   Les cartes, elles, n'ont pas de flou : derrière elles il n'y a que des halos déjà flous,
   un flou de plus serait invisible mais coûterait un rendu par carte.
4. **Material Symbols Rounded embarqués en vecteurs** (tracés des SVG officiels) plutôt que
   `material-icons-extended`, qui ne contient que l'ancienne famille Material Icons.
5. **Polices embarquées** (Inter, Space Mono, licence OFL, dans `docs/licenses/`) : pas de
   police téléchargeable, donc aucun accès réseau. Instances statiques générées depuis la
   police variable et réduites au latin (~90 Ko chacune).
6. **Menus déroulants remplacés par des feuilles d'options** : même boîte que les champs
   texte, grosses cibles, et plus d'API expérimentale `ExposedDropdownMenuBox`.
7. **Modèles de l'écran vide créés directement** (un tap = automatisation enregistrée),
   modifiables ensuite dans l'éditeur.
8. **Réordonnancement par appui long + glisser**, en gardant « Monter / Descendre » dans le
   menu ⋮ pour TalkBack.
9. **Disponibilité réelle des backends dans le sélecteur** : avant, l'éditeur recevait une
   valeur par défaut qui considérait Shizuku et l'accessibilité comme toujours absents. Le
   sélecteur lit maintenant l'état réel de Shizuku et du service d'accessibilité.
10. **CI déclenchée sur `claude/**` et `main`** : sans ça, cette branche n'aurait jamais été
    construite.

## Mise à jour sur le téléphone — à lire

- **Il n'y a pas d'`UpdateChecker` dans la branche principale.** Il existe sur une autre
  branche, `claude/in-app-auto-update-vzu10i` (lot 9, jamais mergé), qui publie chaque
  build en release GitHub. J'ai voulu l'intégrer ici, mais l'opération m'a été refusée
  par les garde-fous de la session (fusion d'une branche qui n'est pas la mienne) : c'est
  à toi de décider.
- En attendant, l'APK de chaque build vert est dans l'onglet **Actions → run → artefact
  `NicoAssistant-debug`**.
- Si tu merges ensuite la branche de mise à jour, attends-toi à deux conflits simples :
  `MainActivity.kt` (elle y ajoute la vérification à l'ouverture) et
  `SystemSettingsScreen.kt` (elle y ajoute une carte « Mises à jour » — à refaire avec
  `GlassCard` / `GlassButton`).

## Reste à faire

- Vérifier sur le Nothing Phone (4a) Pro : fluidité du fond animé avec le flou des barres,
  lisibilité du texte secondaire en plein soleil, taille du dock sur écran réel.
- Écran `MainScreen.kt` (V1) : toujours présent, jamais affiché — à supprimer avec le
  reste du code V1 quand la V2 sera validée.
- Widget d'écran d'accueil (RemoteViews) : pas encore au style verre.
- Réordonnancement par glisser : écrit sans appareil, à essayer en vrai.

## Bugs connus / points de vigilance

- Flou de fenêtre (feuilles, dialogues) : absent si l'économiseur de batterie est actif ou
  sur Android < 12 — le fond reste alors simplement assombri, rien ne casse.
- Le fond animé redessine en continu les barres floutées ; si la batterie en souffre, la
  durée du cycle est dans `NicoMotion.BackdropCycleMs`.
