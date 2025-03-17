// Comprehensive system health check
println "🔍 JENKINS HEALTH CHECK REPORT 🔍"
println "=================================="
println "Jenkins Version: ${Jenkins.instance.version}"
println "System Details: ${System.getProperty('os.name')} (${System.getProperty('os.version')}) ${System.getProperty('os.arch')}"

// Check memory usage
def rt = Runtime.getRuntime()
def usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024
def totalMB = rt.totalMemory() / 1024 / 1024
def maxMB = rt.maxMemory() / 1024 / 1024
println "\n✅ MEMORY USAGE"
println "Used Memory: ${usedMB.round(2)} MB"
println "Total Memory: ${totalMB.round(2)} MB"
println "Max Memory: ${maxMB.round(2)} MB"
println "Memory Utilization: ${(usedMB / maxMB * 100).round(2)}%"
if ((usedMB / maxMB * 100) > 80) {
    println "⚠️ WARNING: Memory usage is high!"
}

// Check thread status
def threadCount = Thread.activeCount()
println "\n✅ THREAD STATUS"
println "Active Threads: ${threadCount}"
if (threadCount > 100) {
    println "⚠️ WARNING: High number of active threads!"
}

// Check disk space
println "\n✅ DISK SPACE"
def jenkins_home = new File(Jenkins.instance.rootDir.absolutePath)
def freeSpaceGB = jenkins_home.getFreeSpace() / 1024 / 1024 / 1024
println "Jenkins Home: ${jenkins_home}"
println "Free Space: ${freeSpaceGB.round(2)} GB"
if (freeSpaceGB < 10) {
    println "⚠️ WARNING: Low disk space!"
}

// Check executor usage
println "\n✅ EXECUTOR USAGE"
def totalExecutors = 0
def busyExecutors = 0
Jenkins.instance.computers.each { computer ->
    totalExecutors += computer.numExecutors
    busyExecutors += computer.countBusy()
}
println "Busy/Total Executors: ${busyExecutors}/${totalExecutors}"
println "Executor Utilization: ${(busyExecutors / (totalExecutors ?: 1) * 100).round(2)}%"

// Check build queue
def queueSize = Jenkins.instance.queue.items.size()
println "\n✅ BUILD QUEUE"
println "Builds in Queue: ${queueSize}"
if (queueSize > 10) {
    println "⚠️ WARNING: Many builds in queue!"
}

println "\n✅ PLUGIN STATUS"
def outdatedPlugins = Jenkins.instance.pluginManager.plugins.findAll { it.hasUpdate() }
println "Total Plugins: ${Jenkins.instance.pluginManager.plugins.size()}"
println "Outdated Plugins: ${outdatedPlugins.size()}"
if (outdatedPlugins.size() > 0) {
    println "⚠️ WARNING: You have outdated plugins that should be updated."
}

println "\n✅ SECURITY CHECKS"
if (!Jenkins.instance.crumbIssuer) {
    println "⚠️ WARNING: CSRF Protection is disabled!"
}
if (Jenkins.instance.securityRealm.getClass().getName().contains("HudsonPrivateSecurityRealm")) {
    println "✓ Using Jenkins own user database for security"
} else {
    println "✓ Using external security realm: ${Jenkins.instance.securityRealm.getClass().getName()}"
}

println "\n=================================="
println "Health Check Completed!"