/*
 * Applitools SDK for Selenium integration.
 */
package com.applitools.eyes.selenium;

import com.applitools.ICheckSettings;
import com.applitools.eyes.*;
import com.applitools.eyes.capture.AppOutputWithScreenshot;
import com.applitools.eyes.capture.EyesScreenshotFactory;
import com.applitools.eyes.capture.ImageProvider;
import com.applitools.eyes.events.ValidationInfo;
import com.applitools.eyes.events.ValidationResult;
import com.applitools.eyes.exceptions.TestFailedException;
import com.applitools.eyes.fluent.GetRegion;
import com.applitools.eyes.fluent.ICheckSettingsInternal;
import com.applitools.eyes.fluent.IgnoreRegionByRectangle;
import com.applitools.eyes.positioning.*;
import com.applitools.eyes.scaling.FixedScaleProviderFactory;
import com.applitools.eyes.scaling.NullScaleProvider;
import com.applitools.eyes.selenium.capture.*;
import com.applitools.eyes.selenium.exceptions.EyesDriverOperationException;
import com.applitools.eyes.selenium.fluent.*;
import com.applitools.eyes.selenium.frames.Frame;
import com.applitools.eyes.selenium.frames.FrameChain;
import com.applitools.eyes.selenium.positioning.*;
import com.applitools.eyes.selenium.regionVisibility.MoveToRegionVisibilityStrategy;
import com.applitools.eyes.selenium.regionVisibility.NopRegionVisibilityStrategy;
import com.applitools.eyes.selenium.regionVisibility.RegionVisibilityStrategy;
import com.applitools.eyes.selenium.wrappers.EyesRemoteWebElement;
import com.applitools.eyes.selenium.wrappers.EyesTargetLocator;
import com.applitools.eyes.selenium.wrappers.EyesWebDriver;
import com.applitools.eyes.triggers.MouseAction;
import com.applitools.utils.*;
import org.openqa.selenium.*;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Point;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.awt.image.BufferedImage;
import java.util.*;

/**
 * The main API gateway for the SDK.
 */
@SuppressWarnings("WeakerAccess")
public class SeleniumEyes extends EyesBase {

    private FrameChain originalFC;
    private WebElement scrollRootElement;
    private PositionProvider currentFramePositionProvider;

    /**
     * The constant UNKNOWN_DEVICE_PIXEL_RATIO.
     */
    public static final double UNKNOWN_DEVICE_PIXEL_RATIO = 0;
    /**
     * The constant DEFAULT_DEVICE_PIXEL_RATIO.
     */
    public static final double DEFAULT_DEVICE_PIXEL_RATIO = 1;

    private static final int USE_DEFAULT_MATCH_TIMEOUT = -1;

    // Seconds
    private static final int RESPONSE_TIME_DEFAULT_DEADLINE = 10;

    // Seconds
    private static final int RESPONSE_TIME_DEFAULT_DIFF_FROM_DEADLINE = 20;

    private EyesWebDriver driver;
    private boolean doNotGetTitle;

    private boolean checkFrameOrElement;
    private Region regionToCheck;
    private String originalOverflow;

    private ImageRotation rotation;
    private double devicePixelRatio;
    private PropertyHandler<RegionVisibilityStrategy> regionVisibilityStrategyHandler;
    private ElementPositionProvider elementPositionProvider;
    private SeleniumJavaScriptExecutor jsExecutor;

    private UserAgent userAgent;
    private ImageProvider imageProvider;
    private RegionPositionCompensation regionPositionCompensation;
    private WebElement targetElement;
    private PositionMemento positionMemento;
    private Region effectiveViewport;

    private EyesScreenshotFactory screenshotFactory;
    private String cachedAUTSessionId;
    private ISeleniumConfigurationProvider configurationProvider;
    private boolean stitchContent;
    private ClassicRunner runner;

    /**
     * Should stitch content boolean.
     *
     * @return the boolean
     */
    public boolean shouldStitchContent() {
        return stitchContent;
    }

    /**
     * The interface Web driver action.
     */
    @SuppressWarnings("UnusedDeclaration")
    public interface WebDriverAction {
        /**
         * Drive.
         *
         * @param driver the driver
         */
        void drive(WebDriver driver);
    }

    /**
     * Creates a new SeleniumEyes instance that interacts with the SeleniumEyes cloud
     * service.
     */
    public SeleniumEyes(ISeleniumConfigurationProvider configurationProvider, ClassicRunner runner) {
        super();
        this.configurationProvider = configurationProvider;
        checkFrameOrElement = false;
        doNotGetTitle = false;
        devicePixelRatio = UNKNOWN_DEVICE_PIXEL_RATIO;
        regionVisibilityStrategyHandler = new SimplePropertyHandler<>();
        regionVisibilityStrategyHandler.set(new MoveToRegionVisibilityStrategy(logger));
        this.runner = runner;
    }

    @Override
    public String getBaseAgentId() {
        return "eyes.selenium.java/3.152.6";
    }

    /**
     * Gets driver.
     *
     * @return the driver
     */
    public WebDriver getDriver() {
        return driver;
    }

    /**
     * Gets original fc.
     *
     * @return the original fc
     */
    public FrameChain getOriginalFC() {
        return originalFC;
    }

    /**
     * Gets current frame position provider.
     *
     * @return the current frame position provider
     */
    public PositionProvider getCurrentFramePositionProvider() {
        return currentFramePositionProvider;
    }

    /**
     * Gets region to check.
     *
     * @return the region to check
     */
    public Region getRegionToCheck() {
        return regionToCheck;
    }

    /**
     * Sets region to check.
     *
     * @param regionToCheck the region to check
     */
    public void setRegionToCheck(Region regionToCheck) {
        this.regionToCheck = regionToCheck;
    }

    /**
     * Turns on/off the automatic scrolling to a region being checked by
     * {@code checkRegion}.
     *
     * @param shouldScroll Whether to automatically scroll to a region being validated.
     */
    public void setScrollToRegion(boolean shouldScroll) {
        if (shouldScroll) {
            regionVisibilityStrategyHandler = new ReadOnlyPropertyHandler<RegionVisibilityStrategy>(logger, new MoveToRegionVisibilityStrategy(logger));
        } else {
            regionVisibilityStrategyHandler = new ReadOnlyPropertyHandler<RegionVisibilityStrategy>(logger, new NopRegionVisibilityStrategy(logger));
        }
    }

    /**
     * Gets scroll to region.
     *
     * @return Whether to automatically scroll to a region being validated.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean getScrollToRegion() {
        return !(regionVisibilityStrategyHandler.get() instanceof NopRegionVisibilityStrategy);
    }

    /**
     * Gets rotation.
     *
     * @return The image rotation model.
     */
    public ImageRotation getRotation() {
        return rotation;
    }

    /**
     * Sets rotation.
     *
     * @param rotation The image rotation model.
     */
    public void setRotation(ImageRotation rotation) {
        this.rotation = rotation;
        if (driver != null) {
            driver.setRotation(rotation);
        }
    }

    /**
     * @return The device pixel ratio, or {@link #UNKNOWN_DEVICE_PIXEL_RATIO}
     * if the DPR is not known yet or if it wasn't possible to extract it.
     */
    public double getDevicePixelRatio() {
        return devicePixelRatio;
    }

    /**
     * Open web driver.
     *
     * @param driver the driver
     * @return the web driver
     * @throws EyesException the eyes exception
     */
    protected WebDriver open(WebDriver driver) throws EyesException {
        openLogger();
        this.cachedAUTSessionId = null;

        if (getIsDisabled()) {
            logger.verbose("Ignored");
            return driver;
        }

        initDriver(driver);

        screenshotFactory = new EyesWebDriverScreenshotFactory(logger, this.driver);

        ensureViewportSize();

        openBase();

        String uaString = sessionStartInfo.getEnvironment().getInferred();
        if (uaString != null) {
            if (uaString.startsWith("useragent:")) {
                uaString = uaString.substring(10);
            }
            userAgent = UserAgent.ParseUserAgentString(uaString, true);
        }

        imageProvider = ImageProviderFactory.getImageProvider(userAgent, this, logger, this.driver);
        regionPositionCompensation = RegionPositionCompensationFactory.getRegionPositionCompensation(userAgent, this, logger);

        ArgumentGuard.notNull(driver, "driver");

        devicePixelRatio = UNKNOWN_DEVICE_PIXEL_RATIO;
        this.jsExecutor = new SeleniumJavaScriptExecutor(this.driver);

        this.driver.setRotation(rotation);
        return this.driver;
    }

    private void ensureViewportSize() {
        if (this.getConfigGetter().getViewportSize() == null) {
            this.getConfigSetter().setViewportSize(driver.getDefaultContentViewportSize());
        }
    }

