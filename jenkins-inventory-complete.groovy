/**
 * Enhanced Jenkins Hardware Inventory with Improved Commands
 * This is a complete script that can be run directly in Jenkins Script Console
 */

import hudson.model.*
import jenkins.model.*
import hudson.remoting.Channel
import hudson.util.*
import hudson.FilePath
import java.util.logging.Logger
import org.jenkinsci.remoting.RoleChecker

// Define serializable command executor class
class SerializableCommandExecutor implements hudson.remoting.Callable<String, Exception>, java.io.Serializable {
    private static final long serialVersionUID = 1L
    private final String command
    
    SerializableCommandExecutor(String command) {
        this.command = command
    }
    
    public String call() throws Exception {
        try {
            // Try different command interpreters based on detected OS
            def interpreter
            def osName = System.getProperty("os.name").toLowerCase()
            def isWindows = osName.contains("windows")
            
            if (isWindows) {
                interpreter = ["cmd", "/c", command] as String[]
            } else {
                interpreter = ["sh", "-c", command] as String[]
            }
            
            def process = new ProcessBuilder(interpreter)
                    .redirectErrorStream(true)
                    .start()
            
            def output = new StringBuilder()
            def reader = new BufferedReader(new InputStreamReader(process.getInputStream()))
            
            String line
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n")
            }
            
            int exitCode = process.waitFor()
            if (exitCode != 0) {
                output.append("[Command exited with code: ").append(exitCode).append("]")
            }
            
            return output.toString()
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage()
        }
    }
    
    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        // Accept any role for this script
        // In production, you should implement proper role checking
    }
}

// Set up logging
def jobLogger = Logger.getLogger("JenkinsInventory")
println "SCRIPT STARTED: " + new Date().format("yyyy-MM-dd HH:mm:ss")

// Get all nodes in Jenkins
def jenkins = Jenkins.getInstance()
def nodes = jenkins.getNodes()

println "=" * 80
println "Found ${nodes.size() + 1} nodes (including master)"
println "=" * 80

// Collection for node information
def allNodesInfo = []  // Initialize the list explicitly

// Add master node to the list for processing
def allNodes = []
allNodes.add([name: "master", node: jenkins])
nodes.each { node ->
    allNodes.add([name: node.getNodeName(), node: node])
}

