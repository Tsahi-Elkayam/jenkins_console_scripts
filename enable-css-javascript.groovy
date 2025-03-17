// Enable CSS and JavaScript in Jenkins job descriptions and other fields
println "🎨 ENABLE CSS AND JAVASCRIPT IN JENKINS 🎨"
println "========================================="

import jenkins.model.*
import hudson.markup.*

println "Current Markup Formatter: ${Jenkins.instance.markupFormatter?.getClass()?.getName() ?: 'None'}"

// Create a new Raw HTML markup formatter that allows CSS and JavaScript
def markupFormatter = new RawHtmlMarkupFormatter(false)

// Apply the new markup formatter
println "\nApplying Raw HTML Markup Formatter..."
try {
    Jenkins.instance.setMarkupFormatter(markupFormatter)
    Jenkins.instance.save()
    println "✅ SUCCESS: CSS and JavaScript are now enabled"
    println "Raw HTML Markup Formatter has been applied with 'disable raw HTML' set to false"
} catch (Exception e) {
    println "❌ ERROR: Failed to update markup formatter"
    println "Error details: ${e.message}"
}

// Security warning
println "\n⚠️ SECURITY WARNING ⚠️"
println "----------------------"
println "Enabling raw HTML with JavaScript poses significant security risks:"
println "1. Cross-Site Scripting (XSS) vulnerabilities"
println "2. Potential for data theft via malicious scripts"
println "3. Possible credential harvesting through fake login forms"
println "4. Client-side attacks targeting users viewing job descriptions"
println ""
println "Only use this setting in controlled environments where:"
println "- All users with job configuration rights are fully trusted"
println "- Your Jenkins instance is not exposed to untrusted networks"
println "- You have a specific need for CSS/JavaScript functionality"

// Provide safer alternatives
println "\n🛡️ SAFER ALTERNATIVES:"
println "--------------------"
println "Consider these safer options if appropriate:"
println "1. Safe HTML Markup Formatter: Allows limited HTML but blocks scripts"
println "2. Plain Text Markup Formatter: Most secure, no HTML interpretation"
println "3. Markdown Formatter: Clean formatting without script execution risk"
println ""
println "To switch to Safe HTML, use the following script:"
println """
import jenkins.model.*
import hudson.markup.*

def safeHtmlMarkupFormatter = new RawHtmlMarkupFormatter(true)
Jenkins.instance.setMarkupFormatter(safeHtmlMarkupFormatter)
Jenkins.instance.save()
"""

// How to use CSS/JS example
println "\n📚 EXAMPLE USAGE:"
println "--------------"
println "Now you can add CSS and JavaScript to job descriptions. For example:"
println """
<style>
  .highlight { color: #00A650; font-weight: bold; }
  .warning { color: #FFA500; background-color: #FFFACD; padding: 5px; }
  .error { color: #FF0000; background-color: #FFE4E1; padding: 5px; }
</style>

<div class="highlight">This is highlighted text</div>
<div class="warning">This is a warning message</div>
<div class="error">This is an error message</div>

<script>
  // Example JavaScript
  document.addEventListener('DOMContentLoaded', function() {
    // Add a button dynamically
    var button = document.createElement('button');
    button.textContent = 'Click Me';
    button.onclick = function() { 
      alert('Hello from Jenkins job description!'); 
    };
    document.body.appendChild(button);
  });
</script>
"""

println "========================================="