    private void initDriver(WebDriver driver) {
        if (driver instanceof RemoteWebDriver) {
            this.driver = new EyesWebDriver(logger, this, (RemoteWebDriver) driver);
        } else if (driver instanceof EyesWebDriver) {
            this.driver = (EyesWebDriver) driver;
        } else {
            String errMsg = "Driver is not a RemoteWebDriver (" +
                    driver.getClass().getName() + ")";
            logger.log(errMsg);
            throw new EyesException(errMsg);
        }
        if (EyesSeleniumUtils.isMobileDevice(driver)) {
            regionVisibilityStrategyHandler.set(new NopRegionVisibilityStrategy(logger));
        }
    }

    /**
     * Gets scroll root element.
     *
     * @return the scroll root element
     */
    public WebElement getScrollRootElement() {
        if (this.scrollRootElement == null) {
            this.scrollRootElement = driver.findElement(By.tagName("html"));
        }
        return this.scrollRootElement;
    }

    private PositionProvider createPositionProvider() {
        return createPositionProvider(this.scrollRootElement);
    }

    private PositionProvider createPositionProvider(WebElement scrollRootElement) {
        // Setting the correct position provider.
        StitchMode stitchMode = getConfigGetter().getStitchMode();
        logger.verbose("initializing position provider. stitchMode: " + stitchMode);
        switch (stitchMode) {
            case CSS:
                return new CssTranslatePositionProvider(logger, this.jsExecutor, scrollRootElement);
            default:
                return new ScrollPositionProvider(logger, this.jsExecutor, scrollRootElement);
        }
    }


    /**
     * See {@link #checkWindow(String)}.
     * {@code tag} defaults to {@code null}.
     * Default match timeout is used.
     */
    public void checkWindow() {
        checkWindow(null);
    }

    /**
     * See {@link #checkWindow(int, String)}.
     * Default match timeout is used.
     *
     * @param tag An optional tag to be associated with the snapshot.
     */
    public void checkWindow(String tag) {
        check(tag, Target.window());
    }

    /**
     * Takes a snapshot of the application under test and matches it with
     * the expected output.
     *
     * @param matchTimeout The amount of time to retry matching (Milliseconds).
     * @param tag          An optional tag to be associated with the snapshot.
     * @throws TestFailedException Thrown if a mismatch is detected and
     *                             immediate failure reports are enabled.
     */
    public void checkWindow(int matchTimeout, String tag) {
        check(tag, Target.window().timeout(matchTimeout));
    }


    /**
     * Takes multiple screenshots at once (given all <code>ICheckSettings</code> objects are on the same level).
     *
     * @param checkSettings Multiple <code>ICheckSettings</code> object representing different regions in the viewport.
     */
    public void check(ICheckSettings... checkSettings) {
        if (getIsDisabled()) {
            logger.log(String.format("check(ICheckSettings[%d]): Ignored", checkSettings.length));
            return;
        }

        Boolean forceFullPageScreenshot = getConfigGetter().getForceFullPageScreenshot();
        boolean originalForceFPS = forceFullPageScreenshot == null ? false : forceFullPageScreenshot ;

        if (checkSettings.length > 1) {
            getConfigSetter().setForceFullPageScreenshot(true);
        }

        logger.verbose(getConfigGetter().toString());

        Dictionary<Integer, GetRegion> getRegions = new Hashtable<>();
        Dictionary<Integer, ICheckSettingsInternal> checkSettingsInternalDictionary = new Hashtable<>();

        for (int i = 0; i < checkSettings.length; ++i) {
            ICheckSettings settings = checkSettings[i];
            ICheckSettingsInternal checkSettingsInternal = (ICheckSettingsInternal) settings;

            checkSettingsInternalDictionary.put(i, checkSettingsInternal);

            Region targetRegion = checkSettingsInternal.getTargetRegion();

            if (targetRegion != null) {
                getRegions.put(i, new IgnoreRegionByRectangle(targetRegion));
            } else {
                ISeleniumCheckTarget seleniumCheckTarget =
                        (settings instanceof ISeleniumCheckTarget) ? (ISeleniumCheckTarget) settings : null;

                if (seleniumCheckTarget != null) {
                    WebElement targetElement = getTargetElement(seleniumCheckTarget);
                    if (targetElement == null && seleniumCheckTarget.getFrameChain().size() == 1) {
                        targetElement = getFrameElement(seleniumCheckTarget.getFrameChain().get(0));
                    }

                    if (targetElement != null) {
                        getRegions.put(i, new IgnoreRegionByElement(targetElement));
                    }
                }
            }
            //check(settings);
        }

        this.scrollRootElement = driver.findElement(By.tagName("html"));
        this.currentFramePositionProvider = null;
        setPositionProvider(createPositionProvider());

        matchRegions(getRegions, checkSettingsInternalDictionary, checkSettings);
        getConfigSetter().setForceFullPageScreenshot(originalForceFPS);
    }

    private void matchRegions(Dictionary<Integer, GetRegion> getRegions,
                              Dictionary<Integer, ICheckSettingsInternal> checkSettingsInternalDictionary,
                              ICheckSettings[] checkSettings) {

        if (getRegions.size() == 0) return;

        this.originalFC = driver.getFrameChain().clone();

        FrameChain originalFC = tryHideScrollbars();

        Region bBox = findBoundingBox(getRegions, checkSettings);

        MatchWindowTask mwt = new MatchWindowTask(logger, serverConnector, runningSession, getConfigGetter().getMatchTimeout(), this);


        ScaleProviderFactory scaleProviderFactory = updateScalingParams();
        FullPageCaptureAlgorithm algo = createFullPageCaptureAlgorithm(scaleProviderFactory);

        BufferedImage screenshotImage = algo.getStitchedRegion(
                Region.EMPTY,
                bBox, positionProviderHandler.get());

        markElementForLayoutRCA(null);

        debugScreenshotsProvider.save(screenshotImage, "original");
        EyesWebDriverScreenshot screenshot = new EyesWebDriverScreenshot(logger, driver, screenshotImage, null, bBox.getNegativeLocation());

        for (int i = 0; i < checkSettings.length; ++i) {
            if (((Hashtable<Integer, GetRegion>) getRegions).containsKey(i)) {
                GetRegion getRegion = getRegions.get(i);
                ICheckSettingsInternal checkSettingsInternal = checkSettingsInternalDictionary.get(i);
                List<EyesScreenshot> subScreenshots = getSubScreenshots(screenshot, getRegion);
                matchRegion(checkSettingsInternal, mwt, subScreenshots);
            }
        }

        tryRestoreScrollbars(originalFC);
        ((EyesTargetLocator) driver.switchTo()).frames(this.originalFC);
    }

    private List<EyesScreenshot> getSubScreenshots(EyesWebDriverScreenshot screenshot, GetRegion getRegion) {
        List<EyesScreenshot> subScreenshots = new ArrayList<>();
        for (Region r : getRegion.getRegions(this, screenshot, true)) {
            logger.verbose("original sub-region: " + r);
            r = regionPositionCompensation.compensateRegionPosition(r, devicePixelRatio);
            logger.verbose("sub-region after compensation: " + r);
            EyesScreenshot subScreenshot = screenshot.getSubScreenshotForRegion(r, false);
            subScreenshots.add(subScreenshot);
        }
        return subScreenshots;
    }

    private void matchRegion(ICheckSettingsInternal checkSettingsInternal, MatchWindowTask mwt, List<EyesScreenshot> subScreenshots) {

        String name = checkSettingsInternal.getName();
        for (EyesScreenshot subScreenshot : subScreenshots) {

            debugScreenshotsProvider.save(subScreenshot.getImage(), String.format("subscreenshot_%s", name));

            ImageMatchSettings ims = mwt.createImageMatchSettings(checkSettingsInternal, subScreenshot, this);
            Location location = subScreenshot.getLocationInScreenshot(Location.ZERO, CoordinatesType.SCREENSHOT_AS_IS);
            AppOutput appOutput = new AppOutput(name, ImageUtils.base64FromImage(subScreenshot.getImage()), null, null);
            AppOutputWithScreenshot appOutputWithScreenshot = new AppOutputWithScreenshot(appOutput, subScreenshot, location);
            MatchResult matchResult = mwt.performMatch(new ArrayList<Trigger>(), appOutputWithScreenshot, name, false, checkSettingsInternal, ims, this);

            logger.verbose("matchResult.asExcepted: " + matchResult.getAsExpected());
        }
    }

    private Region findBoundingBox(Dictionary<Integer, GetRegion> getRegions, ICheckSettings[] checkSettings) {
        RectangleSize rectSize = getViewportSize();
        logger.verbose("rectSize: " + rectSize);
        EyesScreenshot screenshot = new EyesWebDriverScreenshot(logger, driver,
                new BufferedImage(rectSize.getWidth(), rectSize.getHeight(), BufferedImage.TYPE_INT_RGB));

        return findBoundingBox(getRegions, checkSettings, screenshot);
    }

