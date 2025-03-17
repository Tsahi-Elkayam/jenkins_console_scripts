// Monitor Jenkins resource usage (CPU, memory, threads) and output as a dashboard
println "📊 JENKINS RESOURCE MONITOR 📊"
println "=============================="

// Function to format bytes in human-readable format
def formatBytes = { bytes ->
    def units = ['B', 'KB', 'MB', 'GB', 'TB']
    def unitIndex = 0
    def divisor = 1L
    
    while (bytes / divisor > 1024 && unitIndex < units.size() - 1) {
        unitIndex++
        divisor *= 1024
    }
    
    return String.format("%.2f %s", bytes / divisor, units[unitIndex])
}

// Get basic system info
println "🖥️ SYSTEM INFO:"
println "  Jenkins Version: ${Jenkins.instance.version}"
println "  Java Version: ${System.getProperty('java.version')}"
println "  OS: ${System.getProperty('os.name')} ${System.getProperty('os.version')} (${System.getProperty('os.arch')})"
println ""

// Memory usage
def rt = Runtime.getRuntime()
def usedMemory = rt.totalMemory() - rt.freeMemory()
def maxMemory = rt.maxMemory()
def totalMemory = rt.totalMemory()
def memoryUtilization = (usedMemory * 100.0) / maxMemory

println "💾 MEMORY USAGE:"
println "  Max Allowed Memory: ${formatBytes(maxMemory)}"
println "  Current Heap Size: ${formatBytes(totalMemory)}"
println "  Used Memory: ${formatBytes(usedMemory)} (${memoryUtilization.round(2)}% of max)"
println "  Free Memory: ${formatBytes(rt.freeMemory())}"

// Create memory utilization bar
def barWidth = 50
def usedChars = Math.max(1, Math.min(barWidth, (memoryUtilization * barWidth / 100).intValue()))
def bar = "[" + "=".multiply(usedChars) + " ".multiply(barWidth - usedChars) + "]"
println "  ${bar} ${memoryUtilization.round(2)}%"

// Warn if memory usage is high
if (memoryUtilization > 80) {
    println "  ⚠️ WARNING: Memory usage is high! Consider increasing max heap size."
}
println ""

// Thread information
def threadMXBean = java.lang.management.ManagementFactory.getThreadMXBean()
def threadCount = threadMXBean.threadCount
def peakThreadCount = threadMXBean.peakThreadCount
def totalStartedThreadCount = threadMXBean.totalStartedThreadCount
def deadlockedThreads = threadMXBean.findDeadlockedThreads()
def deadlockedThreadCount = deadlockedThreads ? deadlockedThreads.length : 0
def daemonThreadCount = threadMXBean.daemonThreadCount

println "🧵 THREAD INFORMATION:"
println "  Live Threads: ${threadCount}"
println "  Peak Threads: ${peakThreadCount}"
println "  Total Started Threads (since startup): ${totalStartedThreadCount}"
println "  Daemon Threads: ${daemonThreadCount}"
println "  Deadlocked Threads: ${deadlockedThreadCount}"

// Warn if deadlocked threads exist
if (deadlockedThreadCount > 0) {
    println "  ⚠️ WARNING: Deadlocked threads detected! System may be unstable."
}

// List top CPU consuming threads
println "\n🔍 TOP 10 CPU CONSUMING THREADS:"
def threadInfos = threadMXBean.getThreadInfo(threadMXBean.allThreadIds, 3)
def threadCpuTimes = [:]

threadInfos.each { threadInfo ->
    if (threadInfo) {
        def threadId = threadInfo.threadId
        def cpuTime = threadMXBean.getThreadCpuTime(threadId)
        if (cpuTime > 0) {
            threadCpuTimes[threadId] = [
                id: threadId,
                name: threadInfo.threadName,
                cpuTimeMs: cpuTime / 1000000, // nanoseconds to milliseconds
                state: threadInfo.threadState.toString(),
                stackTrace: threadInfo.stackTrace
            ]
        }
    }
}

// Sort by CPU time and display top 10
def topThreads = threadCpuTimes.values().sort { -it.cpuTimeMs }.take(10)
topThreads.eachWithIndex { thread, index ->
    println "  ${index + 1}. ${thread.name} (${thread.state})"
    println "     CPU Time: ${String.format("%.2f", thread.cpuTimeMs)} ms"
    
    if (thread.stackTrace && thread.stackTrace.length > 0) {
        println "     Stack Trace (top 3 elements):"
        thread.stackTrace.take(3).each { stackElement ->
            println "       at ${stackElement}"
        }
    }
    println ""
}

// Garbage Collection stats
println "\n♻️ GARBAGE COLLECTION STATS:"
def gcBeans = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()
gcBeans.each { gcBean ->
    def collectionCount = gcBean.getCollectionCount()
    def collectionTime = gcBean.getCollectionTime()
    
    println "  ${gcBean.name}:"
    println "    Collection Count: ${collectionCount}"
    println "    Total Collection Time: ${collectionTime} ms"
    println "    Average Collection Time: ${collectionCount > 0 ? String.format("%.2f", collectionTime / collectionCount) : 0} ms"
}
println ""

