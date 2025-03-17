// Delete jobs that haven't been built in a specified period
def daysThreshold = 180 // Change this to your desired threshold (180 days = 6 months)
def threshold = System.currentTimeMillis() - (daysThreshold * 24 * 60 * 60 * 1000L)
def dryRun = true // Set to false to actually delete the jobs

println "🗑️ DELETE OLD JOBS REPORT 🗑️"
println "============================"
println "Identifying jobs not built in the last ${daysThreshold} days"
if (dryRun) {
    println "⚠️ DRY RUN MODE - No jobs will be deleted"
    println "    Change 'dryRun = true' to 'dryRun = false' to perform actual deletion"
}
println ""

def jobs = Jenkins.instance.getAllItems(hudson.model.Job)
def deleteCount = 0
def ignoredCount = 0
def errorCount = 0
def jobsToDelete = []

jobs.each { job ->
    try {
        def lastBuild = job.getLastBuild()
        def lastBuildTime = lastBuild?.getTimeInMillis() ?: 0
        
        if (lastBuildTime < threshold || lastBuildTime == 0) {
            def daysSinceLastBuild = lastBuildTime > 0 ? 
                ((System.currentTimeMillis() - lastBuildTime) / (1000 * 60 * 60 * 24)).round() : 
                "Never built"
                
            println "Found old job: ${job.fullName}"
            println "  Last Build: ${lastBuild?.getDisplayName() ?: 'Never'}"
            println "  Days Since Last Build: ${daysSinceLastBuild}"
            println "  URL: ${Jenkins.instance.rootUrl}${job.url}"
            
            jobsToDelete << job
        } else {
            ignoredCount++
        }
    } catch (Exception e) {
        println "❌ Error processing ${job.fullName}: ${e.message}"
        errorCount++
    }
}

// Sorting jobs by name for a more organized deletion
jobsToDelete.sort { it.fullName }

if (!dryRun) {
    println "\n🚫 DELETING JOBS"
    println "==============="
    
    jobsToDelete.each { job ->
        try {
            println "Deleting: ${job.fullName}"
            job.delete()
            deleteCount++
        } catch (Exception e) {
            println "❌ Error deleting ${job.fullName}: ${e.message}"
            errorCount++
        }
    }
} else {
    deleteCount = jobsToDelete.size()
}

println "\n📊 SUMMARY:"
println "Total jobs analyzed: ${jobs.size()}"
if (dryRun) {
    println "Jobs that would be deleted: ${deleteCount}"
} else {
    println "Jobs successfully deleted: ${deleteCount}"
}
println "Jobs within threshold: ${ignoredCount}"
println "Errors encountered: ${errorCount}"
println "============================"