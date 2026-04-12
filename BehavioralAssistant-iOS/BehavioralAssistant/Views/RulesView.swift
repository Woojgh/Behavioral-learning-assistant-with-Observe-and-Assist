import SwiftUI

/// Manage keyword-based fallback rules — port of Android `RulesActivity`.
struct RulesView: View {
    @State private var rules: [RuleEntity] = []
    @State private var keyword = ""
    @State private var selectedActionType: ActionType = .click

    var body: some View {
        VStack(spacing: 0) {
            // Add rule form
            HStack {
                TextField("Keyword", text: $keyword)
                    .textFieldStyle(.roundedBorder)

                Picker("Action", selection: $selectedActionType) {
                    ForEach(ActionType.allCases, id: \.self) { type in
                        Text(type.rawValue).tag(type)
                    }
                }
                .pickerStyle(.menu)
                .fixedSize()

                Button("Add") {
                    addRule()
                }
                .disabled(keyword.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .padding()

            // Rules list
            List {
                if rules.isEmpty {
                    Text("No rules configured.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(rules, id: \.id) { rule in
                        HStack {
                            Text("\(rule.keyword ?? "") → \(rule.actionType ?? "CLICK")")
                                .font(.body)
                            Spacer()
                        }
                    }
                    .onDelete(perform: deleteRules)
                }
            }
            .listStyle(.plain)
        }
        .navigationTitle("Manage Rules")
        .onAppear(perform: loadRules)
    }

    // MARK: - Actions

    private func loadRules() {
        rules = PersistenceController.shared.fetchRules()
    }

    private func addRule() {
        let trimmed = keyword.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        PersistenceController.shared.insertRule(keyword: trimmed, actionType: selectedActionType.rawValue)
        keyword = ""
        // Reload after a short delay to let the background context save
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            loadRules()
        }
    }

    private func deleteRules(at offsets: IndexSet) {
        for index in offsets {
            PersistenceController.shared.deleteRule(rules[index])
        }
        loadRules()
    }
}

#Preview {
    NavigationStack {
        RulesView()
    }
    .environment(\.managedObjectContext, PersistenceController.shared.viewContext)
}
