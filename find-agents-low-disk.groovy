// Find agents with low disk space and generate an alert report
def warningThresholdGB = 10 // Warning threshold in GB
def criticalThresholdGB = 5 // Critical threshold in GB
def checkTempSpace = true // Whether to check temp space as well

println "💽 LOW DISK SPACE DETECTOR 💽"
println "============================="
println "Warning Threshold: ${warningThresholdGB} GB"
println "Critical Threshold: ${criticalThresholdGB} GB"
println "Check Temp Space: ${checkTempSpace}"
println ""

def warningNodes = []
def criticalNodes = []
def unknownNodes = []

Jenkins.instance.computers.each { computer ->
    def nodeName = computer.name ?: "master"
    def isOnline = computer.isOnline()
    
    if (isOnline) {
        def monitorData = computer.getMonitorData()
        
        // Check root disk space
        def rootDiskSpace = monitorData.get(hudson.node_monitors.DiskSpaceMonitor.class.name)
        def rootGBFree = rootDiskSpace?.gbLeft ?: -1
        def rootTotalGB = rootDiskSpace?.totalSize ? (rootDiskSpace.totalSize / (1024*1024*1024)).round(2) : -1
        
        // Check temp space if enabled
        def tempGBFree = -1
        if (checkTempSpace) {
            def tempDiskSpace = monitorData.get(hudson.node_monitors.TemporarySpaceMonitor.class.name)
            tempGBFree = tempDiskSpace?.gbLeft ?: -1
        }
        
        def nodeInfo = [
            name: nodeName,
            rootGBFree: rootGBFree,
            rootTotalGB: rootTotalGB,
            rootPercentFree: (rootGBFree > 0 && rootTotalGB > 0) ? ((rootGBFree / rootTotalGB) * 100).round(2) : -1,
            tempGBFree: tempGBFree
        ]
        
        if (rootGBFree == -1) {
            unknownNodes << nodeInfo
        } else if (rootGBFree < criticalThresholdGB || (checkTempSpace && tempGBFree > 0 && tempGBFree < criticalThresholdGB)) {
            criticalNodes << nodeInfo
        } else if (rootGBFree < warningThresholdGB || (checkTempSpace && tempGBFree > 0 && tempGBFree < warningThresholdGB)) {
            warningNodes << nodeInfo
        }
    }
}

// Sort nodes by available space
criticalNodes.sort { it.rootGBFree }
warningNodes.sort { it.rootGBFree }

// Print critical nodes
if (criticalNodes.size() > 0) {
    println "🔴 CRITICAL - EXTREMELY LOW DISK SPACE:"
    println "------------------------------------"
    criticalNodes.each { node ->
        println "Node: ${node.name}"
        println "  Root Disk: ${node.rootGBFree} GB free of ${node.rootTotalGB > 0 ? node.rootTotalGB + ' GB' : 'unknown'}"
        if (node.rootPercentFree > 0) {
            println "  Percentage Free: ${node.rootPercentFree}%"
        }
        if (checkTempSpace && node.tempGBFree > 0) {
            println "  Temp Space: ${node.tempGBFree} GB free"
        }
        println ""
    }
}

// Print warning nodes
if (warningNodes.size() > 0) {
    println "🟠 WARNING - LOW DISK SPACE:"
    println "-------------------------"
    warningNodes.each { node ->
        println "Node: ${node.name}"
        println "  Root Disk: ${node.rootGBFree} GB free of ${node.rootTotalGB > 0 ? node.rootTotalGB + ' GB' : 'unknown'}"
        if (node.rootPercentFree > 0) {
            println "  Percentage Free: ${node.rootPercentFree}%"
        }
        if (checkTempSpace && node.tempGBFree > 0) {
            println "  Temp Space: ${node.tempGBFree} GB free"
        }
        println ""
    }
}

// Print unknown nodes
if (unknownNodes.size() > 0) {
    println "⚠️ UNKNOWN - DISK SPACE INFORMATION NOT AVAILABLE:"
    println "-----------------------------------------------"
    unknownNodes.each { node ->
        println "Node: ${node.name}"
        println "  Disk space monitor data not available"
        println ""
    }
}

// Print recommendations
if (criticalNodes.size() > 0 || warningNodes.size() > 0) {
    println "📋 RECOMMENDATIONS:"
    println "-----------------"
    println "1. Clean up workspace directories on affected nodes"
    println "   jenkins-script-console> new hudson.model.Hudson.CleanTempDirectoryThread().run()"
    println "2. Increase disk space by removing unnecessary files or expanding storage"
    println "3. Consider adding disk space monitoring alerts to your monitoring system"
    println "4. Review large job workspaces on these agents:"
    println "   jenkins-script-console> def nodeName = 'node-name'; println new File(Jenkins.instance.getNode(nodeName).getRootPath().toString()).listFiles().findAll{it.directory}.collect{[path:it.path, size:calculateSize(it)]}.sort{-it.size}.take(10)"
    println "   (where 'calculateSize' is a recursive directory size calculation function)"
}

// Print summary
println "\n📊 SUMMARY:"
println "Total nodes checked: ${Jenkins.instance.computers.length}"
println "Nodes with critical disk space: ${criticalNodes.size()}"
println "Nodes with warning disk space: ${warningNodes.size()}"
println "Nodes with unknown disk space: ${unknownNodes.size()}"
println "============================="