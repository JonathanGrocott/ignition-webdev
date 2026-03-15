package com.jg.ignition.webcompat.gateway.handlers;

import com.jg.ignition.webcompat.gateway.servlets.PathParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ProjectNotFoundHandler extends BaseHandler {
    private final String projectName;

    public ProjectNotFoundHandler(String projectName) {
        this.projectName = projectName;
    }

    @Override
    public void doGet(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.sendError(404, String.format("Project \"%s\" not found.", projectName));
    }
}
