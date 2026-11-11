# HOMElauncher

Kotlin + Jetpack Compose / Material 3 を使用した Android ホームランチャーです。`minSdk 26`、`compileSdk/targetSdk 37 (Android 17)`、JDK 17 を使用します。

## 実装内容

- Android HOME アプリとしての起動インテント対応（既定ホームアプリ設定画面から選択）
- インストール済みランチャーアプリの一覧・起動
- アプリ一覧の名前順／更新順／起動回数順ソート
- アプリ名の部分一致検索
- アプリ一覧で長押ししてホームへ追加、ホームで長押しして削除
- グリッド配置とドラッグ可能な自由配置の切替
- アイコンサイズ、グリッド列数の設定
- Android 12 以降の Dynamic Color
- JSON形式の設定バックアップ／復元
- AppWidgetHost を用いた標準ウィジェット選択・配置、透明度と表示高さの調整、削除
- NotificationListenerService 経由のアクティブ MediaSession 再生／一時停止／前後スキップ
- ログハウス型 Adaptive Icon
- GitHub Actions によるテスト・APKビルド

## 開発環境・ビルド

- Android Studio の最新安定版
- JDK 17
- Android SDK Platform 37

Android Studio で `HOMElauncher/` を開き、Gradle Sync 後に `app` の `assembleDebug` を実行します。コマンドラインでは同梱の `gradlew`（初回に Gradle 9.6.0 を自動取得）を使用します。

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## 初回設定

1. APKをインストールして起動します。
2. 設定 →「デフォルトホームアプリ設定」から HOMElauncher を既定のホームアプリに指定します。
3. アプリ一覧を開き、アプリアイコンを長押ししてホームに追加します。
4. ウィジェットを利用する場合はウィジェットタブから追加します。提供元アプリやAndroidのウィジェット権限・構成画面に従ってください。
5. メディア操作を使う場合は設定 →「メディア通知アクセス設定」で HOMElauncher の通知アクセスを許可し、ホームのメディア欄の更新ボタンを押します。

## 操作

- ホーム画面を下から上へスワイプ、または「アプリ一覧」をタップ：アプリ一覧
- アプリ一覧の検索欄：アプリ名の部分一致検索
- アプリ一覧のアイコン長押し：ホームに追加
- ホーム上のアイコン長押し：ホームから削除
- 自由配置モード：ホーム上のアイコンをドラッグして位置変更
- 設定：アイコンサイズ、グリッド／自由配置、列数、バックアップ／復元

## 署名とアップデート

Androidの更新インストールでは、**初回リリース時と同じ署名鍵**が必須です。鍵を紛失すると同じアプリとして更新できません。keystoreとパスワードはGitに絶対に登録しないでください。安全なオフライン保管も行ってください。

### GitHub Secrets

Repository → Settings → Secrets and variables → Actions に以下を登録します。

- `ANDROID_KEYSTORE_BASE64`: release keystore の Base64文字列
- `ANDROID_KEYSTORE_PASSWORD`: keystore password
- `ANDROID_KEY_ALIAS`: key alias
- `ANDROID_KEY_PASSWORD`: key password

Base64の作成例:

```bash
base64 -w 0 release.jks > release.jks.base64
```

macOSでは `base64 < release.jks | tr -d '\n'` を使用できます。`.jks` と `.base64` はリポジトリにコミットしないでください。

GitHub Actions の workflow_dispatch または main/master への push でビルドします。Secretsが設定されていれば署名済みRelease APKも生成します。未設定の場合はDebug APKと未署名Release APKを生成します。

ローカルで署名する場合は、`~/.gradle/gradle.properties`（リポジトリ外）に次を設定します。

```properties
RELEASE_STORE_FILE=/secure/path/release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

その後 `./gradlew assembleRelease` を実行します。`versionCode` は `app/build.gradle.kts` でリリースごとに必ず増やしてください。`applicationId` と署名鍵は変更しないでください。

## GitHubへのpush

GitHubで空の `HOMElauncher` リポジトリを作成してから、プロジェクトルートで実行します。

```bash
git init
git add .
git commit -m "Initial HOMElauncher implementation"
git branch -M main
git remote add origin https://github.com/<YOUR_ACCOUNT>/HOMElauncher.git
git push -u origin main
```

## テスト

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

実機で次を確認してください。

1. HOMElauncher を既定ホームに設定してホーム画面へ戻る。
2. 上スワイプでアプリ一覧を表示する。
3. 名前順・更新順・起動順を切り替える。
4. 検索欄にアプリ名の一部を入力する。
5. アイコンを長押ししてホームに追加し、タップで起動する。
6. 設定でアイコンサイズを変更する。
7. グリッド／自由配置を切り替え、自由配置でアイコンをドラッグする。
8. JSONバックアップを作成し、復元する。
9. ウィジェットを追加し、透明度と高さを調整して再起動後の表示を確認する。
10. 通知アクセスを許可し、音楽アプリの再生・一時停止・スキップを確認する。

## 制約・今後の改善候補

- ウィジェットの最小／最大サイズはウィジェット提供元の resizeMode・minResizeWidth/Height に従います。Androidの仕様上、任意の寸法を強制できません。
- AndroidのウィジェットIDは端末・ホストに紐づくため、JSON復元時に別端末へウィジェット本体を移行することはできません。移行先で再追加してください。
- メディアアプリが MediaSession を公開していない場合、操作対象に表示されないことがあります。複数セッション選択UIやアートワーク表示は今後の拡張候補です。
- 自由配置はドラッグ座標を保存する基本実装です。ページ／ドック／フォルダ／ジェスチャー割当／アイコンパック／通知ドット／壁紙ピッカー等の高度なランチャー機能は未実装です。
- `QUERY_ALL_PACKAGES` は全アプリの一覧取得のために宣言しています。Google Play公開時は用途申告・審査が必要となる場合があります。
- この実行環境には Android SDK/Gradle がないため、ここで実機ビルドやAPKの動作確認は実施できていません。GitHub Actions または Android Studio でビルド・実機テストしてください。

## ライセンス

必要に応じてプロジェクト利用者が設定してください。
