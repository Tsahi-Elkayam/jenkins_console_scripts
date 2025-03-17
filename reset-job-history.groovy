// Reset build history for one or more jobs
def jobNames = [] // Leave empty to provide interactive selection, or specify job names like ["job1", "job2/job3"]
def dryRun = true // Set to false to actually delete build history
def preserveLastN = 0 // Set to a positive number to preserve the most recent N builds

println "🧹 JENKINS JOB HISTORY RESET 🧹"
println "==============================="

if (dryRun) {
    println "⚠️ DRY RUN MODE - No builds will be deleted"
    println "    Change 'dryRun = true' to 'dryRun = false' to perform actual deletion"
} else {
    println "⚠️ LIVE RUN MODE - BUILDS WILL BE PERMANENTLY DELETED"
}

println "Preserve last N builds: ${preserveLastN}"
println ""

// Get all jobs in Jenkins
def allJobs = Jenkins.instance.getAllItems(hudson.model.Job)
println "Total jobs in Jenkins: ${allJobs.size()}"

// Function to get jobs to process
def getJobsToProcess = { jobList ->
    def result = []
    
    if (jobList && jobList.size() > 0) {
        // Use provided job names
        jobList.each { jobName ->
            def job = Jenkins.instance.getItemByFullName(jobName)
            if (job != null) {
                result << job
            } else {
                println "❌ Job not found: ${jobName}"
            }
        }
    } else {
        // Interactive selection - list jobs with build count
        def jobsWithBuilds = allJobs.findAll { it.builds.size() > 0 }
        jobsWithBuilds = jobsWithBuilds.sort { a, b -> b.builds.size() <=> a.builds.size() }
        
        println "\n📋 TOP 20 JOBS BY BUILD COUNT:"
        println "=============================="
        jobsWithBuilds.take(20).eachWithIndex { job, index ->
            println "${index + 1}. ${job.fullName} (${job.builds.size()} builds)"
        }
        
        println "\nType a number or job name to reset, or '*' for all jobs with builds."
        println "Leave blank to cancel."
        
        def input = System.console()?.readLine("Enter selection: ")?.trim()
        
        if (!input) {
            return []
        } else if (input == "*") {
            return jobsWithBuilds
        } else if (input.isInteger()) {
            def index = input.toInteger() - 1
            if (index >= 0 && index < jobsWithBuilds.size()) {
                return [jobsWithBuilds[index]]
            } else {
                println "❌ Invalid index: ${input}"
                return []
            }
        } else {
            def job = Jenkins.instance.getItemByFullName(input)
            if (job != null) {
                return [job]
            } else {
                println "❌ Job not found: ${input}"
                return []
            }
        }
    }
    
    return result
}

// Get jobs to process
def jobsToProcess = getJobsToProcess(jobNames)

if (jobsToProcess.isEmpty()) {
    println "No jobs selected. Exiting."
    return
}

// Process each job
def totalBuildsDeleted = 0
def totalJobsProcessed = 0

jobsToProcess.each { job ->
    def buildCount = job.builds.size()
    println "\n📋 Processing Job: ${job.fullName}"
    println "  Total builds: ${buildCount}"
    
    if (buildCount == 0) {
        println "  No builds to delete."
        return
    }
    
    def buildsToDelete = []
    def buildsToPreserve = []
    
    if (preserveLastN > 0 && preserveLastN < buildCount) {
        def allBuilds = job.builds.toList()
        buildsToPreserve = allBuilds.take(preserveLastN)
        buildsToDelete = allBuilds.drop(preserveLastN)
        
        println "  Will preserve ${buildsToPreserve.size()} recent builds:"
        buildsToPreserve.each { build ->
            println "    - #${build.number} (${new Date(build.getTimeInMillis())})"
        }
    } else {
        buildsToDelete = job.builds.toList()
    }
    
    println "  Will delete ${buildsToDelete.size()} builds"
    
    if (!dryRun) {
        def deletedCount = 0
        buildsToDelete.each { build ->
            try {
                build.delete()
                deletedCount++
                if (deletedCount % 10 == 0) {
                    println "    ... deleted ${deletedCount}/${buildsToDelete.size()} builds"
                }
            } catch (Exception e) {
                println "    ❌ Error deleting build #${build.number}: ${e.message}"
            }
        }
        
        println "  ✅ Deleted ${deletedCount}/${buildsToDelete.size()} builds"
        totalBuildsDeleted += deletedCount
    } else {
        // In dry run, pretend we deleted everything
        totalBuildsDeleted += buildsToDelete.size()
    }
    
    totalJobsProcessed++
}

// Print summary
println "\n📊 SUMMARY:"
println "  Jobs processed: ${totalJobsProcessed}"
println "  Builds ${dryRun ? 'would be' : 'were'} deleted: ${totalBuildsDeleted}"
if (dryRun) {
    println "\nThis was a DRY RUN. Set 'dryRun = false' to perform actual deletion."
}

println "==============================="