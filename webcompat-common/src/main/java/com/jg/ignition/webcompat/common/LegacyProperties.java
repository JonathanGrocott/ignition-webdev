package com.jg.ignition.webcompat.common;

import com.inductiveautomation.ignition.common.config.BasicProperty;
import com.inductiveautomation.ignition.common.config.Property;

public final class LegacyProperties {
    private LegacyProperties() {
    }

    public static final Property<String> FACTORY_ID = new BasicProperty<>("factory-id", String.class);
    public static final Property<byte[]> BYTES = new BasicProperty<>("bytes", byte[].class, new byte[0]);
    public static final Property<String> CONTENT_TYPE = new BasicProperty<>("content-type", String.class, "text/plain");
    public static final Property<String> TEXT = new BasicProperty<>("text", String.class, "");
    public static final Property<String> PATH = new BasicProperty<>("folder-path", String.class, "");

    public static final Property<Boolean> REQUIRE_HTTPS = new BasicProperty<>("requireHttps", Boolean.class, false);
    public static final Property<Boolean> REQUIRE_AUTH = new BasicProperty<>("requireAuth", Boolean.class, false);
    public static final Property<String> USER_SOURCE = new BasicProperty<>("userSource", String.class, "");
    public static final Property<String> REQUIRED_ROLES = new BasicProperty<>("requiredRoles", String.class, "");
    public static final Property<Integer> MAX_RETRY_ATTEMPTS = new BasicProperty<>("maxRetryAttempts", Integer.class, 3);
}
