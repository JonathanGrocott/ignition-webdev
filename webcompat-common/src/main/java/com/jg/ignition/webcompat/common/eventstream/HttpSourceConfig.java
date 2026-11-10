package com.jg.ignition.webcompat.common.eventstream;

import com.inductiveautomation.ignition.common.JsonUtilities;
import com.inductiveautomation.ignition.common.gson.JsonObject;

public record HttpSourceConfig(boolean requireHttps, boolean requireAuth, String userSource,
                               String requiredRoles, int maxRetryAttempts) {
    public static HttpSourceConfig fromJson(JsonObject config) {
        JsonObject values = config == null ? new JsonObject() : config;
        return new HttpSourceConfig(
                JsonUtilities.readBoolean(values, "requireHttps", true),
                JsonUtilities.readBoolean(values, "requireAuth", false),
                JsonUtilities.readString(values, "userSource", ""),
                JsonUtilities.readString(values, "requiredRoles", ""),
                JsonUtilities.readInteger(values, "maxRetryAttempts", 0));
    }

    public JsonObject toJson() {
        JsonObject config = new JsonObject();
        config.addProperty("requireHttps", requireHttps);
        config.addProperty("requireAuth", requireAuth);
        config.addProperty("userSource", userSource);
        config.addProperty("requiredRoles", requiredRoles);
        config.addProperty("maxRetryAttempts", maxRetryAttempts);
        return config;
    }
}
