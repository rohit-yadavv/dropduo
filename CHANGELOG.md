# Changelog

Each [GitHub release](https://github.com/rohit-yadavv/dropduo/releases) lists the pull requests it includes. A section here with the exact version adds highlights above that list.

## Unreleased

- Show transferred size, recent speed and estimated time remaining for each active file on both apps, with a separate verification status.
- Keep the Mac awake during active transfers while allowing the display to sleep; release sleep prevention when a transfer ends.
- Received files land where you look for them on Android: photos and videos in your gallery, other files and folders in **Downloads › DropDuo**. They stay after uninstalling DropDuo, so **Save a copy** is gone, and files received with earlier versions move there too.
- Android shows a notification when something arrives while DropDuo is closed: tap it to open the file or link, or choose **Copy** for text. A burst of files alerts once.
- Once connected, Android asks to let DropDuo run in the background, so what the Mac sends arrives while the app is closed.
- Send photos from Android in one tap: **Send photos** opens the photo picker, with **Files** and **Text** beside it.
- Choose where the Mac saves received files in Settings. Files now arrive under their own names, like IMG_2041.jpg (IMG_2041 2.jpg if that's taken), instead of in a folder per phone with an ID in each name. Files received earlier stay where they are.
- Drag received files from the Mac timeline into Finder, Mail or any other app.
- Send text selected in any Mac app with **Services › Send with DropDuo**, and send the clipboard (text, an image, or files copied in Finder) with **Send Clipboard** in the menu bar menu.
- Links from the phone open from the Mac notification, and one-time codes get **Copy Code** in notifications on both apps.
- When the devices can't reach each other, **What to Check** lists the usual fixes: the same Wi-Fi, DropDuo running, Local Network access on the Mac, and networks that block devices.
- Send folders from the Mac: drop one on DropDuo, pick it with **Send Files…**, or right-click it in Finder. It arrives on the phone as a folder with everything inside, resumes after a dropped connection, and is integrity-checked like a file. Both apps need this version.
- Send from Finder: right-click files and choose **Send with DropDuo** (under **Services** on some Macs), or drop them on DropDuo's Dock icon. They go to the phone selected in DropDuo, and wait if it's not reachable.
- Update both apps together: the Mac now admits a phone only after it proves it holds the session keys, so a recorded connection attempt can't take over the phone's connection. Older Android versions can't connect to this Mac version.
- Items waiting to send from the phone go only to the Mac they were meant for; after pairing a different Mac, tap Retry to send them there.
- Sends in progress survive the app closing and resume on reconnect, and waiting sends no longer drop out of a long history.
- Reconnect automatically when the other device stops responding mid-transfer, instead of staying stuck until reconnecting by hand.
- Fix dropping more than four files on the Mac leaving some stuck at "Preparing"; dropped files now send one after another.
- Fix cancelling a text on Android, and rotating the phone after sharing sending the share again.
- Fix a received file staying at "Receiving" when the connection dropped right as it finished.

## 0.1.0-alpha.6

- Send while the other device is offline: files and text you share from the Mac or the phone wait, then go out automatically the next time the devices connect. Clearing recent history keeps items still waiting to send.
- Remove drag and drop onto the menu bar icon; dragging there opened the macOS desktop instead of sending. Use **Send Files…** in the menu bar menu or drop files into the window.
- The Mac app icon now uses a dark tile, so it stays legible in dark mode.

## 0.1.0-alpha.5

- Drop files on the DropDuo menu bar icon to send them to your phone. With several paired phones, choose the target under **Send To** in the menu.
- Show a Mac notification when a file or text arrives: click a file to show it in Finder, or choose **Copy** for text. Sends started from the menu bar confirm with a notification too.
- Open DropDuo at login, so a paired phone can reach the Mac after a restart. It turns on after pairing and can be switched off in Settings. Closing the window keeps DropDuo running in the menu bar.
- Clearer error messages on both apps: which device can't be reached and what to check, paused receiving, a busy receiver, folders, and lost connections.

## 0.1.0-alpha.4

- Keep the Android pairing-code scanner in portrait instead of rotating to landscape.
- Redesign the Android app: focused pairing screen, a single home with Mac status, Files and Text actions, and recent transfers, transfer details in sheets, and a grouped Settings screen. Uses the brand palette with dark mode and custom line icons.

## 0.1.0-alpha.3

- Fix "Frame too large" when sending some large files from the Mac; Foundation JSON escaping could push a chunk past the frame limit.
- Fix the Mac app crashing at launch when its bundled images could not be found (seen opening the downloaded app); images now ship in the app's Resources and missing art falls back to a system symbol.
- Resume sends automatically when the devices reconnect after a dropped connection, with an option to stop resuming.
- Simplify the README and docs; consolidate FAQ answers into the setup guide.
- Add Mac first-launch instructions, a Settings shortcut, and public-source links.

## 0.1.0-alpha.2

- Redesign Mac sharing with a device sidebar, transfer timeline, composer, drop-anywhere sending, and pairing/settings sheets.
- Add user guides and a documentation index for Mac–Android sharing.
- Add a static download website with platform-aware release links and GitHub Pages deployment.
- Reduce duplicate CI runs; skip native builds for docs/web-only changes and run CodeQL on master PRs and weekly.

## 0.1.0-alpha.1

- Add Apple Silicon and Intel Mac development downloads, repair hosted SDK/resource-bundle setup, and include download checksums.
- Add tag-validated release drafts, optional distribution signing/notarization, shared version metadata, workflow guard checks, CodeQL, dependency review, and Dependabot configuration.
- Adopt the bold overlapping double-D icon in ink and cobalt on pure white across native app launchers and headers.
- Adopt DropDuo branding throughout the native apps, package identifiers, protocol namespace, documentation, scripts, and artifacts. Earlier working-name alpha builds require fresh pairing on both devices.
- Native Mac and Android apps with persistent approved pairing and encrypted local transfer.
- Bidirectional file, photo, video, explicit text/link sharing; progress, cancellation, and manual resume/retry.
- Local recent history, receiver controls, device revocation, native Android Share target, and Mac menu bar access.
- Keychain/Keystore credential storage, bounded framing, safe filenames, integrity verification, and incomplete-file retention.
- Open-source documentation, agent contributor guides, Git standards, CI definitions, and repeatable cross-platform validation.

Development artifacts are not production-signed releases. Physical-device and distribution validation remain required.
