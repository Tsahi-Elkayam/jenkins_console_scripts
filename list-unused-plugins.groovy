// Find and list unused plugins in Jenkins
println "🔍 JENKINS UNUSED PLUGINS DETECTOR 🔍"
println "==================================="

// Get all installed plugins
def plugins = Jenkins.instance.pluginManager.plugins.sort { it.getShortName().toLowerCase() }
def totalPlugins = plugins.size()
println "Total installed plugins: ${totalPlugins}"
println ""

// Create a map of all plugins
def pluginMap = [:]
plugins.each { plugin ->
    pluginMap[plugin.shortName] = [
        shortName: plugin.shortName,
        displayName: plugin.getDisplayName(),
        version: plugin.getVersion(),
        dependencies: plugin.getDependencies().collect { it.shortName },
        dependents: [],
        active: plugin.isActive(),
        isUsed: false // Will be set to true if dependency or used by jobs
    ]
}

// Find dependents (plugins that depend on other plugins)
pluginMap.each { name, info ->
    info.dependencies.each { dependency ->
        if (pluginMap.containsKey(dependency)) {
            pluginMap[dependency].dependents << name
        }
    }
}

// Check for plugins used by jobs
def jobTypes = []
def jobPluginUsage = [:]

// Collect all job types in the system
Jenkins.instance.getAllItems().each { item ->
    def jobType = item.getClass().getName()
    if (!jobTypes.contains(jobType)) {
        jobTypes << jobType
    }
}

println "Step 1: Analyzing job types in the system..."
println "Found ${jobTypes.size()} different job types"

// Map job types to probable plugins
jobTypes.each { jobType ->
    // Extract potential plugin name from job type
    def probablePlugin = jobType.toLowerCase()
    
    // Common patterns in job class names that indicate plugin usage
    def patterns = [
        /hudson\.plugins\.(.+?)\./, 
        /\.(.+?)\./, 
        /org\.jenkinsci\.plugins\.(.+?)\./,
        /com\.cloudbees\.(.+?)\./
    ]
    
    patterns.each { pattern ->
        def matcher = jobType =~ pattern
        if (matcher.find()) {
            def extractedName = matcher.group(1)
            // Clean up the extracted name
            extractedName = extractedName.replaceAll("plugin", "").replaceAll("jenkins", "")
            
            // Try to map to known plugins
            pluginMap.each { pluginName, pluginInfo ->
                if (pluginName.contains(extractedName) || 
                    extractedName.contains(pluginName) ||
                    pluginInfo.displayName.toLowerCase().contains(extractedName)) {
                    
                    if (!jobPluginUsage.containsKey(pluginName)) {
                        jobPluginUsage[pluginName] = []
                    }
                    if (!jobPluginUsage[pluginName].contains(jobType)) {
                        jobPluginUsage[pluginName] << jobType
                    }
                    pluginMap[pluginName].isUsed = true
                }
            }
        }
    }
}

println "Step 2: Analyzing build steps and build wrappers..."
// Check for plugins used in build steps and wrappers
Jenkins.instance.getAllItems(hudson.model.AbstractProject.class).each { project ->
    try {
        def builders = project.getBuilders()
        builders.each { builder ->
            def builderClass = builder.getClass().getName()
            pluginMap.each { pluginName, pluginInfo ->
                if (builderClass.toLowerCase().contains(pluginName.toLowerCase())) {
                    if (!jobPluginUsage.containsKey(pluginName)) {
                        jobPluginUsage[pluginName] = []
                    }
                    if (!jobPluginUsage[pluginName].contains(builderClass)) {
                        jobPluginUsage[pluginName] << builderClass
                    }
                    pluginMap[pluginName].isUsed = true
                }
            }
        }
        
        def publishers = project.getPublishersList()
        publishers.each { publisher ->
            def publisherClass = publisher.getClass().getName()
            pluginMap.each { pluginName, pluginInfo ->
                if (publisherClass.toLowerCase().contains(pluginName.toLowerCase())) {
                    if (!jobPluginUsage.containsKey(pluginName)) {
                        jobPluginUsage[pluginName] = []
                    }
                    if (!jobPluginUsage[pluginName].contains(publisherClass)) {
                        jobPluginUsage[pluginName] << publisherClass
                    }
                    pluginMap[pluginName].isUsed = true
                }
            }
        }
        
        // Check build wrappers if available
        if (project.getBuildWrappersList) {
            def wrappers = project.getBuildWrappersList()
            wrappers.each { wrapper ->
                def wrapperClass = wrapper.getClass().getName()
                pluginMap.each { pluginName, pluginInfo ->
                    if (wrapperClass.toLowerCase().contains(pluginName.toLowerCase())) {
                        if (!jobPluginUsage.containsKey(pluginName)) {
                            jobPluginUsage[pluginName] = []
                        }
                        if (!jobPluginUsage[pluginName].contains(wrapperClass)) {
                            jobPluginUsage[pluginName] << wrapperClass
                        }
                        pluginMap[pluginName].isUsed = true
                    }
                }
            }
        }
    } catch (Exception e) {
        // Skip projects that have errors
    }
}

