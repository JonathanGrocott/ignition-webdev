package com.jg.ignition.webcompat.gateway.handlers;

import com.jg.ignition.webcompat.gateway.servlets.PathParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TextResourceHandler extends BaseHandler {
    private final String text;
    private final String contentType;
    private final byte[] utf8Bytes;

    public TextResourceHandler(String text, String contentType) {
        this.text = text == null ? "" : text;
        this.contentType = contentType == null ? "text/plain" : contentType;
        this.utf8Bytes = this.text.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void doHead(PathParser path, HttpServletRequest req, HttpServletResponse resp) {
        resp.setContentType(contentType);
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setContentLength(utf8Bytes.length);
    }

    @Override
    public void doGet(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType(contentType);
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setContentLength(utf8Bytes.length);
        resp.getOutputStream().write(utf8Bytes);
    }
}
