package com.jg.ignition.webcompat.gateway.servlets;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

public record PathParser(String project, String resource) {
    public static PathParser fromRequest(Pattern basePathPattern, Pattern pathPattern, HttpServletRequest request) {
        String pathInfo = StringUtils.defaultString(request.getPathInfo());

        var baseMatcher = basePathPattern.matcher(pathInfo);
        if (baseMatcher.matches()) {
            return new PathParser(baseMatcher.group(1), null);
        }

        var matcher = pathPattern.matcher(pathInfo);
        if (matcher.matches()) {
            return new PathParser(matcher.group(1), matcher.group(2));
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
        return new PathParser(project != null ? project.toLowerCase() : null, resource != null ? resource.toLowerCase() : null);
    }
}
