# DropDuo roadmap

DropDuo's first goal is dependable everyday sharing between a Mac and an Android phone. The [product contract](product.md) defines scope. These are priorities, not release dates or promises of completed features; see [validation evidence](validation.md) for what has actually been tested.

## Make the alpha ready for everyday testing

- Complete the physical-device matrix: QR scanning and approval, local discovery, sending both ways, interrupted transfers, sleep/wake, network changes, and battery controls.
- Exercise large files, failed imports, low storage, and repeated daily use. Confirm progress and errors are understandable.
- Review VoiceOver/TalkBack, keyboard navigation, and translated strings.
- Review the pairing/transfer protocol and document the result; an independent security audit is still outstanding.
- Establish signed Mac/Android distribution, including Mac notarization, consistent Android update signing, and required third-party license texts.

Hosted build, interoperability, quality, dependency-review, and CodeQL workflows are configured. Passing automation doesn't replace the device checks above. Maintainers must configure hosted branch protection separately.

## Improve the repeated-use experience

Use real alpha feedback to prioritize Mac login launch, Android background reliability, batching UX, and clearer discovery troubleshooting. Measure whether testers can pair, send, find their files, and repeat the task without coaching. Use opt-in testing, not hidden analytics.

## Consider broader features only after evidence

Extra desktop/mobile platforms, browser transfer, automatic clipboard synchronization, and internet relay are separate proposals. Each needs a demonstrated everyday use case, an OS feasibility check, and an agreed scope change. They aren't included in v1 or promised by a date.

## Help decide what comes next

Report a reproducible failure or describe a concrete task you couldn't complete. Include device/OS versions and the app revision; keep private files and pairing codes out of reports. See [contributing](../CONTRIBUTING.md).
