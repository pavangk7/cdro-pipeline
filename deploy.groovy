/*
 * Nexus Raw artifact deployment.
 *
 * Import:
 *   ectool evalDsl --dslFile nexus-raw-deploy.groovy
 *
 * Accepts:
 *   payload.asset.name
 *   payload.component.name
 *
 * Execution:
 *   CREATED / UPDATED -> retrieve and deploy
 *   DELETED / empty action -> skip deployment
 */

// UPDATE: CD/RO project.
def deploymentProject = 'CHANGE_ME_PROJECT'

// UPDATE: Existing EC-Nexus configuration.
def nexusConfiguration = 'CHANGE_ME_NEXUS_CONFIG'

// UPDATE: Exact resource name for a persistent Linux agent.
// Both tasks must use the same resource, not a resource pool.
def deploymentResource = 'CHANGE_ME_RESOURCE'

// UPDATE: Dedicated CD/RO webhook service account.
// Select this account when configuring the trigger after import.
// This variable does not create or bind the service account.
def webhookServiceAccount = 'CHANGE_ME_SERVICE_ACCOUNT'

def deploymentPipeline = 'Nexus Raw Deployment'

/*
 * Build JavaScript default expressions evaluated by CD/RO at runtime.
 * Missing runtime context or malformed/missing JSON returns "".
 *
 * Never insert incoming JSON directly into executable script text.
 */
def payloadDefault = { String extraction ->
    '''
$[/javascript
(function () {
    try {
        var payload = myPipelineRuntime.webhookData;

        if (payload === null ||
            typeof payload === "undefined" ||
            payload === "") {
            return "";
        }

        if (typeof payload === "string") {
            payload = JSON.parse(payload);
        }

        if (!payload || typeof payload !== "object") {
            return "";
        }

        __EXTRACTION__
    } catch (error) {
        return "";
    }
})()
]
'''.replace('__EXTRACTION__', extraction).trim()
}

/*
 * Shared path extraction:
 * Prefer asset.name; otherwise accept component.name.
 * Normalize leading slashes before splitting the repository-relative path.
 */
def extractRawPath = '''
    var item = payload.asset &&
               typeof payload.asset.name === "string"
        ? payload.asset : payload.component;

    var name = item && typeof item.name === "string"
        ? item.name : "";

    while (name.charAt(0) === "/") {
        name = name.substring(1);
    }

    var slash = name.lastIndexOf("/");
'''

