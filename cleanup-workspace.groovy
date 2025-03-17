// Clean up workspaces for all jobs or specific jobs
def specificJobs = [] // Leave empty for all jobs, or specify job names like ["job1", "folder/job2"]
def deleteWorkspaceForOfflineNodes = false // Set to true to delete workspaces for offline nodes
def deleteOrphanedWorkspaces = true // Set to true to delete workspaces with no matching jobs
def olderThanDays = 7 // Delete workspaces not used in this many days, -1 to ignore age
def dryRun = true // Set to false to actually delete workspaces

println "🧹 JENKINS WORKSPACE CLEANUP 🧹"
println "==============================="

if (dryRun) {
    println "⚠️ DRY RUN MODE - No workspaces will be deleted"
    println "    Change 'dryRun = true' to 'dryRun = false' to perform actual deletion"
} else {
    println "⚠️ LIVE RUN MODE - WORKSPACES WILL BE PERMANENTLY DELETED"
}

println "Delete workspaces for offline nodes: ${deleteWorkspaceForOfflineNodes}"
println "Delete orphaned workspaces: ${deleteOrphanedWorkspaces}"
println "Delete workspaces older than: ${olderThanDays > 0 ? olderThanDays + ' days' : 'all'}"
println ""

// Function to format size in human-readable format
def formatSize = { size ->
    def units = ['B', 'KB', 'MB', 'GB', 'TB']
    def unitIndex = 0
    def divisor = 1L

    while (size / divisor > 1024 && unitIndex < units.size() - 1) {
        unitIndex++
        divisor *= 1024
    }

    return String.format("%.2f %s", size / divisor, units[unitIndex])
}

// Function to recursively calculate directory size
def calculateDirSize = { dir ->
    def size = 0L
    dir.eachFile { file ->
        if (file.isDirectory()) {
            size += calculateDirSize(file)
        } else {
            size += file.length()
        }
    }
    return size
}

// Function to check if a workspace is older than threshold
def isWorkspaceOld = { workspace, thresholdDays ->
    if (thresholdDays <= 0) {
        return true // Ignore age check
    }

    def now = System.currentTimeMillis()
    def threshold = now - (thresholdDays * 24 * 60 * 60 * 1000L)

    // Check if any file in the workspace is newer than threshold
    def hasRecentFiles = false
    workspace.eachFileRecurse { file ->
        if (file.lastModified() > threshold) {
            hasRecentFiles = true
        }
    }

    return !hasRecentFiles
}

// Get all jobs or specific jobs
def jobs = []
if (specificJobs && specificJobs.size() > 0) {
    specificJobs.each { jobName ->
        def job = Jenkins.instance.getItemByFullName(jobName)
        if (job) {
            jobs.add(job)
        } else {
            println "❌ Job not found: ${jobName}"
        }
    }
} else {
    jobs = Jenkins.instance.getAllItems(hudson.model.Job)
}

println "Total jobs to process: ${jobs.size()}"

// Get all nodes (master and agents)
def nodes = Jenkins.instance.getNodes() + [Jenkins.instance]
println "Total nodes: ${nodes.size()}"

// Track statistics
def totalSpaceFreed = 0L
def totalWorkspacesDeleted = 0
def totalWorkspacesProcessed = 0
def totalOrphanedWorkspaces = 0
def totalOrphanedSpace = 0L

// Keep track of all legitimate workspaces
def legitimateWorkspaces = []