// Loop through each node
allNodes.each { nodeEntry ->
    def nodeName = nodeEntry.name
    def node = nodeEntry.node
    
    println "\n>>> Processing node: ${nodeName}"
    
    // Create a map to store node information
    def nodeInfo = [
        "node_name": nodeName,
        "timestamp": new Date().format("yyyy-MM-dd HH:mm:ss")
    ]
    
    // Get node labels
    if (nodeName == "master") {
        nodeInfo["labels"] = "master"
    } else {
        nodeInfo["labels"] = node.getLabelString()
        println "  Labels: ${nodeInfo.labels}"
    }
    
    // Get computer info
    def computer = (nodeName == "master") ? jenkins.toComputer() : node.toComputer()
    if (computer != null) {
        nodeInfo["online"] = computer.isOnline()
        println "  Online status: ${nodeInfo.online}"
        
        if (computer.isOnline()) {
            // Get executor information
            nodeInfo["executors"] = computer.getNumExecutors()
            nodeInfo["busy_executors"] = computer.countBusy()
            nodeInfo["idle_executors"] = computer.countIdle()
            println "  Executors: Total=${nodeInfo.executors}, Busy=${nodeInfo.busy_executors}, Idle=${nodeInfo.idle_executors}"
            
            // Get connection time
            if (computer.getConnectTime() > 0) {
                nodeInfo["connect_time"] = new Date(computer.getConnectTime()).format("yyyy-MM-dd HH:mm:ss")
                nodeInfo["uptime"] = formatDuration(System.currentTimeMillis() - computer.getConnectTime())
                println "  Connected since: ${nodeInfo.connect_time} (Uptime: ${nodeInfo.uptime})"
            }
            
            // Get basic OS info from monitor data
            try {
                def monitorData = computer.getMonitorData()
                if (monitorData) {
                    println "  Monitor data available, extracting basics..."
                    
                    if (monitorData["hudson.node_monitors.ArchitectureMonitor"]) {
                        def arch = monitorData["hudson.node_monitors.ArchitectureMonitor"].toString()
                        nodeInfo["architecture"] = arch
                        println "  Architecture: ${arch}"
                        
                        // Basic OS detection
                        if (arch.toLowerCase().contains("windows")) {
                            nodeInfo["os_type"] = "Windows"
                            println "  OS Type: Windows"
                        } else if (arch.toLowerCase().contains("linux")) {
                            nodeInfo["os_type"] = "Linux"
                            println "  OS Type: Linux"
                        } else if (arch.toLowerCase().contains("mac") || arch.toLowerCase().contains("darwin")) {
                            nodeInfo["os_type"] = "MacOS"
                            println "  OS Type: MacOS"
                        } else {
                            nodeInfo["os_type"] = "Unknown"
                            println "  OS Type: Unknown"
                        }
                    }
                    
                    // Get disk space info
                    if (monitorData["hudson.node_monitors.DiskSpaceMonitor"]) {
                        def diskMonitor = monitorData["hudson.node_monitors.DiskSpaceMonitor"]
                        def diskPath = diskMonitor.getClass().getDeclaredField("path")
                        diskPath.setAccessible(true)
                        def path = diskPath.get(diskMonitor)
                        
                        def diskSize = diskMonitor.getClass().getDeclaredField("size")
                        diskSize.setAccessible(true)
                        def size = diskSize.get(diskMonitor)
                        
                        nodeInfo["disk_path"] = path
                        nodeInfo["disk_total"] = formatBytes(size)
                        println "  Disk info: ${path}, Total: ${nodeInfo.disk_total}"
                    }
                    
                    // Get memory info with robust fallback methods
                    if (monitorData["hudson.node_monitors.SwapSpaceMonitor"]) {
                        try {
                            def swapMonitor = monitorData["hudson.node_monitors.SwapSpaceMonitor"]
                            
                            // Try using direct access first
                            try {
                                def totalMemField = swapMonitor.getClass().getDeclaredField("totalPhysicalMemory")
                                totalMemField.setAccessible(true)
                                def totalMem = totalMemField.get(swapMonitor)
                                
                                if (totalMem != null) {
                                    nodeInfo["ram"] = formatBytes(totalMem)
                                    println "  Memory: Total=${nodeInfo.ram}"
                                }
                            } catch (Exception e) {
                                // Try using getter method
                                try {
                                    def totalMemMethod = swapMonitor.getClass().getDeclaredMethod("getTotalPhysicalMemory")
                                    totalMemMethod.setAccessible(true)
                                    def totalMem = totalMemMethod.invoke(swapMonitor)
                                    
                                    if (totalMem != null) {
                                        nodeInfo["ram"] = formatBytes(totalMem)
                                        println "  Memory: Total=${nodeInfo.ram}"
                                    }
                                } catch (Exception ex) {
                                    println "  Unable to get total memory: ${ex.message}"
                                }
                            }
                            
                            // Try getting available memory with similar approach
                            try {
                                def availMemField = swapMonitor.getClass().getDeclaredField("availablePhysicalMemory")
                                availMemField.setAccessible(true)
                                def availMem = availMemField.get(swapMonitor)
                                
                                if (availMem != null) {
                                    nodeInfo["ram_available"] = formatBytes(availMem)
                                    println "  Memory Available: ${nodeInfo.ram_available}"
                                }
                            } catch (Exception e) {
                                // Try using getter method
                                try {
                                    def availMemMethod = swapMonitor.getClass().getDeclaredMethod("getAvailablePhysicalMemory")
                                    availMemMethod.setAccessible(true)
                                    def availMem = availMemMethod.invoke(swapMonitor)
                                    
                                    if (availMem != null) {
                                        nodeInfo["ram_available"] = formatBytes(availMem)
                                        println "  Memory Available: ${nodeInfo.ram_available}"
                                    }
                                } catch (Exception ex) {
                                    println "  Unable to get available memory: ${ex.message}"
                                }
                            }
                            
                            // If all direct methods fail, try toString() parsing as a last resort
                            if (!nodeInfo.containsKey("ram")) {
                                String monitorString = swapMonitor.toString()
                                def memMatch = monitorString =~ /total physical memory: (\d+)/
                                if (memMatch) {
                                    try {
                                        def totalMem = Long.parseLong(memMatch[0][1])
                                        nodeInfo["ram"] = formatBytes(totalMem)
                                        println "  Memory: Total=${nodeInfo.ram} (from toString)"
                                    } catch (Exception e) {
                                        println "  Error parsing memory from toString: ${e.message}"
                                    }
                                }
                            }
                        } catch (Exception e) {
                            println "  Error getting memory info: ${e.message}"
                        }
                    }
                } else {
                    println "  No monitor data available"
                }
            } catch (Exception e) {
                println "  Error extracting monitor data: ${e.message}"
            }
            
            // Try to execute commands to get more detailed info
            println "  Attempting to execute remote commands..."
            
            // Determine if we're on Windows or Unix
            def isWindows = nodeInfo.os_type == "Windows"
            
            try {
                // Step 1: Get basic identification with a simple command
                def testCmd = isWindows ? "hostname" : "hostname"
                def hostname = executeRemoteCommand(computer, testCmd)
                nodeInfo["hostname"] = hostname?.trim()
                println "  Hostname: ${hostname?.trim()}"
                
                // If we can execute a basic command, try more detailed ones
                if (hostname != null) {
                    if (isWindows) {
                        // WINDOWS COMMANDS
                        
                        // Get OS Name and Version
                        def osNameCmd = "systeminfo | findstr /B /C:\"OS Name\""
                        def osNameOutput = executeRemoteCommand(computer, osNameCmd)
                        if (osNameOutput) {
                            nodeInfo["os_name"] = osNameOutput.trim()
                            println "  OS Name: ${nodeInfo.os_name}"
                        }
                        
                        def osVersionCmd = "systeminfo | findstr /B /C:\"OS Version\""
                        def osVersionOutput = executeRemoteCommand(computer, osVersionCmd)
                        if (osVersionOutput) {
                            nodeInfo["os_version"] = osVersionOutput.trim()
                            println "  OS Version: ${nodeInfo.os_version}"
                        }
                        
                        // Get CPU info
                        def cpuCmd = "wmic cpu get name"
                        def cpuOutput = executeRemoteCommand(computer, cpuCmd)
                        if (cpuOutput) {
                            def cpuLines = cpuOutput.readLines()
                            if (cpuLines.size() > 1) {
                                nodeInfo["cpu_model"] = cpuLines[1].trim()
                                println "  CPU: ${nodeInfo.cpu_model}"
                            }
                        }
                        
                        // Get CPU cores and threads
                        def cpuCoresCmd = "wmic cpu get NumberOfCores"
                        def cpuCoresOutput = executeRemoteCommand(computer, cpuCoresCmd)
                        if (cpuCoresOutput) {
                            def cpuCoresLines = cpuCoresOutput.readLines()
                            if (cpuCoresLines.size() > 1) {
                                nodeInfo["cpu_cores"] = cpuCoresLines[1].trim()
                                println "  CPU Cores: ${nodeInfo.cpu_cores}"
                            }
                        }
                        
                        def cpuThreadsCmd = "wmic cpu get NumberOfLogicalProcessors"
                        def cpuThreadsOutput = executeRemoteCommand(computer, cpuThreadsCmd)
                        if (cpuThreadsOutput) {
                            def cpuThreadsLines = cpuThreadsOutput.readLines()
                            if (cpuThreadsLines.size() > 1) {
                                nodeInfo["cpu_threads"] = cpuThreadsLines[1].trim()
                                println "  CPU Threads: ${nodeInfo.cpu_threads}"
                            }
                        }
                        
                        // Get System Manufacturer and Model
                        def sysManufacturerCmd = "wmic csproduct get vendor"
                        def sysManufacturerOutput = executeRemoteCommand(computer, sysManufacturerCmd)
                        if (sysManufacturerOutput) {
                            def sysManufacturerLines = sysManufacturerOutput.readLines()
                            if (sysManufacturerLines.size() > 1) {
                                nodeInfo["system_manufacturer"] = sysManufacturerLines[1].trim()
                                println "  System Manufacturer: ${nodeInfo.system_manufacturer}"
                            }
                        }
                        
                        def sysModelCmd = "wmic csproduct get name"
                        def sysModelOutput = executeRemoteCommand(computer, sysModelCmd)
                        if (sysModelOutput) {
                            def sysModelLines = sysModelOutput.readLines()
                            if (sysModelLines.size() > 1) {
                                nodeInfo["system_model"] = sysModelLines[1].trim()
                                println "  System Model: ${nodeInfo.system_model}"
                            }
                        }
                        
                        // Get disk usage
                        def diskCmd = "wmic logicaldisk get deviceid,size,freespace"
                        def diskOutput = executeRemoteCommand(computer, diskCmd)
                        if (diskOutput) {
                            nodeInfo["disk_usage"] = diskOutput.trim()
                            println "  Disk Usage available"
                        }
                        
                        // Check for GPU
                        def gpuCmd = "wmic path win32_VideoController get name"
                        def gpuOutput = executeRemoteCommand(computer, gpuCmd)
                        if (gpuOutput) {
                            def gpuLines = gpuOutput.readLines()
                            if (gpuLines.size() > 1) {
                                nodeInfo["gpu_model"] = gpuLines[1].trim()
                                println "  GPU: ${nodeInfo.gpu_model}"
                            }
                        }
                        
                        // Get DNS servers
                        def dnsCmd = "ipconfig /all | findstr /i \"DNS Servers\""
                        def dnsOutput = executeRemoteCommand(computer, dnsCmd)
                        if (dnsOutput) {
                            nodeInfo["dns_servers"] = dnsOutput.trim()
                            println "  DNS Servers available"
                        }
                        
                        // Get network interfaces
                        def nicCmd = "wmic nic get name,macaddress"
                        def nicOutput = executeRemoteCommand(computer, nicCmd)
                        if (nicOutput) {
                            nodeInfo["network_interfaces"] = nicOutput.trim()
                            println "  Network interfaces available"
                        }
                        
                        // Check RAM
                        def ramCmd = "wmic ComputerSystem get TotalPhysicalMemory"
                        def ramOutput = executeRemoteCommand(computer, ramCmd)
                        if (ramOutput) {
                            def ramLines = ramOutput.readLines()
                            if (ramLines.size() > 1) {
                                try {
                                    def ramBytes = Long.parseLong(ramLines[1].trim())
                                    nodeInfo["ram_total_bytes"] = ramBytes
                                    nodeInfo["ram_total"] = formatBytes(ramBytes)
                                    println "  RAM Total: ${nodeInfo.ram_total}"
                                } catch (Exception e) {
                                    println "  Error parsing RAM: ${e.message}"
                                }
                            }
                        }
                        
                        // Check if virtualized
                        def virtCmd = "systeminfo | findstr /i \"Hypervisor\""
                        def virtOutput = executeRemoteCommand(computer, virtCmd)
                        if (virtOutput && !virtOutput.contains("No")) {
                            nodeInfo["is_virtualized"] = true
                            println "  Virtualized: Yes"
                        } else {
                            nodeInfo["is_virtualized"] = false
                            println "  Virtualized: No or Unknown"
                        }
                        
                    } else {
                        // LINUX COMMANDS
                        
                        // Get OS details
                        def osReleaseCmd = "cat /etc/os-release 2>/dev/null"
                        def osReleaseOutput = executeRemoteCommand(computer, osReleaseCmd)
                        if (osReleaseOutput) {
                            // Parse OS release info
                            def ubuntuMatch = osReleaseOutput =~ /(?m)^NAME="?Ubuntu"?/
                            if (ubuntuMatch) {
                                nodeInfo["is_ubuntu"] = true
                                println "  OS: Ubuntu detected"
                                
                                // Get Ubuntu version
                                def versionMatch = osReleaseOutput =~ /(?m)^VERSION="([^"]+)"/
                                if (versionMatch) {
                                    nodeInfo["ubuntu_version"] = versionMatch[0][1]
                                    println "  Ubuntu Version: ${nodeInfo.ubuntu_version}"
                                }
                            } else {
                                // Check for other distributions
                                def nameMatch = osReleaseOutput =~ /(?m)^NAME="?([^"]+)"?/
                                if (nameMatch) {
                                    nodeInfo["linux_distro"] = nameMatch[0][1]
                                    println "  Linux Distribution: ${nodeInfo.linux_distro}"
                                }
                            }
                        }
                        
                        // Get kernel version
                        def kernelCmd = "uname -r"
                        def kernelOutput = executeRemoteCommand(computer, kernelCmd)
                        if (kernelOutput) {
                            nodeInfo["kernel_version"] = kernelOutput.trim()
                            println "  Kernel Version: ${nodeInfo.kernel_version}"
                        }
                        
                        // Try hardware info via lscpu
                        def cpuCmd = "lscpu 2>/dev/null | grep 'Model name'"
                        def cpuOutput = executeRemoteCommand(computer, cpuCmd)
                        if (cpuOutput) {
                            def modelNameMatch = cpuOutput =~ /Model name:\s+(.+)/
                            if (modelNameMatch) {
                                nodeInfo["cpu_model"] = modelNameMatch[0][1].trim()
                                println "  CPU: ${nodeInfo.cpu_model}"
                            }
                        }
                        
                        // Get CPU cores
                        def coreCmd = "nproc 2>/dev/null || lscpu 2>/dev/null | grep '^CPU(s):'"
                        def coreOutput = executeRemoteCommand(computer, coreCmd)
                        if (coreOutput) {
                            def coreMatch = coreOutput =~ /(\d+)/
                            if (coreMatch) {
                                nodeInfo["cpu_cores"] = coreMatch[0][1]
                                println "  CPU Cores: ${nodeInfo.cpu_cores}"
                            }
                        }
                        
                        // Try disk space usage
                        def dfCmd = "df -h 2>/dev/null"
                        def dfOutput = executeRemoteCommand(computer, dfCmd)
                        if (dfOutput) {
                            nodeInfo["disk_usage"] = dfOutput
                            println "  Disk usage available"
                        }
                        
                        // Check for GPU
                        def gpuCmd = "lspci 2>/dev/null | grep -i vga || lspci 2>/dev/null | grep -i '3d controller'"
                        def gpuOutput = executeRemoteCommand(computer, gpuCmd)
                        if (gpuOutput && !gpuOutput.trim().isEmpty()) {
                            nodeInfo["gpu_model"] = gpuOutput.trim()
                            println "  GPU: ${nodeInfo.gpu_model}"
                        }
                        
                        // Check if NVIDIA GPU with nvidia-smi
                        def nvidiaSmiCmd = "nvidia-smi -L 2>/dev/null || echo 'No NVIDIA GPU'"
                        def nvidiaSmiOutput = executeRemoteCommand(computer, nvidiaSmiCmd)
                        if (nvidiaSmiOutput && !nvidiaSmiOutput.contains("No NVIDIA GPU")) {
                            nodeInfo["has_nvidia_gpu"] = true
                            nodeInfo["nvidia_gpu_info"] = nvidiaSmiOutput.trim()
                            println "  NVIDIA GPU: ${nodeInfo.nvidia_gpu_info}"
                        }
                        
                        // Get DNS servers
                        def dnsCmd = "cat /etc/resolv.conf 2>/dev/null | grep nameserver"
                        def dnsOutput = executeRemoteCommand(computer, dnsCmd)
                        if (dnsOutput) {
                            nodeInfo["dns_servers"] = dnsOutput.trim()
                            println "  DNS Servers: ${dnsOutput.trim()}"
                        }
                        
                        // Get network interfaces
                        def nicCmd = "ip link show 2>/dev/null || ifconfig -a 2>/dev/null"
                        def nicOutput = executeRemoteCommand(computer, nicCmd)
                        if (nicOutput) {
                            nodeInfo["network_interfaces"] = nicOutput.trim()
                            println "  Network interfaces available"
                        }
                        
                        // Get NTP status
                        def ntpCmd = "timedatectl status 2>/dev/null | grep -i 'ntp synchronized' || chronyc sources 2>/dev/null || ntpq -p 2>/dev/null || echo 'NTP not available'"
                        def ntpOutput = executeRemoteCommand(computer, ntpCmd)
                        if (ntpOutput && !ntpOutput.contains("NTP not available")) {
                            nodeInfo["ntp_status"] = ntpOutput.contains("synchronized") ? "synchronized" : "available"
                            println "  NTP: ${nodeInfo.ntp_status}"
                        }
                        
                        // Try to get hardware info with dmidecode
                        def dmiCmd = "dmidecode -s system-manufacturer 2>/dev/null || echo 'Not available'"
                        def dmiOutput = executeRemoteCommand(computer, dmiCmd)
                        if (dmiOutput && !dmiOutput.contains("Not available")) {
                            nodeInfo["manufacturer"] = dmiOutput.trim()
                            println "  Manufacturer: ${nodeInfo.manufacturer}"
                            
                            // Get system model
                            def modelCmd = "dmidecode -s system-product-name 2>/dev/null || echo 'Unknown'"
                            def modelOutput = executeRemoteCommand(computer, modelCmd)
                            if (modelOutput && !modelOutput.contains("Unknown")) {
                                nodeInfo["system_model"] = modelOutput.trim()
                                println "  Model: ${nodeInfo.system_model}"
                            }
                        } else {
                            // Try lshw as alternative
                            def lshwCmd = "lshw -c system -short 2>/dev/null || echo 'Not available'"
                            def lshwOutput = executeRemoteCommand(computer, lshwCmd)
                            if (lshwOutput && !lshwOutput.contains("Not available")) {
                                nodeInfo["hardware_info"] = lshwOutput.trim()
                                println "  Hardware info available from lshw"
                            }
                        }
                        
                        // Check virtualization
                        def virtCmd = "systemd-detect-virt 2>/dev/null || dmesg 2>/dev/null | grep -i 'hypervisor' || echo 'unknown'"
                        def virtOutput = executeRemoteCommand(computer, virtCmd)
                        if (virtOutput && !virtOutput.contains("unknown") && !virtOutput.contains("none")) {
                            nodeInfo["is_virtualized"] = true
                            nodeInfo["virtualization_type"] = virtOutput.trim()
                            println "  Virtualized: Yes (${nodeInfo.virtualization_type})"
                        } else {
                            nodeInfo["is_virtualized"] = false
                            println "  Virtualized: No or Unknown"
                        }
                        
                        // Check SELinux/AppArmor status
                        def selinuxCmd = "getenforce 2>/dev/null || echo 'Not available'"
                        def selinuxOutput = executeRemoteCommand(computer, selinuxCmd)
                        if (selinuxOutput && !selinuxOutput.contains("Not available")) {
                            nodeInfo["selinux_status"] = selinuxOutput.trim()
                            println "  SELinux Status: ${nodeInfo.selinux_status}"
                        }
                        
                        def appArmorCmd = "aa-status 2>/dev/null || echo 'Not available'"
                        def appArmorOutput = executeRemoteCommand(computer, appArmorCmd)
                        if (appArmorOutput && !appArmorOutput.contains("Not available")) {
                            nodeInfo["apparmor_status"] = "Enabled"
                            println "  AppArmor Status: Enabled"
                        }
                    }
                } else {
                    println "  Failed to execute remote commands on this node."
                }
            } catch (Exception e) {
                println "  Error executing remote commands: ${e.message}"
                e.printStackTrace()
            }
        } else {
            println "  Node is offline - skipping detailed info collection"
            if (computer.getOfflineCause() != null) {
                nodeInfo["offline_cause"] = computer.getOfflineCause().toString()
                println "  Offline cause: ${nodeInfo.offline_cause}"
            }
        }
    } else {
        println "  No computer instance found for this node"
        nodeInfo["online"] = false
        nodeInfo["error"] = "No computer instance found"
    }
    
    allNodesInfo.add(nodeInfo)
    println ">>> Completed processing node: ${nodeName}\n"
}

