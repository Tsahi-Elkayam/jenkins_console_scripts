// List all nodes with detailed status information
println "🖥️ JENKINS NODES STATUS REPORT 🖥️"
println "=================================="

def totalExecutors = 0
def busyExecutors = 0
def offlineNodes = 0
def onlineNodes = 0
def nodes = Jenkins.instance.getComputers()

println "Total Nodes: ${nodes.length}"
println ""

nodes.each { computer ->
    def nodeName = computer.name ?: "master"
    def isOnline = computer.isOnline()
    def numExecutors = computer.numExecutors
    def busyCount = computer.countBusy()
    
    // Update counters
    totalExecutors += numExecutors
    busyExecutors += busyCount
    if (isOnline) {
        onlineNodes++
    } else {
        offlineNodes++
    }
    
    println "Node: ${nodeName}"
    println "  Status: ${isOnline ? '✅ Online' : '❌ Offline'}"
    println "  Executors: ${busyCount}/${numExecutors} busy"
    
    // Get node details
    if (nodeName != "master") {
        def node = computer.getNode()
        if (node) {
            println "  Launch Method: ${node.getLauncher().getClass().getSimpleName()}"
            println "  Remote FS: ${node.getRemoteFS()}"
            if (node.getAssignedLabels()) {
                println "  Labels: ${node.getAssignedLabels().join(', ')}"
            }
        }
    }
    
    // If offline, show reason
    if (!isOnline) {
        def offlineCause = computer.getOfflineCause()
        println "  Offline Reason: ${offlineCause?.toString() ?: 'Unknown'}"
        println "  Offline Since: ${computer.getOfflineCauseTime() ? new Date(computer.getOfflineCauseTime()) : 'Unknown'}"
    }
    
    // Show current active builds
    def currentBuilds = computer.getExecutors().findAll { exec -> exec.isBusy() }.collect { exec -> exec.getCurrentExecutable() }
    if (currentBuilds.size() > 0) {
        println "  Current Builds:"
        currentBuilds.each { executable ->
            if (executable) {
                def build = executable.run
                println "    - ${build.fullDisplayName}"
                println "      Started: ${new Date(build.getStartTimeInMillis())}"
                println "      Running for: ${(System.currentTimeMillis() - build.getStartTimeInMillis()) / 60000} minutes"
            }
        }
    }
    
    // Show system metrics if available
    def monitorData = computer.getMonitorData()
    if (monitorData) {
        println "  System Metrics:"
        
        // Disk space
        def diskSpace = monitorData.get(hudson.node_monitors.DiskSpaceMonitor.class.name)
        if (diskSpace) {
            println "    Disk Space: ${diskSpace.gbLeft} GB free of ${diskSpace.totalSize ? (diskSpace.totalSize / (1024*1024*1024)).round(2) + ' GB' : 'Unknown'}"
        }
        
        // Temp space
        def tempSpace = monitorData.get(hudson.node_monitors.TemporarySpaceMonitor.class.name)
        if (tempSpace) {
            println "    Temp Space: ${tempSpace.gbLeft} GB free"
        }
        
        // Memory
        def memory = monitorData.get(hudson.node_monitors.SwapSpaceMonitor.class.name)
        if (memory) {
            println "    Memory: Physical ${memory.physicalMemory ? (memory.physicalMemory / (1024*1024*1024)).round(2) + ' GB' : 'Unknown'}, Swap ${memory.totalSwapSpace ? (memory.totalSwapSpace / (1024*1024*1024)).round(2) + ' GB' : 'Unknown'}"
            println "    Available Memory: ${memory.availablePhysicalMemory ? (memory.availablePhysicalMemory / (1024*1024*1024)).round(2) + ' GB' : 'Unknown'} (${memory.availableSwapSpace ? (memory.availableSwapSpace / (1024*1024*1024)).round(2) + ' GB swap' : 'Unknown swap'})"
        }
        
        // Response time
        def responseTime = monitorData.get(hudson.node_monitors.ResponseTimeMonitor.class.name)
        if (responseTime) {
            println "    Response Time: ${responseTime.average} ms"
        }
    }
    
    println ""
}

// Print summary
println "📊 SUMMARY:"
println "Total Nodes: ${nodes.length}"
println "Online Nodes: ${onlineNodes}"
println "Offline Nodes: ${offlineNodes}"
println "Total Executors: ${totalExecutors}"
println "Busy Executors: ${busyExecutors}"
println "Available Executors: ${totalExecutors - busyExecutors}"
println "Utilization: ${totalExecutors > 0 ? ((busyExecutors / totalExecutors * 100).round(2) + '%') : 'N/A'}"
println "=================================="