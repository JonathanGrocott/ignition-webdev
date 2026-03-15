package com.jg.ignition.webcompat.gateway.handlers;

import com.jg.ignition.webcompat.gateway.servlets.PathParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ErrorMessageHandler extends BaseHandler {
    private final String errorMessage;

    public ErrorMessageHandler(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public void doGet(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.sendError(500, errorMessage);
    }
}
