package com.jg.ignition.webcompat.common;

import com.inductiveautomation.ignition.common.config.BasicProperty;
import com.inductiveautomation.ignition.common.config.PropertySet;
import com.inductiveautomation.ignition.common.gson.JsonObject;

public final class LegacyResourceUtils {
    private LegacyResourceUtils() {
    }

    public static final String RESOURCE_TYPE = "resource-type";
    public static final String RESOURCE_KEY = "config.json";

    public static JsonObject configFromPropertySet(PropertySet config) {
        JsonObject result = new JsonObject();
        String resourceType = config.getNonNull(LegacyProperties.FACTORY_ID, "");
        result.addProperty(RESOURCE_TYPE, resourceType);

        if (PythonResource.TYPE.equals(resourceType)) {
            for (String method : PythonResource.METHODS) {
                MethodObject methodObject = new MethodObject();
                methodObject.enabled = true;
                methodObject.code = config.getNonNull(new BasicProperty<>(method, String.class), "");
                methodObject.requireHttps = config.getNonNull(LegacyProperties.REQUIRE_HTTPS, false);
                methodObject.requireAuth = config.getNonNull(LegacyProperties.REQUIRE_AUTH, false);
                methodObject.userSource = config.getOrDefault(LegacyProperties.USER_SOURCE);
                methodObject.requiredRoles = config.getOrDefault(LegacyProperties.REQUIRED_ROLES);
                methodObject.maxRetryAttempts = config.getNonNull(LegacyProperties.MAX_RETRY_ATTEMPTS, 3);
                result.add(method, WebCompatModule.GSON.toJsonTree(methodObject));
            }
        }

        return result;
    }
}