// Final debug info
println "\nCollected information for ${allNodesInfo.size()} nodes"

// Print the inventory tables - clear problem report if empty
if (allNodesInfo.isEmpty()) {
    println "\nERROR: No node information collected. Review the script for errors."
} else {
    printInventoryTables(allNodesInfo)
}

/**
 * Execute a command on a node and return the output
 * This method tries multiple approaches to execute commands
 * Note: Command execution may fail on remote nodes due to Jenkins security restrictions
 */
def executeRemoteCommand(def computer, String command) {
    try {
        println "    Executing command: ${command}"
        def channel = computer.getChannel()
        if (channel == null) {
            println "    Channel not available for command execution"
            return null
        }
        
        // Only try the Callable method - skip others since they're failing due to security restrictions
        try {
            def output = channel.call(new SerializableCommandExecutor(command))
            println "    Command succeeded using Callable"
            return output
        } catch (Exception e) {
            println "    Error using Callable: ${e.message}"
            if (e.getCause()) {
                println "    Cause: ${e.getCause().message}"
            }
            // If this command is essential for node identification, set a default value based on node information
            if (command.contains("hostname")) {
                // Extract hostname from computer name or node name
                def defaultHostname = computer.getName()
                println "    Using default hostname from node: ${defaultHostname}"
                return defaultHostname
            }
        }
        
        // If command fails, return null
        println "    Command execution failed - this is normal due to Jenkins security restrictions"
        return null
    } catch (Exception e) {
        println "    Error executing command: ${e.message}"
        return null
    }
}

