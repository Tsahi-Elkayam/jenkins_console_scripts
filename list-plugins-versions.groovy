// List all installed plugins with versions and update status
println "🔌 JENKINS PLUGINS REPORT 🔌"
println "==========================="

def plugins = Jenkins.instance.pluginManager.plugins.sort { it.getShortName().toLowerCase() }
def totalPlugins = plugins.size()
def outdatedPlugins = plugins.findAll { it.hasUpdate() }
def failedPlugins = plugins.findAll { !it.isActive() }
def bundledPlugins = plugins.findAll { it.isBundled() }
def compatWarningPlugins = plugins.findAll { it.hasCompatibilityWarning() }

println "Jenkins Version: ${Jenkins.instance.version}"
println "Total Plugins: ${totalPlugins}"
println "Outdated Plugins: ${outdatedPlugins.size()}"
println "Failed Plugins: ${failedPlugins.size()}"
println "Bundled Plugins: ${bundledPlugins.size()}"
println "Plugins with Compatibility Warnings: ${compatWarningPlugins.size()}"
println ""

// Function to format version info
def formatVersion = { plugin ->
    def version = plugin.getVersion()
    def updateInfo = plugin.getUpdateInfo()
    
    if (plugin.hasUpdate()) {
        return "${version} → ${updateInfo.availableVersion} (Update available)"
    } else if (plugin.isPinned()) {
        return "${version} (Pinned)"
    } else {
        return version
    }
}

// Print all plugins
println "📋 ALL INSTALLED PLUGINS:"
println "========================"
plugins.each { plugin ->
    println "${plugin.getShortName()}"
    println "  Display Name: ${plugin.getDisplayName()}"
    println "  Version: ${formatVersion(plugin)}"
    println "  Active: ${plugin.isActive() ? 'Yes' : 'No - ⚠️ INACTIVE'}"
    
    if (plugin.hasCompatibilityWarning()) {
        println "  ⚠️ COMPATIBILITY WARNING: This plugin may not be compatible with your Jenkins version"
    }
    
    if (plugin.isBundled()) {
        println "  Bundled: Yes (Part of Jenkins core package)"
    }
    
    if (plugin.getInfo()?.excerpt) {
        println "  Description: ${plugin.getInfo().excerpt.take(100)}${plugin.getInfo().excerpt.length() > 100 ? '...' : ''}"
    }
    
    def deps = plugin.getDependencies()
    if (deps && !deps.isEmpty()) {
        println "  Dependencies: ${deps.size()}"
        deps.take(5).each { dep ->
            println "    - ${dep.shortName} (${dep.version}${dep.optional ? ' - Optional' : ''})"
        }
        if (deps.size() > 5) {
            println "    ... and ${deps.size() - 5} more"
        }
    }
    
    println ""
}

// Print outdated plugins
if (outdatedPlugins.size() > 0) {
    println "\n🔄 OUTDATED PLUGINS:"
    println "==================="
    outdatedPlugins.sort { b, a -> 
        def versionA = a.getUpdateInfo().installedTimestamp ?: 0
        def versionB = b.getUpdateInfo().installedTimestamp ?: 0
        versionA <=> versionB
    }.each { plugin ->
        def updateInfo = plugin.getUpdateInfo()
        println "${plugin.getShortName()}"
        println "  Current Version: ${plugin.getVersion()}"
        println "  Available Version: ${updateInfo.availableVersion}"
        
        if (updateInfo.installedTimestamp) {
            def installedDate = new Date(updateInfo.installedTimestamp)
            def daysSinceUpdate = (System.currentTimeMillis() - updateInfo.installedTimestamp) / (24 * 60 * 60 * 1000)
            println "  Installed On: ${installedDate} (${daysSinceUpdate.round()} days ago)"
        }
        
        if (updateInfo.warning) {
            println "  ⚠️ Update Warning: ${updateInfo.warning}"
        }
        
        println ""
    }
}

// Print failed plugins
if (failedPlugins.size() > 0) {
    println "\n⚠️ FAILED PLUGINS:"
    println "================="
    failedPlugins.each { plugin ->
        println "${plugin.getShortName()} ${plugin.getVersion()}"
        println "  Reason: Plugin is not active"
        println "  Fix: Check logs for errors, consider reinstalling or removing"
        println ""
    }
}

// Print compatibility warnings
if (compatWarningPlugins.size() > 0) {
    println "\n⚠️ PLUGINS WITH COMPATIBILITY WARNINGS:"
    println "====================================="
    compatWarningPlugins.each { plugin ->
        println "${plugin.getShortName()} ${plugin.getVersion()}"
        println "  Warning: This plugin may not be compatible with your Jenkins version"
        println "  Fix: Check for updates or contact plugin maintainer"
        println ""
    }
}

// Print update command
println "\n📋 UPDATE COMMAND:"
println "================="
println "To update all plugins via script console:"
println """
def updated = false
def plugins = Jenkins.instance.pluginManager.activePlugins.findAll { it.hasUpdate() }
plugins.each {
    def plugin = it.getShortName()
    def version = it.getUpdateInfo().version
    println "Upgrading \${plugin} to \${version}..."
    Jenkins.instance.updateCenter.getPlugin(plugin).deploy(true).get()
    updated = true
}
if (updated) {
    Jenkins.instance.restart()
}
"""

println "==========================="