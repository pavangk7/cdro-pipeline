// CloudBees CD/RO Groovy DSL (100% Groovy: DSL + ec-groovy task script)
// Pipeline + EC-Webhook trigger to receive Sonatype Nexus Repository webhook events,
// inspect/pretty-print the incoming webhook payload and headers using only Groovy
// (no sh, jq, python3, or JavaScript property evaluation), and persist extracted
// Nexus fields onto /myPipelineRuntime/nexus/* for downstream stages.
//
// Import using:
//   ectool evalDsl --dslFile pipeline-groovy.groovy
// Or paste into CloudBees CD/RO UI -> DevOps Essentials -> DSL IDE.

def targetProjectName  = 'Nexus-Webhook-Demo'
def targetPipelineName = 'Nexus-Artifact-Webhook-Groovy-Pipeline'
def targetTriggerName  = 'Nexus-Artifact-Webhook-Groovy-Trigger'
def webhookServiceAcct = 'nexus-webhook-sa' // Ensure this Service Account exists and has Execute access on the pipeline

project targetProjectName, {
    description = 'Project containing Nexus Repository EC-Webhook triggered pipelines.'

    pipeline targetPipelineName, {
        description = 'Pure-Groovy pipeline triggered by Nexus Repository via EC-Webhook. Uses ec-groovy to inspect, parse, and publish webhook payload fields.'

        stage 'Inspect-Webhook-Payload', {
            colorCode = '#289ce1'

            task 'Print-Webhook-Payload', {
                description = 'Uses ec-groovy to read, pretty-print, and parse EC-Webhook payload (webhookData) and headers (webhookHeaders).'
                taskType = 'COMMAND'
                subpluginKey = 'EC-Core'
                subprocedure = 'RunCommand'

                actualParameter = [
                    'shellToUse': 'ec-groovy',
                    'commandToRun': '''
import com.electriccloud.client.groovy.ElectricFlow
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

ElectricFlow ef = new ElectricFlow()
String jobStepId = System.getenv('COMMANDER_JOBSTEPID')

def readRuntimeProperty = { String propPath ->
    try {
        Map args = [propertyName: propPath]
        if (jobStepId) {
            args.jobStepId = jobStepId
        }
        def res = ef.getProperty(args)
        return res?.property?.value?.toString()
    } catch (Exception ignored) {
        return null
    }
}

def writeRuntimeProperty = { String propPath, Object val ->
    if (val == null) {
        return
    }
    try {
        Map args = [propertyName: propPath, value: val.toString()]
        if (jobStepId) {
            args.jobStepId = jobStepId
        }
        ef.setProperty(args)
    } catch (Exception e) {
        println "Warning: could not set ${propPath}: ${e.message}"
    }
}

def formatJsonOrRaw = { String raw ->
    if (!raw?.trim()) {
        return '{}'
    }
    try {
        return JsonOutput.prettyPrint(raw)
    } catch (Exception ignored) {
        return raw
    }
}

String rawHeaders = readRuntimeProperty('/myPipelineRuntime/webhookHeaders')
String rawData    = readRuntimeProperty('/myPipelineRuntime/webhookData')

println '============================================================'
println '  CloudBees CD/RO - Incoming EC-Webhook Payload Inspector'
println '  (Pure Groovy / ec-groovy)'
println '============================================================'
println ''
println '--- 1. Incoming HTTP Headers (/myPipelineRuntime/webhookHeaders) ---'
println formatJsonOrRaw(rawHeaders)

println ''
println '--- 2. Incoming Payload Body (/myPipelineRuntime/webhookData) ---'
println formatJsonOrRaw(rawData)

println ''
println '--- 3. Common Nexus Payload Fields (if present) ---'
if (!rawData?.trim()) {
    println 'No webhookData found (was this pipeline run manually instead of via webhook?).'
    println '============================================================'
    return
}

def parsed
try {
    parsed = new JsonSlurper().parseText(rawData)
} catch (Exception e) {
    println "Could not parse payload as JSON: ${e.message}"
    println '============================================================'
    return
}

if (!(parsed instanceof Map) || parsed.isEmpty()) {
    println 'No JSON object fields found in webhookData.'
    println '============================================================'
    return
}

Map data = (Map) parsed
println "Action         : ${data.action ?: 'N/A'}"
println "Repository     : ${data.repositoryName ?: 'N/A'}"
println "Timestamp      : ${data.timestamp ?: 'N/A'}"
println "Initiator      : ${data.initiator ?: 'N/A'}"

writeRuntimeProperty('/myPipelineRuntime/nexus/action', data.action)
writeRuntimeProperty('/myPipelineRuntime/nexus/repositoryName', data.repositoryName)
writeRuntimeProperty('/myPipelineRuntime/nexus/timestamp', data.timestamp)
writeRuntimeProperty('/myPipelineRuntime/nexus/initiator', data.initiator)

if (data.component instanceof Map) {
    Map c = (Map) data.component
    println 'Event Object   : component'
    println "  Format       : ${c.format ?: 'N/A'}"
    println "  Group        : ${c.group ?: 'N/A'}"
    println "  Name         : ${c.name ?: 'N/A'}"
    println "  Version      : ${c.version ?: 'N/A'}"
    println "  Component ID : ${c.componentId ?: 'N/A'}"

    writeRuntimeProperty('/myPipelineRuntime/nexus/eventType', 'component')
    writeRuntimeProperty('/myPipelineRuntime/nexus/format', c.format)
    writeRuntimeProperty('/myPipelineRuntime/nexus/group', c.group)
    writeRuntimeProperty('/myPipelineRuntime/nexus/name', c.name)
    writeRuntimeProperty('/myPipelineRuntime/nexus/version', c.version)
    writeRuntimeProperty('/myPipelineRuntime/nexus/componentId', c.componentId)
}

if (data.asset instanceof Map) {
    Map a = (Map) data.asset
    println 'Event Object   : asset'
    println "  Format       : ${a.format ?: 'N/A'}"
    println "  Name         : ${a.name ?: 'N/A'}"
    println "  Asset ID     : ${a.assetId ?: 'N/A'}"

    writeRuntimeProperty('/myPipelineRuntime/nexus/eventType', 'asset')
    writeRuntimeProperty('/myPipelineRuntime/nexus/format', a.format)
    writeRuntimeProperty('/myPipelineRuntime/nexus/assetName', a.name)
    writeRuntimeProperty('/myPipelineRuntime/nexus/assetId', a.assetId)
}

println '============================================================'
'''
                ]
            }
        }

        // EC-Webhook trigger attached to this pipeline
        trigger targetTriggerName, {
            description = 'Triggers the pure-Groovy pipeline when Sonatype Nexus Repository sends a webhook event.'
            enabled = '1'
            pluginKey = 'EC-Webhook'
            triggerType = 'webhook'
            serviceAccountName = webhookServiceAcct
            // Optional: set quietTimeMinutes to throttle rapid bursts of Nexus asset events
            // quietTimeMinutes = '0'
        }
    }
}
