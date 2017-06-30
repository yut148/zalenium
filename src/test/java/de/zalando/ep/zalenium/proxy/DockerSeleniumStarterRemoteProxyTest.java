package de.zalando.ep.zalenium.proxy;

import com.spotify.docker.client.exceptions.DockerException;
import de.zalando.ep.zalenium.container.ContainerClient;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.TestUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSession;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.timeout;

@RunWith(value = Parameterized.class)
public class DockerSeleniumStarterRemoteProxyTest {

    private DockerSeleniumStarterRemoteProxy spyProxy;
    private Registry registry;
    private ContainerClient containerClient;
    private RegistrationRequest request;
    private Dimension configuredScreenSize;
    private TimeZone configuredTimeZone;

    public DockerSeleniumStarterRemoteProxyTest(ContainerClient containerClient) {
        this.containerClient = containerClient;
    }

    // Using parameters now, so in the future we can add just something like "TestUtils.getMockedKubernetesClient()"
    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {TestUtils.getMockedDockerContainerClient()}
        });
    }


    @Before
    public void setUp() throws DockerException, InterruptedException {
        registry = Registry.newInstance();

        // Creating the configuration and the registration request of the proxy (node)
        request = TestUtils.getRegistrationRequestForTesting(30000,
                DockerSeleniumStarterRemoteProxy.class.getCanonicalName());

        // Creating the proxy
        DockerSeleniumStarterRemoteProxy.setContainerClient(containerClient);
        DockerSeleniumStarterRemoteProxy.setSleepIntervalMultiplier(0);
        DockerSeleniumStarterRemoteProxy proxy = DockerSeleniumStarterRemoteProxy.getNewInstance(request, registry);

        configuredScreenSize = DockerSeleniumStarterRemoteProxy.getConfiguredScreenSize();
        configuredTimeZone = DockerSeleniumStarterRemoteProxy.getConfiguredTimeZone();

        // Spying on the proxy to see if methods are invoked or not
        spyProxy = spy(proxy);
    }

    @After
    public void afterMethod() {
        registry.removeIfPresent(spyProxy);
        DockerSeleniumStarterRemoteProxy.setSleepIntervalMultiplier(1000);
        DockerSeleniumStarterRemoteProxy.restoreEnvironment();
    }

    @AfterClass
    public static void tearDown() {
        DockerSeleniumStarterRemoteProxy.restoreContainerClient();
        DockerSeleniumStarterRemoteProxy.restoreEnvironment();
    }

    @Test
    public void noContainerIsStartedWhenCapabilitiesAreNotSupported() throws DockerException, InterruptedException {

        // Non supported desired capability for the test session
        Map<String, Object> nonSupportedCapability = new HashMap<>();
        nonSupportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.SAFARI);
        nonSupportedCapability.put(CapabilityType.PLATFORM, Platform.MAC);
        TestSession testSession = spyProxy.getNewSession(nonSupportedCapability);

        Assert.assertNull(testSession);
        verify(spyProxy, never()).startDockerSeleniumContainer(BrowserType.SAFARI, configuredScreenSize,
                configuredTimeZone);
    }

    @Test
    public void noContainerIsStartedWhenPlatformIsNotSupported() {
        // Non supported desired capability for the test session
        Map<String, Object> nonSupportedCapability = new HashMap<>();
        nonSupportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        nonSupportedCapability.put(CapabilityType.PLATFORM, Platform.WINDOWS);
        TestSession testSession = spyProxy.getNewSession(nonSupportedCapability);

        Assert.assertNull(testSession);
        verify(spyProxy, never()).startDockerSeleniumContainer(BrowserType.CHROME, configuredScreenSize,
                configuredTimeZone);
    }

    @Test
    public void containerIsStartedWhenChromeCapabilitiesAreSupported() {

        // Supported desired capability for the test session
        Map<String, Object> supportedCapability = new HashMap<>();
        supportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        supportedCapability.put(CapabilityType.PLATFORM, Platform.LINUX);
        TestSession testSession = spyProxy.getNewSession(supportedCapability);

        Assert.assertNull(testSession);
        verify(spyProxy, timeout(1000).times(1)).startDockerSeleniumContainer(BrowserType.CHROME, configuredScreenSize,
                configuredTimeZone);
    }

    @Test
    public void containerIsStartedWhenBrowserIsSupportedAndLatestIsUsedAsBrowserVersion() {

        // Supported desired capability for the test session
        Map<String, Object> supportedCapability = new HashMap<>();
        supportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        supportedCapability.put(CapabilityType.PLATFORM, Platform.ANY);
        supportedCapability.put(CapabilityType.VERSION, "latest");
        TestSession testSession = spyProxy.getNewSession(supportedCapability);

        Assert.assertNull(testSession);
        verify(spyProxy, timeout(1000).times(1)).startDockerSeleniumContainer(BrowserType.CHROME, configuredScreenSize,
                configuredTimeZone);
    }

    @Test
    public void containerIsStartedWhenScreenResolutionIsProvided() {
        // Supported desired capability for the test session
        Map<String, Object> supportedCapability = new HashMap<>();
        supportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
        supportedCapability.put(CapabilityType.PLATFORM, Platform.ANY);
        supportedCapability.put("screenResolution", "1280x760");
        TestSession testSession = spyProxy.getNewSession(supportedCapability);
        Assert.assertNull(testSession);
        Dimension screenSize = new Dimension(1280, 760);
        verify(spyProxy, timeout(1000).times(1)).startDockerSeleniumContainer(BrowserType.FIREFOX, screenSize,
                configuredTimeZone);
    }

    @Test
    public void containerIsStartedWhenResolutionIsProvided() {
        // Supported desired capability for the test session
        Map<String, Object> supportedCapability = new HashMap<>();
        supportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        supportedCapability.put(CapabilityType.PLATFORM, Platform.ANY);
        supportedCapability.put("resolution", "1300x900");
        TestSession testSession = spyProxy.getNewSession(supportedCapability);
        Assert.assertNull(testSession);
        Dimension screenSize = new Dimension(1300, 900);
        verify(spyProxy, timeout(1000).times(1)).startDockerSeleniumContainer(BrowserType.CHROME, screenSize,
                configuredTimeZone);
    }

    @Test
    public void containerIsStartedWhenCustomScreenResolutionIsProvided() {
        // Supported desired capability for the test session
        Map<String, Object> supportedCapability = new HashMap<>();
        supportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
        supportedCapability.put(CapabilityType.PLATFORM, Platform.ANY);
        supportedCapability.put("screen-resolution", "1500x1000");
        TestSession testSession = spyProxy.getNewSession(supportedCapability);
        Assert.assertNull(testSession);
        Dimension screenSize = new Dimension(1500, 1000);
        verify(spyProxy, timeout(1000).times(1)).startDockerSeleniumContainer(BrowserType.FIREFOX, screenSize,
                configuredTimeZone);
    }

    @Test
    public void containerIsStartedWhenNegativeResolutionIsProvidedUsingDefaults() {
        // Supported desired capability for the test session
        Map<String, Object> supportedCapability = new HashMap<>();
        supportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        supportedCapability.put(CapabilityType.PLATFORM, Platform.ANY);
        supportedCapability.put("resolution", "-1300x800");
        TestSession testSession = spyProxy.getNewSession(supportedCapability);
        Assert.assertNull(testSession);
        verify(spyProxy, timeout(1000).times(1)).startDockerSeleniumContainer(BrowserType.CHROME, configuredScreenSize,
                configuredTimeZone);
    }

    @Test
    public void containerIsStartedWhenAnInvalidResolutionIsProvidedUsingDefaults() {
        // Supported desired capability for the test session
        Map<String, Object> supportedCapability = new HashMap<>();
        supportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        supportedCapability.put(CapabilityType.PLATFORM, Platform.ANY);
        supportedCapability.put("screenResolution", "notAValidScreenResolution");
        TestSession testSession = spyProxy.getNewSession(supportedCapability);
        Assert.assertNull(testSession);
        verify(spyProxy, timeout(1000).times(1)).startDockerSeleniumContainer(BrowserType.CHROME, configuredScreenSize,
                configuredTimeZone);
    }

    @Test
    public void containerIsStartedWhenFirefoxCapabilitiesAreSupported() {

        // Supported desired capability for the test session
        Map<String, Object> supportedCapability = new HashMap<>();
        supportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
        supportedCapability.put(CapabilityType.PLATFORM, Platform.LINUX);
        TestSession testSession = spyProxy.getNewSession(supportedCapability);

        Assert.assertNull(testSession);
        verify(spyProxy, timeout(1000).times(1)).startDockerSeleniumContainer(BrowserType.FIREFOX, configuredScreenSize,
                configuredTimeZone);
    }

    @Test
    public void noContainerIsStartedWhenBrowserCapabilityIsAbsent() {
        // Browser is absent
        Map<String, Object> nonSupportedCapability = new HashMap<>();
        nonSupportedCapability.put(CapabilityType.PLATFORM, Platform.LINUX);
        TestSession testSession = spyProxy.getNewSession(nonSupportedCapability);

        Assert.assertNull(testSession);
        verify(spyProxy, never()).startDockerSeleniumContainer(BrowserType.CHROME, configuredScreenSize,
                configuredTimeZone);
        verify(spyProxy, never()).startDockerSeleniumContainer(BrowserType.FIREFOX, configuredScreenSize,
                configuredTimeZone);
    }

    @Test
    public void noContainerIsStartedForAlreadyProcessedRequest() {
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        requestedCapability.put(CapabilityType.PLATFORM, Platform.LINUX);
        requestedCapability.put("waitingFor_CHROME_Node", 1);
        TestSession testSession = spyProxy.getNewSession(requestedCapability);
        Assert.assertNull(testSession);
        verify(spyProxy, times(0)).startDockerSeleniumContainer(BrowserType.CHROME, configuredScreenSize,
                configuredTimeZone);
    }

    @Test
    public void containerIsStartedForRequestProcessedMoreThan30Times() {
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
        requestedCapability.put(CapabilityType.PLATFORM, Platform.LINUX);
        requestedCapability.put("waitingFor_FIREFOX_Node", 31);
        TestSession testSession = spyProxy.getNewSession(requestedCapability);
        Assert.assertNull(testSession);
        verify(spyProxy, timeout(1000).times(1)).startDockerSeleniumContainer(BrowserType.FIREFOX,
                configuredScreenSize, configuredTimeZone, true);
    }

    @Test
    public void containerIsStartedForRequestWithTimeZoneCapability() {
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
        requestedCapability.put(CapabilityType.PLATFORM, Platform.LINUX);
        requestedCapability.put("tz", "America/Montreal");
        TestSession testSession = spyProxy.getNewSession(requestedCapability);
        Assert.assertNull(testSession);
        TimeZone timeZone = TimeZone.getTimeZone("America/Montreal");
        verify(spyProxy, timeout(1000).times(1)).startDockerSeleniumContainer(BrowserType.FIREFOX,
                configuredScreenSize, timeZone, false);
    }

    /*
        Tests checking the environment variables setup to have a given number of containers on startup
     */

    @Test
    public void fallbackToDefaultAmountOfValuesWhenVariablesAreNotSet() {
        // Mock the environment class that serves as proxy to retrieve env variables
        Environment environment = mock(Environment.class, withSettings().useConstructor());
        when(environment.getEnvVariable(any(String.class))).thenReturn(null);
        when(environment.getIntEnvVariable(any(String.class), any(Integer.class))).thenCallRealMethod();
        when(environment.getStringEnvVariable(any(String.class), any(String.class))).thenCallRealMethod();
        DockerSeleniumStarterRemoteProxy.setEnv(environment);

        registry.add(spyProxy);

        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_AMOUNT_CHROME_CONTAINERS,
                DockerSeleniumStarterRemoteProxy.getChromeContainersOnStartup());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_AMOUNT_FIREFOX_CONTAINERS,
                DockerSeleniumStarterRemoteProxy.getFirefoxContainersOnStartup());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING,
                DockerSeleniumStarterRemoteProxy.getMaxDockerSeleniumContainers());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_SCREEN_SIZE.getWidth(),
                DockerSeleniumStarterRemoteProxy.getConfiguredScreenSize().getWidth());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_SCREEN_SIZE.getHeight(),
                DockerSeleniumStarterRemoteProxy.getConfiguredScreenSize().getHeight());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_TZ.getID(),
                DockerSeleniumStarterRemoteProxy.getConfiguredTimeZone().getID());
    }

    @Test
    public void fallbackToDefaultAmountValuesWhenVariablesAreNotIntegers() {
        // Mock the environment class that serves as proxy to retrieve env variables
        Environment environment = mock(Environment.class, withSettings().useConstructor());
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_CHROME_CONTAINERS))
                .thenReturn("ABC_NON_INTEGER");
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_FIREFOX_CONTAINERS))
                .thenReturn("ABC_NON_INTEGER");
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS))
                .thenReturn("ABC_NON_INTEGER");
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_SCREEN_HEIGHT))
                .thenReturn("ABC_NON_INTEGER");
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_SCREEN_WIDTH))
                .thenReturn("ABC_NON_INTEGER");
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_TZ))
                .thenReturn("ABC_NON_STANDARD_TIME_ZONE");
        when(environment.getIntEnvVariable(any(String.class), any(Integer.class))).thenCallRealMethod();
        when(environment.getStringEnvVariable(any(String.class), any(String.class))).thenCallRealMethod();
        DockerSeleniumStarterRemoteProxy.setEnv(environment);

        DockerSeleniumStarterRemoteProxy.getNewInstance(request, registry);

        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_AMOUNT_CHROME_CONTAINERS,
                DockerSeleniumStarterRemoteProxy.getChromeContainersOnStartup());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_AMOUNT_FIREFOX_CONTAINERS,
                DockerSeleniumStarterRemoteProxy.getFirefoxContainersOnStartup());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING,
                DockerSeleniumStarterRemoteProxy.getMaxDockerSeleniumContainers());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_SCREEN_SIZE.getWidth(),
                DockerSeleniumStarterRemoteProxy.getConfiguredScreenSize().getWidth());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_SCREEN_SIZE.getHeight(),
                DockerSeleniumStarterRemoteProxy.getConfiguredScreenSize().getHeight());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_TZ.getID(),
                DockerSeleniumStarterRemoteProxy.getConfiguredTimeZone().getID());
    }

    @Test
    public void variablesGrabTheConfiguredEnvironmentVariables() {
        // Mock the environment class that serves as proxy to retrieve env variables
        Environment environment = mock(Environment.class, withSettings().useConstructor());
        int amountOfChromeContainers = 4;
        int amountOfFirefoxContainers = 3;
        int amountOfMaxContainers = 8;
        int screenWidth = 1440;
        int screenHeight = 810;
        String timeZone = "America/Montreal";
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_CHROME_CONTAINERS))
                .thenReturn(String.valueOf(amountOfChromeContainers));
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_FIREFOX_CONTAINERS))
                .thenReturn(String.valueOf(amountOfFirefoxContainers));
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS))
                .thenReturn(String.valueOf(amountOfMaxContainers));
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_SCREEN_HEIGHT))
                .thenReturn(String.valueOf(screenHeight));
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_SCREEN_WIDTH))
                .thenReturn(String.valueOf(screenWidth));
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_TZ))
                .thenReturn(timeZone);
        when(environment.getIntEnvVariable(any(String.class), any(Integer.class))).thenCallRealMethod();
        when(environment.getStringEnvVariable(any(String.class), any(String.class))).thenCallRealMethod();
        DockerSeleniumStarterRemoteProxy.setEnv(environment);

        DockerSeleniumStarterRemoteProxy.getNewInstance(request, registry);

        Assert.assertEquals(amountOfChromeContainers, DockerSeleniumStarterRemoteProxy.getChromeContainersOnStartup());
        Assert.assertEquals(amountOfFirefoxContainers, DockerSeleniumStarterRemoteProxy.getFirefoxContainersOnStartup());
        Assert.assertEquals(amountOfMaxContainers, DockerSeleniumStarterRemoteProxy.getMaxDockerSeleniumContainers());
        Assert.assertEquals(screenHeight, DockerSeleniumStarterRemoteProxy.getConfiguredScreenSize().getHeight());
        Assert.assertEquals(screenWidth, DockerSeleniumStarterRemoteProxy.getConfiguredScreenSize().getWidth());
        Assert.assertEquals(timeZone, DockerSeleniumStarterRemoteProxy.getConfiguredTimeZone().getID());
    }

    @Test
    public void amountOfCreatedContainersIsTheConfiguredOne() {
        // Mock the environment class that serves as proxy to retrieve env variables
        Environment environment = mock(Environment.class, withSettings().useConstructor());
        int amountOfChromeContainers = 3;
        int amountOfFirefoxContainers = 4;
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_CHROME_CONTAINERS))
                .thenReturn(String.valueOf(amountOfChromeContainers));
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_FIREFOX_CONTAINERS))
                .thenReturn(String.valueOf(amountOfFirefoxContainers));
        when(environment.getIntEnvVariable(any(String.class), any(Integer.class))).thenCallRealMethod();
        DockerSeleniumStarterRemoteProxy.setEnv(environment);
        DockerSeleniumStarterRemoteProxy.setSleepIntervalMultiplier(0);

        DockerSeleniumStarterRemoteProxy.getNewInstance(request, registry);
        registry.add(spyProxy);

        verify(spyProxy, timeout(5000).times(amountOfChromeContainers))
                .startDockerSeleniumContainer(BrowserType.CHROME, configuredScreenSize, configuredTimeZone, true);
        verify(spyProxy, timeout(5000).times(amountOfFirefoxContainers))
                .startDockerSeleniumContainer(BrowserType.FIREFOX, configuredScreenSize, configuredTimeZone, true);
        Assert.assertEquals(amountOfChromeContainers, DockerSeleniumStarterRemoteProxy.getChromeContainersOnStartup());
        Assert.assertEquals(amountOfFirefoxContainers, DockerSeleniumStarterRemoteProxy.getFirefoxContainersOnStartup());
    }

    @Test
    public void noNegativeValuesAreAllowedForStartup() {
        DockerSeleniumStarterRemoteProxy.setChromeContainersOnStartup(-1);
        DockerSeleniumStarterRemoteProxy.setFirefoxContainersOnStartup(-1);
        DockerSeleniumStarterRemoteProxy.setMaxDockerSeleniumContainers(-1);
        DockerSeleniumStarterRemoteProxy.setConfiguredScreenSize(new Dimension(-1, -1));
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_AMOUNT_CHROME_CONTAINERS,
                DockerSeleniumStarterRemoteProxy.getChromeContainersOnStartup());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_AMOUNT_FIREFOX_CONTAINERS,
                DockerSeleniumStarterRemoteProxy.getFirefoxContainersOnStartup());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING,
                DockerSeleniumStarterRemoteProxy.getMaxDockerSeleniumContainers());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_SCREEN_SIZE.getHeight(),
                DockerSeleniumStarterRemoteProxy.getConfiguredScreenSize().getHeight());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.DEFAULT_SCREEN_SIZE.getWidth(),
                DockerSeleniumStarterRemoteProxy.getConfiguredScreenSize().getWidth());
    }

}