project deploymentProject, {
    pipeline deploymentPipeline, {
        description =
            'Retrieve and deploy Nexus Raw assets. Configure EC-Webhook ' +
            'POST trigger with service account: ' + webhookServiceAccount

        /*
         * The requested staging directory is shared between runs.
         * Reject overlapping starts to prevent cross-run file overwrites.
         * This does not queue incoming webhook events.
         */
        disableMultipleActiveRuns = '1'

        formalParameter 'nexusAction', {
            type = 'entry'
            required = '0'
            defaultValue = payloadDefault('''
                return typeof payload.action === "string"
                    ? payload.action : "";
            ''')
            description =
                'CREATED, UPDATED or DELETED. Empty safely skips deployment.'
        }

        formalParameter 'repoName', {
            type = 'entry'
            required = '0'
            defaultValue = payloadDefault('''
                return typeof payload.repositoryName === "string"
                    ? payload.repositoryName : "";
            ''')
            description = 'Nexus repository name.'
        }

        formalParameter 'rawGroup', {
            type = 'entry'
            required = '0'
            defaultValue = payloadDefault(
                extractRawPath + '''
                    return slash < 0 ? "" : name.substring(0, slash);
                '''
            )
            description =
                'Repository-relative folder path; empty for root uploads.'
        }

        formalParameter 'rawArtifact', {
            type = 'entry'
            required = '0'
            defaultValue = payloadDefault(
                extractRawPath + '''
                    return name.substring(slash + 1);
                '''
            )
            description = 'Exact filename, including its extension.'
        }

        formalParameter 'targetEnv', {
            type = 'entry'
            required = '0'
            defaultValue = 'dev'
            description = 'dev, test, staging or prod.'
        }

        stage 'Retrieve and Deploy', {
            /*
             * Stage Run Condition.
             * Equivalent to checking the value referenced by $[nexusAction].
             * Direct property access avoids script-source interpolation.
             */
            condition = '''$[/javascript
(function () {
    var action = String(myPipelineRuntime.nexusAction);
    return action === "CREATED" || action === "UPDATED";
})()
]'''

            task 'Retrieve Raw Artifact', {
                taskType = 'PLUGIN'
                subpluginKey = 'EC-Nexus'
                subprocedure = 'RetrieveArtifact'
                resourceName = deploymentResource

                /*
                 * Conservative naming policy for repository paths.
                 * Supports the names in your screenshot.
                 * Review before extending to other naming conventions.
                 *
                 * Throw for invalid input rather than returning false,
                 * which would leave a precondition waiting.
                 */
                precondition = '''$[/javascript
(function () {
    var env = String(myPipelineRuntime.targetEnv);
    var repo = String(myPipelineRuntime.repoName);
    var group = String(myPipelineRuntime.rawGroup);
    var artifact = String(myPipelineRuntime.rawArtifact);

    if (!/^(dev|test|staging|prod)$/.test(env)) {
        throw new Error("Invalid targetEnv");
    }

    if (!/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(repo)) {
        throw new Error("Invalid or missing repoName");
    }

    function safeSegment(value) {
        return /^[A-Za-z0-9_][A-Za-z0-9._ -]*$/.test(value);
    }

    if (!safeSegment(artifact)) {
        throw new Error("Invalid or missing rawArtifact");
    }

    if (group !== "") {
        var segments = group.split("/");
        for (var i = 0; i < segments.length; i++) {
            if (!safeSegment(segments[i])) {
                throw new Error("Invalid rawGroup path");
            }
        }
    }

    return true;
})()
]'''

                actualParameter = [
                    config             : nexusConfiguration,
                    repository         : '$[repoName]',

                    // UI: Repository format = raw.
                    // Documented internal EC-Nexus parameter: repoType.
                    repoType           : 'raw',

                    groupId            : '$[rawGroup]',
                    artifactId         : '$[rawArtifact]',

                    // Raw uses the complete filename, including extension.
                    version            : '',
                    classifier         : '',
                    extension          : '',
                    latestVersion      : '0',

                    destination        : '/tmp/nexus-deploy/$[targetEnv]',
                    overwrite          : '1',
                    resultPropertySheet: '/myJob/nexusRetrieval'
                ]
            }

            task 'Deploy Downloaded Artifact', {
                taskType = 'COMMAND'
                resourceName = deploymentResource

                afterTask = 'Retrieve Raw Artifact'
                parallelToPrevious = '0'
                alwaysRun = '0'
                shell = 'ec-groovy'

                /*
                 * Fetch parameters through the API to avoid injecting
                 * webhook values into executable command text.
                 *
                 * Deployment publishes the file into /opt/deployments.
                 * It does not extract archives or restart an application.
                 */
                command = '''
import com.electriccloud.client.groovy.ElectricFlow
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

def ef = new ElectricFlow()

def parameter = { String name ->
    ef.getProperty(
        propertyName: '/myPipelineRuntime/' + name
    ).property.value.toString()
}

String env = parameter('targetEnv')
String artifact = parameter('rawArtifact')

if (!(env in ['dev', 'test', 'staging', 'prod'])) {
    throw new IllegalArgumentException('Invalid targetEnv')
}
if (!(artifact ==~ /[A-Za-z0-9_][A-Za-z0-9._ -]*/)) {
    throw new IllegalArgumentException('Invalid rawArtifact')
}

def sourceDir = Paths.get('/tmp/nexus-deploy', env)
def destinationDir = Paths.get('/opt/deployments', env)

def source = sourceDir.resolve(artifact)
def destination = destinationDir.resolve(artifact)

if (Files.isSymbolicLink(sourceDir) ||
    Files.isSymbolicLink(destinationDir) ||
    Files.isSymbolicLink(source) ||
    Files.isSymbolicLink(destination)) {
    throw new IllegalStateException('Symbolic links are not permitted')
}

if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
    throw new IllegalStateException(
        'Downloaded artifact is missing: ' + source
    )
}
if (!Files.isDirectory(destinationDir, LinkOption.NOFOLLOW_LINKS)) {
    throw new IllegalStateException(
        'Deployment directory is missing: ' + destinationDir
    )
}

/*
 * Copy into a temporary file on the destination filesystem, then
 * atomically replace the deployed file.
 *
 * Fail if the filesystem cannot support atomic replacement.
 */
def temporary = Files.createTempFile(
    destinationDir, '.nexus-deploy-', '.tmp'
)

try {
    Files.copy(
        source,
        temporary,
        StandardCopyOption.REPLACE_EXISTING
    )

    Files.setPosixFilePermissions(
        temporary,
        PosixFilePermissions.fromString('rw-r-----')
    )

    Files.move(
        temporary,
        destination,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING
    )

    println('Deployment completed: ' + destination)
} finally {
    Files.deleteIfExists(temporary)
}
'''
            }
        }
    }
}