    private Region findBoundingBox(Dictionary<Integer, GetRegion> getRegions, ICheckSettings[] checkSettings, EyesScreenshot screenshot) {
        Region bBox = null;
        for (int i = 0; i < checkSettings.length; ++i) {
            GetRegion getRegion = getRegions.get(i);
            if (getRegion != null) {
                List<Region> regions = getRegion.getRegions(this, screenshot, true);
                for (Region region : regions) {
                    if (bBox == null) {
                        bBox = new Region(region);
                    } else {
                        bBox = bBox.expandToContain(region);
                    }
                }
            }
        }
        return bBox;
    }

    private WebElement getFrameElement(FrameLocator frameLocator) {
        WebElement frameReference = frameLocator.getFrameReference();

        if (frameReference == null) {
            By selector = frameLocator.getFrameSelector();
            List<WebElement> possibleFrames = null;
            if (selector != null) {
                possibleFrames = driver.findElements(selector);
            } else {
                String nameOrId = frameLocator.getFrameNameOrId();
                if (nameOrId != null) {
                    possibleFrames = driver.findElementsById(nameOrId);
                    if (possibleFrames.size() == 0) {
                        possibleFrames = driver.findElementsByName(nameOrId);
                        if (possibleFrames.size() == 0) {
                            Integer frameIndex = frameLocator.getFrameIndex();
                            if (frameIndex != null) {
                                possibleFrames = driver.findElements(By.cssSelector(String.format("iframe:nth-of-type(%d)", frameIndex)));
                            }
                        }
                    }
                }
            }
            if (possibleFrames != null && possibleFrames.size() > 0) {
                frameReference = possibleFrames.get(0);
            }
        }
        return frameReference;
    }

    private WebElement getTargetElement(ISeleniumCheckTarget seleniumCheckTarget) {
        assert seleniumCheckTarget != null;
        By targetSelector = seleniumCheckTarget.getTargetSelector();
        WebElement targetElement = seleniumCheckTarget.getTargetElement();
        if (targetElement == null && targetSelector != null) {
            targetElement = this.driver.findElement(targetSelector);
        }
        return targetElement;
    }

    /**
     * Check.
     *
     * @param name          the name
     * @param checkSettings the check settings
     */
    public void check(String name, ICheckSettings checkSettings) {
        if (getIsDisabled()) {
            logger.log(String.format("check('%s', %s): Ignored", name, checkSettings));
            return;
        }

        ArgumentGuard.notNull(checkSettings, "checkSettings");
        if (name != null) {
            checkSettings = checkSettings.withName(name);
        }
        this.check(checkSettings);
    }

    @Override
    public void setIsDisabled(boolean disabled) {
        super.setIsDisabled(disabled);
    }

    @Override
    public String tryCaptureDom() {
        String fullWindowDom = "";
        FrameChain fc = driver.getFrameChain().clone();
        try {
            Frame frame = fc.peek();
            WebElement scrollRootElement = null;
            if (frame != null) {
                scrollRootElement = frame.getScrollRootElement();
            }
            if (scrollRootElement == null) {
                scrollRootElement = driver.findElement(By.tagName("html"));
            }
            PositionProvider positionProvider = new ScrollPositionProvider(logger, jsExecutor, scrollRootElement);

            DomCapture domCapture = new DomCapture(this);
            fullWindowDom = domCapture.getFullWindowDom(positionProvider);
        } catch (Exception e) {
            GeneralUtils.logExceptionStackTrace(logger, e);
        } finally {
            ((EyesTargetLocator) driver.switchTo()).frames(fc);
        }
        return fullWindowDom;
    }

    /**
     * Check.
     *
     * @param checkSettings the check settings
     */
    public void check(ICheckSettings checkSettings) {
        if (getIsDisabled()) {
            logger.log(String.format("check(%s): Ignored", checkSettings));
            return;
        }

        ArgumentGuard.notNull(checkSettings, "checkSettings");
        ArgumentGuard.notOfType(checkSettings, ISeleniumCheckTarget.class, "checkSettings");

        logger.verbose(getConfigGetter().toString());

        ICheckSettingsInternal checkSettingsInternal = (ICheckSettingsInternal) checkSettings;
        ISeleniumCheckTarget seleniumCheckTarget = (checkSettings instanceof ISeleniumCheckTarget) ? (ISeleniumCheckTarget) checkSettings : null;
        String name = checkSettingsInternal.getName();

        logger.verbose(String.format("check(\"%s\", checkSettings) - begin", name));

        this.stitchContent = checkSettingsInternal.getStitchContent() == null ? false : checkSettingsInternal.getStitchContent();
        final Region targetRegion = checkSettingsInternal.getTargetRegion();
        this.scrollRootElement = this.getScrollRootElement(seleniumCheckTarget);

        currentFramePositionProvider = null;
        setPositionProvider(createPositionProvider());
        this.originalFC = driver.getFrameChain().clone();

        ValidationInfo validationInfo = this.fireValidationWillStartEvent(name);

        if (!EyesSeleniumUtils.isMobileDevice(driver)) {
            logger.verbose("URL: " + driver.getCurrentUrl());
        }

        int switchedToFrameCount = this.switchToFrame(seleniumCheckTarget);

        MatchResult result = null;

        EyesTargetLocator switchTo = null;
        if (!EyesSeleniumUtils.isMobileDevice(this.driver)) {
            switchTo = (EyesTargetLocator) driver.switchTo();
        }
        FrameChain originalFC = null;
        if (targetRegion != null) {
            logger.verbose("have target region");
            originalFC = tryHideScrollbars();
            result = this.checkWindowBase(new RegionProvider() {
                @Override
                public Region getRegion() {
                    return new Region(targetRegion.getLocation(), targetRegion.getSize(), CoordinatesType.CONTEXT_RELATIVE);
                }
            }, name, false, checkSettings);
        } else if (seleniumCheckTarget != null) {
            WebElement targetElement = getTargetElement(seleniumCheckTarget);
            if (targetElement != null) {
                logger.verbose("have target element");
                originalFC = tryHideScrollbars();
                this.targetElement = targetElement;
                if (this.stitchContent) {
                    result = this.checkElement(name, checkSettings);
                } else {
                    result = this.checkRegion(name, checkSettings);
                }
                this.targetElement = null;
            } else if (seleniumCheckTarget.getFrameChain().size() > 0) {
                logger.verbose("have frame chain");
                originalFC = tryHideScrollbars();
                if (this.stitchContent) {
                    result = this.checkFullFrameOrElement(name, checkSettings);
                } else {
                    result = this.checkFrameFluent(name, checkSettings);
                }
            } else {
                logger.verbose("default case");
                if (!EyesSeleniumUtils.isMobileDevice(driver)) {
                    // required to prevent cut line on the last stitched part of the page on some browsers (like firefox).
                    switchTo.defaultContent();
                    originalFC = tryHideScrollbars();
                    currentFramePositionProvider = createPositionProvider(driver.findElement(By.tagName("html")));
                }
                result = this.checkWindowBase(NullRegionProvider.INSTANCE, name, false, checkSettings);
                if (!EyesSeleniumUtils.isMobileDevice(driver)) {
                    switchTo.frames(this.originalFC);
                }
            }
        }

        if (result == null) {
            result = new MatchResult();
        }

        while (switchedToFrameCount > 0) {
            this.driver.switchTo().parentFrame();
            switchedToFrameCount--;
        }

        if (this.positionMemento != null) {
            this.positionProviderHandler.get().restoreState(this.positionMemento);
            this.positionMemento = null;
        }

        if (!EyesSeleniumUtils.isMobileDevice(driver)) {
            switchTo.resetScroll();

            if (originalFC != null) {
                tryRestoreScrollbars(originalFC);
            }

            trySwitchToFrames(driver, switchTo, this.originalFC);
        }

        this.stitchContent = false;

        ValidationResult validationResult = new ValidationResult();
        validationResult.setAsExpected(result.getAsExpected());
        getSessionEventHandlers().validationEnded(getAUTSessionId(), validationInfo.getValidationId(), validationResult);

        logger.verbose("check - done!");
    }

