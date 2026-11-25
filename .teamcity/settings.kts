import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

/*
 * Pipeline as code. In TeamCity: Project Settings > Versioned Settings > Synchronisation
 * enabled, settings format Kotlin, "use settings from VCS".
 *
 * Week 3-4: one build. Week 8 adds image push, terraform plan, approval and apply.
 */

version = "2025.07"

project {
    vcsRoot(BankingPlatformRepo)
    buildType(BuildAndTest)
}

object BankingPlatformRepo : GitVcsRoot({
    name = "banking-platform"
    url = "https://github.com/AEdgecombe/banking-platform.git"
    branch = "refs/heads/main"
    branchSpec = "+:refs/heads/*"
})

object BuildAndTest : BuildType({
    name = "Build and test"
    description = "mvn clean verify: unit tests, then Testcontainers integration tests"

    vcs {
        root(BankingPlatformRepo)
    }

    steps {
        maven {
            name = "mvn clean verify"
            goals = "clean verify"
            runnerArgs = "-B -ntp"
            // Build inside a JDK 21 image so the agent's own JDK does not matter.
            // The Docker socket is passed through so Testcontainers can start PostgreSQL and Redis.
            dockerImage = "maven:3.9-eclipse-temurin-21"
            dockerRunParameters = "-v /var/run/docker.sock:/var/run/docker.sock"
        }
    }

    // Surefire and Failsafe reports are picked up automatically by the Maven runner.
    artifactRules = """
        api/target/banking-api.jar
        cli/target/banking-cli.jar
    """.trimIndent()

    triggers {
        vcs {
            branchFilter = "+:*"
        }
    }
})
