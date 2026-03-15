package com.jg.ignition.webcompat.common;

import com.inductiveautomation.ignition.common.JsonUtilities;
import com.inductiveautomation.ignition.common.gson.JsonDeserializationContext;
import com.inductiveautomation.ignition.common.gson.JsonDeserializer;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonSerializationContext;
import com.inductiveautomation.ignition.common.gson.JsonSerializer;
import java.lang.reflect.Type;
import org.apache.commons.lang3.StringUtils;

public class MethodObject {
    public boolean enabled = false;
    public boolean requireHttps = false;
    public boolean requireAuth = false;
    public String userSource = "";
    public String requiredRoles = "";
    public int maxRetryAttempts = 3;
    public String code = "";

    public String getCodeToSave(String method) {
        return getCodeToSave(method, code);
    }

    public static String getCodeToSave(String method, String methodCode) {
        String safeCode = methodCode == null ? "" : methodCode;
        return "def " + method + "(request, session):\n" + safeCode;
    }

    public static class GsonAdapter implements JsonSerializer<MethodObject>, JsonDeserializer<MethodObject> {
        @Override
        public JsonElement serialize(MethodObject src, Type typeOfSrc, JsonSerializationContext context) {
            var obj = new com.inductiveautomation.ignition.common.gson.JsonObject();
            obj.addProperty("enabled", src.enabled);
            obj.addProperty("require-https", src.requireHttps);
            obj.addProperty("require-auth", src.requireAuth);
            obj.addProperty("user-source", StringUtils.defaultString(src.userSource));
            obj.addProperty("required-roles", StringUtils.defaultString(src.requiredRoles));
            obj.addProperty("max-retry-attempts", src.maxRetryAttempts);
            obj.addProperty("code", src.code);
            return JsonUtilities.createDeterministicCopy(obj);
        }

        @Override
        public MethodObject deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            MethodObject method = new MethodObject();
            if (json != null && json.isJsonObject()) {
                method.enabled = JsonUtilities.readBoolean(json, "enabled", false);
                method.requireHttps = JsonUtilities.readBoolean(json, "require-https", false);
                method.requireAuth = JsonUtilities.readBoolean(json, "require-auth", false);
                method.userSource = JsonUtilities.readString(json, "user-source", "");
                method.requiredRoles = JsonUtilities.readString(json, "required-roles", "");
                method.maxRetryAttempts = JsonUtilities.readInteger(json, "max-retry-attempts", 3);
                method.code = JsonUtilities.read(json, "code").map(JsonElement::getAsString).orElse("");
            }
            return method;
        }
    }
}
