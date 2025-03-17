// Find and optionally abort builds that have been running for too long
def hourThreshold = 1 // Consider builds running longer than this many hours as "long-running"
def abortThreshold = 24 // Automatically abort builds running longer than this many hours (set to -1 to disable auto-abort)
def dryRun = true // Set to false to actually abort builds

println "⏱️ LONG-RUNNING BUILDS DETECTOR ⏱️"
println "=================================="
println "Threshold for reporting: ${hourThreshold} hours"
if (abortThreshold > 0) {
    println "Threshold for aborting: ${abortThreshold} hours"
    if (dryRun) {
        println "⚠️ DRY RUN MODE - No builds will be aborted"
        println "    Change 'dryRun = true' to 'dryRun = false' to perform actual termination"
    }
}
println ""

def currentTimeMillis = System.currentTimeMillis()
def reportThresholdMillis = hourThreshold * 60 * 60 * 1000
def abortThresholdMillis = abortThreshold * 60 * 60 * 1000

def longRunningBuilds = []
def excessivelyLongBuilds = []

// Find all running builds
Jenkins.instance.getAllItems(hudson.model.Job).each { job ->
    job.builds.findAll { build -> build.isBuilding() }.each { build ->
        def duration = currentTimeMillis - build.getStartTimeInMillis()
        def durationHours = duration / (1000 * 60 * 60)

        if (duration > reportThresholdMillis) {
            def buildInfo = [
                job: job.fullName,
                build: build.number,
                url: "${Jenkins.instance.rootUrl}${build.url}",
                startTime: new Date(build.getStartTimeInMillis()),
                duration: durationHours.round(2),
                executor: build.getExecutor()?.getOwner()?.getName() ?: "Unknown"
            ]

            longRunningBuilds << buildInfo

            if (abortThreshold > 0 && duration > abortThresholdMillis) {
                excessivelyLongBuilds << buildInfo
            }
        }
    }
}

// Sort builds by duration
longRunningBuilds.sort { -it.duration }

// Print long running builds
if (longRunningBuilds.size() > 0) {
    println "📋 LONG-RUNNING BUILDS (> ${hourThreshold} hours):"
    println "===================================================="
    longRunningBuilds.each { build ->
        println "Job: ${build.job}"
        println "  Build #${build.build}"
        println "  Running for: ${build.duration} hours"
        println "  Started at: ${build.startTime}"
        println "  Running on: ${build.executor}"
        println "  URL: ${build.url}"
        println ""
    }
} else {
    println "✅ No long-running builds found."
}

// Abort excessively long builds if requested
if (abortThreshold > 0 && excessivelyLongBuilds.size() > 0) {
    println "🛑 EXCESSIVELY LONG BUILDS (> ${abortThreshold} hours):"
    println "===================================================="

    excessivelyLongBuilds.each { build ->
        println "Job: ${build.job}"
        println "  Build #${build.build}"
        println "  Running for: ${build.duration} hours"

        def jobObj = Jenkins.instance.getItemByFullName(build.job)
        def buildObj = jobObj?.getBuildByNumber(build.build)

        if (!dryRun && buildObj != null) {
            try {
                println "  ⚠️ ABORTING BUILD"
                buildObj.doStop()
                println "  ✅ Build aborted successfully"
            } catch (Exception e) {
                println "  ❌ Failed to abort: ${e.message}"
            }
        } else if (dryRun) {
            println "  ⚠️ Would abort (dry run)"
        }
        println ""
    }
}

println "📊 SUMMARY:"
println "Total running builds: ${Jenkins.instance.getAllItems(hudson.model.Job).sum { job -> job.builds.count { it.isBuilding() } } ?: 0}"
println "Long-running builds (> ${hourThreshold}h): ${longRunningBuilds.size()}"
println "Excessively long builds (> ${abortThreshold}h): ${excessivelyLongBuilds.size()}"
println "=================================="
