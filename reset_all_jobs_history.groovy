Jenkins.instance.getAllItems(Job).each { job ->
    job.builds.each { build ->
        println "Deleting build ${build.number} from ${job.fullName}"
        build.delete()
    }
}
println "All job histories have been reset."
