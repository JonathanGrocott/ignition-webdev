package com.jg.ignition.webcompat.common;

import java.util.StringJoiner;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public final class PythonResource {
    private PythonResource() {
    }

    public static final String TYPE = "python-resource";

    public static final String[] METHODS = {
            "doGet",
            "doPost",
            "doPut",
            "doDelete",
            "doHead",
            "doOptions",
            "doTrace",
            "doPatch"
    };

    public static final String ENABLED = "enabled";
    public static final String REQUIRE_HTTPS = "require-https";
    public static final String REQUIRE_AUTH = "require-auth";
    public static final String USER_SOURCE = "user-source";
    public static final String REQUIRED_ROLES = "required-roles";
    public static final String MAX_RETRY_ATTEMPTS = "max-retry-attempts";
    public static final String CODE = "code";

    public static final Pattern NEWLINE_PATTERN = Pattern.compile("\\R");

    public static String getDataKey(String methodName) {
        return methodName + ".py";
    }

    public static String getMethodName(String dataKey) {
        return StringUtils.stripEnd(dataKey, ".py");
    }

    // Store designer text body without duplicated def, while supporting old raw-file formats.
    public static String maybeRemoveDef(String source) {
        String[] lines = NEWLINE_PATTERN.split(source == null ? "" : source);
        String prefix = "";
        if (lines.length > 0 && !lines[0].startsWith("def")) {
            prefix = "\t";
        }

        StringJoiner joiner = new StringJoiner("\n");
        int start = prefix.length() ^ 1;
        for (int i = start; i < lines.length; i++) {
            joiner.add(prefix + lines[i]);
        }
        return joiner.toString();
    }
}
