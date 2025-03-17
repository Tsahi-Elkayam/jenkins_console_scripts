import jenkins.model.*
import hudson.markup.*

def jenkins = Jenkins.instance
jenkins.setMarkupFormatter(new RawHtmlMarkupFormatter(false))
jenkins.save()

println "CSS is now enabled by setting markup formatter to 'Raw HTML'."
