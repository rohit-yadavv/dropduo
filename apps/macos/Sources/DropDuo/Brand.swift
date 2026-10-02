import AppKit

enum Brand {
    static let menuIcon: NSImage = {
        let url = Bundle.module.url(forResource: "DropDuoMenu", withExtension: "png")!
        let image = NSImage(contentsOf: url)!
        image.size = NSSize(width: 22, height: 22)
        image.isTemplate = true
        return image
    }()
}
