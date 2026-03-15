package com.jg.ignition.webcompat.common;

import com.inductiveautomation.ignition.common.Base64;
import com.inductiveautomation.ignition.common.config.BasicProperty;
import com.inductiveautomation.ignition.common.config.PropertySet;
import com.inductiveautomation.ignition.common.gson.JsonObject;

public final class LegacyResourceUtils {
    private LegacyResourceUtils() {
    }

    public static final String RESOURCE_TYPE = "resource-type";
    public static final String RESOURCE_KEY = "config.json";
    public static final String CONTENT_TYPE = "content-type";
    public static final String TEXT = "text";
    public static final String FOLDER_PATH = "folder-path";
    public static final String BYTES = "bytes";

    public static final String FILE_RESOURCE = "file-resource";
    public static final String TEXT_RESOURCE = "text-resource";
    public static final String MOUNTED_FOLDER_RESOURCE = "mounted-folder-resource";
    public static final String DEFAULT_CONTENT_TYPE = "text/plain";

    public static JsonObject configFromPropertySet(PropertySet config) {
        JsonObject result = new JsonObject();
        String resourceType = config.getNonNull(LegacyProperties.FACTORY_ID, "");
        result.addProperty(RESOURCE_TYPE, resourceType);

        switch (resourceType) {
            case PythonResource.TYPE -> {
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
            case FILE_RESOURCE -> {
                String contentType = config.getOrElse(LegacyProperties.CONTENT_TYPE, "application/octet-stream");
                result.addProperty(CONTENT_TYPE, contentType);
                byte[] bytes = config.getNonNull(LegacyProperties.BYTES, new byte[0]);
                result.addProperty(BYTES, Base64.encodeBytes(bytes));
            }
            case TEXT_RESOURCE -> {
                result.addProperty(TEXT, config.getNonNull(LegacyProperties.TEXT, ""));
                result.addProperty(CONTENT_TYPE, config.getOrElse(LegacyProperties.CONTENT_TYPE, DEFAULT_CONTENT_TYPE));
            }
            case MOUNTED_FOLDER_RESOURCE -> result.addProperty(FOLDER_PATH, config.getNonNull(LegacyProperties.PATH, ""));
            default -> {
            }
        }

        return result;
    }
}
