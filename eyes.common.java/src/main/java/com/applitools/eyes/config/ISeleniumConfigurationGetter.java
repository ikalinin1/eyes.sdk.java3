package com.applitools.eyes.config;

import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.selenium.StitchMode;
import com.applitools.eyes.visualgridclient.model.RenderBrowserInfo;

import java.util.List;

public interface ISeleniumConfigurationGetter extends IConfigurationGetter{
    boolean getForceFullPageScreenshot();

    int getWaitBeforeScreenshots();

    StitchMode getStitchMode();

    boolean getHideScrollbars();

    boolean getHideCaret();

    int getConcurrentSessions();

    List<RenderBrowserInfo> getBrowsersInfo();

    boolean isThrowExceptionOn();

    String getTestName();

    void setTestName(String testName);

    RectangleSize getViewportSize();

    boolean isForceFullPageScreenshot();

    boolean isRenderingConfig();

    boolean isStitchContent();

    boolean isSendDom();
}
