package com.applitools.eyes.visualgrid.model;

import com.applitools.eyes.selenium.BrowserType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RenderRequest {
    @JsonInclude
    private String testId;

    @JsonInclude
    private String renderId;

    @JsonIgnore
    private String stepId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String agentId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String stitchingService;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String webhook;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String url;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private RGridDom snapshot;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, RGridResource> resources;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private RenderInfo renderInfo;

    @JsonIgnore
    private String platformName;

    @JsonIgnore
    private final String platformType;

    @JsonIgnore
    private BrowserType browserName;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object scriptHooks;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<VisualGridSelector> selectorsToFindRegionsFor;

    @JsonInclude
    private boolean sendDom = false;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Object> options;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String renderer;

    public RenderRequest(String testId, String webHook, String url, RGridDom snapshot, Map<String, RGridResource> resources, RenderInfo renderInfo,
                         String platformName, String platformType, BrowserType browserName, Object scriptHooks, List<VisualGridSelector> selectorsToFindRegionsFor,
                         boolean sendDom, String renderer, String stepId, String stitchingService, String agentId, List<VisualGridOption> visualGridOptions) {
        this.testId = testId;
        this.webhook = webHook;
        this.url = url;
        this.snapshot = snapshot;
        this.resources = resources;
        this.renderInfo = renderInfo;
        this.renderer = renderer;
        this.stepId = stepId;
        this.platformName = platformName;
        this.platformType = platformType;
        this.browserName = browserName;
        this.scriptHooks = scriptHooks;
        this.selectorsToFindRegionsFor = selectorsToFindRegionsFor;
        this.sendDom = sendDom;
        this.stitchingService = stitchingService;
        this.agentId = agentId;
        this.options = new HashMap<>();
        for (VisualGridOption option : visualGridOptions) {
            this.options.put(option.getKey(), option.getValue());
        }
    }

    public RenderRequest(String testId, String webHook, RGridDom snapshot, RenderInfo renderInfo, String platformName,
                         String platformType, Object scriptHooks, String renderer, String stepId, String stitchingService,
                         String agentId, List<VisualGridOption> visualGridOptions) {
        this.testId = testId;
        this.webhook = webHook;
        this.snapshot = snapshot;
        this.renderInfo = renderInfo;
        this.platformName = platformName;
        this.platformType = platformType;
        this.scriptHooks = scriptHooks;
        this.renderer = renderer;
        this.stepId = stepId;
        this.stitchingService = stitchingService;
        this.agentId = agentId;
        this.options = new HashMap<>();
        for (VisualGridOption option : visualGridOptions) {
            this.options.put(option.getKey(), option.getValue());
        }
    }

    /**
     * A smaller constructor for creating a request for job-info
     */
    public RenderRequest(RenderInfo renderInfo, String platformName, String platformType, BrowserType browserName) {
        this.renderInfo = renderInfo;
        this.platformName = platformName;
        this.platformType = platformType;
        this.browserName = browserName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public RGridDom getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(RGridDom snapshot) {
        this.snapshot = snapshot;
    }

    public Map<String, RGridResource> getResources() {
        return resources;
    }

    public void setResources(Map<String, RGridResource> resources) {
        this.resources = resources;
    }

    public RenderInfo getRenderInfo() {
        return renderInfo;
    }

    public void setRenderInfo(RenderInfo renderInfo) {
        this.renderInfo = renderInfo;
    }

    public void setPlatformName(String platformName) {
        this.platformName = platformName;
    }

    public void setBrowserName(BrowserType browserName) {
        this.browserName = browserName;
    }

    public Object getScriptHooks() {
        return scriptHooks;
    }

    public void setScriptHooks(Object scriptHooks) {
        this.scriptHooks = scriptHooks;
    }

    public List<VisualGridSelector> getSelectorsToFindRegionsFor() {
        return selectorsToFindRegionsFor;
    }

    public void setSelectorsToFindRegionsFor(List<VisualGridSelector> selectorsToFindRegionsFor) {
        this.selectorsToFindRegionsFor = selectorsToFindRegionsFor;
    }

    public boolean isSendDom() {
        return sendDom;
    }

    public void setSendDom(boolean sendDom) {
        this.sendDom = sendDom;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    @JsonProperty("browser")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, Object> getBrowser() {
        if (browserName == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("name", this.browserName);
        return map;
    }

    @JsonProperty("platform")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, Object> getPlatform() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", this.platformName);
        map.put("type", this.platformType);
        return map;
    }

    public String getStepId() {
        return stepId;
    }

    public String getWebhook() {
        return webhook;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getRenderId() {
        return renderId;
    }

    public void setRenderId(String renderId) {
        this.renderId = renderId;
    }

    public String getStitchingService() {
        return stitchingService;
    }

    public void setStitchingService(String stitchingService) {
        this.stitchingService = stitchingService;
    }

    public BrowserType getBrowserName() {
        return browserName;
    }

    @Override
    public String toString() {
        return "RenderRequest{" +
                "testId='" + testId + '\'' +
                "renderId='" + renderId + '\'' +
                ", stepId=" + stepId +
                ", agentId='" + agentId + '\'' +
                ", webhook='" + webhook + '\'' +
                ", url='" + url + '\'' +
                ", snapshot=" + snapshot +
                ", resources=" + resources +
                ", renderInfo=" + renderInfo +
                ", platform='" + platformName + '\'' +
                ", browserName='" + browserName + '\'' +
                ", scriptHooks=" + scriptHooks +
                ", selectorsToFindRegionsFor=" + selectorsToFindRegionsFor +
                ", sendDom=" + sendDom +
                ", options=" + options +
                '}';
    }

    public String getRenderer() {
        return renderer;
    }

    public String getTestId() {
        return testId;
    }

    public void setTestId(String testId) {
        this.testId = testId;
    }
}
