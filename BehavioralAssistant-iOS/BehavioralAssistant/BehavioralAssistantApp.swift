import SwiftUI

@main
struct BehavioralAssistantApp: App {
    let persistence = PersistenceController.shared

    init() {
        // Seed default rules on first launch (mirrors Android's seedDefaultRules)
        persistence.seedDefaultRules()
    }

    var body: some Scene {
        WindowGroup {
            MainView()
                .environment(\.managedObjectContext, persistence.viewContext)
        }
    }
}