// Process each job
println "\n📋 PROCESSING JOB WORKSPACES..."
jobs.each { job ->
    def jobName = job.fullName

    // Get all workspace locations for this job
    nodes.each { node ->
        if (!deleteWorkspaceForOfflineNodes && node != Jenkins.instance && node.getComputer().isOffline()) {
            return // Skip offline nodes unless configured to clean them
        }

        try {
            def jobWorkspace = node.getWorkspaceFor(job)
            if (jobWorkspace && jobWorkspace.exists()) {
                def workspace = new File(jobWorkspace.remote)
                legitimateWorkspaces << workspace.absolutePath

                def size = calculateDirSize(workspace)
                def isOld = isWorkspaceOld(workspace, olderThanDays)

                if (isOld) {
                    println "Found workspace for ${jobName} on ${node.displayName}:"
                    println "  Path: ${workspace}"
                    println "  Size: ${formatSize(size)}"
                    println "  Last Modified: ${new Date(workspace.lastModified())}"
                    println "  Status: Will delete (unused for more than ${olderThanDays} days)"

                    if (!dryRun) {
                        boolean deleted = workspace.deleteDir()
                        if (deleted) {
                            println "  ✅ Workspace deleted successfully"
                            totalSpaceFreed += size
                            totalWorkspacesDeleted++
                        } else {
                            println "  ❌ Failed to delete workspace"
                        }
                    } else {
                        // In dry run, count as deleted
                        totalSpaceFreed += size
                        totalWorkspacesDeleted++
                    }
                } else {
                    println "Found workspace for ${jobName} on ${node.displayName}:"
                    println "  Path: ${workspace}"
                    println "  Size: ${formatSize(size)}"
                    println "  Last Modified: ${new Date(workspace.lastModified())}"
                    println "  Status: Skipped (used in the last ${olderThanDays} days)"
                }

                totalWorkspacesProcessed++
                println ""
            }
        } catch (Exception e) {
            println "❌ Error processing workspace for ${jobName} on ${node.displayName}: ${e.message}"
        }
    }
}

// Check for orphaned workspaces
if (deleteOrphanedWorkspaces) {
    println "\n📋 CHECKING FOR ORPHANED WORKSPACES..."

    // Get the base workspace directory for each node
    nodes.each { node ->
        if (!deleteWorkspaceForOfflineNodes && node != Jenkins.instance && node.getComputer().isOffline()) {
            return // Skip offline nodes unless configured to clean them
        }

        try {
            def rootPath = node.getRootPath()
            if (rootPath) {
                def workspaceDir = new File(rootPath.child("workspace").remote)
                if (workspaceDir.exists() && workspaceDir.isDirectory()) {
                    workspaceDir.eachDir { dir ->
                        def workspace = dir.absolutePath

                        if (!legitimateWorkspaces.contains(workspace)) {
                            def size = calculateDirSize(dir)
                            def isOld = isWorkspaceOld(dir, olderThanDays)

                            if (isOld) {
                                println "Found orphaned workspace on ${node.displayName}:"
                                println "  Path: ${workspace}"
                                println "  Size: ${formatSize(size)}"
                                println "  Last Modified: ${new Date(dir.lastModified())}"
                                println "  Status: Will delete (orphaned)"

                                if (!dryRun) {
                                    boolean deleted = dir.deleteDir()
                                    if (deleted) {
                                        println "  ✅ Workspace deleted successfully"
                                        totalOrphanedSpace += size
                                        totalOrphanedWorkspaces++
                                    } else {
                                        println "  ❌ Failed to delete workspace"
                                    }
                                } else {
                                    // In dry run, count as deleted
                                    totalOrphanedSpace += size
                                    totalOrphanedWorkspaces++
                                }
                            } else {
                                println "Found orphaned workspace on ${node.displayName}:"
                                println "  Path: ${workspace}"
                                println "  Size: ${formatSize(size)}"
                                println "  Last Modified: ${new Date(dir.lastModified())}"
                                println "  Status: Skipped (used in the last ${olderThanDays} days)"
                            }

                            println ""
                        }
                    }
                }
            }
        } catch (Exception e) {
            println "❌ Error checking orphaned workspaces on ${node.displayName}: ${e.message}"
        }
    }
}

// Print summary
println "\n📊 CLEANUP SUMMARY:"
println "  Total workspaces processed: ${totalWorkspacesProcessed}"
println "  Job workspaces deleted: ${totalWorkspacesDeleted}"
println "  Job workspace space freed: ${formatSize(totalSpaceFreed)}"
println "  Orphaned workspaces deleted: ${totalOrphanedWorkspaces}"
println "  Orphaned workspace space freed: ${formatSize(totalOrphanedSpace)}"
println "  Total space freed: ${formatSize(totalSpaceFreed + totalOrphanedSpace)}"

if (dryRun) {
    println "\nThis was a DRY RUN. Set 'dryRun = false' to perform actual deletion."
}

println "==============================="
