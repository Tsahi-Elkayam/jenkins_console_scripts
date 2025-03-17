// List all Jenkins users with detailed information
println "👤 JENKINS USERS REPORT 👤"
println "=========================="

def users = hudson.model.User.getAll()
println "Total Users: ${users.size()}"
println ""

// Collect user details
def userDetails = []
users.each { user ->
    def userId = user.id
    def userName = user.fullName
    def email = user.getProperty(hudson.tasks.Mailer.UserProperty)?.getAddress() ?: 'No email'
    
    // Get last login time if available
    def lastLogin = null
    try {
        lastLogin = user.getProperty(jenkins.security.LastGrantedAuthoritiesProperty)?.timestamp
    } catch (Exception e) {
        // LastGrantedAuthoritiesProperty might not be available in all Jenkins versions
    }
    
    // Get API token status if available
    def hasApiToken = false
    try {
        hasApiToken = user.getProperty(jenkins.security.ApiTokenProperty)?.getTokenList()?.size() > 0
    } catch (Exception e) {
        // ApiTokenProperty might not be available in all Jenkins versions
    }
    
    userDetails << [
        id: userId,
        name: userName,
        email: email,
        lastLogin: lastLogin,
        lastLoginDate: lastLogin ? new Date(lastLogin) : null,
        hasApiToken: hasApiToken
    ]
}

// Sort users by last login time (most recent first)
userDetails.sort { a, b -> 
    def aTime = a.lastLogin ?: 0
    def bTime = b.lastLogin ?: 0
    return bTime <=> aTime
}

// Print user details
userDetails.each { user ->
    println "User: ${user.name} (${user.id})"
    println "  Email: ${user.email}"
    
    if (user.lastLogin) {
        def daysSinceLogin = (System.currentTimeMillis() - user.lastLogin) / (24 * 60 * 60 * 1000)
        println "  Last Login: ${user.lastLoginDate} (${daysSinceLogin.round()} days ago)"
    } else {
        println "  Last Login: Unknown"
    }
    
    println "  Has API Token: ${user.hasApiToken ? 'Yes' : 'No'}"
    println ""
}

// Generate statistics
def usersWithEmail = userDetails.count { it.email != 'No email' }
def usersWithoutEmail = userDetails.count { it.email == 'No email' }
def usersWithApiToken = userDetails.count { it.hasApiToken }
def usersWithoutRecentLogin = userDetails.count { it.lastLogin == null || it.lastLogin < System.currentTimeMillis() - (365 * 24 * 60 * 60 * 1000) }

println "📊 STATISTICS:"
println "Users with email: ${usersWithEmail} (${(usersWithEmail / userDetails.size() * 100).round()}%)"
println "Users without email: ${usersWithoutEmail} (${(usersWithoutEmail / userDetails.size() * 100).round()}%)"
println "Users with API token: ${usersWithApiToken} (${(usersWithApiToken / userDetails.size() * 100).round()}%)"
println "Users without login in past year: ${usersWithoutRecentLogin} (${(usersWithoutRecentLogin / userDetails.size() * 100).round()}%)"

println "=========================="