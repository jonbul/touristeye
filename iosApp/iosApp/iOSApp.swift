import SwiftUI

@main
struct iOSApp: App {
    init() {
        // Keep the screen on while sightseeing.
        UIApplication.shared.isIdleTimerDisabled = true
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
