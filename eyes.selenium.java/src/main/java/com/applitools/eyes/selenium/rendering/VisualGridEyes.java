package com.applitools.eyes.selenium.rendering;

import com.applitools.ICheckSettings;
import com.applitools.ICheckSettingsInternal;
import com.applitools.eyes.*;
import com.applitools.eyes.fluent.CheckSettings;
import com.applitools.eyes.fluent.GetFloatingRegion;
import com.applitools.eyes.fluent.GetRegion;
import com.applitools.eyes.selenium.*;
import com.applitools.eyes.selenium.fluent.*;
import com.applitools.eyes.selenium.frames.Frame;
import com.applitools.eyes.selenium.frames.FrameChain;
import com.applitools.eyes.selenium.wrappers.EyesTargetLocator;
import com.applitools.eyes.selenium.wrappers.EyesWebDriver;
import com.applitools.eyes.visualgrid.model.*;
import com.applitools.eyes.visualgrid.services.*;
import com.applitools.utils.ArgumentGuard;
import com.applitools.utils.ClassVersionGetter;
import com.applitools.utils.GeneralUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class VisualGridEyes implements ISeleniumEyes, IRenderingEyes {

    private static long DOM_EXTRACTION_TIMEOUT = 5 * 60 * 1000;
    private Logger logger;

    private String apiKey;
    private String serverUrl;

    private final VisualGridRunner renderingGridRunner;
    private final List<RunningTest> testList = Collections.synchronizedList(new ArrayList<RunningTest>());
    private final List<RunningTest> testsInCloseProcess = Collections.synchronizedList(new ArrayList<RunningTest>());
    private AtomicBoolean isVGEyesIssuedOpenTasks = new AtomicBoolean(false);
    private IRenderingEyes.EyesListener listener;
    List<TestResultContainer> allTestResults = new ArrayList<>();

    private String PROCESS_RESOURCES;
    private EyesWebDriver webDriver;
    private RenderingInfo renderingInfo;
    private IEyesConnector VGEyesConnector;
    private IDebugResourceWriter debugResourceWriter;
    private String url;
    private Set<Future<TestResultContainer>> closeFuturesSet = new HashSet<>();
    private Boolean isDisabled = Boolean.FALSE;
    private IServerConnector serverConnector = null;
    private ISeleniumConfigurationProvider configProvider;
    private UserAgent userAgent = null;
    private RectangleSize viewportSize;
    private AtomicBoolean isCheckTimerTimedOut = new AtomicBoolean(false);
    private Timer timer = null;
    private final List<PropertyData> properties = new ArrayList<>();

    private static final String GET_ELEMENT_XPATH_JS =
            "var el = arguments[0];" +
                    "var xpath = '';" +
                    "do {" +
                    " var parent = el.parentElement;" +
                    " var index = 1;" +
                    " if (parent !== null) {" +
                    "  var children = parent.children;" +
                    "  for (var childIdx in children) {" +
                    "    var child = children[childIdx];" +
                    "    if (child === el) break;" +
                    "    if (child.tagName === el.tagName) index++;" +
                    "  }" +
                    "}" +
                    "xpath = '/' + el.tagName + '[' + index + ']' + xpath;" +
                    " el = parent;" +
                    "} while (el !== null);" +
                    "return '/' + xpath;";


    {
        try {
            PROCESS_RESOURCES = GeneralUtils.readToEnd(VisualGridEyes.class.getResourceAsStream("/processPageAndSerializePoll.js"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public VisualGridEyes(VisualGridRunner renderingGridManager, ISeleniumConfigurationProvider configProvider) {
        this.configProvider = configProvider;
        ArgumentGuard.notNull(renderingGridManager, "renderingGridRunner");
        this.renderingGridRunner = renderingGridManager;
        this.logger = renderingGridManager.getLogger();
    }

    private RunningTest.RunningTestListener testListener = new RunningTest.RunningTestListener() {
        @Override
        public void onTaskComplete(VisualGridTask task, RunningTest test) {
            switch (task.getType()) {
                case CLOSE:
                case ABORT:
                    VisualGridEyes.this.isVGEyesIssuedOpenTasks.set(false);
            }
            if (VisualGridEyes.this.listener != null) {
                VisualGridEyes.this.listener.onTaskComplete(task, VisualGridEyes.this);
            }
        }

        @Override
        public void onRenderComplete() {
            VisualGridEyes.this.listener.onRenderComplete();
        }

    };


    /**
     * Sets a handler of log messages generated by this API.
     * @param logHandler Handles log messages generated by this API.
     */
    public void setLogHandler(LogHandler logHandler) {
        if (getIsDisabled()) return;
        LogHandler currentLogHandler = logger.getLogHandler();
        this.logger = new Logger();
        this.logger.setLogHandler(new MultiLogHandler(currentLogHandler, logHandler));

        if (currentLogHandler.isOpen() && !logHandler.isOpen()) {
            logHandler.open();
        }
    }

    public LogHandler getLogHandler() {
        if (getIsDisabled()) return NullLogHandler.instance;
        return this.logger.getLogHandler();
    }

    public void apiKey(String apiKey) {
        setApiKey(apiKey);
    }

    public void serverUrl(String serverUrl) {
        setServerUrl(serverUrl);
    }

    public void serverUrl(URI serverUrl) {
        setServerUrl(serverUrl.toString());
    }

    @Override
    public WebDriver open(WebDriver driver, String appName, String testName, RectangleSize viewportSize) throws EyesException {
        IConfigurationSetter configSetter = (IConfigurationSetter) getConfigSetter().setAppName(appName).setTestName(testName);
        if (viewportSize != null && !viewportSize.isEmpty()) {
            configSetter.setViewportSize(new RectangleSize(viewportSize));
        }
        return open(driver);
    }

    public WebDriver open(WebDriver webDriver) {
        logger.verbose("enter");

        if (!validateEyes()) return webDriver;

        ArgumentGuard.notNull(webDriver, "webDriver");
        ArgumentGuard.notNull(getConfigGetter().getTestName(), "testName");
        ArgumentGuard.notNull(getConfigGetter().getAppName(), "appName");

        initDriver(webDriver);

        String uaString = this.webDriver.getUserAgent();
        if (uaString != null) {
            logger.verbose(String.format("User-Agent: %s", uaString));
            userAgent = UserAgent.parseUserAgentString(uaString, true);
        }

        setViewportSize(this.webDriver);

        ensureBrowsers();

        if (getConfigGetter().getBatch() == null) {
            getConfigSetter().setBatch(new BatchInfo(null));
        }
        logger.verbose("getting all browsers info...");
        List<RenderBrowserInfo> browserInfoList = getConfigGetter().getBrowsersInfo();
        logger.verbose("creating test descriptors for each browser info...");
        IConfigurationSetter configurationSetter = configProvider.set();
        configurationSetter.setViewportSize(viewportSize);

        if (getConfigGetter().getBrowsersInfo() == null) {
            RectangleSize viewportSize = getConfigGetter().getViewportSize();
            configurationSetter.addBrowser(new RenderBrowserInfo(viewportSize.getWidth(), viewportSize.getHeight(), BrowserType.CHROME, getConfigGetter().getBaselineEnvName()));
        }

        for (RenderBrowserInfo browserInfo : browserInfoList) {
            logger.verbose("creating test descriptor");
            RunningTest test = new RunningTest(createVGEyesConnector(browserInfo), configProvider, browserInfo, logger, testListener);
            this.testList.add(test);
        }

        logger.verbose(String.format("opening %d tests...", testList.size()));
        this.renderingGridRunner.open(this, renderingInfo);
        logger.verbose("done");
        return this.webDriver != null ? this.webDriver : webDriver;
    }

    private void ensureBrowsers() {
        if (this.configProvider.get().getBrowsersInfo().isEmpty()) {
            this.configProvider.get().getBrowsersInfo().add(new RenderBrowserInfo(viewportSize, BrowserType.CHROME));
        }
    }

    private void setViewportSize(EyesWebDriver webDriver) {
        viewportSize = configProvider.get().getViewportSize();

        if (viewportSize == null) {
            List<RenderBrowserInfo> browserInfoList = getConfigGetter().getBrowsersInfo();
            if (browserInfoList != null && !browserInfoList.isEmpty()) {
                for (RenderBrowserInfo renderBrowserInfo : browserInfoList) {
                    if (renderBrowserInfo.getEmulationInfo() != null) continue;
                    viewportSize = new RectangleSize(renderBrowserInfo.getWidth(), renderBrowserInfo.getHeight());
                }
            }
        }

        if (viewportSize == null) {
            viewportSize = EyesSeleniumUtils.getViewportSize(webDriver);

        }

        try {

            EyesSeleniumUtils.setViewportSize(logger, webDriver, viewportSize);

        } catch (Exception e) {
            GeneralUtils.logExceptionStackTrace(logger, e);
        }
    }

    private IEyesConnector createVGEyesConnector(RenderBrowserInfo browserInfo) {
        logger.verbose("creating VisualGridEyes server connector");
        EyesConnector VGEyesConnector = new EyesConnector(this.configProvider, this.properties, browserInfo);
        if (browserInfo.getEmulationInfo() != null) {
            VGEyesConnector.setDevice(browserInfo.getEmulationInfo().getDeviceName());
        }
        VGEyesConnector.setLogHandler(this.logger.getLogHandler());
        VGEyesConnector.setProxy(this.getConfigGetter().getProxy());
        if (serverConnector != null) {
            VGEyesConnector.setServerConnector(serverConnector);
        }

        URI serverUri = this.getServerUrl();
        if (serverUri != null) {
            VGEyesConnector.setServerUrl(serverUri.toString());
        }

        String apiKey = this.getApiKey();
        if (apiKey != null) {
            VGEyesConnector.setApiKey(apiKey);
        } else {
            throw new EyesException("Missing API key");
        }

        if (this.renderingInfo == null) {
            logger.verbose("initializing rendering info...");
            this.renderingInfo = VGEyesConnector.getRenderingInfo();
        }
        VGEyesConnector.setRenderInfo(this.renderingInfo);

        this.VGEyesConnector = VGEyesConnector;
        return VGEyesConnector;
    }

    private void initDriver(WebDriver webDriver) {
        if (webDriver instanceof RemoteWebDriver) {
            this.webDriver = new EyesWebDriver(logger, null, (RemoteWebDriver) webDriver);
        }
        @SuppressWarnings("UnnecessaryLocalVariable") String currentUrl = webDriver.getCurrentUrl();
        this.url = currentUrl;
    }

    public RunningTest getNextTestToClose() {
        synchronized (testsInCloseProcess) {
            synchronized (testList) {
                for (RunningTest runningTest : testList) {
                    if (!runningTest.isTestClose() && runningTest.isTestReadyToClose() &&
                            !this.testsInCloseProcess.contains(runningTest)) {
                        return runningTest;
                    }
                }
            }
        }
        return null;
    }

    public Collection<Future<TestResultContainer>> close() {
        if (!validateEyes()) return new ArrayList<>();
        return closeAndReturnResults(false);
    }

    public TestResults close(boolean throwException) {
        Collection<Future<TestResultContainer>> close = close();
        return parseCloseFutures(close, throwException);
    }

    private TestResults parseCloseFutures(Collection<Future<TestResultContainer>> close, boolean shouldThrowException) {
        if (close != null && !close.isEmpty()) {
            TestResultContainer errorResult = null;
            TestResultContainer firstResult = null;
            try {
                for (Future<TestResultContainer> closeFuture : close) {
                    TestResultContainer testResultContainer = closeFuture.get();
                    if (firstResult == null) {
                        firstResult = testResultContainer;
                    }
                    Throwable error = testResultContainer.getException();
                    if (error != null && errorResult == null) {
                        errorResult = testResultContainer;
                    }

                }
//                    return firstCloseFuture.get().getTestResults();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }

            if (errorResult != null) {
                if (shouldThrowException) {
                    throw new Error(errorResult.getException());
                } else {
                    return errorResult.getTestResults();
                }
            } else { // returning the first result
                if (firstResult != null) {
                    return firstResult.getTestResults();
                }
            }

        }
        return null;
    }

    public TestResults abortIfNotClosed() {
        List<Future<TestResultContainer>> futures = abortAndCollectTasks();
        return parseCloseFutures(futures, false);
    }

    public boolean getIsOpen() {
        return !isEyesClosed();
    }

    public String getApiKey() {
        return this.apiKey == null ? this.renderingGridRunner.getApiKey() : this.apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setIsDisabled(Boolean disabled) {
        this.isDisabled = disabled;
    }

    public boolean getIsDisabled() {
        return this.isDisabled == null ? this.renderingGridRunner.getIsDisabled() : this.isDisabled;
    }

    public URI getServerUrl() {
        if (this.VGEyesConnector != null) {
            URI uri = this.VGEyesConnector.getServerUrl();
            if (uri != null) return uri;
        }
        String str = this.serverUrl == null ? this.renderingGridRunner.getServerUrl() : this.serverUrl;
        return str == null ? null : URI.create(str);
    }

    private Collection<Future<TestResultContainer>> closeAndReturnResults(boolean throwException) {
        if (!validateEyes()) return new ArrayList<>();

        if (this.closeFuturesSet == null) {
            closeFuturesSet = new HashSet<>();
        }
        Throwable exception = null;
        logger.verbose("enter " + getConfigGetter().getBatch());
        try {
            Collection<Future<TestResultContainer>> futureList = closeAsync();
            this.renderingGridRunner.close(this);
            for (Future<TestResultContainer> future : futureList) {
                try {
                    TestResultContainer testResultContainer = future.get();
                    if (exception == null && testResultContainer.getException() != null) {
                        exception = testResultContainer.getException();
                    }
                } catch (Throwable e) {
                    GeneralUtils.logExceptionStackTrace(logger, e);
                    if (exception == null) {
                        exception = e;
                    }
                }


            }
        } catch (Throwable e) {
            GeneralUtils.logExceptionStackTrace(logger, e);
        }
        if (throwException && exception != null) {
            throw new Error(exception);
        }
        return closeFuturesSet;
    }

    public Collection<Future<TestResultContainer>> closeAsync() {
        if (!validateEyes()) return new ArrayList<>();
        List<Future<TestResultContainer>> futureList = null;
        try {
            futureList = new ArrayList<>();
            for (RunningTest runningTest : testList) {
                logger.verbose("running test name: " + getConfigGetter().getTestName());
                logger.verbose("is current running test open: " + runningTest.isTestOpen());
                logger.verbose("is current running test ready to close: " + runningTest.isTestReadyToClose());
                logger.verbose("is current running test closed: " + runningTest.isTestClose());
                logger.verbose("closing current running test");
                FutureTask<TestResultContainer> closeFuture = runningTest.close();
                futureList.addAll(Collections.singleton(closeFuture));
                logger.verbose("adding closeFuture to futureList");
            }
            closeFuturesSet.addAll(futureList);
        } catch (Throwable e) {
            GeneralUtils.logExceptionStackTrace(logger, e);
        }
        return futureList;
    }

    @Override
    public synchronized ScoreTask getBestScoreTaskForCheck() {

        int bestScore = -1;

        ScoreTask currentBest = null;
        for (RunningTest runningTest : testList) {

            List<VisualGridTask> visualGridTaskList = runningTest.getVisualGridTaskList();

            VisualGridTask visualGridTask;
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (visualGridTaskList) {
                if (visualGridTaskList.isEmpty()) continue;

                visualGridTask = visualGridTaskList.get(0);
                if (!runningTest.isTestOpen() || visualGridTask.getType() != VisualGridTask.TaskType.CHECK || !visualGridTask.isTaskReadyToCheck())
                    continue;
            }


            ScoreTask scoreTask = runningTest.getScoreTaskObjectByType(VisualGridTask.TaskType.CHECK);

            if (scoreTask == null) continue;

            if (bestScore < scoreTask.getScore()) {
                currentBest = scoreTask;
                bestScore = scoreTask.getScore();
            }
        }
        return currentBest;
    }

    @Override
    public ScoreTask getBestScoreTaskForOpen() {
        int bestMark = -1;
        ScoreTask currentBest = null;
        synchronized (testList) {
            for (RunningTest runningTest : testList) {

                ScoreTask currentScoreTask = runningTest.getScoreTaskObjectByType(VisualGridTask.TaskType.OPEN);
                if (currentScoreTask == null) continue;

                if (bestMark < currentScoreTask.getScore()) {
                    bestMark = currentScoreTask.getScore();
                    currentBest = currentScoreTask;

                }
            }
        }
        return currentBest;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    @Override
    public boolean isEyesClosed() {
        boolean isVGEyesClosed = true;
        for (RunningTest runningTest : testList) {
            isVGEyesClosed = isVGEyesClosed && runningTest.isTestClose();
        }
        return isVGEyesClosed;
    }

    public void setListener(EyesListener listener) {
        this.listener = listener;
    }


    public void check(ICheckSettings... checkSettings) {
        if (!validateEyes()) return;
        for (ICheckSettings checkSetting : checkSettings) {
            this.check(checkSetting);
        }
    }

    public void check(String name, ICheckSettings checkSettings) {
        if (!validateEyes()) return;
        ArgumentGuard.notNull(checkSettings, "checkSettings");
        if (name != null) {
            checkSettings = checkSettings.withName(name);
        }
        this.check(checkSettings);
    }

    public void check(ICheckSettings checkSettings) {
        logger.verbose("enter");

        if (!validateEyes()) return;

        ArgumentGuard.notOfType(checkSettings, ICheckSettings.class, "checkSettings");

        waitBeforeDomSnapshot();

        try {
            FrameChain originalFC = webDriver.getFrameChain().clone();
            EyesTargetLocator switchTo = ((EyesTargetLocator) webDriver.switchTo());
            checkSettings = switchFramesAsNeeded(checkSettings, switchTo);
            ICheckSettingsInternal checkSettingsInternal = (ICheckSettingsInternal) checkSettings;

            isCheckTimerTimedOut.set(false);

            List<VisualGridTask> openVisualGridTasks = addOpenTaskToAllRunningTest();
            List<VisualGridTask> visualGridTaskList = new ArrayList<>();

            FrameData scriptResult = captureDomSnapshot(originalFC, switchTo, checkSettingsInternal);

            List<VisualGridSelector[]> regionsXPaths = getRegionsXPaths(checkSettingsInternal);
            logger.verbose("regionXPaths : " + regionsXPaths);

            trySetTargetSelector((SeleniumCheckSettings) checkSettings);

            checkSettingsInternal = updateCheckSettings(checkSettings);

            List<RunningTest> filteredTests = collectFilteredTests();

            String source = webDriver.getCurrentUrl();
            for (RunningTest runningTest : filteredTests) {
                VisualGridTask checkVisualGridTask = runningTest.check((ICheckSettings) checkSettingsInternal, regionsXPaths, source);
                visualGridTaskList.add(checkVisualGridTask);
            }

            logger.verbose("added check tasks  (" + checkSettingsInternal.toString() + ")");

            this.renderingGridRunner.check((ICheckSettings) checkSettingsInternal, debugResourceWriter, scriptResult,
                    this.VGEyesConnector, visualGridTaskList, openVisualGridTasks,
                    new VisualGridRunner.RenderListener() {
                        @Override
                        public void onRenderSuccess() {

                        }

                        @Override
                        public void onRenderFailed(Exception e) {
                            GeneralUtils.logExceptionStackTrace(logger, e);
                        }
                    }, regionsXPaths, userAgent);
            logger.verbose("created renderTask  (" + checkSettings.toString() + ")");
            switchTo.frames(originalFC);
        } catch (Throwable e) {
            Error error = new Error(e);
            abort(e);
            for (RunningTest runningTest : testList) {
                runningTest.setTestInExceptionMode(error);
            }
            GeneralUtils.logExceptionStackTrace(logger, e);
        } finally {
            if (timer != null) {
                timer.cancel();
            }
        }
    }

    private List<RunningTest> collectFilteredTests() {
        List<RunningTest> filteredTests = new ArrayList<>();
        for (final RunningTest test : testList) {
            List<VisualGridTask> taskList = test.getVisualGridTaskList();
            VisualGridTask visualGridTask = null;
            if (!taskList.isEmpty()) {
                visualGridTask = taskList.get(taskList.size() - 1);
            }

            VisualGridTask.TaskType taskType = null;
            if (visualGridTask != null) {
                taskType = visualGridTask.getType();
            }

            if ((taskType == null && test.isOpenTaskIssued() && !test.isCloseTaskIssued()) ||
                    (taskType != VisualGridTask.TaskType.CLOSE && taskType != VisualGridTask.TaskType.ABORT)) {
                filteredTests.add(test);
            }
        }
        return filteredTests;
    }

    private ICheckSettings switchFramesAsNeeded(ICheckSettings checkSettings, EyesTargetLocator switchTo) {
        int switchedToCount = switchToFrame((ISeleniumCheckTarget) checkSettings);
        boolean isFullPage = isFullPage((ICheckSettingsInternal) checkSettings);
        if (switchedToCount > 0 && isFullPage) {
            FrameChain frameChain = webDriver.getFrameChain().clone();
            Frame frame = frameChain.pop();
            checkSettings = ((SeleniumCheckSettings) checkSettings).region(frame.getReference());
            switchTo.parentFrame();
        }
        return checkSettings;
    }

    private boolean isFullPage(ICheckSettingsInternal checkSettingsInternal) {
        boolean isFullPage = true;
        Boolean b;
        if ((b = checkSettingsInternal.isStitchContent()) != null) {
            isFullPage = b;
        } else if ((b = getConfigGetter().isForceFullPageScreenshot()) != null) {
            isFullPage = b;
        }
        return isFullPage;
    }

    private FrameData captureDomSnapshot(FrameChain originalFC, EyesTargetLocator switchTo, ICheckSettingsInternal checkSettingsInternal) throws InterruptedException {
        logger.verbose("Dom extraction starting   (" + checkSettingsInternal.toString() + ")");
        timer = new Timer("VG_Check_StopWatch", true);
        timer.schedule(new TimeoutTask(), DOM_EXTRACTION_TIMEOUT);
        String resultAsString;
        ScriptResponse.Status status = null;
        ScriptResponse scriptResponse = null;
        do {
            resultAsString = (String) this.webDriver.executeScript(PROCESS_RESOURCES + "return __processPageAndSerializePoll();");
            try {
                scriptResponse = GeneralUtils.parseJsonToObject(resultAsString, ScriptResponse.class);
                logger.verbose("Dom extraction polling...");
                status = scriptResponse.getStatus();
            } catch (IOException e) {
                GeneralUtils.logExceptionStackTrace(logger, e);
            }
            Thread.sleep(200);
        } while (status == ScriptResponse.Status.WIP && !isCheckTimerTimedOut.get());

        if (status == ScriptResponse.Status.ERROR) {
            switchTo.frames(originalFC);
            throw new EyesException("DomSnapshot Error: " + scriptResponse.getError());
        }

        if (isCheckTimerTimedOut.get()) {
            switchTo.frames(originalFC);
            throw new EyesException("Domsnapshot Timed out");
        }
        FrameData scriptResult = scriptResponse != null ? scriptResponse.getValue() : null;

        logger.verbose("Dom extracted  (" + checkSettingsInternal.toString() + ")");
        return scriptResult;
    }

    private void waitBeforeDomSnapshot() {
        int waitBeforeScreenshots = this.getConfigGetter().getWaitBeforeScreenshots();
        try {
            Thread.sleep(waitBeforeScreenshots);
        } catch (InterruptedException e) {
            GeneralUtils.logExceptionStackTrace(logger, e);
        }
    }

    private ICheckSettingsInternal updateCheckSettings(ICheckSettings checkSettings) {
        ICheckSettingsInternal checkSettingsInternal = (ICheckSettingsInternal) checkSettings;

        MatchLevel matchLevel = checkSettingsInternal.getMatchLevel();

        Boolean fully = checkSettingsInternal.isStitchContent();
        Boolean sendDom = checkSettingsInternal.isSendDom();
        Boolean ignoreDisplacements = checkSettingsInternal.isIgnoreDisplacements();

        Boolean b;

        if (matchLevel == null) {
            checkSettings = checkSettings.matchLevel(getConfigGetter().getMatchLevel());
        }

        if (fully == null) {
            checkSettings = checkSettings.fully((b = getConfigGetter().isForceFullPageScreenshot()) == null ? true : b);
        }

        if (sendDom == null) {
            checkSettings = checkSettings.sendDom((b = getConfigGetter().isSendDom()) == null ? true : b);
        }

        if (ignoreDisplacements == null) {
            checkSettings = checkSettings.ignoreDisplacements(getConfigGetter().getIgnoreDisplacements());
        }

        return (ICheckSettingsInternal) checkSettings;
    }

    private void trySetTargetSelector(SeleniumCheckSettings checkSettings) {
        WebElement element = checkSettings.getTargetElement();
        FrameChain frameChain = webDriver.getFrameChain().clone();
        EyesTargetLocator switchTo = (EyesTargetLocator) webDriver.switchTo();
        switchToFrame(checkSettings);
        if (element == null) {
            By targetSelector = checkSettings.getTargetSelector();
            if (targetSelector != null) {
                element = webDriver.findElement(targetSelector);
            }
        }
        if (element != null) {
            String xpath = (String) webDriver.executeScript(GET_ELEMENT_XPATH_JS, element);
            VisualGridSelector vgs = new VisualGridSelector(xpath, "target");
            checkSettings.setTargetSelector(vgs);
        }
        switchTo.frames(frameChain);
    }

    @SuppressWarnings("UnusedReturnValue")
    private int switchToFrame(ISeleniumCheckTarget checkTarget) {
        if (checkTarget == null) {
            return 0;
        }

        List<FrameLocator> frameChain = checkTarget.getFrameChain();
        int switchedToFrameCount = 0;
        for (FrameLocator frameLocator : frameChain) {
            if (switchToFrame(frameLocator)) {
                switchedToFrameCount++;
            }
        }
        return switchedToFrameCount;
    }

    private boolean switchToFrame(ISeleniumFrameCheckTarget frameTarget) {
        WebDriver.TargetLocator switchTo = this.webDriver.switchTo();

        if (frameTarget.getFrameIndex() != null) {
            switchTo.frame(frameTarget.getFrameIndex());
            updateFrameScrollRoot(frameTarget);
            return true;
        }

        if (frameTarget.getFrameNameOrId() != null) {
            switchTo.frame(frameTarget.getFrameNameOrId());
            updateFrameScrollRoot(frameTarget);
            return true;
        }

        if (frameTarget.getFrameReference() != null) {
            switchTo.frame(frameTarget.getFrameReference());
            updateFrameScrollRoot(frameTarget);
            return true;
        }

        if (frameTarget.getFrameSelector() != null) {
            WebElement frameElement = this.webDriver.findElement(frameTarget.getFrameSelector());
            if (frameElement != null) {
                switchTo.frame(frameElement);
                updateFrameScrollRoot(frameTarget);
                return true;
            }
        }

        return false;
    }

    private void updateFrameScrollRoot(IScrollRootElementContainer frameTarget) {
        WebElement rootElement = getScrollRootElement(frameTarget);
        Frame frame = webDriver.getFrameChain().peek();
        frame.setScrollRootElement(rootElement);
    }

    private WebElement getScrollRootElement(IScrollRootElementContainer scrollRootElementContainer) {
        WebElement scrollRootElement = null;
        if (!EyesSeleniumUtils.isMobileDevice(webDriver)) {
            if (scrollRootElementContainer == null) {
                scrollRootElement = webDriver.findElement(By.tagName("html"));
            } else {
                scrollRootElement = scrollRootElementContainer.getScrollRootElement();
                if (scrollRootElement == null) {
                    By scrollRootSelector = scrollRootElementContainer.getScrollRootSelector();
                    scrollRootElement = webDriver.findElement(scrollRootSelector != null ? scrollRootSelector : By.tagName("html"));
                }
            }
        }

        return scrollRootElement;
    }

    private synchronized List<VisualGridTask> addOpenTaskToAllRunningTest() {
        logger.verbose("enter");
        List<VisualGridTask> visualGridTasks = new ArrayList<>();
        for (RunningTest runningTest : testList) {
            if (!runningTest.isOpenTaskIssued()) {
                VisualGridTask visualGridTask = runningTest.open();
                visualGridTasks.add(visualGridTask);
            }
        }
        logger.verbose("calling addOpenTaskToAllRunningTest.open");
        this.isVGEyesIssuedOpenTasks.set(true);
        logger.verbose("exit");
        return visualGridTasks;
    }

    public Logger getLogger() {
        return logger;
    }

    public List<RunningTest> getAllRunningTests() {
        return testList;
    }

    public void setDebugResourceWriter(IDebugResourceWriter debugResourceWriter) {
        this.debugResourceWriter = debugResourceWriter;
    }

    @Override
    public String toString() {
        return "SeleniumVGEyes - url: " + url;
    }

    public void setServerConnector(IServerConnector serverConnector) {
        this.serverConnector = serverConnector;
    }


    /**
     * @return The full agent id composed of both the base agent id and the
     * user given agent id.
     */
    public String getFullAgentId() {
        String agentId = getConfigGetter().getAgentId();
        if (agentId == null) {
            return getBaseAgentId();
        }
        return String.format("%s [%s]", agentId, getBaseAgentId());
    }

    private IConfigurationGetter getConfigGetter() {
        return this.configProvider.get();
    }

    private IConfigurationSetter getConfigSetter() {
        return this.configProvider.set();
    }

    @SuppressWarnings("WeakerAccess")
    public String getBaseAgentId() {
        //noinspection SpellCheckingInspection
        return "eyes.selenium.visualgrid.java/" + ClassVersionGetter.CURRENT_VERSION;
    }

    /**
     * Sets the batch in which context future tests will run or {@code null}
     * if tests are to run standalone.
     * @param batch The batch info to set.
     */
    public void setBatch(BatchInfo batch) {
        if (isDisabled) {
            logger.verbose("Ignored");
            return;
        }

        logger.verbose("setBatch(" + batch + ")");

        this.getConfigSetter().setBatch(batch);
    }

    private List<VisualGridSelector[]> getRegionsXPaths(ICheckSettingsInternal csInternal) {
        List<VisualGridSelector[]> result = new ArrayList<>();
        List<WebElementRegion>[] elementLists = collectSeleniumRegions(csInternal);
        for (List<WebElementRegion> elementList : elementLists) {
            //noinspection SpellCheckingInspection
            List<VisualGridSelector> xpaths = new ArrayList<>();
            for (WebElementRegion webElementRegion : elementList) {
                if (webElementRegion.getElement() == null) continue;
                String xpath = (String) webDriver.executeScript(GET_ELEMENT_XPATH_JS, webElementRegion.getElement());
                xpaths.add(new VisualGridSelector(xpath, webElementRegion.getRegion()));
            }
            result.add(xpaths.toArray(new VisualGridSelector[0]));
        }
        return result;
    }

    private List<WebElementRegion>[] collectSeleniumRegions(ICheckSettingsInternal csInternal) {
        CheckSettings settings = (CheckSettings) csInternal;
        GetRegion[] ignoreRegions = settings.getIgnoreRegions();
        GetRegion[] layoutRegions = settings.getLayoutRegions();
        GetRegion[] strictRegions = settings.getStrictRegions();
        GetRegion[] contentRegions = settings.getContentRegions();
        GetFloatingRegion[] floatingRegions = settings.getFloatingRegions();
        IGetAccessibilityRegion[] accessibilityRegions = settings.getAccessibilityRegions();

        List<WebElementRegion> ignoreElements = getElementsFromRegions(Arrays.asList(ignoreRegions));
        List<WebElementRegion> layoutElements = getElementsFromRegions(Arrays.asList(layoutRegions));
        List<WebElementRegion> strictElements = getElementsFromRegions(Arrays.asList(strictRegions));
        List<WebElementRegion> contentElements = getElementsFromRegions(Arrays.asList(contentRegions));
        List<WebElementRegion> floatingElements = getElementsFromRegions(Arrays.asList(floatingRegions));
        List<WebElementRegion> accessibilityElements = getElementsFromRegions(Arrays.asList(accessibilityRegions));


        ISeleniumCheckTarget iSeleniumCheckTarget = (ISeleniumCheckTarget) csInternal;
        WebElement targetElement = iSeleniumCheckTarget.getTargetElement();

        if (targetElement == null) {
            By targetSelector = iSeleniumCheckTarget.getTargetSelector();
            if (targetSelector != null) {
                targetElement = webDriver.findElement(targetSelector);
            }
        }

        WebElementRegion target = new WebElementRegion(targetElement, "target");
        List<WebElementRegion> targetElementList = new ArrayList<>();
        targetElementList.add(target);
        //noinspection UnnecessaryLocalVariable,unchecked
        List<WebElementRegion>[] lists = new List[]{ignoreElements, layoutElements, strictElements, contentElements, floatingElements, accessibilityElements, targetElementList};
        return lists;
    }


    private List<WebElementRegion> getElementsFromRegions(List regionsProvider) {
        List<WebElementRegion> elements = new ArrayList<>();
        for (Object getRegion : regionsProvider) {
            if (getRegion instanceof IGetSeleniumRegion) {
                IGetSeleniumRegion getSeleniumRegion = (IGetSeleniumRegion) getRegion;
                List<WebElement> webElements = getSeleniumRegion.getElements(webDriver);
                for (WebElement webElement : webElements) {
                    elements.add(new WebElementRegion(webElement, getRegion));
                }
            }
        }
        return elements;
    }

    /**
     * Adds a property to be sent to the server.
     * @param name  The property name.
     * @param value The property value.
     */
    public void addProperty(String name, String value) {
        PropertyData pd = new PropertyData(name, value);
        properties.add(pd);
    }

    /**
     * Clears the list of custom properties.
     */
    public void clearProperties() {
        properties.clear();
    }

    private class TimeoutTask extends TimerTask {
        @Override
        public void run() {
            logger.verbose("Check Timer timeout.");
            isCheckTimerTimedOut.set(true);
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean validateEyes() {
        if (isDisabled) {
            logger.verbose("WARNING! Invalid Operation - Eyes Disabled!");
            return false;
        }
        if (!renderingGridRunner.isServicesOn()) {
            logger.verbose("WARNING! Invalid Operation - visualGridRunner.getAllTestResults already called!");
            return false;
        }
        return true;
    }

    @Override
    public List<TestResultContainer> getAllTestResults() {
        return allTestResults;
    }

    public EyesWebDriver getDriver() {
        return webDriver;
    }

    @Override
    public IBatchCloser getBatchCloser() {
        return this.testList.get(0).getBatchCloser();
    }

    @Override
    public String getBatchId() {
        return this.getConfigGetter().getBatch().getId();
    }

    private void abort(Throwable e) {
        for (RunningTest runningTest : testList) {
            runningTest.abort(true, e);
        }
    }

    public void abortAsync() {
        abortAndCollectTasks();
    }

    public TestResults abort() {
        List<Future<TestResultContainer>> tasks = abortAndCollectTasks();
        return parseCloseFutures(tasks, false);
    }

    private List<Future<TestResultContainer>> abortAndCollectTasks() {
        List<Future<TestResultContainer>> tasks = new ArrayList<>();
        for (RunningTest runningTest : testList) {
            Future<TestResultContainer> task = runningTest.abort(false, null);
            tasks.add(task);
        }

        return tasks;
    }
}
