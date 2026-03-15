package com.jg.ignition.webcompat.gateway.handlers;

import com.jg.ignition.webcompat.gateway.servlets.PathParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ResourceNotFoundHandler extends BaseHandler {
    private final PathParser path;

    public ResourceNotFoundHandler(PathParser path) {
        this.path = path;
    }

    @Override
    public void doGet(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.sendError(404, String.format("Resource \"%s\" not found.", this.path.resource()));
    }
}
