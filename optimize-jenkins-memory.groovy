// Jenkins memory optimization script
println "🧹 JENKINS MEMORY OPTIMIZATION 🧹"
println "================================="

// Force garbage collection
println "\n♻️ Triggering garbage collection..."
System.gc()
println "Garbage collection triggered."

// Calculate memory usage after GC
def rt = Runtime.getRuntime()
def usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024
def totalMB = rt.totalMemory() / 1024 / 1024
def maxMB = rt.maxMemory() / 1024 / 1024
println "Current Memory Usage: ${usedMB.round(2)} MB / ${maxMB.round(2)} MB (${(usedMB / maxMB * 100).round(2)}%)"

// Clear build queue if it's too large
def queueSize = Jenkins.instance.queue.items.size()
if (queueSize > 100) {
    println "\n♻️ Clearing excessive queued builds..."
    def oldestItems = Jenkins.instance.queue.items.sort { it.getInQueueSince() }.take(queueSize - 50)
    oldestItems.each {
        println "Canceling: ${it.task.name} (queued for ${(System.currentTimeMillis() - it.getInQueueSince()) / 60000} minutes)"
        Jenkins.instance.queue.cancel(it)
    }
    println "Cleared ${oldestItems.size()} queued builds."
}

// Kill hung builds (running for more than 12 hours)
println "\n♻️ Checking for hung builds..."
def threshold = 12 * 60 * 60 * 1000 // 12 hours
def hungBuilds = 0
Jenkins.instance.getAllItems(hudson.model.Job).each { job ->
    job.builds.findAll { build -> 
        build.isBuilding() && (System.currentTimeMillis() - build.getStartTimeInMillis() > threshold)
    }.each { build ->
        println "Terminating hung build: ${build.fullDisplayName} (running for ${(System.currentTimeMillis() - build.getStartTimeInMillis()) / 3600000} hours)"
        build.doStop()
        hungBuilds++
    }
}
println "Terminated ${hungBuilds} hung builds."

// Clear build history for large jobs
println "\n♻️ Trimming build history for large jobs..."
def maxBuildsToKeep = 50
def trimmedJobs = 0
Jenkins.instance.getAllItems(hudson.model.Job).each { job ->
    def buildCount = job.getBuilds().size()
    if (buildCount > maxBuildsToKeep) {
        println "Trimming ${job.fullName}: ${buildCount} builds -> ${maxBuildsToKeep} builds"
        def buildsToRemove = job.getBuilds().iterator()
        def counter = 0
        while (buildsToRemove.hasNext() && counter < (buildCount - maxBuildsToKeep)) {
            def build = buildsToRemove.next()
            build.delete()
            counter++
        }
        trimmedJobs++
    }
}
println "Trimmed build history for ${trimmedJobs} jobs."

// Remove old workspaces
println "\n♻️ Checking for stale workspaces..."
def workspaceDir = new File(Jenkins.instance.root.getPath() + "/workspace")
def deletedWorkspaces = 0
if (workspaceDir.exists()) {
    workspaceDir.listFiles()?.each { dir ->
        if (!Jenkins.instance.getAllItems(hudson.model.Job).find { it.fullName.replace('/', '-') == dir.name }) {
            println "Removing stale workspace: ${dir.name}"
            if (dir.deleteDir()) {
                deletedWorkspaces++
            } else {
                println "Failed to delete ${dir.name}"
            }
        }
    }
}
println "Removed ${deletedWorkspaces} stale workspaces."

// Clear fingerprint database older than 1 year
println "\n♻️ Cleaning up old fingerprints..."
def fingerprintThreshold = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000
def fingerprintCount = 0
Jenkins.instance.fingerprintMap.each { id, fingerprint ->
    if (fingerprint.getTimestamp() < fingerprintThreshold) {
        Jenkins.instance.fingerprintMap.remove(id)
        fingerprintCount++
    }
}
println "Removed ${fingerprintCount} old fingerprints."

// Final memory usage check
System.gc()
usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024
println "\n✅ OPTIMIZATION COMPLETE"
println "Memory Usage After Optimization: ${usedMB.round(2)} MB / ${maxMB.round(2)} MB (${(usedMB / maxMB * 100).round(2)}%)"
println "================================="