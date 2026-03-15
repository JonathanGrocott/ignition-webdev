package com.jg.ignition.webcompat.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.inductiveautomation.ignition.common.gson.JsonObject;
import org.junit.jupiter.api.Test;

class MethodObjectGsonTest {
    @Test
    void serializesAndDeserializesLegacyHyphenatedKeys() {
        MethodObject method = new MethodObject();
        method.enabled = true;
        method.requireHttps = true;
        method.requireAuth = true;
        method.userSource = "users";
        method.requiredRoles = "Admin,API";
        method.maxRetryAttempts = 8;
        method.code = "\treturn {'response': 'ok'}";

        JsonObject json = WebCompatModule.GSON.toJsonTree(method).getAsJsonObject();
        assertTrue(json.get("require-https").getAsBoolean());
        assertTrue(json.get("require-auth").getAsBoolean());
        assertEquals("users", json.get("user-source").getAsString());
        assertEquals("Admin,API", json.get("required-roles").getAsString());
        assertEquals(8, json.get("max-retry-attempts").getAsInt());

        MethodObject restored = WebCompatModule.GSON.fromJson(json, MethodObject.class);
        assertTrue(restored.enabled);
        assertTrue(restored.requireHttps);
        assertTrue(restored.requireAuth);
        assertEquals("users", restored.userSource);
        assertEquals("Admin,API", restored.requiredRoles);
        assertEquals(8, restored.maxRetryAttempts);
        assertEquals("\treturn {'response': 'ok'}", restored.code);
    }

    @Test
    void stripsFunctionDefBodyFromStoredPython() {
        String body = PythonResource.maybeRemoveDef("def doGet(request, session):\n\treturn {'response':'ok'}");
        assertEquals("\treturn {'response':'ok'}", body);

        String plain = PythonResource.maybeRemoveDef("\treturn {'response':'ok'}");
        assertEquals("\t\treturn {'response':'ok'}", plain);
    }

    @Test
    void wrapsCodeWithFunctionDefOnSave() {
        String code = MethodObject.getCodeToSave("doGet", "\treturn {'response': 'ok'}");
        assertTrue(code.startsWith("def doGet(request, session):\n"));
        assertFalse(code.contains("def doPost"));
    }
}
