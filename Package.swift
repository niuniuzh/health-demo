// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "HealthDemo",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "HealthDemo",
            targets: ["HealthPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0")
    ],
    targets: [
        .target(
            name: "HealthPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/HealthPlugin"),
        .testTarget(
            name: "HealthPluginTests",
            dependencies: ["HealthPlugin"],
            path: "ios/Tests/HealthPluginTests")
    ]
)