import SwiftUI

/// Agent operating mode — mirrors Android `AgentMode`.
enum AgentMode: String, CaseIterable {
    case off = "OFF"
    case observe = "OBSERVE"
    case assist = "ASSIST"

    /// Cycle to the next mode: OFF → OBSERVE → ASSIST → OFF
    var next: AgentMode {
        switch self {
        case .off:     return .observe
        case .observe: return .assist
        case .assist:  return .off
        }
    }

    var buttonLabel: String {
        switch self {
        case .off:     return "Turn ON (Observe Mode)"
        case .observe: return "Switch to Assist Mode"
        case .assist:  return "Turn OFF"
        }
    }

    // Persist via UserDefaults (mirrors SharedPreferences)
    private static let key = "agent_mode"

    static var current: AgentMode {
        get {
            let raw = UserDefaults.standard.string(forKey: key) ?? "OFF"
            return AgentMode(rawValue: raw) ?? .off
        }
        set {
            UserDefaults.standard.set(newValue.rawValue, forKey: key)
        }
    }
}

/// Main control panel — port of Android `MainActivity`.
struct MainView: View {
    @State private var mode: AgentMode = .current
    @State private var patternCount = 0
    @State private var appCount = 0
    @State private var statusText = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // Title
                    Text("AI Assistant")
                        .font(.largeTitle)
                        .fontWeight(.bold)
                        .padding(.top, 8)

                    // Status
                    Text(statusText)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    // Learning stats
                    Text("\(patternCount) patterns learned across \(appCount) apps")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    Divider()

                    // Mode cycle button
                    Button(action: cycleMode) {
                        Text(mode.buttonLabel)
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)

                    // Status banner (replaces OverlayService floating bubble)
                    if mode != .off {
                        HStack {
                            Circle()
                                .fill(mode == .assist ? .green : .orange)
                                .frame(width: 10, height: 10)
                            Text(mode == .assist ? "Assist Active" : "Observing...")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                        .padding(8)
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 8))
                    }

                    Divider()

                    // Navigation links (mirror Android's button-based navigation)
                    Group {
                        NavigationLink("Safety Settings") {
                            SafetyView()
                        }
                        NavigationLink("Manage Rules") {
                            RulesView()
                        }
                        NavigationLink("View History") {
                            LogView()
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 4)

                    Spacer()

                    // iOS limitation notice
                    Text("Note: iOS sandboxing prevents system-wide observation. See README for details.")
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                        .multilineTextAlignment(.center)
                        .padding(.top, 24)
                }
                .padding()
            }
            .navigationBarTitleDisplayMode(.inline)
            .onAppear(perform: refresh)
        }
    }

    // MARK: - Actions

    private func cycleMode() {
        mode = mode.next
        AgentMode.current = mode
        refresh()
    }

    private func refresh() {
        mode = .current
        let persistence = PersistenceController.shared
        patternCount = persistence.totalPatterns()
        appCount = persistence.distinctApps()
        statusText = "Mode: \(mode.rawValue)"
    }
}

#Preview {
    MainView()
        .environment(\.managedObjectContext, PersistenceController.shared.viewContext)
}
