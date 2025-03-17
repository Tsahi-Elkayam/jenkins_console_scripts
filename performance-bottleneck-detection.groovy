// Identify performance bottlenecks
println "🔍 JENKINS PERFORMANCE BOTTLENECK ANALYSIS 🔍"
println "============================================="

// Check build time distribution
println "\n📊 BUILD TIME DISTRIBUTION"
def jobs = Jenkins.instance.getAllItems(hudson.model.Job)
def jobBuildTimes = [:]

jobs.each { job ->
    def builds = job.getBuilds().limit(10)
    builds.each { build ->
        if (build.getDuration() > 0) {
            if (!jobBuildTimes[job.fullName]) {
                jobBuildTimes[job.fullName] = []
            }
            jobBuildTimes[job.fullName] << build.getDuration()
        }
    }
}

// Find slowest jobs
def slowestJobs = jobBuildTimes.collectEntries { name, times ->
    [(name): times.sum() / times.size()]
}.sort { -it.value }

println "Top 10 Slowest Jobs (Average Duration in ms):"
slowestJobs.take(10).each { name, avgTime ->
    println "${name}: ${avgTime.toLong()} ms"
}

// Check for agents with slow connection
println "\n📊 AGENT CONNECTION SPEED"
def agents = Jenkins.instance.getComputers()
println "Agent Round Trip Times:"
agents.each { agent ->
    if (agent.node != Jenkins.instance.nodes.find { it.nodeName == "master" }) {
        def roundTrip = agent.getChannel()?.ping() ?: -1
        println "${agent.name}: ${roundTrip} ms"
        if (roundTrip > 100) {
            println "⚠️ WARNING: Agent ${agent.name} has high latency (${roundTrip} ms)"
        }
    }
}

// Check concurrent builds
println "\n📊 CONCURRENT BUILD ANALYSIS"
def currentBuilds = Jenkins.instance.getComputers().collectMany { it.getExecutors().findAll { exec -> exec.isBusy() } }
println "Current Active Builds: ${currentBuilds.size()}"

// Check frequently failing jobs
println "\n📊 BUILD STABILITY ANALYSIS"
def unstableJobs = []
jobs.each { job ->
    def builds = job.getBuilds().limit(10).toList()
    if (builds.size() > 0) {
        def failCount = builds.count { build -> build.getResult() != hudson.model.Result.SUCCESS }
        def failRate = failCount / builds.size()
        if (failRate > 0.3) {
            unstableJobs << [name: job.fullName, failRate: failRate]
        }
    }
}

println "Unstable Jobs (Failure Rate > 30%):"
unstableJobs.sort { -it.failRate }.each { job ->
    println "${job.name}: ${(job.failRate * 100).round()}% failure rate"
}

// Check average queue time
println "\n📊 BUILD QUEUE ANALYSIS"
def queueStats = []
Jenkins.instance.queue.getItems().each { item ->
    def inQueueFor = System.currentTimeMillis() - item.getInQueueSince()
    queueStats << [job: item.task.name, time: inQueueFor]
}

if (queueStats.size() > 0) {
    def avgQueueTime = queueStats.sum { it.time } / queueStats.size()
    println "Average Queue Time: ${(avgQueueTime / 1000).round()} seconds"
    if (avgQueueTime > 300000) {
        println "⚠️ WARNING: Average queue time exceeds 5 minutes"
    }
    
    println "\nLongest Queued Items:"
    queueStats.sort { -it.time }.take(5).each { item ->
        println "${item.job}: ${(item.time / 1000 / 60).round()} minutes"
    }
}

println "\n============================================="
println "Performance Analysis Completed!"