import CoreData
import Foundation

/// Core Data persistence controller — replaces Android Room `DatabaseHelper`.
/// Provides the shared `NSPersistentContainer` and all CRUD helpers.
final class PersistenceController {
    static let shared = PersistenceController()

    let container: NSPersistentContainer

    init(inMemory: Bool = false) {
        container = NSPersistentContainer(name: "BehavioralAssistant")
        if inMemory {
            container.persistentStoreDescriptions.first?.url = URL(fileURLWithPath: "/dev/null")
        }
        container.loadPersistentStores { _, error in
            if let error = error as NSError? {
                fatalError("Core Data store failed to load: \(error), \(error.userInfo)")
            }
        }
        container.viewContext.automaticallyMergesChangesFromParent = true
    }

    var viewContext: NSManagedObjectContext { container.viewContext }

    /// Perform work on a background context and save.
    func performBackground(_ block: @escaping (NSManagedObjectContext) -> Void) {
        let context = container.newBackgroundContext()
        context.perform {
            block(context)
            if context.hasChanges {
                try? context.save()
            }
        }
    }

    // MARK: - Log Helpers

    func logAction(packageName: String, state: String, actionType: String, actionDetail: String, success: Bool) {
        performBackground { ctx in
            let log = LogEntity(context: ctx)
            log.id = UUID()
            log.timestamp = Date()
            log.packageName = packageName
            log.state = state
            log.actionType = actionType
            log.actionDetail = actionDetail
            log.success = success
        }
    }

    func fetchLogs() -> [LogEntity] {
        let request: NSFetchRequest<LogEntity> = LogEntity.fetchRequest()
        request.sortDescriptors = [NSSortDescriptor(key: "timestamp", ascending: false)]
        return (try? viewContext.fetch(request)) ?? []
    }

    func clearLogs() {
        performBackground { ctx in
            let request: NSFetchRequest<NSFetchRequestResult> = LogEntity.fetchRequest()
            let batchDelete = NSBatchDeleteRequest(fetchRequest: request)
            try? ctx.execute(batchDelete)
        }
    }

    // MARK: - Rule Helpers

    func fetchRules() -> [RuleEntity] {
        let request: NSFetchRequest<RuleEntity> = RuleEntity.fetchRequest()
        return (try? viewContext.fetch(request)) ?? []
    }

    func fetchEnabledRules() -> [RuleEntity] {
        let request: NSFetchRequest<RuleEntity> = RuleEntity.fetchRequest()
        request.predicate = NSPredicate(format: "enabled == YES")
        return (try? viewContext.fetch(request)) ?? []
    }

    func insertRule(keyword: String, actionType: String) {
        performBackground { ctx in
            let rule = RuleEntity(context: ctx)
            rule.id = UUID()
            rule.keyword = keyword
            rule.actionType = actionType
            rule.enabled = true
        }
    }

    func deleteRule(_ rule: RuleEntity) {
        let ctx = viewContext
        ctx.delete(rule)
        try? ctx.save()
    }

    func rulesCount() -> Int {
        let request: NSFetchRequest<RuleEntity> = RuleEntity.fetchRequest()
        return (try? viewContext.count(for: request)) ?? 0
    }

    func seedDefaultRules() {
        guard rulesCount() == 0 else { return }
        let defaults = [
            ("skip", "CLICK"),
            ("allow", "CLICK"),
            ("ok", "CLICK"),
            ("accept", "CLICK"),
            ("continue", "CLICK"),
            ("dismiss", "CLICK")
        ]
        performBackground { ctx in
            for (kw, at) in defaults {
                let rule = RuleEntity(context: ctx)
                rule.id = UUID()
                rule.keyword = kw
                rule.actionType = at
                rule.enabled = true
            }
        }
    }

    // MARK: - Pattern Helpers

    func fetchTopPattern(forState state: String) -> UserPatternEntity? {
        let request: NSFetchRequest<UserPatternEntity> = UserPatternEntity.fetchRequest()
        request.predicate = NSPredicate(format: "state == %@", state)
        request.sortDescriptors = [NSSortDescriptor(key: "count", ascending: false)]
        request.fetchLimit = 1
        return (try? viewContext.fetch(request))?.first
    }

    func fetchPatterns(forState state: String) -> [UserPatternEntity] {
        let request: NSFetchRequest<UserPatternEntity> = UserPatternEntity.fetchRequest()
        request.predicate = NSPredicate(format: "state == %@", state)
        request.sortDescriptors = [NSSortDescriptor(key: "count", ascending: false)]
        return (try? viewContext.fetch(request)) ?? []
    }

    func recordPattern(state: String, packageName: String, actionText: String, actionType: String) {
        performBackground { ctx in
            let request: NSFetchRequest<UserPatternEntity> = UserPatternEntity.fetchRequest()
            request.predicate = NSPredicate(format: "state == %@ AND actionText == %@", state, actionText)
            request.fetchLimit = 1

            if let existing = (try? ctx.fetch(request))?.first {
                existing.count += 1
                existing.lastSeen = Date()
            } else {
                let pattern = UserPatternEntity(context: ctx)
                pattern.id = UUID()
                pattern.state = state
                pattern.packageName = packageName
                pattern.actionText = actionText
                pattern.actionType = actionType
                pattern.count = 1
                pattern.lastSeen = Date()
            }
        }
    }

    func totalPatterns() -> Int {
        let request: NSFetchRequest<UserPatternEntity> = UserPatternEntity.fetchRequest()
        return (try? viewContext.count(for: request)) ?? 0
    }

    func distinctApps() -> Int {
        let request: NSFetchRequest<NSDictionary> = NSFetchRequest(entityName: "UserPatternEntity")
        request.resultType = .dictionaryResultType
        request.propertiesToFetch = ["packageName"]
        request.returnsDistinctResults = true
        return (try? viewContext.count(for: request as! NSFetchRequest<NSFetchRequestResult>)) ?? 0
    }
}
