// List all jobs and their last build status with additional metrics
println "📋 JENKINS JOBS STATUS REPORT 📋"
println "================================"

def jobs = Jenkins.instance.getAllItems(hudson.model.Job)
def totalJobs = jobs.size()
def activeJobs = 0
def disabledJobs = 0
def successfulJobs = 0
def failingJobs = 0
def unstableJobs = 0
def jobsNeverRun = 0

println "\nJOB STATUS SUMMARY:"
println "================="

jobs.each { job ->
    def lastBuild = job.getLastBuild()
    def lastSuccess = job.getLastSuccessfulBuild()
    def disabled = false
    
    // Check if job is disabled
    if (job.hasProperty('disabled')) {
        disabled = job.disabled
    }
    
    // Count job status
    if (disabled) {
        disabledJobs++
        println "${job.fullName}"
        println "  Status: DISABLED"
        println "  Last Build: ${lastBuild?.getDisplayName() ?: 'Never'}"
        if (lastBuild) {
            println "  Last Build Result: ${lastBuild.result ?: 'IN PROGRESS'}"
            println "  Last Build Time: ${new Date(lastBuild.getTimeInMillis())}"
        }
    } else {
        activeJobs++
        if (lastBuild == null) {
            jobsNeverRun++
            println "${job.fullName}"
            println "  Status: ACTIVE (NEVER RUN)"
        } else {
            def result = lastBuild.result
            if (result == hudson.model.Result.SUCCESS) {
                successfulJobs++
                println "${job.fullName}"
                println "  Status: SUCCESSFUL"
                println "  Last Build: ${lastBuild.getDisplayName()}"
                println "  Last Build Time: ${new Date(lastBuild.getTimeInMillis())}"
            } else if (result == hudson.model.Result.FAILURE) {
                failingJobs++
                println "${job.fullName}"
                println "  Status: FAILING"
                println "  Last Build: ${lastBuild.getDisplayName()}"
                println "  Last Build Time: ${new Date(lastBuild.getTimeInMillis())}"
                println "  Last Success: ${lastSuccess?.getDisplayName() ?: 'Never'}"
                if (lastSuccess) {
                    println "  Last Success Time: ${new Date(lastSuccess.getTimeInMillis())}"
                    def failureDuration = System.currentTimeMillis() - lastSuccess.getTimeInMillis()
                    println "  Failing for: ${(failureDuration / (1000 * 60 * 60 * 24)).round()} days"
                }
            } else if (result == hudson.model.Result.UNSTABLE) {
                unstableJobs++
                println "${job.fullName}"
                println "  Status: UNSTABLE"
                println "  Last Build: ${lastBuild.getDisplayName()}"
                println "  Last Build Time: ${new Date(lastBuild.getTimeInMillis())}"
                println "  Last Success: ${lastSuccess?.getDisplayName() ?: 'Never'}"
            } else if (lastBuild.isBuilding()) {
                println "${job.fullName}"
                println "  Status: BUILDING"
                println "  Current Build: ${lastBuild.getDisplayName()}"
                println "  Started: ${new Date(lastBuild.getTimeInMillis())}"
                println "  Duration so far: ${(System.currentTimeMillis() - lastBuild.getTimeInMillis()) / 1000 / 60} minutes"
            }
        }
    }
    println "  URL: ${Jenkins.instance.rootUrl}${job.url}"
    println ""
}

// Print summary statistics
println "\n📊 STATISTICS SUMMARY"
println "Total Jobs: ${totalJobs}"
println "Active Jobs: ${activeJobs} (${(activeJobs / totalJobs * 100).round()}%)"
println "Disabled Jobs: ${disabledJobs} (${(disabledJobs / totalJobs * 100).round()}%)"
println "Successful Jobs: ${successfulJobs} (${(successfulJobs / totalJobs * 100).round()}%)"
println "Failing Jobs: ${failingJobs} (${(failingJobs / totalJobs * 100).round()}%)"
println "Unstable Jobs: ${unstableJobs} (${(unstableJobs / totalJobs * 100).round()}%)"
println "Never Run Jobs: ${jobsNeverRun} (${(jobsNeverRun / totalJobs * 100).round()}%)"

println "\n================================"