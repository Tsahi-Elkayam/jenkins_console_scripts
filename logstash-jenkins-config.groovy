/**
 * Jenkins Logstash Plugin Configuration Script
 *
 * This script configures the Jenkins Logstash plugin to send logs to a Logstash server.
 * It can be run from the Jenkins Script Console (Manage Jenkins > Script Console).
 */

import jenkins.model.*
import jenkins.plugins.logstash.*
import jenkins.plugins.logstash.configuration.*
import java.net.URI

// ========== CONFIGURATION PARAMETERS ==========
// Modify these parameters to match your environment
def logstashHost = "logstash.logstash-network.svc.cluster.local"
def logstashPort = 5045
def connectionTimeout = 5000  // Connection timeout in milliseconds
def enableGlobally = true     // Whether to enable Logstash for all jobs
def useMillisecondTimestamps = true  // Use millisecond precision for timestamps
def mimeType = "application/json"  // MIME type for the indexer
def protocol = "http"  // Protocol for connecting to Logstash (http or https)
def waitForJenkinsInit = 3000  // Time to wait for Jenkins initialization (ms)
// =============================================

// Wait for Jenkins to fully initialize
Thread.sleep(waitForJenkinsInit)

def jenkins = Jenkins.getInstance()
println("Starting Logstash plugin configuration...")

// Check if Logstash plugin is installed
def logstashPlugin = jenkins.pluginManager.getPlugin("logstash")
if (logstashPlugin == null) {
    println("ERROR: Logstash plugin is not installed!")
    return
}
println("Logstash plugin found: ${logstashPlugin.version}")

try {
    // Get LogstashConfiguration descriptor
    println("Getting LogstashConfiguration descriptor...")
    def descriptor = jenkins.getDescriptorByType(LogstashConfiguration.class)

    if (descriptor == null) {
        // Alternative approach to get the descriptor from extension list
        def extensionList = jenkins.getExtensionList(LogstashConfiguration.class)
        if (!extensionList.isEmpty()) {
            descriptor = extensionList.get(0)
            println("Found descriptor via extension list")
        } else {
            println("ERROR: Could not find LogstashConfiguration descriptor")
            return
        }
    }

    // Test the connection to Logstash
    println("Testing connection to Logstash at ${logstashHost}:${logstashPort}...")
    def socket = new Socket()
    try {
        socket.connect(new InetSocketAddress(logstashHost, logstashPort), connectionTimeout)
        println("SUCCESS: Connection to Logstash server successful")
    } catch (Exception ex) {
        println("WARNING: Failed to connect to Logstash server: ${ex.message}")
        println("Continuing with configuration - the service might not be up yet")
    } finally {
        try { socket.close() } catch (Exception ignored) {}
    }

    // Configure the Logstash indexer
    println("Configuring the Logstash indexer...")
    def indexer = null

    // Create an ElasticSearch indexer
    def elasticSearchClass = Class.forName("jenkins.plugins.logstash.configuration.ElasticSearch",
                                         true, logstashPlugin.classLoader)
    indexer = elasticSearchClass.newInstance()

    // Create and set URI
    def uri = new URI(protocol, null, logstashHost, logstashPort, null, null, null)
    println("Created URI: ${uri}")

    if (indexer.metaClass.respondsTo(indexer, "setUri", URI)) {
        indexer.setUri(uri)
    } else if (indexer.metaClass.respondsTo(indexer, "setUri", String)) {
        indexer.setUri(uri.toString())
    }

    // Set MIME type if the method exists
    if (indexer.metaClass.respondsTo(indexer, "setMimeType")) {
        indexer.setMimeType(mimeType)
    }

    // Set the indexer in the descriptor
    if (descriptor.metaClass.respondsTo(descriptor, "setLogstashIndexer")) {
        descriptor.setLogstashIndexer(indexer)
    }

    // Enable Logstash
    if (descriptor.metaClass.respondsTo(descriptor, "setEnabled")) {
        descriptor.setEnabled(true)
        println("Enabled Logstash")
    }

    // Set additional configurations if available
    if (descriptor.metaClass.respondsTo(descriptor, "setEnableGlobally")) {
        descriptor.setEnableGlobally(enableGlobally)
        println("Enabled Logstash globally: ${enableGlobally}")
    }

    if (descriptor.metaClass.respondsTo(descriptor, "setMilliSecondTimestamps")) {
        descriptor.setMilliSecondTimestamps(useMillisecondTimestamps)
        println("Enabled millisecond timestamps: ${useMillisecondTimestamps}")
    }

    // Save configuration
    descriptor.save()
    jenkins.save()
    println("Configuration saved successfully")

    // Display the current configuration
    println("\n=== Current Logstash Configuration ===")
    println("Enabled: ${descriptor.isEnabled()}")
    println("Indexer type: ${descriptor.getLogstashIndexer().getClass().simpleName}")
    if (descriptor.metaClass.respondsTo(descriptor, "getEnableGlobally")) {
        println("Enabled globally: ${descriptor.getEnableGlobally()}")
    }

    println("\nLogstash configuration completed successfully")

} catch (Exception e) {
    println("\nERROR during Logstash configuration: ${e.message}")
    e.printStackTrace()
}
