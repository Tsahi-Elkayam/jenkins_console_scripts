// Create a comprehensive backup of Jenkins configuration
def backupDir = "/var/jenkins_backup/" + new Date().format("yyyy-MM-dd_HH-mm-ss")
def includeWorkspaces = false // Set to true to include workspace contents (WARNING: can be very large)
def includeBuilds = false // Set to true to include build records and artifacts

println "📦 JENKINS BACKUP SCRIPT 📦"
println "==========================="
println "Backup Directory: ${backupDir}"
println "Include Workspaces: ${includeWorkspaces}"
println "Include Builds: ${includeBuilds}"
println ""

// Function to format size in human-readable format
def formatSize = { size ->
    def units = ['B', 'KB', 'MB', 'GB', 'TB']
    def unitIndex = 0
    def divisor = 1L
    
    while (size / divisor > 1024 && unitIndex < units.size() - 1) {
        unitIndex++
        divisor *= 1024
    }
    
    return String.format("%.2f %s", size / divisor, units[unitIndex])
}

try {
    // Create backup directory
    def backupDirFile = new File(backupDir)
    backupDirFile.mkdirs()
    println "✅ Created backup directory: ${backupDir}"
    
    // Backup Jenkins home directory structure
    def jenkinsHome = Jenkins.instance.rootDir
    println "Jenkins Home: ${jenkinsHome}"
    
    // Files to always include
    def criticalFiles = [
        "config.xml",             // Main Jenkins configuration
        "credentials.xml",        // Credentials
        "secrets/**",             // Secret keys
        "users/**",               // User configurations
        "nodes/**",               // Slave node configurations
        "plugins/*.jpi",          // Plugin files
        "jobs/**/config.xml",     // Job configurations
        "nodeMonitors.xml",       // Node monitors configuration
        "scriptApproval.xml",     // Script Security approvals
    ]
    
    // Conditionally include build records
    if (includeBuilds) {
        criticalFiles.add("jobs/**/builds/**")
    }
    
    // Create log file
    def logFile = new File(backupDir, "backup.log")
    def log = logFile.newPrintWriter()
    log.println("Jenkins Backup Log - ${new Date()}")
    log.println("Jenkins Home: ${jenkinsHome}")
    log.println("Included Files:")
    
    // Track backup statistics
    def fileCount = 0
    def totalSize = 0L
    def startTime = System.currentTimeMillis()
    
    // Backup critical files
    criticalFiles.each { pattern ->
        log.println("  - ${pattern}")
        println "Backing up: ${pattern}"
        
        // Handle glob patterns
        if (pattern.contains("*")) {
            def ant = new AntBuilder()
            def scanner = ant.fileScanner {
                fileset(dir: jenkinsHome) {
                    include(name: pattern)
                }
            }
            
            scanner.each { file ->
                def relativePath = file.absolutePath.substring(jenkinsHome.absolutePath.length() + 1)
                def targetFile = new File(backupDir, relativePath)
                targetFile.parentFile.mkdirs()
                
                try {
                    targetFile << file.bytes
                    def fileSize = file.length()
                    totalSize += fileSize
                    fileCount++
                    log.println("    - ${relativePath} (${formatSize(fileSize)})")
                } catch (Exception e) {
                    log.println("    - ERROR on ${relativePath}: ${e.message}")
                }
            }
        } else {
            // Simple file copy
            def sourceFile = new File(jenkinsHome, pattern)
            if (sourceFile.exists()) {
                def targetFile = new File(backupDir, pattern)
                targetFile.parentFile.mkdirs()
                
                try {
                    targetFile << sourceFile.bytes
                    def fileSize = sourceFile.length()
                    totalSize += fileSize
                    fileCount++
                    log.println("    - ${pattern} (${formatSize(fileSize)})")
                } catch (Exception e) {
                    log.println("    - ERROR on ${pattern}: ${e.message}")
                }
            }
        }
    }
    
    // Backup workspaces if requested
    if (includeWorkspaces) {
        println "Backing up workspaces (this may take a while)..."
        log.println("Workspace backups:")
        
        def workspaceDir = new File(jenkinsHome, "workspace")
        if (workspaceDir.exists()) {
            def ant = new AntBuilder()
            def scanner = ant.fileScanner {
                fileset(dir: workspaceDir) {
                    include(name: "**/*")
                }
            }
            
            scanner.each { file ->
                if (file.isFile()) {
                    def relativePath = "workspace/" + file.absolutePath.substring(workspaceDir.absolutePath.length() + 1)
                    def targetFile = new File(backupDir, relativePath)
                    targetFile.parentFile.mkdirs()
                    
                    try {
                        targetFile << file.bytes
                        def fileSize = file.length()
                        totalSize += fileSize
                        fileCount++
                        if (fileCount % 1000 == 0) {
                            log.println("    - ... (processing ${fileCount} files so far)")
                            println "  ... processed ${fileCount} files (${formatSize(totalSize)})"
                        }
                    } catch (Exception e) {
                        log.println("    - ERROR on ${relativePath}: ${e.message}")
                    }
                }
            }
        }
    }
    
    // Create a backup info file
    def infoFile = new File(backupDir, "backup-info.txt")
    infoFile.withWriter { writer ->
        writer.println("Jenkins Backup")
        writer.println("==============")
        writer.println("Date: ${new Date()}")
        writer.println("Jenkins Version: ${Jenkins.instance.version}")
        writer.println("Total Files: ${fileCount}")
        writer.println("Total Size: ${formatSize(totalSize)}")
        writer.println("Include Workspaces: ${includeWorkspaces}")
        writer.println("Include Builds: ${includeBuilds}")
        writer.println("\nInstalled Plugins:")
        
        Jenkins.instance.pluginManager.plugins.sort { it.getShortName().toLowerCase() }.each { plugin ->
            writer.println("  - ${plugin.getShortName()}: ${plugin.getVersion()}")
        }
    }
    
    // Close log file
    def endTime = System.currentTimeMillis()
    def durationSeconds = (endTime - startTime) / 1000
    log.println("\nBackup Summary:")
    log.println("  Total Files: ${fileCount}")
    log.println("  Total Size: ${formatSize(totalSize)}")
    log.println("  Duration: ${durationSeconds} seconds")
    log.close()
    
    println "\n📊 BACKUP SUMMARY:"
    println "Total Files: ${fileCount}"
    println "Total Size: ${formatSize(totalSize)}"
    println "Duration: ${durationSeconds} seconds"
    println "Backup completed successfully to: ${backupDir}"
    
    // Create readme with restoration instructions
    def readmeFile = new File(backupDir, "README.txt")
    readmeFile.withWriter { writer ->
        writer.println("Jenkins Backup Restoration Instructions")
        writer.println("=====================================")
        writer.println("To restore this backup:")
        writer.println("1. Stop Jenkins service")
        writer.println("2. Copy these files to their respective locations within your Jenkins home directory")
        writer.println("3. Start Jenkins service")
        writer.println("\nNote: If you're moving to a new server, make sure Jenkins version is compatible.")
    }
    
    println "\n✅ Backup completed successfully!"
    println "Backup location: ${backupDir}"
    
} catch (Exception e) {
    println "❌ Backup failed: ${e.message}"
    e.printStackTrace()
}

println "==========================="