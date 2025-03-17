// Cancel all builds in the queue or only specific ones
def cancelAll = false // Set to true to cancel ALL queued builds
def olderThanMinutes = 60 // Cancel builds queued for longer than this many minutes
def specificJobsToCancel = [] // List of job names to cancel, leave empty to use time-based cancellation

println "❌ CANCEL QUEUED BUILDS ❌"
println "========================="

def queueItems = Jenkins.instance.queue.items
def totalQueued = queueItems.size()

if (totalQueued == 0) {
    println "Queue is empty. Nothing to cancel."
    return
}

println "Total builds in queue: ${totalQueued}"

if (cancelAll) {
    println "\nCanceling ALL queued builds..."
} else if (!specificJobsToCancel.isEmpty()) {
    println "\nCanceling specific jobs: ${specificJobsToCancel}"
} else {
    println "\nCanceling builds queued for more than ${olderThanMinutes} minutes..."
}

def cancelCount = 0
def threshold = System.currentTimeMillis() - (olderThanMinutes * 60 * 1000)

queueItems.each { item ->
    def name = item.task.name
    def fullName = item.task.getFullDisplayName()
    def queueTime = System.currentTimeMillis() - item.getInQueueSince()
    def queueMinutes = (queueTime / 60000).round(2)
    
    def shouldCancel = false
    if (cancelAll) {
        shouldCancel = true
    } else if (!specificJobsToCancel.isEmpty()) {
        shouldCancel = specificJobsToCancel.any { jobName -> 
            fullName.contains(jobName) || name.contains(jobName)
        }
    } else {
        shouldCancel = item.getInQueueSince() < threshold
    }
    
    if (shouldCancel) {
        println "Canceling: ${fullName}"
        println "  In queue for: ${queueMinutes} minutes"
        println "  Cause: ${item.getCauses()[0]?.getShortDescription() ?: 'Unknown'}"
        
        try {
            Jenkins.instance.queue.cancel(item)
            cancelCount++
        } catch (Exception e) {
            println "  ❌ Failed to cancel: ${e.message}"
        }
    } else {
        println "Skipping: ${fullName} (${queueMinutes} minutes in queue)"
    }
}

println "\n📊 SUMMARY:"
println "Total builds in queue: ${totalQueued}"
println "Builds canceled: ${cancelCount}"
println "Builds remaining in queue: ${totalQueued - cancelCount}"
println "========================="