/**
 * Print inventory tables with the collected information
 * Uses safer methods to handle null values and string operations
 */
def printInventoryTables(List allNodesInfo) {
    println "\n\n" + "=" * 115
    println "|" + " " * 42 + "COMPREHENSIVE INVENTORY TABLE" + " " * 42 + "|"
    println "=" * 115
    
    // Debug info about table content
    println "\nPrinting inventory tables for ${allNodesInfo.size()} nodes..."
    println "Data type check: allNodesInfo is a ${allNodesInfo.getClass().getName()}"
    
    // Show a sample of the first node's data if available
    if (!allNodesInfo.isEmpty()) {
        def sampleNode = allNodesInfo[0]
        println "Sample node data (first node): " + sampleNode.node_name
        println "Available fields for this node: " + sampleNode.keySet().join(", ")
    }
    
    // BASIC SYSTEM INFORMATION
    println "\n1. BASIC SYSTEM INFORMATION"
    println "-" * 115
    printf "| %-20s | %-15s | %-10s | %-30s | %-25s |\n", 
           "NODE NAME", "IP ADDRESS", "STATUS", "OS", "ARCHITECTURE"
    println "-" * 115
    
    try {
        println "Debug: Starting to process ${allNodesInfo.size()} nodes for table 1..."
        int count = 0
        
        for (def node : allNodesInfo) {
            try {
                count++
                String status = (node.online == true) ? "Online" : "Offline"
                String ipAddress = "Unknown"
                if (node.ip_address != null) ipAddress = node.ip_address.toString()
                else if (node.node_name != null) ipAddress = node.node_name.toString()
                
                String nodeName = node.node_name != null ? node.node_name.toString() : "Unknown"
                
                String os = "Unknown"
                if (node.os_name != null) {
                    os = node.os_name.toString()
                } else if (node.is_ubuntu) {
                    os = "Ubuntu"
                    if (node.ubuntu_version) {
                        os += " " + node.ubuntu_version
                    }
                } else if (node.linux_distro) {
                    os = node.linux_distro
                } else if (node.os_details) {
                    os = node.os_details.toString()
                } else if (node.os_type) {
                    os = node.os_type.toString()
                    if (node.os_version) {
                        os += " " + node.os_version
                    }
                }
                
                String architecture = node.architecture != null ? node.architecture.toString() : "Unknown"
                
                // Safer truncation
                if (nodeName.length() > 20) nodeName = nodeName.substring(0, 17) + "..."
                if (ipAddress.length() > 15) ipAddress = ipAddress.substring(0, 12) + "..."
                if (os.length() > 30) os = os.substring(0, 27) + "..."
                if (architecture.length() > 25) architecture = architecture.substring(0, 22) + "..."
                
                printf("| %-20s | %-15s | %-10s | %-30s | %-25s |\n", 
                      nodeName, ipAddress, status, os, architecture)
                
                if (count % 50 == 0) {
                    println "Debug: Processed ${count} nodes so far for table 1..."
                }
            } catch (Exception e) {
                println "Error processing node in table 1: ${e.message}"
                // Still print something for this node
                printf("| %-20s | %-15s | %-10s | %-30s | %-25s |\n", 
                      "Error", "Error", "Error", "Error", "Error")
            }
        }
        println "Debug: Completed processing ${count} nodes for table 1"
    } catch (Exception e) {
        println "Error processing node table 1: ${e.message}"
        e.printStackTrace()
    }
    println "-" * 115
    
    // RESOURCE INFORMATION
    println "\n2. RESOURCE INFORMATION"
    println "-" * 115
    printf "| %-20s | %-15s | %-15s | %-15s | %-10s | %-22s |\n", 
           "NODE NAME", "RAM", "DISK SPACE", "EXECUTORS", "BUSY", "IDLE" 
    println "-" * 115
    
    try {
        for (def node : allNodesInfo) {
            try {
                String nodeName = node.node_name != null ? node.node_name.toString() : "Unknown"
                String ram = node.ram != null ? node.ram.toString() : (node.ram_total != null ? node.ram_total.toString() : "Unknown")
                String diskSpace = node.disk_total != null ? node.disk_total.toString() : "Unknown"
                String executors = node.executors != null ? node.executors.toString() : "Unknown"
                String busyExecutors = node.busy_executors != null ? node.busy_executors.toString() : "N/A"
                String idleExecutors = node.idle_executors != null ? node.idle_executors.toString() : "N/A"
                
                // Safe truncation
                if (nodeName.length() > 20) nodeName = nodeName.substring(0, 17) + "..."
                
                printf("| %-20s | %-15s | %-15s | %-15s | %-10s | %-22s |\n", 
                      nodeName, ram, diskSpace, executors, busyExecutors, idleExecutors)
            } catch (Exception e) {
                println "Error processing node in table 2: ${e.message}"
                // Still print something for this node
                printf("| %-20s | %-15s | %-15s | %-15s | %-10s | %-22s |\n", 
                      "Error", "Error", "Error", "Error", "Error", "Error")
            }
        }
    } catch (Exception e) {
        println "Error processing table 2: ${e.message}"
        e.printStackTrace()
    }
    println "-" * 115
    
    // CPU INFORMATION
    println "\n3. CPU INFORMATION"
    println "-" * 115
    printf "| %-20s | %-55s | %-15s | %-15s |\n", 
           "NODE NAME", "CPU MODEL", "ARCHITECTURE", "CORES"
    println "-" * 115
    
    try {
        int unknownCount = 0;
        
        for (def node : allNodesInfo) {
            try {
                String nodeName = node.node_name != null ? node.node_name.toString() : "Unknown"
                String cpuModel = node.cpu_model != null ? node.cpu_model.toString() : "Unknown"
                String architecture = node.architecture != null ? node.architecture.toString() : "Unknown"
                String cores = node.cpu_cores != null ? node.cpu_cores.toString() : "Unknown"
                
                // Safe truncation
                if (nodeName.length() > 20) nodeName = nodeName.substring(0, 17) + "..."
                if (cpuModel.length() > 55) cpuModel = cpuModel.substring(0, 52) + "..."
                if (architecture.length() > 15) architecture = architecture.substring(0, 12) + "..."
                
                if (cpuModel.equals("Unknown")) {
                    unknownCount++;
                }
                
                printf("| %-20s | %-55s | %-15s | %-15s |\n", 
                      nodeName, cpuModel, architecture, cores)
            } catch (Exception e) {
                // Still print something for this node on error
                printf("| %-20s | %-55s | %-15s | %-15s |\n", 
                      "Error", "Error", "Error", "Error")
            }
        }
        
        // Add a note if most CPU models are unknown
        if (unknownCount > allNodesInfo.size() / 2) {
            println "-" * 115
            println "| Note: Most CPU information is unavailable due to Jenkins security restrictions on            |"
            println "| remote command execution. This is expected behavior.                                         |"
        }
    } catch (Exception e) {
        println "Error processing table 3: ${e.message}"
    }
    println "-" * 115
    
    // SYSTEM MANUFACTURER & MODEL (NEW TABLE)
    println "\n4. SYSTEM HARDWARE INFORMATION"
    println "-" * 115
    printf "| %-20s | %-40s | %-40s | %-8s |\n", 
           "NODE NAME", "MANUFACTURER", "MODEL", "VIRTUAL"
    println "-" * 115
    
    try {
        int unknownCount = 0;
        
        for (def node : allNodesInfo) {
            try {
                String nodeName = node.node_name != null ? node.node_name.toString() : "Unknown"
                String manufacturer = node.system_manufacturer != null ? node.system_manufacturer.toString() : 
                                      (node.manufacturer != null ? node.manufacturer.toString() : "Unknown")
                String model = node.system_model != null ? node.system_model.toString() : "Unknown"
                String isVirtual = node.is_virtualized == true ? "Yes" : 
                                   (node.virtualization_type != null ? "Yes" : "Unknown")
                
                // Safe truncation
                if (nodeName.length() > 20) nodeName = nodeName.substring(0, 17) + "..."
                if (manufacturer.length() > 40) manufacturer = manufacturer.substring(0, 37) + "..."
                if (model.length() > 40) model = model.substring(0, 37) + "..."
                
                if (manufacturer.equals("Unknown")) {
                    unknownCount++;
                }
                
                printf("| %-20s | %-40s | %-40s | %-8s |\n", 
                      nodeName, manufacturer, model, isVirtual)
            } catch (Exception e) {
                printf("| %-20s | %-40s | %-40s | %-8s |\n", 
                       "Error", "Error", "Error", "Error")
            }
        }
        
        if (unknownCount > allNodesInfo.size() / 2) {
            println "-" * 115
            println "| Note: Hardware manufacturer/model information is limited due to security restrictions.       |"
        }
    } catch (Exception e) {
        println "Error processing hardware info table: ${e.message}"
    }
    println "-" * 115
    
    // GPU INFORMATION
    println "\n5. GPU INFORMATION"
    println "-" * 115
    printf "| %-20s | %-60s | %-25s |\n", 
           "NODE NAME", "GPU MODEL", "GPU VENDOR"
    println "-" * 115
    
    try {
        boolean foundGpu = false;
        
        for (def node : allNodesInfo) {
            try {
                // Only process nodes with GPU info
                if (node.gpu_model != null || node.has_nvidia_gpu == true || node.nvidia_gpu_info != null) {
                    foundGpu = true;
                    String nodeName = node.node_name != null ? node.node_name.toString() : "Unknown"
                    
                    String gpuModel = "GPU Present (Model Unknown)"
                    if (node.gpu_model != null) gpuModel = node.gpu_model.toString()
                    else if (node.nvidia_gpu_info != null) gpuModel = node.nvidia_gpu_info.toString()
                    
                    String gpuVendor = "Unknown"
                    if (node.has_nvidia_gpu == true || (gpuModel != null && gpuModel.toLowerCase().contains("nvidia"))) {
                        gpuVendor = "NVIDIA"
                    } else if (gpuModel != null && gpuModel.toLowerCase().contains("amd")) {
                        gpuVendor = "AMD"
                    } else if (gpuModel != null && gpuModel.toLowerCase().contains("intel")) {
                        gpuVendor = "Intel"
                    } else if (gpuModel != null && gpuModel.toLowerCase().contains("vmware")) {
                        gpuVendor = "VMware (Virtual)"
                    }
                    
                    // Safe truncation
                    if (nodeName.length() > 20) nodeName = nodeName.substring(0, 17) + "..."
                    if (gpuModel.length() > 60) gpuModel = gpuModel.substring(0, 57) + "..."
                    
                    printf("| %-20s | %-60s | %-25s |\n", 
                          nodeName, gpuModel, gpuVendor)
                }
            } catch (Exception e) {
                // Print error row
                printf("| %-20s | %-60s | %-25s |\n", 
                      "Error", "Error processing GPU info", "Unknown")
            }
        }
        
        if (!foundGpu) {
            println "| No nodes with GPU information found                                                           |"
        } else {
            println "-" * 115
            println "| Note: GPU information is limited due to Jenkins security restrictions.                        |"
            println "| GPU data is only available where it could be detected from basic system information.          |"
        }
    } catch (Exception e) {
        println "Error processing GPU table: ${e.message}"
    }
    println "-" * 115
    
    // DISK SPACE INFORMATION
    println "\n6. DISK SPACE INFORMATION"
    println "-" * 115
    printf "| %-20s | %-15s | %-70s |\n", 
           "NODE NAME", "TOTAL SPACE", "DISK PATH"
    println "-" * 115
    
    try {
        for (def node : allNodesInfo) {
            try {
                String nodeName = node.node_name != null ? node.node_name.toString() : "Unknown"
                String totalSpace = node.disk_total != null ? node.disk_total.toString() : "Unknown"
                String diskPath = node.disk_path != null ? node.disk_path.toString() : "Unknown"
                
                // Safe truncation
                if (nodeName.length() > 20) nodeName = nodeName.substring(0, 17) + "..."
                if (diskPath.length() > 70) diskPath = diskPath.substring(0, 67) + "..."
                
                printf("| %-20s | %-15s | %-70s |\n", 
                      nodeName, totalSpace, diskPath)
            } catch (Exception e) {
                // Print error row
                printf("| %-20s | %-15s | %-70s |\n", 
                      "Error", "Error", "Error")
            }
        }
    } catch (Exception e) {
        println "Error processing disk table: ${e.message}"
    }
    println "-" * 115
    
    // NETWORK INFORMATION
    println "\n7. NETWORK INFORMATION"
    println "-" * 115
    printf "| %-20s | %-15s | %-40s | %-30s |\n", 
           "NODE NAME", "IP ADDRESS", "HOSTNAME", "CONNECTIVITY"
    println "-" * 115
    
    try {
        for (def node : allNodesInfo) {
            try {
                String nodeName = node.node_name != null ? node.node_name.toString() : "Unknown"
                String ipAddress = node.ip_address != null ? node.ip_address.toString() : "Unknown"
                String hostname = node.hostname != null ? node.hostname.toString() : "Unknown"
                
                String connectivity = node.online == true ? "Connected" : "Offline"
                if (node.connect_time != null) {
                    connectivity += " since " + node.connect_time
                }
                
                // Safe truncation
                if (nodeName.length() > 20) nodeName = nodeName.substring(0, 17) + "..."
                if (ipAddress.length() > 15) ipAddress = ipAddress.substring(0, 12) + "..."
                if (hostname.length() > 40) hostname = hostname.substring(0, 37) + "..."
                if (connectivity.length() > 30) connectivity = connectivity.substring(0, 27) + "..."
                
                printf("| %-20s | %-15s | %-40s | %-30s |\n", 
                      nodeName, ipAddress, hostname, connectivity)
            } catch (Exception e) {
                // Print error row
                printf("| %-20s | %-15s | %-40s | %-30s |\n", 
                      "Error", "Error", "Error", "Error")
            }
        }
    } catch (Exception e) {
        println "Error processing network table: ${e.message}"
    }
    println "-" * 115
    
    // Filter Ubuntu nodes
    println "\n8. UBUNTU SPECIFIC INFORMATION"
    println "-" * 115
    printf "| %-20s | %-25s | %-40s | %-15s |\n", 
           "NODE NAME", "UBUNTU VERSION", "DNS SERVERS", "NTP STATUS"
    println "-" * 115
    
    try {
        boolean foundUbuntu = false;
        
        for (def node : allNodesInfo) {
            try {
                if (node.is_ubuntu == true) {
                    foundUbuntu = true;
                    String nodeName = node.node_name != null ? node.node_name.toString() : "Unknown"
                    String ubuntuVersion = node.ubuntu_version != null ? node.ubuntu_version.toString() : "Unknown Version"
                    String dnsServers = node.dns_servers != null ? node.dns_servers.toString() : "Unknown"
                    String ntpStatus = node.ntp_status != null ? node.ntp_status.toString() : "Unknown"
                    
                    // Safe truncation
                    if (nodeName.length() > 20) nodeName = nodeName.substring(0, 17) + "..."
                    if (ubuntuVersion.length() > 25) ubuntuVersion = ubuntuVersion.substring(0, 22) + "..."
                    if (dnsServers.length() > 40) dnsServers = dnsServers.substring(0, 37) + "..."
                    if (ntpStatus.length() > 15) ntpStatus = ntpStatus.substring(0, 12) + "..."
                    
                    printf("| %-20s | %-25s | %-40s | %-15s |\n", 
                          nodeName, ubuntuVersion, dnsServers, ntpStatus)
                }
            } catch (Exception e) {
                // Print error row
                printf("| %-20s | %-25s | %-40s | %-15s |\n", 
                      "Error", "Error", "Error", "Error")
            }
        }
        
        if (!foundUbuntu) {
            println "| No Ubuntu nodes found                                                                     |"
        } else {
            println "-" * 115
            println "| Note: Only Ubuntu-specific information is shown here. Other Linux distributions            |"
            println "| and Windows nodes have their OS information in other tables.                               |"
        }
    } catch (Exception e) {
        println "Error processing Ubuntu table: ${e.message}"
    }
    println "-" * 115
    
    // SECURITY INFORMATION (NEW TABLE)
    println "\n9. SECURITY INFORMATION"
    println "-" * 115
    printf "| %-20s | %-15s | %-15s | %-50s |\n", 
           "NODE NAME", "OS TYPE", "SELINUX", "SECURITY NOTES"
    println "-" * 115
    
    try {
        for (def node : allNodesInfo) {
            try {
                String nodeName = node.node_name != null ? node.node_name.toString() : "Unknown"
                String osType = node.os_type != null ? node.os_type.toString() : "Unknown"
                String selinux = node.selinux_status != null ? node.selinux_status.toString() : "N/A"
                
                String securityNotes = ""
                if (node.apparmor_status != null) {
                    securityNotes = "AppArmor: " + node.apparmor_status
                }
                
                // Safe truncation
                if (nodeName.length() > 20) nodeName = nodeName.substring(0, 17) + "..."
                if (securityNotes.length() > 50) securityNotes = securityNotes.substring(0, 47) + "..."
                
                printf("| %-20s | %-15s | %-15s | %-50s |\n", 
                      nodeName, osType, selinux, securityNotes)
            } catch (Exception e) {
                printf("| %-20s | %-15s | %-15s | %-50s |\n", 
                       "Error", "Error", "Error", "Error")
            }
        }
    } catch (Exception e) {
        println "Error processing security table: ${e.message}"
    }
    println "-" * 115
    
    // JENKINS EXECUTION INFORMATION
    println "\n10. JENKINS EXECUTION INFORMATION"
    println "-" * 115
    printf "| %-20s | %-15s | %-15s | %-15s | %-35s |\n", 
           "NODE NAME", "EXECUTORS", "BUSY", "IDLE", "UPTIME"
    println "-" * 115
    
    try {
        for (def node : allNodesInfo) {
            try {
                String nodeName = node.node_name != null ? node.node_name.toString() : "Unknown"
                String executors = node.executors != null ? node.executors.toString() : "Unknown"
                String busyExecutors = node.busy_executors != null ? node.busy_executors.toString() : "N/A"
                String idleExecutors = node.idle_executors != null ? node.idle_executors.toString() : "N/A"
                String uptime = node.uptime != null ? node.uptime.toString() : "N/A"
                
                // Safe truncation
                if (nodeName.length() > 20) nodeName = nodeName.substring(0, 17) + "..."
                if (uptime.length() > 35) uptime = uptime.substring(0, 32) + "..."
                
                printf("| %-20s | %-15s | %-15s | %-15s | %-35s |\n", 
                      nodeName, executors, busyExecutors, idleExecutors, uptime)
            } catch (Exception e) {
                // Print error row
                printf("| %-20s | %-15s | %-15s | %-15s | %-35s |\n", 
                      "Error", "Error", "Error", "Error", "Error")
            }
        }
    } catch (Exception e) {
        println "Error processing Jenkins execution table: ${e.message}"
    }
    println "-" * 115
    
    // Summary counts by OS and connection status using safer methods
    def totalNodes = allNodesInfo.size()
    
    def onlineNodes = 0
    def windowsNodes = 0
    def linuxNodes = 0
    def ubuntuCount = 0
    def virtualNodes = 0
    
    // Use safer counting with explicit checks
    for (def node : allNodesInfo) {
        try {
            if (node.online == true) onlineNodes++
            if (node.os_type == "Windows") windowsNodes++
            if (node.os_type == "Linux") linuxNodes++
            if (node.is_ubuntu == true) ubuntuCount++
            if (node.is_virtualized == true) virtualNodes++
        } catch (Exception e) {
            println "Error counting node stats: ${e.message}"
        }
    }
    
    println "\n11. INVENTORY SUMMARY"
    println "-" * 115
    println "Total nodes: ${totalNodes}"
    println "Online nodes: ${onlineNodes}"
    println "Offline nodes: ${totalNodes - onlineNodes}"
    println "Windows nodes: ${windowsNodes}"
    println "Linux nodes: ${linuxNodes}"
    println "Ubuntu nodes: ${ubuntuCount}"
    println "Virtualized nodes: ${virtualNodes}"
    println "-" * 115
    
    // Final list debug - if tables are empty
    if (allNodesInfo.isEmpty()) {
        println "\nWARNING: No node information found in the list. Check collection logic."
    } else {
        println "\nSuccessfully displayed inventory for ${allNodesInfo.size()} nodes."
        println "\nNote: Some information fields show 'Unknown' when data could not be retrieved due to Jenkins"
        println "security restrictions. This is expected behavior when running this script in a secure Jenkins"
        println "environment where remote command execution is limited."
    }
    
    println "\nInventory collection completed at: ${new Date().format('yyyy-MM-dd HH:mm:ss')}"
    println "=" * 115
}

