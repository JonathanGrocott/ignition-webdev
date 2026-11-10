package com.jg.ignition.webcompat.gateway.servlets;

import com.jg.ignition.webcompat.common.WebCompatModule;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;
import java.util.Locale;

public record PathParser(String project, String resource) {
    private static final Pattern PATH = Pattern.compile("/([^/]+)/([A-Za-z_][A-Za-z0-9_-]*)(?:/([^\\p{Cntrl}]*))?");

    static PathParser fromRequest(HttpServletRequest request) {
        return fromRequest(request, WebCompatModule.SERVLET_KEY);
    }

    static PathParser fromRequest(HttpServletRequest request, String servletKey) {
        var matcher = PATH.matcher(StringUtils.defaultString(request.getPathInfo()));
        if (matcher.matches() && servletKey.equals(matcher.group(1))) {
            return new PathParser(matcher.group(2), StringUtils.defaultIfEmpty(matcher.group(3), null));
        }

        return new PathParser(null, null);
    }

    public boolean isValid() {
        return StringUtils.isNotEmpty(project);
    }

    public boolean isRootPath() {
        return StringUtils.isEmpty(resource);
    }

    public String getProject() {
        return project;
    }

    public String getResource() {
        return resource;
    }

    public PathParser lowercase() {
        return new PathParser(project != null ? project.toLowerCase(Locale.ROOT) : null,
                resource != null ? resource.toLowerCase(Locale.ROOT) : null);
    }
}
