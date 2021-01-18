package com.applitools.eyes.services;

import com.applitools.connectivity.ServerConnector;
import com.applitools.eyes.EyesException;
import com.applitools.eyes.Logger;
import com.applitools.eyes.TaskListener;
import com.applitools.eyes.visualgrid.model.*;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class ResourceCollectionService extends EyesService<FrameData, RGridDom> {
    final Map<String, RGridResource> resourcesCacheMap;
    private IDebugResourceWriter debugResourceWriter;

    final Map<String, DomAnalyzer> tasksInDomAnalyzingProcess = Collections.synchronizedMap(new HashMap<String, DomAnalyzer>());

    public ResourceCollectionService(Logger logger, ServerConnector serverConnector, IDebugResourceWriter debugResourceWriter,
                                     Map<String, RGridResource> resourcesCacheMap) {
        super(logger, serverConnector);
        this.debugResourceWriter = debugResourceWriter != null ? debugResourceWriter : new NullDebugResourceWriter();
        this.resourcesCacheMap = resourcesCacheMap;
    }

    public void setDebugResourceWriter(IDebugResourceWriter debugResourceWriter) {
        this.debugResourceWriter = debugResourceWriter != null ? debugResourceWriter : new NullDebugResourceWriter();
    }

    @Override
    public void run() {
        while (!inputQueue.isEmpty()) {
            final Pair<String, FrameData> nextInput = inputQueue.remove(0);
            final FrameData frameData = nextInput.getRight();
            try {
                DomAnalyzer domAnalyzer = new DomAnalyzer(logger, serverConnector, debugResourceWriter, frameData,
                        resourcesCacheMap, new TaskListener<Map<String, RGridResource>>() {
                    @Override
                    public void onComplete(final Map<String, RGridResource> resourceMap) {
                        RGridDom dom = new RGridDom(frameData.getCdt(), resourceMap, frameData.getUrl());
                        dom.setTestIds(frameData.getTestIds());
                        outputQueue.add(Pair.of(nextInput.getLeft(), dom));
                        tasksInDomAnalyzingProcess.remove(nextInput.getLeft());
                    }

                    @Override
                    public void onFail() {
                        errorQueue.add(Pair.<String, Throwable>of(nextInput.getLeft(), new EyesException("Dom analyzer failed")));
                        tasksInDomAnalyzingProcess.remove(nextInput.getLeft());
                    }
                });
                tasksInDomAnalyzingProcess.put(nextInput.getLeft(), domAnalyzer);
            } catch (Throwable t) {
                errorQueue.add(Pair.of(nextInput.getLeft(), t));
            }
        }

        List<DomAnalyzer> domAnalyzers;
        synchronized (tasksInDomAnalyzingProcess) {
            domAnalyzers = new ArrayList<>(tasksInDomAnalyzingProcess.values());
        }

        for (DomAnalyzer domAnalyzer : domAnalyzers) {
            domAnalyzer.run();
        }
    }
}
