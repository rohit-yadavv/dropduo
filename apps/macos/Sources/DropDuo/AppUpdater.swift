import AppKit
import Combine
import Sparkle
import SwiftUI

/// Sparkle verifies signed archives and owns download, cancellation, installation and relaunch UI.
/// Background probes only announce availability; downloading always requires a user action.
@MainActor final class AppUpdater: NSObject, ObservableObject, SPUUpdaterDelegate {
    @Published var availableVersion: String?
    @Published var dismissed = false
    @Published var message: String?
    @Published var waitingForTransfers = false
    @Published private(set) var canCheck = false
    private var controller: SPUStandardUpdaterController?
    private var probe = false
    private weak var model: AppModel?
    private var relaunchTask: Task<Void, Never>?
    private var checkObservation: NSKeyValueObservation?

    func start(model: AppModel) {
        guard controller == nil else { return }
        self.model = model
        #if DEBUG
        if ProcessInfo.processInfo.environment["DROPDUO_PREVIEW"] == "1" {
            if ProcessInfo.processInfo.environment["DROPDUO_PREVIEW_SCENE"] == "update" { availableVersion = "0.2.0"; canCheck = true }
            return
        }
        #endif
        guard Bundle.main.bundleURL.pathExtension == "app",
              let key = Bundle.main.object(forInfoDictionaryKey: "SUPublicEDKey") as? String,
              Data(base64Encoded: key)?.count == 32 else {
            message = "In-app updates aren't configured in this build."
            return
        }
        let controller = SPUStandardUpdaterController(startingUpdater: false, updaterDelegate: self, userDriverDelegate: nil)
        self.controller = controller
        let defaults = UserDefaults.standard
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? ""
        if defaults.string(forKey: "dropduo.updateBuild") != build {
            defaults.removeObject(forKey: "dropduo.availableUpdate")
            defaults.removeObject(forKey: "dropduo.updateAttempt")
            defaults.set(build, forKey: "dropduo.updateBuild")
        }
        availableVersion = defaults.string(forKey: "dropduo.availableUpdate")
        dismissed = availableVersion != nil && defaults.string(forKey: "dropduo.dismissedUpdate") == availableVersion
        do { try controller.updater.start() } catch {
            self.controller = nil
            message = "The updater couldn't start. Try reopening DropDuo."
            return
        }
        checkObservation = controller.updater.observe(\.canCheckForUpdates, options: [.initial, .new]) { [weak self] updater, _ in
            Task { @MainActor in self?.canCheck = updater.canCheckForUpdates }
        }
        checkAutomatically()
    }

    func checkAutomatically() {
        guard let updater = controller?.updater, updater.canCheckForUpdates else { return }
        let last = UserDefaults.standard.double(forKey: "dropduo.updateAttempt")
        let now = Date().timeIntervalSince1970
        guard now - last >= 86_400 || now < last else { return }
        UserDefaults.standard.set(now, forKey: "dropduo.updateAttempt")
        probe = true
        updater.checkForUpdateInformation()
    }

    func check() {
        guard let controller, controller.updater.canCheckForUpdates else { return }
        probe = false; message = nil; dismissed = false
        controller.checkForUpdates(nil)
    }

    func updater(_ updater: SPUUpdater, didFindValidUpdate item: SUAppcastItem) {
        if availableVersion != item.displayVersionString { dismissed = UserDefaults.standard.string(forKey: "dropduo.dismissedUpdate") == item.displayVersionString }
        availableVersion = item.displayVersionString
        UserDefaults.standard.set(availableVersion, forKey: "dropduo.availableUpdate")
        message = nil
    }

    func updaterDidNotFindUpdate(_ updater: SPUUpdater, error: Error) {
        availableVersion = nil; UserDefaults.standard.removeObject(forKey: "dropduo.availableUpdate")
    }
    func dismiss() { dismissed = true; UserDefaults.standard.set(availableVersion, forKey: "dropduo.dismissedUpdate") }

    func updater(_ updater: SPUUpdater, didAbortWithError error: Error) {
        if !probe { message = "Couldn't update DropDuo. Check your internet connection and try again." }
        relaunchTask?.cancel(); relaunchTask = nil
        waitingForTransfers = false
        model?.finishUpdateAttempt()
    }

    func updater(_ updater: SPUUpdater, shouldPostponeRelaunchForUpdate item: SUAppcastItem,
                 untilInvokingBlock installHandler: @escaping () -> Void) -> Bool {
        guard let model else { return false }
        waitingForTransfers = true
        model.installingUpdate = true
        relaunchTask = Task { @MainActor [weak self] in
            await model.pauseReceivingForUpdate()
            while model.hasActiveTransfers {
                do { try await Task.sleep(for: .milliseconds(500)) } catch { return }
            }
            guard !Task.isCancelled else { return }
            self?.waitingForTransfers = false
            installHandler()
        }
        return true
    }

    func allowedSystemProfileKeys(for updater: SPUUpdater) -> [String]? { [] }
}

struct UpdateBanner: View {
    @ObservedObject var updater: AppUpdater
    var body: some View {
        if updater.waitingForTransfers {
            Text("The update is ready. Waiting for transfers to finish before relaunching…")
                .font(.callout).padding(12).frame(maxWidth: .infinity).background(Color(nsColor: .windowBackgroundColor))
        } else if let version = updater.availableVersion, !updater.dismissed {
            HStack {
                Text("DropDuo \(version) is available.")
                Spacer()
                Button("Update…") { updater.check() }.disabled(!updater.canCheck)
                Button("Later") { updater.dismiss() }
            }.font(.callout).padding(12).background(Color(nsColor: .windowBackgroundColor))
        }
    }
}
