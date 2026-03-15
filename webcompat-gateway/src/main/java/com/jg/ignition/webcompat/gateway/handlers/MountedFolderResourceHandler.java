package com.jg.ignition.webcompat.gateway.handlers;

import com.jg.ignition.webcompat.gateway.servlets.PathParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.lang3.StringUtils;

public class MountedFolderResourceHandler extends BaseHandler {
    private final String filePath;
    private final String resourcePath;

    public MountedFolderResourceHandler(String filePath, String resourcePath) {
        this.filePath = filePath;
        this.resourcePath = resourcePath;
    }

    @Override
    public void doGet(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String urlPath = StringUtils.replaceOnce(path.getResource(), resourcePath, "");
        if (urlPath.startsWith("/")) {
            urlPath = urlPath.substring(1);
        }

        File target = Path.of(filePath, urlPath).toFile();
        if (!target.isFile() || !target.canRead()) {
            resp.sendError(404);
            return;
        }

        String contentType = Files.probeContentType(target.toPath());
        if (urlPath.endsWith(".js") && contentType == null) {
            contentType = "text/javascript";
        }

        if (contentType != null) {
            resp.setContentType(contentType);
        }
        resp.setContentLengthLong(target.length());
        try (OutputStream out = resp.getOutputStream()) {
            Files.copy(target.toPath(), out);
        }
    }
}
