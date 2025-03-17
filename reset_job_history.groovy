def jobName = "MyJob"  // Change this to your job name

def job = Jenkins.instance.getItemByFullName(jobName)
if (job) {
    job.builds.each { build ->
        println "Deleting build: ${build.number}"
        build.delete()
    }
    println "Build history cleared for job: ${jobName}"
} else {
    println "Job '${jobName}' not found!"
}
