# 🎵 DONIX Music Player

懐かしいMDウォークマンをイメージした
Android音楽プレイヤーです。

## 📥 Androidアプリをダウンロード

[![Download APK](https://img.shields.io/badge/Android-APKをダウンロード-brightgreen?style=for-the-badge&logo=android)](https://github.com/884poki-beep/Build-DONIX-Android/releases/download/v1.0.1/app-release.apk)

### 🎧 主な機能

- MP3音楽再生
- アルバムジャケット表示
- 歌詞表示
- MD交換アニメーション
- 本体カラー変更
- プレイリスト機能

**開発：dongurikobo**



# DONIX Music Player — Androidテスト版

Ver.14のWebアプリを、オフラインで動くAndroid WebViewに同梱したプロジェクトです。

## AndroidスマホだけでAPKを作る
1. GitHubで新しいリポジトリを作成（例: `donix-android`）。
2. このZIPを展開して、中の **.github フォルダを含むすべてのファイル** をリポジトリのルートにアップロードしてください。ZIP自体をアップロードするだけではビルドされません。
3. GitHubの **Actions** → **Build DONIX Android APK** → **Run workflow** を押す（mainへのpushでも実行）。
4. 緑のチェックになったら、その実行結果の **Artifacts → DONIX-Android-debug-APK** をダウンロード。
5. ダウンロードしたZIPを展開し `app-debug.apk` をAndroid端末にインストール。Androidの警告が出る場合は信頼できる自分のビルドであることを確認してください。

## 現段階の対応範囲
- 画面・画像・JavaScriptなどVer.14のファイルをアプリ内に同梱。起動時の通信不要。
- Androidファイル選択画面を経由してMP3を選択可能。WebViewのIndexedDBでの保持は端末・容量・アプリ削除等の影響を受けます。
- **バックグラウンド再生、ロック画面操作、永続的な音楽ライブラリ保証は未実装です。** 端末で確認が必要です。
- Apple Music・Spotifyの外部リンク先は通信が必要で、現状はWebView内での動作です。
- このAPKは開発用デバッグ署名です。Google Play提出には別途AAB、リリース署名、審査対応が必要です。
- GitHub Actionsが成功するかは実際のGitHub上で確認してください（この環境ではビルド実行していません）。
