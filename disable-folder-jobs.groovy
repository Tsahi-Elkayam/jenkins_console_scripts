// Disable all jobs in a specified folder
def folderName = "MyFolder" // Change this to your folder name

println "🔒 DISABLING JOBS IN FOLDER: ${folderName} 🔒"
println "========================================"

def folder = Jenkins.instance.getItemByFullName(folderName)
if (folder == null) {
    println "❌ ERROR: Folder '${folderName}' not found!"
    return
}

def jobs = []
def findJobs
findJobs = { item ->
    if (item instanceof com.cloudbees.hudson.plugins.folder.Folder) {
        item.items.each { child ->
            findJobs(child)
        }
    } else if (item instanceof hudson.model.Job) {
        jobs << item
    }
}

findJobs(folder)

def disabledCount = 0
def alreadyDisabledCount = 0
def errorCount = 0

jobs.each { job ->
    try {
        if (job.hasProperty('disabled')) {
            if (!job.disabled) {
                job.disabled = true
                job.save()
                disabledCount++
                println "✅ Disabled: ${job.fullName}"
            } else {
                alreadyDisabledCount++
                println "ℹ️ Already disabled: ${job.fullName}"
            }
        } else {
            println "⚠️ Cannot disable: ${job.fullName} (Job type does not support disabling)"
            errorCount++
        }
    } catch (Exception e) {
        println "❌ Error disabling ${job.fullName}: ${e.message}"
        errorCount++
    }
}

println "\n📊 SUMMARY:"
println "Total jobs found: ${jobs.size()}"
println "Successfully disabled: ${disabledCount}"
println "Already disabled: ${alreadyDisabledCount}"
println "Failed to disable: ${errorCount}"
println "========================================"