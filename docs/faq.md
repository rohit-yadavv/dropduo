# DropDuo FAQ

## What is DropDuo?

A free, open-source app for sharing files, photos, videos, text, and links between your Mac and Android phone. Install both apps, pair them once, and share in either direction over your local network.

## Who is it for?

People who use a Mac and an Android phone regularly: move phone photos or scans to the laptop, put a document on the phone before leaving, or send a link to the device where you want to use it. Its native sharing controls and remembered pairing are the focus. See [everyday examples](../README.md#everyday-things-you-can-do).

## Why use it instead of LocalSend or Flitdrop?

Try DropDuo if you want its focused Mac–Android workflow, with an installed Android Share target and Mac menu-bar access. LocalSend covers more operating systems; Flitdrop's phone workflow needs no installed phone app. Encryption, local transfer, and no-account sharing aren't exclusive to DropDuo. See the [comparison](comparison.md) for sources and tradeoffs.

## Which devices are supported?

macOS 14+ on Apple Silicon or Intel, and Android 10+. Android v1 pairs with one Mac at a time; a Mac can pair multiple Android devices. Both ends need DropDuo. There is no iPhone, Windows, Linux, or browser transfer client in v1.

## Do I need an account or an internet connection?

No account is required. Sharing works without internet access, but the devices must be on a reachable local network. The same Wi-Fi network is the usual setup. Guest-network isolation, firewalls, and VPN settings can block local connections; see [troubleshooting](troubleshooting.md).

## Do my files go through your server?

No. Transfers go directly between the paired devices. DropDuo has no cloud file store, internet relay, or offline queue. The receiving device must be available when you send.

## Do I have to pair every time?

No. After scanning a valid pairing code and approving the phone on the Mac, both apps store pairing credentials. Reconnect when needed. Forgetting a device, uninstalling, or an incompatible alpha update can require fresh pairing.

## Does it keep receiving when the apps are closed?

The Mac must stay awake with DropDuo running; menu-bar access is available while the app runs. Android uses a foreground connection service with a visible notification. Force-stopping it, rebooting, revoked permissions, or device battery controls can interrupt availability. Pairing doesn't bypass OS restrictions.

## Where are received files saved?

On Mac: `~/Downloads/DropDuo/<pair ID>/`. Use **Show** in Recent or **Open received files** from the menu bar.

On Android: DropDuo's app-specific external Downloads directory. In **Recent**, select **Save a copy** to export a file to a folder you choose. Uninstalling DropDuo removes app-specific files, so export anything you want to keep first.

## Can I find something I shared a year ago?

Only if the file still exists where it was saved or exported. DropDuo doesn't keep an additional cloud copy. Recent contains at most 100 local entries, so it isn't a permanent archive. Use a backup service or your own saved folders for long-term retention.

## Does DropDuo compress photos or videos?

DropDuo transfers the file bytes supplied by your picker or the sending app without resizing or re-encoding. If another app exports a reduced-quality or edited file before handing it to DropDuo, that supplied file is what transfers. Received files are integrity-checked before completion.

## What happens if a transfer is interrupted?

Reconnect and choose **Retry** on the sender. A matching retained partial file can resume while the source remains available. This is manual retry, not guaranteed automatic recovery from every network failure. Abandoned receiver partials expire after seven days; cancelling a transfer removes its partial data. See [files and history](usage.md#files-and-history).

## Does it sync my clipboard or folders?

No. Share text or a link explicitly, then choose **Copy** on the receiver. DropDuo doesn't monitor your clipboard, mirror folders, or browse the other device's files. Sharing copies the selected data; it doesn't move or delete the original.

## Is there a file-size limit?

The v1 protocol caps a file at **32 GiB**. This is a protocol ceiling, not a tested performance guarantee for files of that size. Both devices need enough free storage, and large-file endurance remains part of physical-device validation. See [protocol limits](../protocol/SPEC.md#messages) and [testing](testing.md).

## Is it secure?

Sessions authenticate paired devices and encrypt transfers using standard cryptographic primitives. You can pause receiving or revoke local trust. The protocol hasn't received an independent security audit and has no forward secrecy in v1. Received files aren't automatically safe to open or execute. Read the [security design](security.md); report vulnerabilities through [SECURITY.md](../SECURITY.md).

## Can I install a finished public release today?

No public release has been published yet. Testers can use [development downloads](downloads.md). These use ad-hoc Mac signing without notarization and Android debug signing. Signing identities can change between Android development builds, preventing an in-place upgrade. Public distribution and real-device validation are tracked in the [roadmap](roadmap.md).
