package features.configLoadingAndReloading

import framework.ReposeConfigurationProvider
import framework.ReposeLogSearch
import framework.ReposeValveLauncher
import org.rackspace.gdeproxy.Deproxy
import org.rackspace.gdeproxy.PortFinder
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Created with IntelliJ IDEA.
 * User: izrik
 *
 */
class TransitionGoodToBadConfigs extends Specification {

    int reposePort
    int stopPort
    int targetPort
    String url
    Properties properties
    def configDirectory
    ReposeConfigurationProvider reposeConfigProvider
    def logFile
    ReposeLogSearch reposeLogSearch
    ReposeValveLauncher repose
    Map params = [:]
    Deproxy deproxy

    def setup() {

        PortFinder pf = new PortFinder()
        this.reposePort = pf.getNextOpenPort() as int
        this.stopPort = pf.getNextOpenPort() as int
        this.targetPort = pf.getNextOpenPort() as int
        this.url = "http://localhost:${this.reposePort}/"

        params = [
                'reposePort': this.reposePort,
                'targetHostname': 'localhost',
                'targetPort': targetPort,
        ]

        // start a deproxy
        deproxy = new Deproxy()
        deproxy.addEndpoint(this.targetPort)

        // setup config provider
        properties = new Properties()
        properties.load(ClassLoader.getSystemResource("test.properties").openStream())
        logFile = properties.getProperty("repose.log")
        configDirectory = properties.getProperty("repose.config.directory")
        def configSamples = properties.getProperty("repose.config.samples")
        reposeConfigProvider = new ReposeConfigurationProvider(configDirectory, configSamples)

    }

    @Unroll
    def "start with good #componentLabel configs, change to bad, should still get #expectedResponseCode"() {

        given:
        // set the common and good configs
        reposeConfigProvider.cleanConfigDirectory()
        reposeConfigProvider.applyConfigsRuntime(
                "features/configLoadingAndReloading/${componentLabel}-common",
                params)
        reposeConfigProvider.applyConfigsRuntime(
                "features/configLoadingAndReloading/${componentLabel}-good",
                params)

        // start repose
        repose = new ReposeValveLauncher(
                reposeConfigProvider,
                properties.getProperty("repose.jar"),
                url,
                configDirectory,
                reposePort,
                stopPort
        )
        repose.enableDebug()
        reposeLogSearch = new ReposeLogSearch(logFile);
        repose.start(killOthersBeforeStarting: false,
                     waitOnJmxAfterStarting: false)
        repose.waitForNon500FromUrl(url)


        expect: "starting Repose with good configs should yield #expectedResponseCode"
        deproxy.makeRequest(url: url).receivedResponse.code == "${expectedResponseCode}"


        when: "the configs are changed to bad ones and we wait for Repose to pick up the change"
        reposeConfigProvider.applyConfigsRuntime(
                "features/configLoadingAndReloading/${componentLabel}-bad",
                params)
        sleep 15000

        then: "Repose should still return #expectedResponseCode"
        deproxy.makeRequest(url: url).receivedResponse.code == "${expectedResponseCode}"




        where:
        componentLabel            | expectedResponseCode
        "system-model"            | 200
        "container"               | 200
        "response-messaging"      | 200
        "rate-limiting"           | 200
        "versioning"              | 200
        "translation"             | 200
        "client-auth-n"           | 200
        "openstack-authorization" | 200
        "dist-datastore"          | 200
        "http-logging"            | 401
        "uri-identity"            | 200
        "header-identity"         | 200
        "ip-identity"             | 200
        "validator"               | 200
    }

    def cleanup() {
        if (repose) {
            repose.stop()
        }
        if (deproxy) {
            deproxy.shutdown()
        }
    }
}

