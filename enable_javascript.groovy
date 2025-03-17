import jenkins.model.*
import hudson.markup.*

def jenkins = Jenkins.instance
jenkins.setMarkupFormatter(new RawHtmlMarkupFormatter(false))  // Allows raw HTML & JavaScript
jenkins.save()

println "JavaScript is now enabled in job descriptions."
