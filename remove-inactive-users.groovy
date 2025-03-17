// Remove users who haven't logged in for a specified period
def daysThreshold = 365 // Change this to your desired threshold (365 days = 1 year)
def dryRun = true // Set to false to actually delete the users
def skipAdmins = true // Set to false to include admin users in the cleanup

println "🧹 INACTIVE USERS CLEANUP 🧹"
println "============================"
println "Identifying users inactive for more than ${daysThreshold} days"
if (dryRun) {
    println "⚠️ DRY RUN MODE - No users will be deleted"
    println "    Change 'dryRun = true' to 'dryRun = false' to perform actual deletion"
}
if (skipAdmins) {
    println "ℹ️ Admin users will be skipped even if inactive"
}
println ""

import jenkins.model.*
import hudson.security.*

def threshold = System.currentTimeMillis() - (daysThreshold * 24 * 60 * 60 * 1000L)
def users = hudson.model.User.getAll()
def deleteCount = 0
def skippedCount = 0
def adminCount = 0
def errorCount = 0

// Find admin users to exclude if skipAdmins is true
def adminUsers = []
if (skipAdmins) {
    def authStrategy = Jenkins.instance.getAuthorizationStrategy()
    if (authStrategy instanceof GlobalMatrixAuthorizationStrategy) {
        authStrategy.getAllPermissions().findAll { permission, userList ->
            permission.group.title == 'Overall' && permission.name == 'Administer'
        }.each { permission, userList ->
            adminUsers.addAll(userList)
        }
    }
    println "Found ${adminUsers.size()} admin users who will be skipped:"
    adminUsers.each { println "  - ${it}" }
    println ""
}

// Process each user
users.each { user ->
    def userId = user.id
    def userName = user.fullName ?: userId
    def email = user.getProperty(hudson.tasks.Mailer.UserProperty)?.getAddress() ?: 'No email'
    
    // Skip admin users if configured
    if (skipAdmins && adminUsers.contains(userId)) {
        println "Skipping admin user: ${userName} (${userId})"
        adminCount++
        return
    }
    
    // Get last login time if available
    def lastLogin = null
    try {
        lastLogin = user.getProperty(jenkins.security.LastGrantedAuthoritiesProperty)?.timestamp
    } catch (Exception e) {
        // LastGrantedAuthoritiesProperty might not be available in all Jenkins versions
    }
    
    // Calculate days since last login
    def daysSinceLogin = -1
    if (lastLogin != null) {
        daysSinceLogin = (System.currentTimeMillis() - lastLogin) / (24 * 60 * 60 * 1000)
    }
    
    println "Checking user: ${userName} (${userId})"
    println "  Email: ${email}"
    
    if (lastLogin == null) {
        println "  Last Login: Unknown"
        println "  Status: Skipped (no login data available)"
        skippedCount++
    } else if (lastLogin < threshold) {
        println "  Last Login: ${new Date(lastLogin)} (${daysSinceLogin.round()} days ago)"
        println "  Status: Inactive (exceeds ${daysThreshold} day threshold)"
        
        if (!dryRun) {
            try {
                user.delete()
                println "  ✅ User deleted successfully"
                deleteCount++
            } catch (Exception e) {
                println "  ❌ Error deleting user: ${e.message}"
                errorCount++
            }
        } else {
            println "  ⚠️ Would delete (dry run)"
            deleteCount++
        }
    } else {
        println "  Last Login: ${new Date(lastLogin)} (${daysSinceLogin.round()} days ago)"
        println "  Status: Active (within ${daysThreshold} day threshold)"
        skippedCount++
    }
    println ""
}

// Print summary
println "📊 SUMMARY:"
println "Total users analyzed: ${users.size()}"
if (dryRun) {
    println "Users that would be deleted: ${deleteCount}"
} else {
    println "Users successfully deleted: ${deleteCount}"
}
println "Admin users skipped: ${adminCount}"
println "Other users skipped: ${skippedCount}"
println "Errors encountered: ${errorCount}"
println "============================"