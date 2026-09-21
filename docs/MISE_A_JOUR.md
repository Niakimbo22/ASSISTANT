# Mise à jour automatique in-app (lot 9)

L'app se met à jour toute seule : elle regarde la dernière release GitHub, compare
son numéro de build au sien, télécharge l'APK et le passe à l'installeur système.
Aucun câble, aucun compte, aucun téléchargement manuel.

## Pourquoi des releases et pas les artefacts Actions

L'API `/actions/artifacts` **exige un jeton d'authentification, même sur un dépôt
public** : un `GET` anonyme depuis le téléphone reçoit un 401. Un asset de release,
lui, se télécharge sans rien. Le workflow publie donc l'APK dans une release taguée
`build-<run_number>`, et l'app interroge :

```
https://api.github.com/repos/Niakimbo22/ASSISTANT/releases/latest
```

L'artefact Actions reste publié en parallèle — il sert de filet, pas de source.

## Pourquoi une keystore fixe

Chaque runner GitHub génère une clé debug **aléatoire**. Deux builds successifs sont
donc signés par deux clés différentes, et Android refuse d'installer le second
par-dessus le premier (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Une keystore fixe,
stockée en base64 dans les secrets et décodée par la CI, résout ça définitivement.

Si le secret `KEYSTORE_BASE64` est absent, le build ne casse pas : il retombe sur la
clé debug et affiche un avertissement dans le résumé du run. L'APK produit est
installable — mais seulement après avoir désinstallé la version précédente.

## Les secrets à créer

Sur **github.com/Niakimbo22/ASSISTANT** → `Settings` → `Secrets and variables` →
`Actions` → `New repository secret`.

| Nom | Contenu | Obligatoire |
| --- | --- | --- |
| `KEYSTORE_BASE64` | la keystore encodée en base64, sur une seule ligne | oui |
| `KEYSTORE_PASSWORD` | le mot de passe de la keystore | oui |
| `KEY_ALIAS` | l'alias de la clé | non — vaut `nicoassistant` par défaut |
| `KEY_PASSWORD` | le mot de passe de la clé | non — reprend `KEYSTORE_PASSWORD` |

Deux secrets suffisent donc si la keystore utilise l'alias `nicoassistant` et le même
mot de passe pour le magasin et pour la clé.

### Régénérer la keystore plus tard

À faire sur une machine avec un JDK — **et une seule fois pour toute la vie de
l'app** : changer de clé oblige à désinstaller l'app du téléphone avant de pouvoir
réinstaller.

```bash
keytool -genkeypair -v \
  -keystore release.jks -storetype PKCS12 \
  -keyalg RSA -keysize 4096 -validity 10950 \
  -alias nicoassistant \
  -dname "CN=NicoAssistant, OU=Nico, O=NicoAssistant, L=Paris, C=FR" \
  -storepass "<mot-de-passe>" -keypass "<mot-de-passe>"

base64 -w0 release.jks     # le contenu de KEYSTORE_BASE64
```

Garde le fichier `release.jks` **hors du dépôt** (`.gitignore` le bloque déjà) et
sauvegarde-le ailleurs : il est irremplaçable.

## Ce que fait le workflow

1. `testDebugUnitTest` — rien n'est assemblé si les tests sont rouges.
2. Décode `KEYSTORE_BASE64` dans un fichier temporaire du runner.
3. `assembleRelease -PbuildNumber=<run_number>` : le numéro devient à la fois
   `BuildConfig.BUILD_NUMBER`, le `versionCode` de l'APK et le `versionName`.
4. Renomme l'APK en `NicoAssistant-build-<N>.apk`.
5. Crée la release `build-<N>` avec l'APK en asset.

Le `versionCode` suit le numéro de build : Android refuse d'installer un APK dont le
`versionCode` est inférieur à celui déjà en place, donc la numérotation croissante
n'est pas cosmétique.

## Ce que fait l'app

- **À l'ouverture** : vérification silencieuse. Elle n'affiche une popup que si une
  release plus récente existe. Une panne réseau ne dit rien.
- **Réglages système → Mise à jour** : version installée, bouton
  « Vérifier les mises à jour », puis télécharger / autoriser / installer.
- **Autorisation** : Android exige « installer des applications inconnues ».
  L'app envoie directement sur l'écran de réglage concerné et revérifie au retour.
- **Installation** : `PackageInstaller` d'abord — il rend un vrai code d'erreur.
  Si la session échoue, repli automatique sur `FileProvider` + `ACTION_VIEW`, qui
  reste aussi accessible par un bouton manuel.

## Si ça ne marche pas

| Symptôme | Cause probable |
| --- | --- |
| « Aucune release publiée » | aucun build n'a encore tourné depuis ce workflow |
| « GitHub limite les requêtes (403) » | plus de 60 appels anonymes dans l'heure — attendre |
| « Conflit avec la version installée » | l'APK installé vient d'une autre clé : désinstaller l'app une fois, puis réinstaller |
| Rien ne s'affiche après « Installer » | utiliser « Ouvrir l'installeur système » |
| L'APK n'est pas signé avec la bonne clé | secret `KEYSTORE_BASE64` absent — voir l'avertissement dans le résumé du run |
