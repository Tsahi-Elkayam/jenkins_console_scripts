// Enable CSRF protection in Jenkins if it's disabled
println "🛡️ JENKINS CSRF PROTECTION 🛡️"
println "============================"

def jenkins = Jenkins.instance
def descriptor = jenkins.getDescriptorByType(jenkins.security.csrf.DefaultCrumbIssuer.DescriptorImpl)
def currentIssuer = descriptor.getCrumbIssuer()

if (currentIssuer == null) {
    // CSRF protection is disabled, so enable it
    println "Current Status: ❌ CSRF Protection is DISABLED"
    println "\nEnabling CSRF Protection..."
    
    try {
        descriptor.setCrumbIssuer(new jenkins.security.csrf.DefaultCrumbIssuer(true))
        jenkins.save()
        println "✅ SUCCESS: CSRF Protection has been enabled"
        println "  Configuration: DefaultCrumbIssuer with 'Prevent Cross Site Request Forgery exploits' enabled"
    } catch (Exception e) {
        println "❌ ERROR: Failed to enable CSRF Protection"
        println "  Error message: ${e.message}"
    }
} else {
    // CSRF protection is already enabled
    println "Current Status: ✅ CSRF Protection is ENABLED"
    println "  Using: ${currentIssuer.getClass().getName()}"
    
    if (currentIssuer instanceof jenkins.security.csrf.DefaultCrumbIssuer) {
        def excludeClientIPFromCrumb = currentIssuer.isExcludeClientIPFromCrumb()
        println "  Exclude Client IP From Crumb: ${excludeClientIPFromCrumb}"
        
        if (!excludeClientIPFromCrumb) {
            println "\n⚠️ WARNING: Client IP is included in the crumb"
            println "  This can cause issues with proxies or network changes."
            println "  Consider enabling 'Exclude Client IP from Crumb' for better compatibility."
            println "  You can update this with the following script:"
            println """
  def descriptor = Jenkins.instance.getDescriptorByType(jenkins.security.csrf.DefaultCrumbIssuer.DescriptorImpl)
  descriptor.setCrumbIssuer(new jenkins.security.csrf.DefaultCrumbIssuer(true))
  Jenkins.instance.save()
"""
        }
    }
}

// Provide some security recommendations
println "\n📋 CSRF SECURITY RECOMMENDATIONS:"
println "--------------------------------"
println "1. Always ensure CSRF protection is enabled in production environments"
println "2. For environments behind proxies, enable 'Exclude Client IP from Crumb'"
println "3. Keep Jenkins and all plugins up-to-date to benefit from security fixes"
println "4. Use HTTPS for Jenkins to prevent token leakage via network sniffing"
println "5. If using API automation, ensure your scripts properly handle CSRF tokens"

// Explain CSRF
println "\n📚 WHAT IS CSRF?"
println "-------------"
println "Cross-Site Request Forgery (CSRF) is an attack that forces end users to"
println "execute unwanted actions on web applications in which they're currently"
println "authenticated. With CSRF protection enabled, Jenkins requires a special"
println "token (crumb) with each request that modifies data, preventing attackers"
println "from tricking users into performing actions they did not intend to perform."

println "\n============================"