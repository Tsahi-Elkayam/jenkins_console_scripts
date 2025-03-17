// Disconnect and optionally remove offline agents
def disconnectOnly = true // Set to false to completely remove offline agents
def offlineMoreThan = 7 // Agents offline for more than this many days will be processed
def dryRun = true // Set to false to actually disconnect/remove agents

println "🔌 DISCONNECT OFFLINE AGENTS 🔌"
println "==============================="
println "Mode: ${disconnectOnly ? 'Disconnect Only' : 'Complete Removal'}"
println "Threshold: ${offlineMoreThan} days offline"
if (dryRun) {
    println "⚠️ DRY RUN MODE - No agents will be modified"
    println "    Change 'dryRun = true' to 'dryRun = false' to perform actual operations"
}
println ""

def offlineThreshold = System.currentTimeMillis() - (offlineMoreThan * 24 * 60 * 60 * 1000)
def disconnectedCount = 0
def removedCount = 0
def skippedCount = 0
def errorCount = 0

Jenkins.instance.nodes.each { node ->
    def computer = node.toComputer()
    if (computer && !computer.isOnline()) {
        def nodeName = node.getDisplayName()
        def offlineCause = computer.getOfflineCause()
        def offlineTime = computer.getOfflineCauseTime() ?: 0
        def offlineDays = (System.currentTimeMillis() - offlineTime) / (24 * 60 * 60 * 1000)
        
        println "Found offline node: ${nodeName}"
        println "  Offline since: ${offlineTime > 0 ? new Date(offlineTime) : 'Unknown'}"
        println "  Offline for: ${offlineDays.round(2)} days"
        println "  Offline reason: ${offlineCause?.toString() ?: 'Unknown'}"
        
        if (offlineTime > 0 && offlineTime < offlineThreshold) {
            println "  Status: Will ${disconnectOnly ? 'disconnect' : 'remove'} (offline > ${offlineMoreThan} days)"
            
            if (!dryRun) {
                try {
                    if (disconnectOnly) {
                        // Set temporarily offline with a reason
                        computer.setTemporarilyOffline(true, 
                            new hudson.slaves.OfflineCause.ByCLI("Automatically disconnected due to being offline for more than ${offlineMoreThan} days"))
                        disconnectedCount++
                        println "  ✅ Successfully disconnected"
                    } else {
                        // Remove the node completely
                        Jenkins.instance.removeNode(node)
                        removedCount++
                        println "  ✅ Successfully removed"
                    }
                } catch (Exception e) {
                    println "  ❌ Error: ${e.message}"
                    errorCount++
                }
            } else {
                println "  ⚠️ Would ${disconnectOnly ? 'disconnect' : 'remove'} (dry run)"
                if (disconnectOnly) disconnectedCount++ else removedCount++
            }
        } else {
            println "  Status: Skipped (offline < ${offlineMoreThan} days or unknown offline time)"
            skippedCount++
        }
        
        println ""
    }
}

// Print summary
println "📊 SUMMARY:"
println "Total offline nodes evaluated: ${disconnectedCount + removedCount + skippedCount + errorCount}"
if (disconnectOnly) {
    println "Nodes marked for disconnection: ${disconnectedCount}"
} else {
    println "Nodes marked for removal: ${removedCount}"
}
println "Nodes skipped: ${skippedCount}"
println "Errors encountered: ${errorCount}"
println "==============================="