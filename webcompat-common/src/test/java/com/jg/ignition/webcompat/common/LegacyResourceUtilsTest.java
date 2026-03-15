package com.jg.ignition.webcompat.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.inductiveautomation.ignition.common.Base64;
import com.inductiveautomation.ignition.common.config.BasicProperty;
import com.inductiveautomation.ignition.common.config.BasicPropertySet;
import org.junit.jupiter.api.Test;

class LegacyResourceUtilsTest {
    @Test
    void convertsLegacyPythonPropertySetToConfigJsonShape() {
        BasicPropertySet ps = new BasicPropertySet();
        ps.set(LegacyProperties.FACTORY_ID, PythonResource.TYPE);
        ps.set(new BasicProperty<>("doGet", String.class), "\treturn {'response':'ok'}");
        ps.set(LegacyProperties.REQUIRE_HTTPS, true);
        ps.set(LegacyProperties.REQUIRE_AUTH, true);
        ps.set(LegacyProperties.USER_SOURCE, "users");
        ps.set(LegacyProperties.REQUIRED_ROLES, "Admin");
        ps.set(LegacyProperties.MAX_RETRY_ATTEMPTS, 4);

        var config = LegacyResourceUtils.configFromPropertySet(ps);
        assertEquals(PythonResource.TYPE, config.get("resource-type").getAsString());

        var doGet = config.getAsJsonObject("doGet");
        assertTrue(doGet.get("enabled").getAsBoolean());
        assertTrue(doGet.get("require-https").getAsBoolean());
        assertTrue(doGet.get("require-auth").getAsBoolean());
        assertEquals("users", doGet.get("user-source").getAsString());
        assertEquals("Admin", doGet.get("required-roles").getAsString());
        assertEquals(4, doGet.get("max-retry-attempts").getAsInt());
        assertEquals("\treturn {'response':'ok'}", doGet.get("code").getAsString());
    }

    @Test
    void convertsLegacyFilePropertySetToConfigJsonShape() {
        BasicPropertySet ps = new BasicPropertySet();
        ps.set(LegacyProperties.FACTORY_ID, "file-resource");
        ps.set(LegacyProperties.CONTENT_TYPE, "application/pdf");
        ps.set(LegacyProperties.BYTES, new byte[]{1, 2, 3, 4});

        var config = LegacyResourceUtils.configFromPropertySet(ps);
        assertEquals("file-resource", config.get("resource-type").getAsString());
        assertEquals("application/pdf", config.get("content-type").getAsString());
        assertEquals(Base64.encodeBytes(new byte[]{1, 2, 3, 4}), config.get("bytes").getAsString());
    }

    @Test
    void convertsLegacyTextAndMountedPropertySetsToConfigJsonShape() {
        BasicPropertySet text = new BasicPropertySet();
        text.set(LegacyProperties.FACTORY_ID, "text-resource");
        text.set(LegacyProperties.TEXT, "hello");
        text.set(LegacyProperties.CONTENT_TYPE, "text/plain");

        var textConfig = LegacyResourceUtils.configFromPropertySet(text);
        assertEquals("text-resource", textConfig.get("resource-type").getAsString());
        assertEquals("hello", textConfig.get("text").getAsString());
        assertEquals("text/plain", textConfig.get("content-type").getAsString());

        BasicPropertySet mounted = new BasicPropertySet();
        mounted.set(LegacyProperties.FACTORY_ID, "mounted-folder-resource");
        mounted.set(LegacyProperties.PATH, "/var/data/web");

        var mountedConfig = LegacyResourceUtils.configFromPropertySet(mounted);
        assertEquals("mounted-folder-resource", mountedConfig.get("resource-type").getAsString());
        assertEquals("/var/data/web", mountedConfig.get("folder-path").getAsString());
    }
}