// Executor usage
def totalExecutors = 0
def busyExecutors = 0
Jenkins.instance.computers.each { computer ->
    totalExecutors += computer.numExecutors
    busyExecutors += computer.countBusy()
}

def executorUtilization = totalExecutors > 0 ? (busyExecutors * 100.0) / totalExecutors : 0
println "🏗️ BUILD EXECUTOR USAGE:"
println "  Busy/Total Executors: ${busyExecutors}/${totalExecutors}"
println "  Utilization: ${executorUtilization.round(2)}%"

// Create executor utilization bar
usedChars = Math.max(1, Math.min(barWidth, (executorUtilization * barWidth / 100).intValue()))
bar = "[" + "=".multiply(usedChars) + " ".multiply(barWidth - usedChars) + "]"
println "  ${bar} ${executorUtilization.round(2)}%"

// Build queue information
def queueSize = Jenkins.instance.queue.items.size()
println "\n🔄 BUILD QUEUE:"
println "  Builds in Queue: ${queueSize}"

if (queueSize > 0) {
    def now = System.currentTimeMillis()
    def queueItems = Jenkins.instance.queue.items.sort { a, b -> 
        a.getInQueueSince() <=> b.getInQueueSince() 
    }
    
    println "  Oldest 5 queued builds:"
    queueItems.take(5).each { item ->
        def waitTime = (now - item.getInQueueSince()) / 1000 / 60 // convert to minutes
        println "    - ${item.task.name} (waiting for ${waitTime.round(2)} minutes)"
        
        def why = item.getWhy()
        if (why) {
            println "      Reason: ${why}"
        }
    }
}

// Disk space
println "\n💽 DISK SPACE:"
def jenkinsHome = new File(Jenkins.instance.rootDir.absolutePath)
def totalSpace = jenkinsHome.getTotalSpace()
def freeSpace = jenkinsHome.getFreeSpace()
def usedSpace = totalSpace - freeSpace
def diskUtilization = (usedSpace * 100.0) / totalSpace

println "  Jenkins Home: ${jenkinsHome}"
println "  Total Space: ${formatBytes(totalSpace)}"
println "  Used Space: ${formatBytes(usedSpace)} (${diskUtilization.round(2)}%)"
println "  Free Space: ${formatBytes(freeSpace)}"

// Create disk utilization bar
usedChars = Math.max(1, Math.min(barWidth, (diskUtilization * barWidth / 100).intValue()))
bar = "[" + "=".multiply(usedChars) + " ".multiply(barWidth - usedChars) + "]"
println "  ${bar} ${diskUtilization.round(2)}%"

// Warn if disk space is low
if (freeSpace < 10 * 1024 * 1024 * 1024) { // less than 10GB
    println "  ⚠️ WARNING: Free disk space is low! Consider cleaning up old builds and workspaces."
}

// Network connections
println "\n🌐 NETWORK CONNECTIONS:"
try {
    def p = ["netstat", "-an"].execute()
    p.waitFor()
    
    def netstatOutput = p.text
    def tcpConnections = netstatOutput.readLines().findAll { it.contains('TCP') }
    def establishedConnections = tcpConnections.findAll { it.contains('ESTABLISHED') }
    
    println "  Total TCP Connections: ${tcpConnections.size()}"
    println "  Established Connections: ${establishedConnections.size()}"
    
    // Count connections by state
    def connectionStates = tcpConnections.collect { 
        def parts = it.trim().split(/\s+/)
        parts.length > 5 ? parts[5] : "UNKNOWN" 
    }
    
    def stateCount = [:]
    connectionStates.each { state ->
        stateCount[state] = (stateCount[state] ?: 0) + 1
    }
    
    println "  Connection States:"
    stateCount.sort { -it.value }.each { state, count ->
        println "    - ${state}: ${count}"
    }
} catch (Exception e) {
    println "  Unable to get network connections: ${e.message}"
}

// Performance recommendations
println "\n📋 PERFORMANCE RECOMMENDATIONS:"
if (memoryUtilization > 70) {
    println "  1. Consider increasing JVM heap size (-Xmx parameter)"
}

if (threadCount > 200) {
    println "  2. High thread count detected - review active plugins for possible thread leaks"
}

if (executorUtilization > 90) {
    println "  3. Executor utilization is high - consider adding more build agents"
}

if (diskUtilization > 80) {
    println "  4. Disk usage is high - implement a job cleanup strategy"
    println "     Run: 'Jenkins.instance.getAllItems(hudson.model.Job).each{job -> job.logRotator = new hudson.tasks.LogRotator(7,-1,-1,-1); job.save()}'"
}

// Forced garbage collection to clean up after this script
println "\nRunning garbage collection to clean up..."
System.gc()

println "=============================="