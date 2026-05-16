import SwiftUI

/// Toggle apps the assistant should NOT interact with — port of Android `SafetyActivity`.
struct SafetyView: View {
    @State private var observedApps: [String] = []
    @State private var excludedApps: Set<String> = []
    @State private var newBundleId = ""

    var body: some View {
        VStack(spacing: 0) {
            // Description
            Text("Toggle apps the assistant should NOT interact with.")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .padding(.horizontal)
                .padding(.top, 8)

            // Manual add row
            HStack {
                TextField("com.example.app", text: $newBundleId)
                    .textFieldStyle(.roundedBorder)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)

                Button("Exclude") {
                    addExclusion()
                }
                .disabled(newBundleId.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .padding()

            // App list
            List {
                if observedApps.isEmpty {
                    Text("No apps observed yet.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(observedApps, id: \.self) { bundleId in
                        HStack {
                            Text(bundleId)
                                .font(.body)
                            Spacer()
                            let isExcluded = excludedApps.contains(bundleId)
                            Button(isExcluded ? "Allow" : "Block") {
                                toggleExclusion(bundleId)
                            }
                            .buttonStyle(.bordered)
                            .tint(isExcluded ? .green : .red)
                        }
                    }
                }
            }
            .listStyle(.plain)
        }
        .navigationTitle("Safety Settings")
        .onAppear(perform: loadData)
    }

    // MARK: - Actions

    private func loadData() {
        excludedApps = SafetyChecker.getExcludedApps()

        // Get distinct packages from log history
        let logs = PersistenceController.shared.fetchLogs()
        let logPackages = Set(logs.compactMap { $0.packageName })

        // Merge with excluded apps that may not be in logs
        let allApps = logPackages.union(excludedApps)
        observedApps = allApps.sorted()
    }

    private func addExclusion() {
        let trimmed = newBundleId.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        SafetyChecker.addExcludedApp(trimmed)
        newBundleId = ""
        loadData()
    }

    private func toggleExclusion(_ bundleId: String) {
        if excludedApps.contains(bundleId) {
            SafetyChecker.removeExcludedApp(bundleId)
        } else {
            SafetyChecker.addExcludedApp(bundleId)
        }
        loadData()
    }
}

struct SafetyView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationStack {
            SafetyView()
        }
        .environment(\.managedObjectContext, PersistenceController.shared.viewContext)
    }
}
