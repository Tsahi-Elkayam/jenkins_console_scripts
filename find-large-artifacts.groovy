// Find large or old artifacts in Jenkins
def minSizeMB = 100  // Minimum size in MB to report
def olderThanDays = -1  // Only show artifacts older than this many days (-1 to ignore age)
def checkAllJobs = true  // Set to false to only check jobs with known issues
def specificJobsToCheck = []  // Only used if checkAllJobs is false, e.g. ["folder/job1", "job2"]
def maxResults = 100  // Maximum number of artifacts to report
def sortBySize = true  // true to sort by size (largest first), false to sort by age (oldest first)
def includeLogFiles = true  // Whether to include build logs in the analysis
def dryRun = true  // Set to false to enable deletion options

println "🔍 JENKINS LARGE ARTIFACT FINDER 🔍"
println "==================================="

if (!dryRun) {
    println "⚠️ DELETION MODE ENABLED - You will be able to delete artifacts"
} else {
    println "ℹ️ READ-ONLY MODE - Set dryRun = false to enable deletion"
}

println "Minimum Size: ${minSizeMB} MB"
println "Age Filter: ${olderThanDays > 0 ? "Older than ${olderThanDays} days" : "Any age"}"
println "Sort By: ${sortBySize ? "Size (largest first)" : "Age (oldest first)"}"
println "Maximum Results: ${maxResults}"
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
if (checkAllJobs) {
    jobs = Jenkins.instance.getAllItems(hudson.model.Job)
} else {
    specificJobsToCheck.each { jobName ->
        def job = Jenkins.instance.getItemByFullName(jobName)
        if (job) {
            jobs.add(job)
        } else {
            println "❌ Job not found: ${jobName}"
        }
    }
}

println "Total jobs to analyze: ${jobs.size()}"

// Calculate the time threshold
def now = System.currentTimeMillis()
def timeThreshold = olderThanDays > 0 ? now - (olderThanDays * 24 * 60 * 60 * 1000L) : 0

// Track all large artifacts
def largeArtifacts = []

// Process each job
println "\n🔍 ANALYZING JOBS FOR LARGE ARTIFACTS..."

jobs.each { job ->
    def jobName = job.fullName
    def displayName = job.displayName ?: jobName
    
    // Process each build
    job.builds.each { build ->
        def buildNumber = build.number
        def buildTime = build.getTimeInMillis()
        def buildAge = (now - buildTime) / (24 * 60 * 60 * 1000L)
        
        // Skip if not old enough
        if (olderThanDays > 0 && buildTime > timeThreshold) {
            return
        }
        
        // Check build artifacts directory
        def artifactsDir = build.artifactsDir
        if (artifactsDir.exists()) {
            def totalSize = calculateDirSize(artifactsDir)
            def sizeInMB = totalSize / (1024 * 1024)
            
            // Add to list if large enough
            if (sizeInMB >= minSizeMB) {
                largeArtifacts << [
                    job: displayName,
                    jobName: jobName,
                    build: buildNumber,
                    directory: artifactsDir,
                    size: totalSize,
                    time: buildTime,
                    age: buildAge,
                    type: "Artifacts"
                ]
            }
            
            // Check individual artifacts to identify specific large files
            artifactsDir.eachFileRecurse { file ->
                if (!file.isDirectory()) {
                    def fileSize = file.length()
                    def fileSizeInMB = fileSize / (1024 * 1024)
                    
                    if (fileSizeInMB >= minSizeMB) {
                        def relativePath = file.absolutePath.substring(artifactsDir.absolutePath.length() + 1)
                        largeArtifacts << [
                            job: displayName,
                            jobName: jobName,
                            build: buildNumber,
                            file: file,
                            relativePath: relativePath,
                            size: fileSize,
                            time: buildTime,
                            age: buildAge,
                            type: "File"
                        ]
                    }
                }
            }
        }
        
        // Check log files if enabled
        if (includeLogFiles) {
            def logFile = build.logFile
            if (logFile.exists()) {
                def logSize = logFile.length()
                def logSizeInMB = logSize / (1024 * 1024)
                
                if (logSizeInMB >= minSizeMB) {
                    largeArtifacts << [
                        job: displayName,
                        jobName: jobName,
                        build: buildNumber,
                        file: logFile,
                        size: logSize,
                        time: buildTime,
                        age: buildAge,
                        type: "Log"
                    ]
                }
            }
        }
    }
}

