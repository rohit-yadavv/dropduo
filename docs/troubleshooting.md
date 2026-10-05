# Troubleshooting

| Symptom | What to check |
| --- | --- |
| Pairing cannot connect | Same reachable Wi-Fi; correct Mac Wi-Fi address in the QR; code under five minutes old; approve on the Mac. |
| Paired but offline | Mac awake and app running; Android service running; local-network permission and Mac firewall allow DropDuo. |
| Works at home, fails on guest Wi-Fi | Access-point client isolation may block peer traffic. Use a network that permits local device communication. |
| Mac IP changed | Allow discovery time. If multicast is blocked, generate a fresh pairing code with the current Wi-Fi address. |
| Interrupted file | Connect again and select Retry on the sender. If source was deleted or changed, share it again. |
| Android stops receiving | Reopen DropDuo and reconnect. Force-stop, revoked permissions, or vendor battery controls can stop its service. |
| Android file missing after uninstall | App-specific storage is removed by uninstall. Export important files using Save copy first. |
| File will not open | Install an app supporting its format, or Save copy and open elsewhere. |
| Development Mac app blocked | Follow the [first-launch steps](downloads.md#mac-first-launch) for a download you trust. The alpha is not notarized. |

Never paste pairing codes, secrets, or private files into bug reports. Include OS versions, build revision, sanitized error text, and whether the failure was on a physical device or emulator.