/**
 * Format bytes to a human-readable format
 */
def formatBytes(Object bytes) {
    if (bytes == null) return "0 B"
    
    def val = 0
    try {
        val = bytes instanceof Number ? bytes.doubleValue() : Double.parseDouble(bytes.toString())
    } catch (Exception e) {
        return bytes.toString()
    }
    
    def units = ["B", "KB", "MB", "GB", "TB", "PB", "EB"]
    def divisor = 1024
    
    int unitIndex = 0
    def size = val
    
    while (size >= divisor && unitIndex < units.size() - 1) {
        size /= divisor
        unitIndex++
    }
    
    return String.format("%.2f %s", size, units[unitIndex])
}

/**
 * Format a duration in milliseconds to a human-readable format
 */
def formatDuration(def milliseconds) {
    // Convert all values to long to avoid BigDecimal issues with modulo
    long ms = milliseconds instanceof Number ? milliseconds.longValue() : 0
    long totalSeconds = ms / 1000
    long totalMinutes = totalSeconds / 60
    long totalHours = totalMinutes / 60
    long days = totalHours / 24
    
    // Use integer division and remainder operations
    long hours = totalHours - (days * 24)
    long minutes = totalMinutes - (totalHours * 60)
    long seconds = totalSeconds - (totalMinutes * 60)
    
    if (days > 0) {
        return String.format("%d days, %02d:%02d:%02d", days, hours, minutes, seconds)
    } else {
        return String.format("%02d:%02d:%02d", totalHours, minutes, seconds)
    }
}
