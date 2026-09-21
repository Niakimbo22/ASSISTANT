# NicoAssistant V2 — état des lots

Suivi de l'implémentation de `docs/NICOASSISTANT_V2_SPEC.md`.
Branche : `claude/lot1-socle-donnees-mbecj8`.

## Ce qui est en place

| Lot | Contenu | État |
| --- | --- | --- |
| 1 | Room : entités, enums, converters, DAO, repository, tests CRUD | ✅ |
| 2 | TextNormalizer, FuzzyMatcher, SlotExtractor, MatchEngine, seuils | ✅ |
| 3 | Contrat Action, registre, 9 actions, ActionExecutor | ✅ |
| 4 | Écran liste, éditeur, sélecteur d'action généré | ✅ |
| 5 | SpeechManager, pipeline vocal complet, seeds §12 | ✅ |
| 6 | Shizuku, actions système, replis, écran d'onboarding | ✅ |
| 7 | Conditions, composition (RUN_AUTOMATION), catalogue complet | ✅ |
| 8 | Thème Nothing, journal, seuils, import/export, tuile, widget, raccourci | ✅ |

Les 31 types d'actions déclarés ont leur implémentation, et un test le vérifie
à chaque build.

## Écarts assumés par rapport à la spec

Chacun est un choix, pas un oubli — à rediscuter si tu préfères l'autre option.

1. **Réordonnancement des actions par boutons monter/descendre**, pas par
   glisser-déposer. Un DnD non testable sur appareil est plus risqué qu'utile.
2. **Pas de `ListeningService` en premier plan ni d'overlay flottant.** La tuile,
   le widget et le raccourci ouvrent l'écran d'écoute de l'app. Depuis
   Android 12, un service de premier plan « micro » démarré sans fenêtre visible
   est soumis à des restrictions qu'on ne peut pas vérifier sans appareil.
3. **Widget en `RemoteViews`, pas en Glance.** Même résultat pour un widget d'un
   seul bouton, sans ajouter un framework d'UI non testable ici.
4. **Pas d'`UpdateChecker`.** La spec parle de « reprise de l'UpdateChecker V1 »,
   mais il n'existe pas dans ce dépôt — il n'y a donc rien à reprendre. Écrire
   un installeur d'APK à l'aveugle serait la pire chose à livrer non testée.
5. **`APP_FOREGROUND` ne bloque jamais une automatisation.** Évaluer cette
   condition exige l'accès aux statistiques d'usage ; bloquer silencieusement
   sur une condition non mesurable serait pire que de l'ignorer.
6. **Deux `ContactMatch` coexistent** (`call/` côté V1, `util/` côté V2), comme
   l'écran d'écoute V1 et son ViewModel. Le ménage se fera quand tu auras
   confirmé que la V2 te convient sur le téléphone.

## Comment vérifier

### La CI

Chaque push sur cette branche lance `.github/workflows/build.yml` :
`./gradlew testDebugUnitTest` **puis** l'assemblage de l'APK. Les tests passent
avant qu'un APK existe, donc un artefact publié est un artefact dont les tests
sont verts.

- APK : onglet Actions → dernier run → artefact `NicoAssistant-debug`.
- Rapport de tests : artefact `test-reports` (HTML, lisible au téléphone).

### Sur le téléphone, dans l'ordre

1. **Au premier lancement**, la liste doit contenir les 8 automatisations
   livrées. Si elle est vide, la base existait déjà : les seeds ne s'installent
   qu'à la création.
2. **Éditeur** : ouvre « Lancer une app », bouton `▶ TESTER MAINTENANT`. Le
   rapport action par action dit ce qui a marché. C'est le moyen de déboguer
   une chaîne sans parler.
3. **Voix** : icône micro → dis « ouvre YouTube ». En cas d'échec, le Journal
   d'exécution (Réglages système → Journal) donne le texte entendu et le score.
4. **Phrase inconnue** : dis n'importe quoi. L'app doit proposer « créer une
   automatisation pour … » avec la phrase déjà pré-remplie.
5. **Shizuku** : Réglages système → état + bouton « Tester » (`echo ok`). Sans
   Shizuku, « coupe le wifi » doit ouvrir le panneau Wifi plutôt qu'échouer.
6. **Musique** : l'automatisation « Musique » reprend l'app choisie en V1. Si
   elle est vide, choisis-la dans l'éditeur (le sélecteur liste les apps
   réellement installées, RVX comprise).

### Si quelque chose ne marche pas

Le Journal d'exécution enregistre chaque tentative, reconnue ou non, avec son
score de matching et la raison de l'échec. Les seuils se règlent dans Réglages
système si l'app se déclenche trop ou pas assez.
