// Find and optionally update outdated plugins
def autoUpdate = false // Set to true to automatically update outdated plugins
def updateCore = false // Set to true to also check for and update Jenkins core
def restartAfterUpdate = false // Set to true to restart Jenkins after updates (requires autoUpdate=true)

println "🔄 JENKINS PLUGIN UPDATES CHECKER 🔄"
println "=================================="
println "Auto-Update: ${autoUpdate}"
println "Update Core: ${updateCore}"
if (autoUpdate) {
    println "Restart After Update: ${restartAfterUpdate}"
}
println ""

// Check for plugin updates
println "🔍 CHECKING FOR PLUGIN UPDATES..."
def updateCenter = Jenkins.instance.updateCenter
def result = updateCenter.updateAllSites()
def availablePluginUpdates = updateCenter.getAvailables()
def installedPlugins = Jenkins.instance.pluginManager.plugins

// Create a list of outdated plugins with details
def outdatedPlugins = []
installedPlugins.each { plugin ->
    def pluginInfo = [
        name: plugin.getShortName(),
        displayName: plugin.getDisplayName(),
        currentVersion: plugin.getVersion(),
        newVersion: null,
        releaseTimestamp: null,
        releaseUrl: null,
        security: false
    ]
    
    if (plugin.hasUpdate()) {
        def update = updateCenter.getPlugin(plugin.getShortName())
        if (update) {
            pluginInfo.newVersion = update.version
            pluginInfo.releaseTimestamp = update.releaseTimestamp
            pluginInfo.releaseUrl = update.url
            pluginInfo.security = update.isSecurityUpdate()
            outdatedPlugins << pluginInfo
        }
    }
}

// Sort by security updates first, then by plugin name
outdatedPlugins.sort { a, b ->
    if (a.security && !b.security) return -1
    if (!a.security && b.security) return 1
    return a.name <=> b.name
}

// Print outdated plugins
if (outdatedPlugins.size() > 0) {
    println "📋 OUTDATED PLUGINS (${outdatedPlugins.size()}):"
    println "========================================="
    outdatedPlugins.each { plugin ->
        println "${plugin.name}"
        println "  Display Name: ${plugin.displayName}"
        println "  Current Version: ${plugin.currentVersion}"
        println "  New Version: ${plugin.newVersion} ${plugin.security ? '🔒 SECURITY UPDATE' : ''}"
        if (plugin.releaseTimestamp) {
            println "  Release Date: ${new Date(plugin.releaseTimestamp)}"
        }
        if (plugin.releaseUrl) {
            println "  Release URL: ${plugin.releaseUrl}"
        }
        println ""
    }
} else {
    println "✅ All plugins are up to date."
}

// Check for Jenkins core update
def updateCoreDetails = null
if (updateCore) {
    println "\n🔍 CHECKING FOR JENKINS CORE UPDATE..."
    def coreUpdates = updateCenter.getCoreUpdates()
    if (coreUpdates.size() > 0) {
        def latestCore = coreUpdates.get(0)
        updateCoreDetails = [
            currentVersion: Jenkins.instance.version,
            newVersion: latestCore.version,
            security: latestCore.isSecurityUpdate(),
            url: latestCore.url
        ]
        
        println "🔄 JENKINS CORE UPDATE AVAILABLE:"
        println "================================="
        println "Current Version: ${updateCoreDetails.currentVersion}"
        println "New Version: ${updateCoreDetails.newVersion} ${updateCoreDetails.security ? '🔒 SECURITY UPDATE' : ''}"
        println "Download URL: ${updateCoreDetails.url}"
        println ""
    } else {
        println "✅ Jenkins core is up to date."
    }
}

// Update plugins if requested
def updatedPlugins = []
def failedUpdates = []

if (autoUpdate && outdatedPlugins.size() > 0) {
    println "\n🔄 UPDATING PLUGINS..."
    println "===================="
    
    outdatedPlugins.each { plugin ->
        println "Updating ${plugin.name} from ${plugin.currentVersion} to ${plugin.newVersion}..."
        def installFuture = updateCenter.getPlugin(plugin.name).deploy(true)
        try {
            installFuture.get()
            updatedPlugins << plugin
            println "  ✅ Update successful"
        } catch (Exception e) {
            failedUpdates << [name: plugin.name, error: e.message]
            println "  ❌ Update failed: ${e.message}"
        }
    }
}

// Update Jenkins core if requested
def coreUpdated = false
if (autoUpdate && updateCore && updateCoreDetails) {
    println "\n🔄 UPDATING JENKINS CORE..."
    println "========================="
    
    try {
        updateCenter.getCore().deploy(true).get()
        coreUpdated = true
        println "✅ Jenkins core updated successfully to ${updateCoreDetails.newVersion}"
        println "NOTE: A restart is required to complete the core update"
        restartAfterUpdate = true  // Force restart when core is updated
    } catch (Exception e) {
        println "❌ Jenkins core update failed: ${e.message}"
    }
}

// Print summary
println "\n📊 SUMMARY:"
println "Total installed plugins: ${installedPlugins.size()}"
println "Outdated plugins: ${outdatedPlugins.size()}"
if (autoUpdate) {
    println "Plugins updated: ${updatedPlugins.size()}"
    println "Failed updates: ${failedUpdates.size()}"
}
if (updateCore) {
    println "Core update available: ${updateCoreDetails != null}"
    if (autoUpdate && updateCoreDetails) {
        println "Core update status: ${coreUpdated ? 'Success' : 'Failed'}"
    }
}

// Restart Jenkins if requested
if (autoUpdate && restartAfterUpdate && (updatedPlugins.size() > 0 || coreUpdated)) {
    println "\n🔄 RESTARTING JENKINS..."
    println "======================="
    println "Jenkins will restart to complete the update process"
    println "This script console session will end"
    
    // Schedule a restart with a slight delay to allow this script to complete
    Thread.start {
        println "Sleeping for 10 seconds before restart"
        sleep(10000)
        Jenkins.instance.restart()
    }
} else if (autoUpdate && updatedPlugins.size() > 0) {
    println "\n⚠️ RESTART RECOMMENDED"
    println "Some plugins may require a restart to complete the update"
}

println "=================================="