println "Step 3: Marking core plugins and dependencies as used..."
// Mark core plugins as used
def corePlugins = [
    "credentials", "matrix-auth", "ssh-credentials", "script-security", 
    "structs", "workflow-api", "workflow-step-api", "workflow-support",
    "cloudbees-folder", "antisamy-markup-formatter", "build-timeout", 
    "mailer", "matrix-project", "credentials-binding", "git", "git-client",
    "scm-api", "jsch", "ssh-slaves", "pam-auth", "ldap", "email-ext",
    "junit", "timestamper", "token-macro", "windows-slaves", "display-url-api"
]

corePlugins.each { plugin ->
    if (pluginMap.containsKey(plugin)) {
        pluginMap[plugin].isUsed = true
    }
}

// Mark all plugins that are dependencies of used plugins as used
def markDependenciesAsUsed
markDependenciesAsUsed = { plugin ->
    pluginMap[plugin].dependencies.each { dependency ->
        if (pluginMap.containsKey(dependency) && !pluginMap[dependency].isUsed) {
            pluginMap[dependency].isUsed = true
            markDependenciesAsUsed(dependency)
        }
    }
}

pluginMap.each { name, info ->
    if (info.isUsed) {
        markDependenciesAsUsed(name)
    }
}

// Count unused plugins
def unusedPlugins = pluginMap.findAll { name, info -> !info.isUsed }
def potentiallyUnusedPlugins = unusedPlugins.findAll { name, info -> info.dependents.isEmpty() }
def inactivatePlugins = pluginMap.findAll { name, info -> !info.active }

println "Step 4: Generating report..."
println "\n📋 UNUSED PLUGINS REPORT (Potentially Safe to Remove):"
println "=================================================="

if (potentiallyUnusedPlugins.size() > 0) {
    potentiallyUnusedPlugins.each { name, info ->
        println "${name}"
        println "  Display Name: ${info.displayName}"
        println "  Version: ${info.version}"
        println "  Status: ${info.active ? 'Active' : '⚠️ Inactive'}"
        println "  No dependencies on this plugin found"
        println ""
    }
} else {
    println "No unused plugins detected"
}

// Print inactive plugins
if (inactivatePlugins.size() > 0) {
    println "\n⚠️ INACTIVE PLUGINS:"
    println "==================="
    inactivatePlugins.each { name, info ->
        println "${name}"
        println "  Display Name: ${info.displayName}"
        println "  Version: ${info.version}"
        println "  Dependents: ${info.dependents.size() > 0 ? info.dependents.join(', ') : 'None'}"
        println ""
    }
}

// Print plugins with dependents
println "\n⚠️ UNUSED PLUGINS WITH DEPENDENTS (Remove with caution):"
println "===================================================="
def unusedWithDependents = unusedPlugins.findAll { name, info -> !info.dependents.isEmpty() }

if (unusedWithDependents.size() > 0) {
    unusedWithDependents.each { name, info ->
        println "${name}"
        println "  Display Name: ${info.displayName}"
        println "  Version: ${info.version}"
        println "  Dependents: ${info.dependents.join(', ')}"
        println ""
    }
} else {
    println "No unused plugins with dependents found"
}

// Print summary
println "\n📊 SUMMARY:"
println "Total plugins: ${totalPlugins}"
println "Used plugins: ${totalPlugins - unusedPlugins.size()}"
println "Unused plugins: ${unusedPlugins.size()}"
println "  - Safe to remove (no dependents): ${potentiallyUnusedPlugins.size()}"
println "  - Remove with caution (has dependents): ${unusedWithDependents.size()}"
println "Inactive plugins: ${inactivatePlugins.size()}"

// Print removal script example
if (potentiallyUnusedPlugins.size() > 0) {
    println "\n📝 EXAMPLE REMOVAL SCRIPT:"
    println "To remove unused plugins, you can use the following script:"
    println """
def pluginsToRemove = [
${potentiallyUnusedPlugins.collect { name, info -> "    '${name}'" }.join(",\n")}
]

def pluginManager = Jenkins.instance.pluginManager
pluginsToRemove.each { pluginName ->
    if (pluginManager.getPlugin(pluginName)) {
        println "Removing plugin: \${pluginName}"
        pluginManager.getPlugin(pluginName).disable()
    }
}

Jenkins.instance.restart()
"""
}

println "==================================="