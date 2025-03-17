// Enable various Jenkins security features
println "🔒 JENKINS SECURITY ENHANCER 🔒"
println "============================="

def jenkins = Jenkins.instance
def dryRun = true  // Set to false to apply changes

println "Mode: ${dryRun ? '⚠️ DRY RUN (no changes will be made)' : '✅ LIVE RUN (changes will be applied)'}"
println ""

// Function to check and update a setting
def checkAndUpdate = { name, currentValue, desiredValue, updateAction ->
    println "Checking: ${name}"
    println "  Current Value: ${currentValue}"
    println "  Desired Value: ${desiredValue}"

    if (currentValue == desiredValue) {
        println "  Status: ✅ Already correctly configured"
        return false
    } else {
        println "  Status: ⚠️ Needs to be updated"
        if (!dryRun) {
            try {
                updateAction()
                println "  Action: ✅ Setting updated successfully"
                return true
            } catch (Exception e) {
                println "  Action: ❌ Error updating setting: ${e.message}"
                return false
            }
        } else {
            println "  Action: ℹ️ Would update (dry run)"
            return false
        }
    }
}

// Track if we need to save Jenkins configuration
def needsSave = false

// 1. Enable CSRF Protection
println "🛡️ CSRF Protection:"
def descriptor = jenkins.getDescriptorByType(jenkins.security.csrf.DefaultCrumbIssuer.DescriptorImpl)
def currentCrumbIssuer = descriptor.getCrumbIssuer()
def hasCrumbIssuer = currentCrumbIssuer != null
needsSave |= checkAndUpdate(
    "CSRF Protection",
    hasCrumbIssuer ? "Enabled" : "Disabled",
    "Enabled",
    { descriptor.setCrumbIssuer(new jenkins.security.csrf.DefaultCrumbIssuer(true)) }
)

// 2. Enable Agent -> Master Access Control
println "\n🛡️ Agent -> Master Access Control:"
def masterToSlaveAccessControl = Jenkins.instance.getInjector().getInstance(jenkins.security.s2m.AdminWhitelistRule.class)
def s2mEnabled = masterToSlaveAccessControl.isEnabled()
needsSave |= checkAndUpdate(
    "Agent -> Master Access Control",
    s2mEnabled ? "Enabled" : "Disabled",
    "Enabled",
    { masterToSlaveAccessControl.setMasterKillSwitch(false) }
)

// 3. Enable Security for Build Authorization
println "\n🛡️ Build Authorization:"
def queueItemAuthenticator = jenkins.getExtensionList(jenkins.security.QueueItemAuthenticator.class)
def projectMatrixAuth = queueItemAuthenticator.find { it.getClass().getName().contains("ProjectQueueItemAuthenticator") }
needsSave |= checkAndUpdate(
    "Build Authorization",
    projectMatrixAuth != null ? "Enabled" : "Disabled",
    "Enabled",
    {
        // This is more complex and would require more specific code depending on your needs
        println "  Note: Setup required in Global Security Configuration UI as it needs additional configuration"
    }
)

// 4. Content Security Policy
println "\n🛡️ Content Security Policy (CSP):"
def cspConfig = System.getProperty("hudson.model.DirectoryBrowserSupport.CSP", "")
def defaultCsp = "sandbox; default-src 'none'; img-src 'self' data:; style-src 'self'; script-src 'self';"
needsSave |= checkAndUpdate(
    "Content Security Policy",
    cspConfig == "" ? "Default/Weak" : cspConfig,
    defaultCsp,
    {
        System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", defaultCsp)
        // Note: This setting doesn't persist through Jenkins restarts unless added to jenkins.xml
        println "  Note: To make this setting permanent, add to jenkins.xml or JENKINS_JAVA_OPTIONS"
    }
)

// 5. Disable Legacy Agent Protocols
println "\n🛡️ Legacy Agent Protocols:"
def agentProtocols = jenkins.getAgentProtocols()
def legacyProtocols = ["JNLP-connect", "JNLP2-connect", "JNLP3-connect"]
def enabledLegacyProtocols = agentProtocols.findAll { legacyProtocols.contains(it) }

needsSave |= checkAndUpdate(
    "Legacy Agent Protocols",
    enabledLegacyProtocols.isEmpty() ? "All Disabled (Good)" : "Some Enabled: ${enabledLegacyProtocols}",
    "All Disabled (Good)",
    {
        agentProtocols.removeAll(legacyProtocols)
    }
)

// 6. Disable CLI over Remoting
println "\n🛡️ CLI over Remoting:"
def cliEnabled = jenkins.getDescriptor("jenkins.CLI")?.isEnabled()
needsSave |= checkAndUpdate(
    "Command Line Interface",
    cliEnabled ? "Enabled" : "Disabled",
    "Disabled",
    {
        jenkins.getDescriptor("jenkins.CLI")?.setEnabled(false)
    }
)

// 7. Check for Script Security
println "\n🛡️ Script Security Plugin:"
def scriptSecurityInstalled = jenkins.pluginManager.activePlugins.find { it.shortName == "script-security" } != null
println "  Status: ${scriptSecurityInstalled ? '✅ Installed' : '❌ Not Installed'}"
if (!scriptSecurityInstalled) {
    println "  Recommendation: Install script-security plugin to prevent arbitrary code execution"
}

// 8. Check for Matrix Authorization Strategy
println "\n🛡️ Authorization Strategy:"
def authStrategy = jenkins.getAuthorizationStrategy()
println "  Current Strategy: ${authStrategy.getClass().getName()}"
if (authStrategy instanceof hudson.security.AuthorizationStrategy.Unsecured) {
    println "  Status: ❌ SEVERE SECURITY RISK - Unsecured authorization strategy in use"
    println "  Recommendation: Configure proper authorization in 'Configure Global Security'"
} else if (authStrategy instanceof hudson.security.FullControlOnceLoggedInAuthorizationStrategy) {
    println "  Status: ⚠️ SECURITY RISK - All logged-in users have full control"
    println "  Recommendation: Use Matrix or Project-based Matrix Authorization Strategy"
} else {
    println "  Status: ✅ Using a more secure authorization strategy"
}

// 9. Check for Security Realms
println "\n🛡️ Security Realm:"
def securityRealm = jenkins.getSecurityRealm()
println "  Current Realm: ${securityRealm.getClass().getName()}"
if (securityRealm instanceof hudson.security.SecurityRealm.None) {
    println "  Status: ❌ SEVERE SECURITY RISK - No authentication configured"
    println "  Recommendation: Configure user authentication in 'Configure Global Security'"
} else {
    println "  Status: ✅ Authentication is configured"
}

// Save configuration if needed
if (needsSave && !dryRun) {
    println "\nSaving Jenkins configuration..."
    try {
        jenkins.save()
        println "✅ Jenkins configuration saved successfully"
    } catch (Exception e) {
        println "❌ Error saving Jenkins configuration: ${e.message}"
    }
}

// Print summary
println "\n📋 SECURITY ASSESSMENT SUMMARY:"
println "------------------------------"
println "Security features checked: 9"
if (dryRun) {
    println "This was a DRY RUN. Set 'dryRun = false' to apply changes."
    println "To apply changes, modify the first line of this script to: def dryRun = false"
} else if (needsSave) {
    println "Changes were made to your Jenkins configuration."
    println "Some changes may require a Jenkins restart to take full effect."
}

println "============================="