    /**
     * Check frame fluent match result.
     *
     * @param name          the name
     * @param checkSettings the check settings
     * @return the match result
     */
    protected MatchResult checkFrameFluent(String name, ICheckSettings checkSettings) {
        FrameChain frameChain = driver.getFrameChain().clone();
        Frame targetFrame = frameChain.pop();
        this.targetElement = targetFrame.getReference();

        EyesTargetLocator switchTo = (EyesTargetLocator) driver.switchTo();
        switchTo.framesDoScroll(frameChain);

        MatchResult result = this.checkRegion(name, checkSettings);

        this.targetElement = null;
        return result;
    }

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
        WebDriver.TargetLocator switchTo = this.driver.switchTo();

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
            WebElement frameElement = this.driver.findElement(frameTarget.getFrameSelector());
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
        Frame frame = driver.getFrameChain().peek();
        frame.setScrollRootElement(rootElement);
    }

    private MatchResult checkFullFrameOrElement(String name, ICheckSettings checkSettings) {
        checkFrameOrElement = true;

        logger.verbose("enter");

        MatchResult result = checkWindowBase(new RegionProvider() {
            @Override
            public Region getRegion() {
                return getFullFrameOrElementRegion();
            }
        }, name, false, checkSettings);

        checkFrameOrElement = false;
        return result;
    }

    private Region getFullFrameOrElementRegion() {
        logger.verbose("checkFrameOrElement: " + checkFrameOrElement);
        if (checkFrameOrElement) {

            FrameChain fc = ensureFrameVisible();

            // FIXME - Scaling should be handled in a single place instead
            ScaleProviderFactory scaleProviderFactory = updateScalingParams();

            BufferedImage screenshotImage = imageProvider.getImage();

            debugScreenshotsProvider.save(screenshotImage, "checkFullFrameOrElement");

            scaleProviderFactory.getScaleProvider(screenshotImage.getWidth());

            EyesTargetLocator switchTo = (EyesTargetLocator) driver.switchTo();
            switchTo.frames(fc);

            final EyesWebDriverScreenshot screenshot = new EyesWebDriverScreenshot(logger, driver, screenshotImage);

            logger.verbose("replacing regionToCheck");
            setRegionToCheck(screenshot.getFrameWindow());
        }

        return Region.EMPTY;
    }

    private FrameChain ensureFrameVisible() {
        logger.verbose("scrollRootElement_: " + scrollRootElement);
        FrameChain originalFC = driver.getFrameChain().clone();
        FrameChain fc = driver.getFrameChain().clone();
        driver.executeScript("window.scrollTo(0,0);");
        while (fc.size() > 0) {
            logger.verbose("fc.Count: " + fc.size());
            //driver.getRemoteWebDriver().switchTo().parentFrame();
            EyesTargetLocator.parentFrame(logger, driver.getRemoteWebDriver().switchTo(), fc);
            driver.executeScript("window.scrollTo(0,0);");
            Frame prevFrame = fc.pop();
            Frame frame = fc.peek();
            WebElement scrollRootElement = null;
            if (fc.size() == this.originalFC.size()) {
                logger.verbose("PositionProvider: " + positionProviderHandler.get());
                positionMemento = positionProviderHandler.get().getState();
                scrollRootElement = this.scrollRootElement;
            } else {
                if (frame != null) {
                    scrollRootElement = frame.getScrollRootElement();
                }
                if (scrollRootElement == null) {
                    scrollRootElement = driver.findElement(By.tagName("html"));
                }
            }
            logger.verbose("scrollRootElement: " + scrollRootElement);

            PositionProvider positionProvider = getElementPositionProvider(scrollRootElement);
            positionProvider.setPosition(prevFrame.getLocation());

            Region reg = new Region(Location.ZERO, prevFrame.getInnerSize());
            effectiveViewport.intersect(reg);
        }
        ((EyesTargetLocator) driver.switchTo()).frames(originalFC);
        return originalFC;
    }

    private void ensureElementVisible(WebElement element) {
        if (this.targetElement == null || !getScrollToRegion()) {
            // No element? we must be checking the window.
            return;
        }

        if (EyesSeleniumUtils.isMobileDevice(driver.getRemoteWebDriver())) {
            logger.log("NATIVE context identified, skipping 'ensure element visible'");
            return;
        }

        FrameChain originalFC = driver.getFrameChain().clone();
        EyesTargetLocator switchTo = (EyesTargetLocator) driver.switchTo();

        EyesRemoteWebElement eyesRemoteWebElement = new EyesRemoteWebElement(logger, driver, element);
        Region elementBounds = eyesRemoteWebElement.getBounds();

        Location currentFrameOffset = originalFC.getCurrentFrameOffset();
        elementBounds = elementBounds.offset(currentFrameOffset.getX(), currentFrameOffset.getY());

        Region viewportBounds = getViewportScrollBounds();

        logger.verbose("viewportBounds: " + viewportBounds + " ; elementBounds: " + elementBounds);

        if (!viewportBounds.contains(elementBounds)) {
            ensureFrameVisible();

            Point p = element.getLocation();
            Location elementLocation = new Location(p.getX(), p.getY());

            WebElement scrollRootElement;
            if (originalFC.size() > 0 && !element.equals(originalFC.peek().getReference())) {
                switchTo.frames(originalFC);
                scrollRootElement = driver.findElement(By.tagName("html"));
            } else {
                scrollRootElement = this.scrollRootElement;
            }

            PositionProvider positionProvider = getElementPositionProvider(scrollRootElement);
            positionProvider.setPosition(elementLocation);
        }
    }

    private Region getViewportScrollBounds() {
        if (!getScrollToRegion()) {
            logger.log("WARNING: no region visibility strategy! returning an empty region!");
            return Region.EMPTY;
        }
        FrameChain originalFrameChain = driver.getFrameChain().clone();
        EyesTargetLocator switchTo = (EyesTargetLocator) driver.switchTo();
        switchTo.frames(this.originalFC);
        ScrollPositionProvider spp = new ScrollPositionProvider(logger, jsExecutor, scrollRootElement);
        Location location;
        try {
            location = spp.getCurrentPosition();
        } catch (EyesDriverOperationException e) {
            logger.log("WARNING: " + e.getMessage());
            logger.log("Assuming position is 0,0");
            location = new Location(0, 0);
        }
        Region viewportBounds = new Region(location, getViewportSize());
        switchTo.frames(originalFrameChain);
        return viewportBounds;
    }

    private MatchResult checkRegion(String name, ICheckSettings checkSettings) {
//        // If needed, scroll to the top/left of the element (additional help
//        // to make sure it's visible).
//        Point locationAsPoint = targetElement.getLocation();
//        RegionVisibilityStrategy regionVisibilityStrategy = regionVisibilityStrategyHandler.get();
//
//        regionVisibilityStrategy.moveToRegion(positionProvider,
//                new Location(locationAsPoint.getX(), locationAsPoint.getY()));

        MatchResult result = checkWindowBase(new RegionProvider() {
            @Override
            public Region getRegion() {
                Point p = targetElement.getLocation();
                Dimension d = targetElement.getSize();
                return new Region(p.getX(), p.getY(), d.getWidth(), d.getHeight(), CoordinatesType.CONTEXT_RELATIVE);
            }
        }, name, false, checkSettings);
        logger.verbose("Done! trying to scroll back to original position.");

        //regionVisibilityStrategy.returnToOriginalPosition(positionProvider);
        return result;
    }

    /**
     * Updates the state of scaling related parameters.
     *
     * @return the scale provider factory
     */
    protected ScaleProviderFactory updateScalingParams() {
        // Update the scaling params only if we haven't done so yet, and the user hasn't set anything else manually.
        if (devicePixelRatio == UNKNOWN_DEVICE_PIXEL_RATIO &&
                scaleProviderHandler.get() instanceof NullScaleProvider) {
            ScaleProviderFactory factory;
            logger.verbose("Trying to extract device pixel ratio...");
            try {
                devicePixelRatio = EyesSeleniumUtils.getDevicePixelRatio(this.jsExecutor);
            } catch (Exception e) {
                logger.verbose(
                        "Failed to extract device pixel ratio! Using default.");
                devicePixelRatio = DEFAULT_DEVICE_PIXEL_RATIO;
            }
            logger.verbose(String.format("Device pixel ratio: %f", devicePixelRatio));

            logger.verbose("Setting scale provider...");
            try {
                factory = getScaleProviderFactory();
            } catch (Exception e) {
                // This can happen in Appium for example.
                logger.verbose("Failed to set ContextBasedScaleProvider.");
                logger.verbose("Using FixedScaleProvider instead...");
                factory = new FixedScaleProviderFactory(logger, 1 / devicePixelRatio, scaleProviderHandler);
            }
            logger.verbose("Done!");
            return factory;
        }
        // If we already have a scale provider set, we'll just use it, and pass a mock as provider handler.
        PropertyHandler<ScaleProvider> nullProvider = new SimplePropertyHandler<>();
        return new ScaleProviderIdentityFactory(logger, scaleProviderHandler.get(), nullProvider);
    }

    private ScaleProviderFactory getScaleProviderFactory() {
        WebElement element = driver.findElement(By.tagName("html"));
        RectangleSize entireSize = EyesSeleniumUtils.getEntireElementSize(logger, jsExecutor, element);
        return new ContextBasedScaleProviderFactory(logger, entireSize,
                viewportSizeHandler.get(), devicePixelRatio, false,
                scaleProviderHandler);
    }

    /**
     * Gets current frame scroll root element.
     *
     * @return the current frame scroll root element
     */
    public WebElement getCurrentFrameScrollRootElement() {
        FrameChain fc = driver.getFrameChain().clone();
        Frame currentFrame = fc.peek();
        WebElement scrollRootElement = null;
        if (currentFrame != null) {
            scrollRootElement = currentFrame.getScrollRootElement();
        }
        if (scrollRootElement == null && !EyesSeleniumUtils.isMobileDevice(this.driver)) {
            scrollRootElement = driver.findElement(By.tagName("html"));
        }
        return scrollRootElement;
    }

    /**
     * Verifies the current frame.
     *
     * @param matchTimeout The amount of time to retry matching. (Milliseconds)
     * @param tag          An optional tag to be associated with the snapshot.
     */
    protected void checkCurrentFrame(int matchTimeout, String tag) {
        try {
            logger.verbose(String.format("CheckCurrentFrame(%d, '%s')", matchTimeout, tag));

            checkFrameOrElement = true;

            logger.verbose("Getting screenshot as base64..");
            String screenshot64 = driver.getScreenshotAs(OutputType.BASE64);
            logger.verbose("Done! Creating image object...");
            BufferedImage screenshotImage = ImageUtils.imageFromBase64(screenshot64);

            // FIXME - Scaling should be handled in a single place instead
            ScaleProvider scaleProvider = updateScalingParams().getScaleProvider(screenshotImage.getWidth());

            screenshotImage = ImageUtils.scaleImage(screenshotImage, scaleProvider);
            logger.verbose("Done! Building required object...");
            final EyesWebDriverScreenshot screenshot = new EyesWebDriverScreenshot(logger, driver, screenshotImage);
            logger.verbose("Done!");

            logger.verbose("replacing regionToCheck");
            setRegionToCheck(screenshot.getFrameWindow());

            super.checkWindowBase(NullRegionProvider.INSTANCE, tag, false, matchTimeout);
        } finally {
            checkFrameOrElement = false;
            regionToCheck = null;
        }
    }

    /**
     * See {@link #checkFrame(String, int, String)}.
     * {@code tag} defaults to {@code null}. Default match timeout is used.
     *
     * @param frameNameOrId the frame name or id
     */
    public void checkFrame(String frameNameOrId) {
        check(null, Target.frame(frameNameOrId));
    }

    /**
     * See {@link #checkFrame(String, int, String)}.
     * Default match timeout is used.
     *
     * @param frameNameOrId the frame name or id
     * @param tag           the tag
     */
    public void checkFrame(String frameNameOrId, String tag) {
        check(tag, Target.frame(frameNameOrId).fully());
    }

    /**
     * Matches the frame given as parameter, by switching into the frame and
     * using stitching to get an image of the frame.
     *
     * @param frameNameOrId The name or id of the frame to check. (The same                      name/id as would be used in a call to                      driver.switchTo().frame()).
     * @param matchTimeout  The amount of time to retry matching. (Milliseconds)
     * @param tag           An optional tag to be associated with the match.
     */
    public void checkFrame(String frameNameOrId, int matchTimeout, String tag) {
        check(tag, Target.frame(frameNameOrId).timeout(matchTimeout).fully());
    }

    /**
     * See {@link #checkFrame(int, int, String)}.
     * {@code tag} defaults to {@code null}. Default match timeout is used.
     *
     * @param frameIndex the frame index
     */
    public void checkFrame(int frameIndex) {
        checkFrame(frameIndex, USE_DEFAULT_MATCH_TIMEOUT, null);
    }

    /**
     * See {@link #checkFrame(int, int, String)}.
     * Default match timeout is used.
     *
     * @param frameIndex the frame index
     * @param tag        the tag
     */
    public void checkFrame(int frameIndex, String tag) {
        checkFrame(frameIndex, USE_DEFAULT_MATCH_TIMEOUT, tag);
    }

    /**
     * Matches the frame given as parameter, by switching into the frame and
     * using stitching to get an image of the frame.
     *
     * @param frameIndex   The index of the frame to switch to. (The same index                     as would be used in a call to                     driver.switchTo().frame()).
     * @param matchTimeout The amount of time to retry matching. (Milliseconds)
     * @param tag          An optional tag to be associated with the match.
     */
    public void checkFrame(int frameIndex, int matchTimeout, String tag) {
        if (getIsDisabled()) {
            logger.log(String.format("CheckFrame(%d, %d, '%s'): Ignored", frameIndex, matchTimeout, tag));
            return;
        }

        ArgumentGuard.greaterThanOrEqualToZero(frameIndex, "frameIndex");

        logger.log(String.format("CheckFrame(%d, %d, '%s')", frameIndex, matchTimeout, tag));

        check(tag, Target.frame(frameIndex).timeout(matchTimeout).fully());
    }

    /**
     * See {@link #checkFrame(WebElement, int, String)}.
     * {@code tag} defaults to {@code null}.
     * Default match timeout is used.
     *
     * @param frameReference the frame reference
     */
    public void checkFrame(WebElement frameReference) {
        checkFrame(frameReference, USE_DEFAULT_MATCH_TIMEOUT, null);
    }

    /**
     * See {@link #checkFrame(WebElement, int, String)}.
     * Default match timeout is used.
     *
     * @param frameReference the frame reference
     * @param tag            the tag
     */
    public void checkFrame(WebElement frameReference, String tag) {
        checkFrame(frameReference, USE_DEFAULT_MATCH_TIMEOUT, tag);
    }

    /**
     * Matches the frame given as parameter, by switching into the frame and
     * using stitching to get an image of the frame.
     *
     * @param frameReference The element which is the frame to switch to. (as                       would be used in a call to                       driver.switchTo().frame() ).
     * @param matchTimeout   The amount of time to retry matching (milliseconds).
     * @param tag            An optional tag to be associated with the match.
     */
    public void checkFrame(WebElement frameReference, int matchTimeout, String tag) {
        check(tag, Target.frame(frameReference).timeout(matchTimeout));
    }

    /**
     * Matches the frame given by the frames path, by switching into the frame
     * and using stitching to get an image of the frame.
     *
     * @param framePath    The path to the frame to check. This is a list of                     frame names/IDs (where each frame is nested in the                     previous frame).
     * @param matchTimeout The amount of time to retry matching (milliseconds).
     * @param tag          An optional tag to be associated with the match.
     */
    public void checkFrame(String[] framePath, int matchTimeout, String tag) {

        SeleniumCheckSettings settings = Target.frame(framePath[0]);
        for (int i = 1; i < framePath.length; i++) {
            settings.frame(framePath[i]);
        }
        check(tag, settings.timeout(matchTimeout).fully());
    }

    /**
     * Switches into the given frame, takes a snapshot of the application under
     * test and matches a region specified by the given selector.
     *
     * @param framePath     The path to the frame to check. This is a list of                      frame names/IDs (where each frame is nested in the previous frame).
     * @param selector      A Selector specifying the region to check.
     * @param matchTimeout  The amount of time to retry matching (milliseconds).
     * @param tag           An optional tag to be associated with the snapshot.
     * @param stitchContent Whether or not to stitch the internal content of the                      region (i.e., perform {@link #checkElement(By, int, String)} on the region.
     */
    public void checkRegionInFrame(String[] framePath, By selector,
                                   int matchTimeout, String tag,
                                   boolean stitchContent) {

        SeleniumCheckSettings settings = Target.frame(framePath[0]);
        for (int i = 1; i < framePath.length; i++) {
            settings = settings.frame(framePath[i]);
        }
        check(tag, settings.region(selector).timeout(matchTimeout).fully(stitchContent));
    }


    private MatchResult checkElement(String name, ICheckSettings checkSettings) {
        return this.checkElement(this.targetElement, name, checkSettings);
    }

    private MatchResult checkElement(WebElement element, String name, ICheckSettings checkSettings) {

        // Since the element might already have been found using EyesWebDriver.
        final EyesRemoteWebElement eyesElement = (element instanceof EyesRemoteWebElement) ?
                (EyesRemoteWebElement) element : new EyesRemoteWebElement(logger, driver, element);

        this.regionToCheck = null;
        WebElement scrollRootElement = getCurrentFrameScrollRootElement();
        PositionProvider positionProvider = createPositionProvider(scrollRootElement);
        PositionMemento originalPositionMemento = positionProvider.getState();

        ensureElementVisible(targetElement);

        String originalOverflow = null;

        Point pl = eyesElement.getLocation();
        MatchResult result;
        try {
            checkFrameOrElement = true;

            String displayStyle = eyesElement.getComputedStyle("display");

            if (getConfigGetter().getHideScrollbars()) {
                originalOverflow = eyesElement.getOverflow();
                eyesElement.setOverflow("hidden");
            }

            SizeAndBorders sizeAndBorders = eyesElement.getSizeAndBorders();
            Borders borderWidths = sizeAndBorders.getBorders();
            RectangleSize elementSize = sizeAndBorders.getSize();

            if (!("inline").equals(displayStyle) &&
                    elementSize.getHeight() <= effectiveViewport.getHeight() &&
                    elementSize.getWidth() <= effectiveViewport.getWidth()) {
                elementPositionProvider = new ElementPositionProvider(logger, driver, eyesElement);
            } else {
                elementPositionProvider = null;
            }

            final Region elementRegion = new Region(
                    pl.getX() + borderWidths.getLeft(), pl.getY() + borderWidths.getTop(),
                    elementSize.getWidth(), elementSize.getHeight(), CoordinatesType.SCREENSHOT_AS_IS);

            logger.verbose("Element region: " + elementRegion);

            regionToCheck = elementRegion;

            if (!effectiveViewport.isSizeEmpty()) {
                regionToCheck.intersect(effectiveViewport);
            }

            result = checkWindowBase(NullRegionProvider.INSTANCE, name, false, checkSettings);
        } catch (Exception ex) {
            GeneralUtils.logExceptionStackTrace(logger, ex);
            throw ex;
        } finally {
            if (originalOverflow != null) {
                eyesElement.setOverflow(originalOverflow);
            }

            checkFrameOrElement = false;

            positionProvider.restoreState(originalPositionMemento);
            regionToCheck = null;
            elementPositionProvider = null;
        }

        return result;
    }

    /**
     * Takes a snapshot of the application under test and matches a specific
     * element with the expected region output.
     *
     * @param element      The element to check.
     * @param matchTimeout The amount of time to retry matching. (Milliseconds)
     * @param tag          An optional tag to be associated with the snapshot.
     * @throws TestFailedException if a mismatch is detected and immediate failure reports are enabled
     */
    public void checkElement(WebElement element, int matchTimeout, String tag) {
        check(tag, Target.region(element).timeout(matchTimeout).fully());
    }

    /**
     * See {@link #checkElement(By, String)}.
     * {@code tag} defaults to {@code null}.
     *
     * @param selector the selector
     */
    public void checkElement(By selector) {
        check(null, Target.region(selector).fully());
    }

    /**
     * See {@link #checkElement(By, int, String)}.
     * Default match timeout is used.
     *
     * @param selector the selector
     * @param tag      the tag
     */
    public void checkElement(By selector, String tag) {
        check(tag, Target.region(selector).fully());
    }

    /**
     * Takes a snapshot of the application under test and matches an element
     * specified by the given selector with the expected region output.
     *
     * @param selector     Selects the element to check.
     * @param matchTimeout The amount of time to retry matching. (Milliseconds)
     * @param tag          An optional tag to be associated with the screenshot.
     * @throws TestFailedException if a mismatch is detected and                             immediate failure reports are enabled
     */
    public void checkElement(By selector, int matchTimeout, String tag) {
        check(tag, Target.region(selector).timeout(matchTimeout).fully());
    }

    /**
     * Adds a mouse trigger.
     *
     * @param action  Mouse action.
     * @param control The control on which the trigger is activated (context relative coordinates).
     * @param cursor  The cursor's position relative to the control.
     */
    public void addMouseTrigger(MouseAction action, Region control, Location cursor) {
        if (getIsDisabled()) {
            logger.verbose(String.format("Ignoring %s (disabled)", action));
            return;
        }

        // Triggers are actually performed on the previous window.
        if (lastScreenshot == null) {
            logger.verbose(String.format("Ignoring %s (no screenshot)", action));
            return;
        }

        if (!FrameChain.isSameFrameChain(driver.getFrameChain(),
                ((EyesWebDriverScreenshot) lastScreenshot).getFrameChain())) {
            logger.verbose(String.format("Ignoring %s (different frame)", action));
            return;
        }

        addMouseTriggerBase(action, control, cursor);
    }

    /**
     * Adds a mouse trigger.
     *
     * @param action  Mouse action.
     * @param element The WebElement on which the click was called.
     */
    public void addMouseTrigger(MouseAction action, WebElement element) {
        if (getIsDisabled()) {
            logger.verbose(String.format("Ignoring %s (disabled)", action));
            return;
        }

        ArgumentGuard.notNull(element, "element");

        Point pl = element.getLocation();
        Dimension ds = element.getSize();

        Region elementRegion = new Region(pl.getX(), pl.getY(), ds.getWidth(),
                ds.getHeight());

        // Triggers are actually performed on the previous window.
        if (lastScreenshot == null) {
            logger.verbose(String.format("Ignoring %s (no screenshot)", action));
            return;
        }

        if (!FrameChain.isSameFrameChain(driver.getFrameChain(),
                ((EyesWebDriverScreenshot) lastScreenshot).getFrameChain())) {
            logger.verbose(String.format("Ignoring %s (different frame)", action));
            return;
        }

        // Get the element region which is intersected with the screenshot,
        // so we can calculate the correct cursor position.
        elementRegion = lastScreenshot.getIntersectedRegion
                (elementRegion, CoordinatesType.CONTEXT_RELATIVE);

        addMouseTriggerBase(action, elementRegion,
                elementRegion.getMiddleOffset());
    }

    /**
     * Adds a keyboard trigger.
     *
     * @param control The control's context-relative region.
     * @param text    The trigger's text.
     */
    public void addTextTrigger(Region control, String text) {
        if (getIsDisabled()) {
            logger.verbose(String.format("Ignoring '%s' (disabled)", text));
            return;
        }

        if (lastScreenshot == null) {
            logger.verbose(String.format("Ignoring '%s' (no screenshot)", text));
            return;
        }

        if (!FrameChain.isSameFrameChain(driver.getFrameChain(),
                ((EyesWebDriverScreenshot) lastScreenshot).getFrameChain())) {
            logger.verbose(String.format("Ignoring '%s' (different frame)", text));
            return;
        }

        addTextTriggerBase(control, text);
    }

    /**
     * Adds a keyboard trigger.
     *
     * @param element The element for which we sent keys.
     * @param text    The trigger's text.
     */
    public void addTextTrigger(WebElement element, String text) {
        if (getIsDisabled()) {
            logger.verbose(String.format("Ignoring '%s' (disabled)", text));
            return;
        }

        ArgumentGuard.notNull(element, "element");

        Point pl = element.getLocation();
        Dimension ds = element.getSize();

        Region elementRegion = new Region(pl.getX(), pl.getY(), ds.getWidth(), ds.getHeight());

        addTextTrigger(elementRegion, text);
    }

    /**
     * Use this method only if you made a previous call to {@link #open
     * (WebDriver, String, String)} or one of its variants.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public RectangleSize getViewportSize() {
        RectangleSize viewportSize = viewportSizeHandler.get();
        if (viewportSize == null) {
            viewportSize = driver.getDefaultContentViewportSize();
        }
        return viewportSize;
    }

    /**
     * @param driver The driver to use for getting the viewport.
     * @return The viewport size of the current context.
     */
    static RectangleSize getViewportSize(WebDriver driver) {
        ArgumentGuard.notNull(driver, "driver");
        return EyesSeleniumUtils.getViewportSizeOrDisplaySize(new Logger(), driver);
    }

    /**
     * Use this method only if you made a previous call to {@link #open
     * (WebDriver, String, String)} or one of its variants.
     * <p>
     * {@inheritDoc}
     */
    @Override
    protected IConfigurationSetter setViewportSize(RectangleSize size) {
        if (viewportSizeHandler instanceof ReadOnlyPropertyHandler) {
            logger.verbose("Ignored (viewport size given explicitly)");
            return getConfigSetter();
        }

        if (!EyesSeleniumUtils.isMobileDevice(driver)) {
            FrameChain originalFrame = driver.getFrameChain();
            driver.switchTo().defaultContent();

            try {
                EyesSeleniumUtils.setViewportSize(logger, driver, size);
                effectiveViewport = new Region(Location.ZERO, size);
            } catch (EyesException e1) {
                // Just in case the user catches this error
                ((EyesTargetLocator) driver.switchTo()).frames(originalFrame);

                throw new TestFailedException("Failed to set the viewport size", e1);
            }
            ((EyesTargetLocator) driver.switchTo()).frames(originalFrame);
        }

        viewportSizeHandler.set(new RectangleSize(size.getWidth(), size.getHeight()));
        return getConfigSetter();
    }

    /**
     * @param driver The driver to use for setting the viewport.
     * @param size   The required viewport size.
     */
    static void setViewportSize(WebDriver driver, RectangleSize size) {
        ArgumentGuard.notNull(driver, "driver");
        EyesSeleniumUtils.setViewportSize(new Logger(), driver, size);
    }

    @Override
    protected void beforeOpen() {
    }

    private void trySwitchToFrames(WebDriver driver, EyesTargetLocator switchTo, FrameChain frames) {
        if (EyesSeleniumUtils.isMobileDevice(driver)) {
            return;
        }
        try {
            switchTo.frames(frames);
        } catch (WebDriverException e) {
            logger.log("WARNING: Failed to switch to original frame chain! " + e.getMessage());
        }
    }

    private FrameChain tryHideScrollbars() {
        if (EyesSeleniumUtils.isMobileDevice(driver)) {
            return new FrameChain(logger);
        }
        if (getConfigGetter().getHideScrollbars() || (getConfigGetter().getStitchMode() == StitchMode.CSS && stitchContent)) {
            FrameChain originalFC = driver.getFrameChain().clone();
            FrameChain fc = driver.getFrameChain().clone();
            Frame frame = fc.peek();
            if (fc.size() > 0) {
                while (fc.size() > 0) {
                    logger.verbose("fc.Count = " + fc.size());
                    if (stitchContent || fc.size() != originalFC.size()) {
                        if (frame != null) {
                            frame.hideScrollbars(driver);
                        } else {
                            logger.verbose("hiding scrollbars of element (1): " + this.scrollRootElement);
                            EyesSeleniumUtils.setOverflow(this.driver, "hidden", scrollRootElement);
                        }
                    }
                    driver.switchTo().parentFrame();
                    fc.pop();
                    frame = fc.peek();
                }
            } else {
                logger.verbose("hiding scrollbars of element (2): " + scrollRootElement);
                this.originalOverflow = EyesSeleniumUtils.setOverflow(this.driver, "hidden", scrollRootElement);
            }
            logger.verbose("switching back to original frame");
            ((EyesTargetLocator) driver.switchTo()).frames(originalFC);
            logger.verbose("done hiding scrollbars.");
            return originalFC;
        }
        return new FrameChain(logger);
    }

    private void tryRestoreScrollbars(FrameChain frameChain) {
        if (EyesSeleniumUtils.isMobileDevice(driver)) {
            return;
        }
        if (getConfigGetter().getHideScrollbars() || (getConfigGetter().getStitchMode() == StitchMode.CSS && stitchContent)) {
            ((EyesTargetLocator) driver.switchTo()).frames(frameChain);
            FrameChain originalFC = frameChain.clone();
            FrameChain fc = frameChain.clone();
            if (fc.size() > 0) {
                while (fc.size() > 0) {
                    Frame frame = fc.pop();
                    frame.returnToOriginalOverflow(driver);
                    EyesTargetLocator.parentFrame(logger, driver.getRemoteWebDriver().switchTo(), fc);
                }
            } else {
                logger.verbose("returning overflow of element to its original value: " + scrollRootElement);
                EyesSeleniumUtils.setOverflow(driver, originalOverflow, scrollRootElement);
            }
            ((EyesTargetLocator) driver.switchTo()).frames(originalFC);
            logger.verbose("done restoring scrollbars.");
        } else {
            logger.verbose("no need to restore scrollbars.");
        }
        driver.getFrameChain().clear();
    }

    @Override
    protected EyesScreenshot getSubScreenshot(EyesScreenshot screenshot, Region region, ICheckSettingsInternal
            checkSettingsInternal) {
        ISeleniumCheckTarget seleniumCheckTarget = (checkSettingsInternal instanceof ISeleniumCheckTarget) ? (ISeleniumCheckTarget) checkSettingsInternal : null;

        logger.verbose("original region: " + region);
        region = regionPositionCompensation.compensateRegionPosition(region, devicePixelRatio);
        logger.verbose("compensated region: " + region);

        if (seleniumCheckTarget == null) {
            // we should't get here, but just in case
            return screenshot.getSubScreenshot(region, false);
        }

        // For check frame continue as usual
        if (seleniumCheckTarget.getFrameChain().size() > 0) {
            return screenshot.getSubScreenshot(region, false);
        }

        // For check region, we want the screenshot window to know it's a region.
        return ((EyesWebDriverScreenshot) screenshot).getSubScreenshotForRegion(region, false);
    }

    @Override
    protected EyesScreenshot getScreenshot(ICheckSettingsInternal checkSettingsInternal) {

        logger.verbose("enter()");
        ScaleProviderFactory scaleProviderFactory = updateScalingParams();

        FrameChain originalFrameChain = driver.getFrameChain().clone();
        EyesTargetLocator switchTo = null;
        if (!EyesSeleniumUtils.isMobileDevice(this.driver)) {
            switchTo = (EyesTargetLocator) driver.switchTo();
            switchTo.frames(this.originalFC);
        }

        PositionProvider positionProvider = getPositionProvider();
        PositionMemento originalPosition = null;
        if (positionProvider != null && !EyesSeleniumUtils.isMobileDevice(this.driver)) {
            originalPosition = positionProvider.getState();
        }
        if (!EyesSeleniumUtils.isMobileDevice(this.driver)) {
            switchTo.frames(originalFrameChain);
        }

        FullPageCaptureAlgorithm algo = createFullPageCaptureAlgorithm(scaleProviderFactory);

        EyesWebDriverScreenshot result;

        Object activeElement = null;
        if (getConfigGetter().getHideCaret()) {
            try {
                activeElement = driver.executeScript("var activeElement = document.activeElement; activeElement && activeElement.blur(); return activeElement;");
            } catch (WebDriverException e) {
                logger.verbose("WARNING: Cannot hide caret! " + e.getMessage());
            }
        }

        if (checkFrameOrElement) {
            logger.verbose("Check frame/element requested");

            if (!EyesSeleniumUtils.isMobileDevice(this.driver)) {
                switchTo.frames(originalFrameChain);
            }

            BufferedImage entireFrameOrElement;
            if (elementPositionProvider == null) {
                WebElement scrollRootElement = driver.findElement(By.tagName("html"));
                PositionProvider elemPositionProvider = this.getElementPositionProvider(scrollRootElement);
                markElementForLayoutRCA(elemPositionProvider);
                entireFrameOrElement = algo.getStitchedRegion(regionToCheck, null, elemPositionProvider);
            } else {
                markElementForLayoutRCA(elementPositionProvider);
                entireFrameOrElement = algo.getStitchedRegion(regionToCheck, null, elementPositionProvider);
            }


            logger.verbose("Building screenshot object...");
            result = new EyesWebDriverScreenshot(logger, driver, entireFrameOrElement,
                    new RectangleSize(entireFrameOrElement.getWidth(), entireFrameOrElement.getHeight()));
        } else {
            boolean forceFullPageScreenshot = getConfigGetter().getForceFullPageScreenshot() != null ? getConfigGetter().getForceFullPageScreenshot() : false;
            if (forceFullPageScreenshot || stitchContent) {
                logger.verbose("Full page screenshot requested.");

                // Save the current frame path.
                Location originalFramePosition =
                        originalFrameChain.size() > 0
                                ? originalFrameChain.getDefaultContentScrollPosition()
                                : Location.ZERO;

                switchTo.frames(this.originalFC);
                algo = createFullPageCaptureAlgorithm(scaleProviderFactory);
                ////////////
                EyesRemoteWebElement eyesScrollRootElement;
                if (scrollRootElement instanceof EyesRemoteWebElement) {
                    eyesScrollRootElement = (EyesRemoteWebElement) scrollRootElement;
                } else {
                    eyesScrollRootElement = new EyesRemoteWebElement(logger, driver, scrollRootElement);
                }
                Point location = eyesScrollRootElement.getLocation();
                SizeAndBorders sizeAndBorders = eyesScrollRootElement.getSizeAndBorders();

                Region region = new Region(
                        location.getX() + sizeAndBorders.getBorders().getLeft(),
                        location.getY() + sizeAndBorders.getBorders().getTop(),
                        sizeAndBorders.getSize().getWidth(),
                        sizeAndBorders.getSize().getHeight());

                markElementForLayoutRCA(null);

                BufferedImage fullPageImage = algo.getStitchedRegion(region, null, positionProviderHandler.get());

                switchTo.frames(originalFrameChain);

                result = new EyesWebDriverScreenshot(logger, driver, fullPageImage, null, originalFramePosition);
            } else {
                ensureElementVisible(this.targetElement);

                try {
                    Thread.sleep(getConfigGetter().getWaitBeforeScreenshots());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                logger.verbose("Screenshot requested...");
                BufferedImage screenshotImage = imageProvider.getImage();
                debugScreenshotsProvider.save(screenshotImage, "original");

                ScaleProvider scaleProvider = scaleProviderFactory.getScaleProvider(screenshotImage.getWidth());
                if (scaleProvider.getScaleRatio() != 1.0) {
                    logger.verbose("scaling...");
                    screenshotImage = ImageUtils.scaleImage(screenshotImage, scaleProvider);
                    debugScreenshotsProvider.save(screenshotImage, "scaled");
                }

                CutProvider cutProvider = cutProviderHandler.get();
                if (!(cutProvider instanceof NullCutProvider)) {
                    logger.verbose("cutting...");
                    screenshotImage = cutProvider.cut(screenshotImage);
                    debugScreenshotsProvider.save(screenshotImage, "cut");
                }

                logger.verbose("Creating screenshot object...");
                result = new EyesWebDriverScreenshot(logger, driver, screenshotImage);
            }
        }

        if (getConfigGetter().getHideCaret() && activeElement != null) {
            try {
                driver.executeScript("arguments[0].focus();", activeElement);
            } catch (WebDriverException e) {
                logger.verbose("WARNING: Could not return focus to active element! " + e.getMessage());
            }
        }

        if (EyesSeleniumUtils.isMobileDevice(this.driver)) {
            logger.verbose("Done!");
            return result;
        }
        result.setDomUrl(tryCaptureAndPostDom(checkSettingsInternal));
        switchTo.frames(this.originalFC);
        if (positionProvider != null) {
            positionProvider.restoreState(originalPosition);
        }
        switchTo.frames(originalFrameChain);

        logger.verbose("Done!");
        return result;
    }

    private void markElementForLayoutRCA(PositionProvider elemPositionProvider) {
        ISeleniumPositionProvider positionProvider = elemPositionProvider != null ? (ISeleniumPositionProvider) elemPositionProvider : ((ISeleniumPositionProvider) getPositionProvider());
        WebElement scrolledElement = positionProvider.getScrolledElement();
        if (scrolledElement != null) {
            try {
                jsExecutor.executeScript("var e = arguments[0]; if (e != null) e.setAttribute('data-applitools-scroll','true');", scrolledElement);
            } catch (Exception e) {
                GeneralUtils.logExceptionStackTrace(logger, e);
            }
        }
    }

    private FullPageCaptureAlgorithm createFullPageCaptureAlgorithm(ScaleProviderFactory scaleProviderFactory) {
        WebElement scrollRootElement = getCurrentFrameScrollRootElement();
        PositionProvider originProvider = new ScrollPositionProvider(logger, jsExecutor, scrollRootElement);

        return new FullPageCaptureAlgorithm(logger, regionPositionCompensation,
                getConfigGetter().getWaitBeforeScreenshots(), debugScreenshotsProvider, screenshotFactory,
                originProvider,
                scaleProviderFactory,
                cutProviderHandler.get(),
                getConfigGetter().getStitchOverlap(),
                imageProvider);
    }

    @Override
    protected String getTitle() {
        if (!doNotGetTitle) {
            try {
                return driver.getTitle();
            } catch (Exception ex) {
                logger.verbose("failed (" + ex.getMessage() + ")");
                doNotGetTitle = true;
            }
        }

        return "";
    }

    @Override
    protected String getInferredEnvironment() {
        String userAgent = driver.getUserAgent();
        if (userAgent != null) {
            return "useragent:" + userAgent;
        }

        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This override also checks for mobile operating system.
     */
    @Override
    protected AppEnvironment getAppEnvironment() {

        AppEnvironment appEnv = super.getAppEnvironment();
        RemoteWebDriver underlyingDriver = driver.getRemoteWebDriver();
        // If hostOs isn't set, we'll try and extract and OS ourselves.
        if (appEnv.getOs() == null) {
            logger.log("No OS set, checking for mobile OS...");
            if (EyesSeleniumUtils.isMobileDevice(underlyingDriver)) {
                String platformName = null;
                logger.log("Mobile device detected! Checking device type..");
                if (EyesSeleniumUtils.isAndroid(underlyingDriver)) {
                    logger.log("Android detected.");
                    platformName = "Android";
                } else if (EyesSeleniumUtils.isIOS(underlyingDriver)) {
                    logger.log("iOS detected.");
                    platformName = "iOS";
                } else {
                    logger.log("Unknown device type.");
                }
                // We only set the OS if we identified the device type.
                if (platformName != null) {
                    String os = platformName;
                    String platformVersion =
                            EyesSeleniumUtils.getPlatformVersion(underlyingDriver);
                    if (platformVersion != null) {
                        String majorVersion =
                                platformVersion.split("\\.", 2)[0];

                        if (!majorVersion.isEmpty()) {
                            os += " " + majorVersion;
                        }
                    }

                    logger.verbose("Setting OS: " + os);
                    appEnv.setOs(os);
                }
            } else {
                logger.log("No mobile OS detected.");
            }
        }
        logger.log("Done!");
        return appEnv;
    }

    private WebElement getScrollRootElement(IScrollRootElementContainer scrollRootElementContainer) {
        WebElement scrollRootElement = null;
        if (!EyesSeleniumUtils.isMobileDevice(driver)) {
            if (scrollRootElementContainer == null) {
                scrollRootElement = driver.findElement(By.tagName("html"));
            } else {
                scrollRootElement = scrollRootElementContainer.getScrollRootElement();
                if (scrollRootElement == null) {
                    By scrollRootSelector = scrollRootElementContainer.getScrollRootSelector();
                    scrollRootElement = driver.findElement(scrollRootSelector != null ? scrollRootSelector : By.tagName("html"));
                }
            }
        }

        return scrollRootElement;
    }

    private PositionProvider getElementPositionProvider(WebElement scrollRootElement) {
        PositionProvider positionProvider = ((EyesRemoteWebElement) scrollRootElement).getPositionProvider();
        if (positionProvider == null) {
            positionProvider = createPositionProvider(scrollRootElement);
            ((EyesRemoteWebElement) scrollRootElement).setPositionProvider(positionProvider);
        }
        logger.verbose("position provider: " + positionProvider);
        currentFramePositionProvider = positionProvider;
        return positionProvider;
    }

    @Override
    protected String getAUTSessionId() {
        try {
            if (this.cachedAUTSessionId == null) {
                this.cachedAUTSessionId = driver.getRemoteWebDriver().getSessionId().toString();
            }
            return this.cachedAUTSessionId;
        } catch (Exception e) {
            logger.log("WARNING: Failed to get AUT session ID! (maybe driver is not available?). Error: "
                    + e.getMessage());
            return "";
        }
    }

    @Override
    public TestResults close(boolean throwEx) {
        TestResults results = super.close(throwEx);
        if (runner != null) {
            this.runner.aggregateResult(results);
        }
        this.cachedAUTSessionId = null;
        return results;
    }

    /**
     * The type Eyes selenium agent setup.
     */
    @SuppressWarnings("UnusedDeclaration")
    class EyesSeleniumAgentSetup {
        /**
         * The type Web driver info.
         */
        class WebDriverInfo {
            /**
             * Gets name.
             *
             * @return the name
             */
            public String getName() {
                return remoteWebDriver.getClass().getName();
            }

            /**
             * Gets capabilities.
             *
             * @return the capabilities
             */
            public Capabilities getCapabilities() {
                return remoteWebDriver.getCapabilities();
            }
        }

        /**
         * Instantiates a new Eyes selenium agent setup.
         */
        public EyesSeleniumAgentSetup() {
            remoteWebDriver = driver.getRemoteWebDriver();
        }

        private RemoteWebDriver remoteWebDriver;

        /**
         * Gets selenium session id.
         *
         * @return the selenium session id
         */
        public String getSeleniumSessionId() {
            return remoteWebDriver.getSessionId().toString();
        }

        /**
         * Gets web driver.
         *
         * @return the web driver
         */
        public WebDriverInfo getWebDriver() {
            return new WebDriverInfo();
        }

        /**
         * Gets device pixel ratio.
         *
         * @return the device pixel ratio
         */
        public double getDevicePixelRatio() {
            return SeleniumEyes.this.getDevicePixelRatio();
        }

        /**
         * Gets cut provider.
         *
         * @return the cut provider
         */
        public String getCutProvider() {
            return SeleniumEyes.this.cutProviderHandler.get().getClass().getName();
        }

        /**
         * Gets scale provider.
         *
         * @return the scale provider
         */
        public String getScaleProvider() {
            return SeleniumEyes.this.scaleProviderHandler.get().getClass().getName();
        }

        /**
         * Gets stitch mode.
         *
         * @return the stitch mode
         */
        public StitchMode getStitchMode() {
            return SeleniumEyes.this.getConfigGetter().getStitchMode();
        }

        /**
         * Gets hide scrollbars.
         *
         * @return the hide scrollbars
         */
        public boolean getHideScrollbars() {
            return SeleniumEyes.this.getConfigGetter().getHideScrollbars();
        }

        /**
         * Gets force full page screenshot.
         *
         * @return the force full page screenshot
         */
        public boolean getForceFullPageScreenshot() {
            Boolean forceFullPageScreenshot = getConfigGetter().getForceFullPageScreenshot();
            if(forceFullPageScreenshot == null ) return false;
            return forceFullPageScreenshot;
        }
    }

    @Override
    public Object getAgentSetup() {
        return new EyesSeleniumAgentSetup();
    }

    /**
     * Gets server connector.
     *
     * @return the server connector
     */
    public IServerConnector getServerConnector() {
        return this.serverConnector;
    }

    @Override
    public Boolean isSendDom() {
        return !EyesSeleniumUtils.isMobileDevice(driver) && super.isSendDom();
    }

    @Override
    public IConfigurationGetter getConfigGetter() {
        return configurationProvider.get();
    }

    @Override
    protected IConfigurationSetter getConfigSetter() {
        return this.configurationProvider.set();
    }


}
