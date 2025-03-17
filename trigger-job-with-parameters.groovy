// Trigger a parameterized job with specific parameters
def jobName = "MyJob" // Change this to your job name
def params = [
    new StringParameterValue("BRANCH", "develop"),
    new StringParameterValue("ENVIRONMENT", "staging"),
    new BooleanParameterValue("DEBUG", true)
    // Add more parameters as needed
]

println "🚀 TRIGGERING PARAMETERIZED BUILD 🚀"
println "===================================="

// Find the job
def job = Jenkins.instance.getItemByFullName(jobName)
if (job == null) {
    println "❌ ERROR: Job '${jobName}' not found!"
    return
}

println "Job: ${job.fullName}"
println "Parameters:"
params.each { param ->
    println "  ${param.name} = ${param.value}"
}

// Check if job accepts parameters
def isParameterized = job.isParameterized()
if (!isParameterized) {
    println "\n⚠️ WARNING: This job doesn't appear to be parameterized!"
    println "Proceeding anyway, but parameters may be ignored."
}

// Trigger the build
try {
    def cause = new hudson.model.Cause.UserIdCause(userName: "Script")
    def future = job.scheduleBuild2(0, cause, new ParametersAction(params))
    
    if (future != null) {
        println "\n✅ Build scheduled successfully!"
        println "Build number: ${future.get()?.number}"
        println "URL: ${Jenkins.instance.rootUrl}${future.get()?.url}"
    } else {
        println "\n⚠️ Build scheduled, but unable to get future object."
        println "Check job status manually."
    }
} catch (Exception e) {
    println "\n❌ ERROR: Failed to schedule build: ${e.message}"
}

println "\n===================================="