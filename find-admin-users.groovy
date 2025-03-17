// Find users with administrator rights in Jenkins
println "🔑 JENKINS ADMIN USERS REPORT 🔑"
println "==============================="

import jenkins.model.*
import hudson.security.*

def authStrategy = Jenkins.instance.getAuthorizationStrategy()
def adminUsers = []
def configureUsers = []
def otherPrivilegedUsers = []

if (authStrategy instanceof GlobalMatrixAuthorizationStrategy) {
    println "Authorization Strategy: GlobalMatrixAuthorizationStrategy"

    // Find users with admin permissions
    authStrategy.getAllPermissions().findAll { permission, userList ->
        permission.group.title == 'Overall' && permission.name == 'Administer'
    }.each { permission, userList ->
        adminUsers.addAll(userList)
    }

    // Find users with configure permissions
    authStrategy.getAllPermissions().findAll { permission, userList ->
        permission.group.title == 'Overall' && permission.name == 'SystemRead'
    }.each { permission, userList ->
        configureUsers.addAll(userList)
    }

    // Find users with other significant permissions
    def significantPermissions = [
        'hudson.model.Hudson.Run',
        'hudson.model.Item.Create',
        'hudson.model.Item.Configure',
        'hudson.model.Item.Delete',
        'hudson.model.View.Create',
        'hudson.model.View.Configure',
        'hudson.model.View.Delete',
        'hudson.model.Computer.Configure',
        'hudson.model.Computer.Delete',
        'hudson.model.Computer.Connect',
        'hudson.model.Computer.Disconnect',
        'com.cloudbees.plugins.credentials.CredentialsProvider.Update'
    ]

    significantPermissions.each { permString ->
        authStrategy.getAllPermissions().findAll { permission, userList ->
            permission.toString().contains(permString)
        }.each { permission, userList ->
            otherPrivilegedUsers.addAll(userList)
        }
    }

    // Remove admins from the other lists to avoid duplicates
    configureUsers.removeAll(adminUsers)
    otherPrivilegedUsers.removeAll(adminUsers)
    otherPrivilegedUsers.removeAll(configureUsers)

} else if (authStrategy instanceof ProjectMatrixAuthorizationStrategy) {
    println "Authorization Strategy: ProjectMatrixAuthorizationStrategy"
    // Similar logic for ProjectMatrixAuthorizationStrategy

    // Find users with admin permissions
    authStrategy.getAllPermissions().findAll { permission, userList ->
        permission.group.title == 'Overall' && permission.name == 'Administer'
    }.each { permission, userList ->
        adminUsers.addAll(userList)
    }

    // Similar logic for other permissions...

} else if (authStrategy instanceof hudson.security.AuthorizationStrategy.Unsecured) {
    println "Authorization Strategy: Unsecured (anyone can do anything)"
    adminUsers << "anonymous"
} else if (authStrategy instanceof hudson.security.LegacyAuthorizationStrategy) {
    println "Authorization Strategy: Legacy (build users can do anything)"
    adminUsers << "authenticated"
} else {
    println "Authorization Strategy: ${authStrategy.getClass().getName()}"
    println "Warning: Cannot determine admin users for this authorization strategy"
}

// Get user details
def getDisplayName = { userId ->
    def user = hudson.model.User.getByIdOrNull(userId)
    return user ? user.getDisplayName() : userId
}

def getEmail = { userId ->
    def user = hudson.model.User.getByIdOrNull(userId)
    return user ? user.getProperty(hudson.tasks.Mailer.UserProperty)?.getAddress() ?: 'No email' : 'No email'
}

// Get security realm info
def securityRealm = Jenkins.instance.getSecurityRealm()
println "Security Realm: ${securityRealm.getClass().getSimpleName()}"
println ""

// Print administrator users
println "👑 ADMINISTRATOR USERS (Full Control):"
println "------------------------------------"
if (adminUsers.size() > 0) {
    adminUsers.sort().each { userId ->
        println "User: ${getDisplayName(userId)} (${userId})"
        println "  Email: ${getEmail(userId)}"
        println ""
    }
} else {
    println "None found"
    println ""
}

// Print configure users
println "🔧 SYSTEM READER USERS (Can view system configuration):"
println "---------------------------------------------------"
if (configureUsers.size() > 0) {
    configureUsers.sort().each { userId ->
        println "User: ${getDisplayName(userId)} (${userId})"
        println "  Email: ${getEmail(userId)}"
        println ""
    }
} else {
    println "None found"
    println ""
}

// Print other privileged users
println "👤 OTHER PRIVILEGED USERS:"
println "------------------------"
if (otherPrivilegedUsers.size() > 0) {
    otherPrivilegedUsers.sort().each { userId ->
        println "User: ${getDisplayName(userId)} (${userId})"
        println "  Email: ${getEmail(userId)}"
        println ""
    }
} else {
    println "None found"
    println ""
}

// Print security recommendations
println "🛡️ SECURITY RECOMMENDATIONS:"
println "--------------------------"
println "1. Regularly audit administrative access"
println "2. Implement the principle of least privilege"
println "3. Use role-based access control when possible"
println "4. Consider implementing multi-factor authentication"
println "5. Rotate API tokens regularly"
println "6. Ensure admin users have secure, unique passwords"
println "7. Remove admin rights from service accounts when possible"

println "==============================="
