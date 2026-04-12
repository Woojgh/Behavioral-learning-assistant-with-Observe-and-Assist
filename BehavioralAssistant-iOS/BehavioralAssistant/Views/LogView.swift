import SwiftUI

/// Scrollable history of all actions taken — port of Android `LogActivity`.
struct LogView: View {
    @State private var logs: [LogEntity] = []

    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "MM/dd HH:mm:ss"
        return f
    }()

    var body: some View {
        List {
            if logs.isEmpty {
                Text("No actions recorded yet.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(logs, id: \.id) { log in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 6) {
                            Text(log.success ? "✓" : "✗")
                                .foregroundStyle(log.success ? .green : .red)
                            Text("\(log.actionType ?? ""): \(log.actionDetail ?? "")")
                                .font(.body)
                        }
                        HStack {
                            Text(formatDate(log.timestamp))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text("|")
                                .font(.caption)
                                .foregroundStyle(.tertiary)
                            Text(log.packageName ?? "")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 2)
                }
            }
        }
        .listStyle(.plain)
        .navigationTitle("Action History")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Clear All") {
                    clearLogs()
                }
                .disabled(logs.isEmpty)
            }
        }
        .onAppear(perform: loadLogs)
    }

    // MARK: - Actions

    private func loadLogs() {
        logs = PersistenceController.shared.fetchLogs()
    }

    private func clearLogs() {
        PersistenceController.shared.clearLogs()
        logs = []
    }

    private func formatDate(_ date: Date?) -> String {
        guard let date = date else { return "—" }
        return dateFormatter.string(from: date)
    }
}

#Preview {
    NavigationStack {
        LogView()
    }
    .environment(\.managedObjectContext, PersistenceController.shared.viewContext)
}
