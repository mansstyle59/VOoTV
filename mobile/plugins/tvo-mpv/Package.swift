// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "TvoMpv",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "TvoMpv", targets: ["TvoMpvPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0"),
        .package(url: "https://github.com/mpvkit/MPVKit.git", exact: "1.1.0-n9.0.2")
    ],
    targets: [
        .target(
            name: "TvoMpvPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm"),
                .product(name: "MPVKit", package: "MPVKit")
            ],
            path: "ios/Sources/TvoMpvPlugin")
    ]
)
