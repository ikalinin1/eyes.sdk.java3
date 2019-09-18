package com.applitools.eyes.selenium;

import com.applitools.eyes.*;
import com.applitools.eyes.selenium.fluent.Target;
import com.applitools.eyes.utils.CommUtils;
import com.applitools.eyes.utils.SeleniumUtils;
import org.openqa.selenium.WebDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

public final class TestBatchAPI {
    @Test
    public void testCloseBatch() {
        ClassicRunner classicRunner = new ClassicRunner();

        eyesTest(classicRunner);

    }

    private void eyesTest(EyesRunner runner) {

        WebDriver driver = SeleniumUtils.createChromeDriver();

        // Initialize the VisualGridEyes SDK and set your private API key.
        Eyes eyes = new Eyes(runner);
        eyes.setLogHandler(new FileLogger("runnerTest.log", false, true));
//        eyes.setLogHandler(new StdoutLogHandler(true));
        eyes.setServerUrl("https://eyesfabric4eyes.applitools.com/");
//        eyes.setProxy(new ProxySettings("http://127.0.0.1:8888"));

        // Switch sendDom flag on
        eyes.setSendDom(true);
        eyes.setStitchMode(StitchMode.CSS);
        BatchInfo batchInfo = new BatchInfo("Runner Testing");
        eyes.setBatch(batchInfo);
        eyes.setApiKey("CAE7aS103TDz7XyegELya3tHpEIXTFi0gBBwvgq104PSHIU110");
        // Navigate the browser to the "hello world!" web-site.
//        driver.get("https://applitools.com/helloworld");

        eyes.open(driver, "Applitools Eyes Java SDK", "Classic Runner Test",
                new RectangleSize(1200, 800));

        boolean batchBeforeDelete = CommUtils.isBatchOpen(batchInfo.getId(), eyes.getServerUrl().toString(), eyes.getApiKey());

        Assert.assertTrue(batchBeforeDelete);

        eyes.closeAsync();

        driver.quit();

        runner.getAllTestResults(false);
        boolean batchAfterClose = CommUtils.isBatchOpen(batchInfo.getId(), eyes.getServerUrl().toString(), eyes.getApiKey());
        Assert.assertFalse(batchAfterClose);


    }
}