// Sort the results
if (sortBySize) {
    largeArtifacts.sort { -it.size }
} else {
    largeArtifacts.sort { it.time }
}

// Display the results
if (largeArtifacts.size() > 0) {
    println "\n📋 FOUND ${largeArtifacts.size()} LARGE ARTIFACTS:"
    println "=============================================="
    
    largeArtifacts.take(maxResults).eachWithIndex { artifact, index ->
        println "${index + 1}. ${artifact.job} #${artifact.build} (${artifact.type})"
        println "   Size: ${formatSize(artifact.size)}"
        println "   Age: ${artifact.age.round(2)} days (${new Date(artifact.time)})"
        
        if (artifact.type == "Artifacts") {
            println "   Directory: ${artifact.directory}"
        } else {
            println "   File: ${artifact.file}${artifact.relativePath ? " (${artifact.relativePath})" : ""}"
        }
        
        if (!dryRun) {
            println "   Options: [1] Delete  [2] Skip"
            def input = System.console()?.readLine("   Enter option: ")?.trim()
            
            if (input == "1") {
                try {
                    if (artifact.type == "Artifacts") {
                        boolean deleted = artifact.directory.deleteDir()
                        println "   ${deleted ? "✅ Deleted successfully" : "❌ Failed to delete"}"
                    } else {
                        boolean deleted = artifact.file.delete()
                        println "   ${deleted ? "✅ Deleted successfully" : "❌ Failed to delete"}"
                    }
                } catch (Exception e) {
                    println "   ❌ Error during deletion: ${e.message}"
                }
            } else {
                println "   Skipped"
            }
        }
        
        println ""
    }
    
    if (largeArtifacts.size() > maxResults) {
        println "... and ${largeArtifacts.size() - maxResults} more (increase maxResults to see more)"
    }
} else {
    println "\n✅ No artifacts found matching the criteria."
}

// Group artifacts by job for summary
def jobSizes = [:]
largeArtifacts.each { artifact ->
    def key = artifact.jobName
    jobSizes[key] = (jobSizes[key] ?: 0) + artifact.size
}

// Display summary by job
println "\n📊 SUMMARY BY JOB:"
println "================="
jobSizes.sort { -it.value }.each { jobName, size ->
    println "${jobName}: ${formatSize(size)}"
}

// Provide cleanup recommendations
println "\n📋 CLEANUP RECOMMENDATIONS:"
println "========================="
println "1. For managing logs:"
println "   - Configure log rotation in jobs: Job > Configure > Discard old builds"
println "   - Recommended settings: Days to keep: 30, Max # of builds: 100"
println ""
println "2. For managing artifacts:"
println "   - Configure artifact retention policy: Job > Configure > Discard old builds > Advanced"
println "   - Use 'Days to keep artifacts' and 'Max # of builds to keep with artifacts'"
println ""
println "3. Batch cleaning script (example):"
println """
   def maxArtifactAgeDays = 90
   def ageThreshold = System.currentTimeMillis() - (maxArtifactAgeDays * 24 * 60 * 60 * 1000L)
   Jenkins.instance.getAllItems(hudson.model.Job).each { job ->
       job.builds.each { build ->
           if (build.getTimeInMillis() < ageThreshold && build.artifactsDir.exists()) {
               println "Deleting artifacts for \${job.fullName} #\${build.number}"
               build.artifactsDir.deleteDir()
           }
       }
   }
"""

if (dryRun) {
    println "\nTo enable interactive deletion, set dryRun = false"
}

println "==================================="