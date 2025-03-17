// Automatically purge old artifacts in Jenkins to free up disk space
def maxArtifactAgeDays = 90  // Artifacts older than this will be deleted
def minSizeMB = 10  // Only consider artifacts larger than this size (MB)
def specificJobs = []  // Leave empty for all jobs, or specify job names like ["job1", "folder/job2"]
def preserveLastBuildsCount = 5  // Always keep artifacts from this many recent builds, regardless of age
def skipSuccessfulBuilds = false  // Set to true to skip artifacts from successful builds
def skipUnstableBuilds = true  // Set to true to skip artifacts from unstable builds
def skipFailedBuilds = false  // Set to true to skip artifacts from failed builds
def dryRun = true  // Set to false to actually delete artifacts

println "🧹 JENKINS ARTIFACT PURGE 🧹"
println "============================"

if (dryRun) {
    println "⚠️ DRY RUN MODE - No artifacts will be deleted"
    println "    Change 'dryRun = true' to 'dryRun = false' to perform actual deletion"
} else {
    println "⚠️ LIVE RUN MODE - ARTIFACTS WILL BE PERMANENTLY DELETED"
}

println "Artifact Age Threshold: ${maxArtifactAgeDays} days"
println "Minimum Size Threshold: ${minSizeMB} MB"
println "Preserve Last Builds: ${preserveLastBuildsCount}"
println "Skip Successful Builds: ${skipSuccessfulBuilds}"
println "Skip Unstable Builds: ${skipUnstableBuilds}"
println "Skip Failed Builds: ${skipFailedBuilds}"
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
    if (!dir.exists() || !dir.isDirectory()) {
        return 0L
    }
    
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

// Calculate the time threshold
def now = System.currentTimeMillis()
def timeThreshold = now - (maxArtifactAgeDays * 24 * 60 * 60 * 1000L)
def minSizeBytes = minSizeMB * 1024 * 1024

// Track statistics
def totalArtifactsChecked = 0
def totalArtifactsDeleted = 0
def totalSpaceFreed = 0L
def jobStats = [:]

// Process each job
jobs.each { job ->
    def jobName = job.fullName
    def displayName = job.displayName ?: jobName
    
    println "\n📋 Processing Job: ${displayName}"
    
    // Get all builds for the job
    def builds = job.builds.toList()
    def totalBuilds = builds.size()
    
    // Skip the most recent N builds
    def buildsToProcess = []
    if (preserveLastBuildsCount > 0 && preserveLastBuildsCount < totalBuilds) {
        buildsToProcess = builds.subList(preserveLastBuildsCount, totalBuilds)
        println "  Preserving ${preserveLastBuildsCount} most recent builds, processing ${buildsToProcess.size()} older builds"
    } else {
        buildsToProcess = builds
        println "  Processing all ${buildsToProcess.size()} builds"
    }
    
    // Track job-specific stats
    def jobArtifactsChecked = 0
    def jobArtifactsDeleted = 0
    def jobSpaceFreed = 0L
    
    // Process each build
    buildsToProcess.each { build ->
        def buildNumber = build.number
        def buildTime = build.getTimeInMillis()
        def buildAge = (now - buildTime) / (24 * 60 * 60 * 1000L)
        def result = build.getResult()
        
        // Check if we should skip based on build result
        def skipBuild = false
        if (skipSuccessfulBuilds && result == hudson.model.Result.SUCCESS) {
            skipBuild = true
        } else if (skipUnstableBuilds && result == hudson.model.Result.UNSTABLE) {
            skipBuild = true
        } else if (skipFailedBuilds && result == hudson.model.Result.FAILURE) {
            skipBuild = true
        }
        
        // Skip if not old enough
        if (buildTime > timeThreshold) {
            skipBuild = true
        }
        
        if (skipBuild) {
            return
        }
        
        // Check build artifacts directory
        def artifactsDir = build.artifactsDir
        if (artifactsDir.exists()) {
            def artifactSize = calculateDirSize(artifactsDir)
            jobArtifactsChecked++
            totalArtifactsChecked++
            
            // Process only if larger than minimum size
            if (artifactSize >= minSizeBytes) {
                println "  Build #${buildNumber} (${new Date(buildTime)}, ${buildAge.round(1)} days old):"
                println "    Artifacts Size: ${formatSize(artifactSize)}"
                println "    Result: ${result}"
                
                if (!dryRun) {
                    try {
                        boolean deleted = artifactsDir.deleteDir()
                        if (deleted) {
                            println "    ✅ Artifacts deleted successfully"
                            jobArtifactsDeleted++
                            totalArtifactsDeleted++
                            jobSpaceFreed += artifactSize
                            totalSpaceFreed += artifactSize
                        } else {
                            println "    ❌ Failed to delete artifacts"
                        }
                    } catch (Exception e) {
                        println "    ❌ Error deleting artifacts: ${e.message}"
                    }
                } else {
                    println "    ⚠️ Would delete (dry run)"
                    jobArtifactsDeleted++
                    totalArtifactsDeleted++
                    jobSpaceFreed += artifactSize
                    totalSpaceFreed += artifactSize
                }
            }
        }
    }
    
    // Update job stats
    jobStats[jobName] = [
        checked: jobArtifactsChecked,
        deleted: jobArtifactsDeleted, 
        spaceFreed: jobSpaceFreed
    ]
    
    println "  Job Summary: Checked ${jobArtifactsChecked} artifact directories, would delete ${jobArtifactsDeleted}, freeing ${formatSize(jobSpaceFreed)}"
}

// Print overall summary
println "\n📊 PURGE SUMMARY:"
println "  Total artifacts checked: ${totalArtifactsChecked}"
println "  Total artifacts ${dryRun ? 'that would be' : ''} deleted: ${totalArtifactsDeleted}"
println "  Total space ${dryRun ? 'that would be' : ''} freed: ${formatSize(totalSpaceFreed)}"

// Print top jobs by space freed
println "\n📊 TOP 10 JOBS BY SPACE FREED:"
jobStats.sort { -it.value.spaceFreed }.take(10).each { jobName, stats ->
    println "  ${jobName}: ${formatSize(stats.spaceFreed)} (${stats.deleted} of ${stats.checked} artifact directories)"
}

// Print recommendations for future management
println "\n📋 ARTIFACT MANAGEMENT RECOMMENDATIONS:"
println "  1. Configure Log Rotation for all jobs:"
println "     def daysToKeep = 30; def numToKeep = 100; def artifactDaysToKeep = 10; def artifactNumToKeep = 10;"
println "     Jenkins.instance.getAllItems(hudson.model.Job).each { job ->"
println "         job.logRotator = new hudson.tasks.LogRotator(daysToKeep, numToKeep, artifactDaysToKeep, artifactNumToKeep)"
println "         job.save()"
println "     }"
println ""
println "  2. Schedule this script to run periodically using Job DSL or a freestyle job with system Groovy script"
println "  3. Consider using Artifact Cleanup Plugin for more granular control"
println "  4. Monitor disk usage with disk-usage-plugin"

if (dryRun) {
    println "\nThis was a DRY RUN. Set 'dryRun = false' to perform actual deletion."
}

println "============================"