package com.jg.ignition.webcompat.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
