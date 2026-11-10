package com.jg.ignition.webcompat.common;

import com.inductiveautomation.ignition.common.gson.Gson;
import com.inductiveautomation.ignition.common.gson.GsonBuilder;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceType;

public final class WebCompatModule {
    private WebCompatModule() {
    }

    public static final String MODULE_ID = "com.jg.ignition.webcompat";

    // Compatibility contract: execute existing Web Dev resources in-place.
    public static final ResourceType RESOURCE_TYPE =
            new ResourceType("com.inductiveautomation.webdev", "resources");

    public static final String SERVLET_KEY = "webdev";
    public static final String EVENT_STREAM_MODULE_ID = "com.inductiveautomation.eventstream";
    public static final String EVENT_STREAM_SOURCE_ID = "com.inductiveautomation.webdev.http";
    public static final String EVENT_STREAM_SERVLET_KEY = "eventstream";
    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter(MethodObject.class, new MethodObject.GsonAdapter())
            .create();
}
