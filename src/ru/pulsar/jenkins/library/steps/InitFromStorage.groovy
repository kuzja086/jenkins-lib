package ru.pulsar.jenkins.library.steps

import com.cloudbees.groovy.cps.NonCPS
import hudson.FilePath
import org.jenkinsci.plugins.workflow.support.actions.EnvironmentAction
import ru.pulsar.jenkins.library.IStepExecutor
import ru.pulsar.jenkins.library.configuration.JobConfiguration
import ru.pulsar.jenkins.library.configuration.Secrets
import ru.pulsar.jenkins.library.ioc.ContextRegistry
import ru.pulsar.jenkins.library.utils.FileUtils
import ru.pulsar.jenkins.library.utils.Logger
import ru.pulsar.jenkins.library.utils.VRunner
import ru.pulsar.jenkins.library.utils.VersionParser

import static ru.pulsar.jenkins.library.configuration.Secrets.UNKNOWN_ID

class InitFromStorage implements Serializable {

    final static REPO_SLUG_REGEXP = ~/(?m)^(?:[^:\/?#\n]+:)?(?:\/+[^\/?#\n]*)?\/?([^?\n]*)/
    final static PRELOAD_DT_LOCAL_PATH = "build/out/preload.dt"

    private final JobConfiguration config

    InitFromStorage(JobConfiguration config) {
        this.config = config
    }

    def run() {
        IStepExecutor steps = ContextRegistry.getContext().getStepExecutor()

        Logger.printLocation()

        if (config.infobaseFromFiles()) {
            Logger.println("init infoBase from storage is disabled")
            return
        }

        steps.installLocalDependencies()

        steps.createDir('build/out')

        String storageVersion = VersionParser.storage(config.getSrcDir())
        String storageVersionParameter = storageVersion == "" ? "" : "--storage-ver $storageVersion"

        EnvironmentAction env = steps.env()
        String repoSlug = computeRepoSlug(env.GIT_URL)

        Secrets secrets = config.secrets

        String storageCredentials = secrets.storage == UNKNOWN_ID ? repoSlug + "_STORAGE_USER" : secrets.storage
        String storagePath = secrets.storagePath == UNKNOWN_ID ? repoSlug + "_STORAGE_PATH" : secrets.storagePath

        steps.withCredentials([
            steps.usernamePassword(
                storageCredentials,
                'RUNNER_STORAGE_USER',
                'RUNNER_STORAGE_PWD'
            ),
            steps.string(
                storagePath,
                'RUNNER_STORAGE_NAME'
            )
        ]) {
            String vrunnerPath = VRunner.getVRunnerPath()

            String preloadDTURL = config.initInfobaseOptions.getPreloadDTURL()
            if (!preloadDTURL.isEmpty()) {
                FilePath localPathToPreloadDT = FileUtils.getFilePath("$env.WORKSPACE/$PRELOAD_DT_LOCAL_PATH")
                Logger.println("Скачивание DT в $localPathToPreloadDT")
                localPathToPreloadDT.copyFrom(new URL("$preloadDTURL"))
                Logger.println("Загрузка DT")
                VRunner.exec "$vrunnerPath init-dev --dt $localPathToPreloadDT"

                String command = vrunnerPath + "update-dev --storage $storageVersionParameter --ibconnection \"/F./build/ib\""

                String vrunnerSettings = config.initInfobaseOptions.vrunnerSettings
                if (steps.fileExists(vrunnerSettings)) {
                    command += " --settings $vrunnerSettings"
                }

                VRunner.exec(command)

            } else {
                 VRunner.exec "$vrunnerPath init-dev --storage $storageVersionParameter --ibconnection \"/F./build/ib\""
            }
        }
    }

    @NonCPS
    private static String computeRepoSlug(String text) {
        def matcher = text =~ REPO_SLUG_REGEXP
        String repoSlug = matcher != null && matcher.getCount() == 1 ? matcher[0][1] : ""
        if (repoSlug.endsWith(".git")) {
            repoSlug = repoSlug[0..-5]
        }
        repoSlug = repoSlug.replace('/', '_')
        return repoSlug
    }
}
