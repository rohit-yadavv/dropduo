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

/// Sits at the bottom of the sidebar, or on the welcome screen, while an update is available or waiting to install.
struct UpdateNotice: View {
    @ObservedObject var updater: AppUpdater
    var body: some View {
        if updater.waitingForTransfers {
            card {
                Image(systemName: "hourglass").foregroundStyle(Brand.cobalt)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Update ready").font(.callout.weight(.semibold))
                    Text("Installs when transfers finish").font(.caption).foregroundStyle(.secondary)
                }
            }
        } else if let version = updater.availableVersion, !updater.dismissed {
            card {
                Image(systemName: "arrow.down.circle.fill").font(.title3).foregroundStyle(Brand.cobalt)
                VStack(alignment: .leading, spacing: 8) {
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Update available").font(.callout.weight(.semibold))
                        Text("DropDuo \(version)").font(.caption).foregroundStyle(.secondary)
                    }
                    Button("Update…") { updater.check() }.controlSize(.small).disabled(!updater.canCheck)
                }
                Spacer(minLength: 0)
                Button { updater.dismiss() } label: { Image(systemName: "xmark").font(.caption.weight(.semibold)) }
                    .buttonStyle(.borderless).foregroundStyle(.secondary).help("Later").accessibilityLabel("Later")
            }
        }
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        HStack(alignment: .top, spacing: 10, content: content)
            .padding(12).frame(maxWidth: .infinity, alignment: .leading)
            .background(Brand.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(Brand.hairline))
            .shadow(color: Brand.shadow, radius: 3, y: 1)
    }
}
