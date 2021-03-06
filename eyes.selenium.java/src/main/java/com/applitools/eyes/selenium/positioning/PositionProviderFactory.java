package com.applitools.eyes.selenium.positioning;

import com.applitools.eyes.BrowserNames;
import com.applitools.eyes.IEyesJsExecutor;
import com.applitools.eyes.Logger;
import com.applitools.eyes.UserAgent;
import com.applitools.eyes.positioning.PositionProvider;
import com.applitools.eyes.selenium.StitchMode;
import com.applitools.utils.ArgumentGuard;

import org.openqa.selenium.WebElement;

public class PositionProviderFactory {
    public static PositionProvider getPositionProvider(Logger logger, StitchMode stitchMode, IEyesJsExecutor executor, WebElement scrollRootElement, UserAgent userAgent) {
        ArgumentGuard.notNull(logger, "logger");
        ArgumentGuard.notNull(executor, "executor");
        ArgumentGuard.notNull(stitchMode, "stitchMode");

        switch (stitchMode) {
            case CSS:
                return new CssTranslatePositionProvider(logger, executor, scrollRootElement);
            case SCROLL:
                if (userAgent != null && userAgent.getBrowser().equalsIgnoreCase(BrowserNames.EDGE)) {
                    return new EdgeBrowserScrollPositionProvider(logger, executor, scrollRootElement);
                }
                return new SeleniumScrollPositionProvider(logger, executor, scrollRootElement);
            default:
                return getPositionProvider(logger, StitchMode.SCROLL, executor, scrollRootElement, userAgent);
        }
    }
}
