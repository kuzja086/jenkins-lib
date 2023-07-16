package ru.pulsar.jenkins.library.configuration

import com.cloudbees.groovy.cps.NonCPS
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class Extension implements Serializable {
    String name
    String src

    boolean isCFE() {
        return this.src.endsWith("cfe")
    }

    @Override
    @NonCPS
    String toString() {
        return name
    }

}
