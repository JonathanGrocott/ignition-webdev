package com.jg.ignition.webcompat.gateway.handlers;

import com.jg.ignition.webcompat.gateway.servlets.PathParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class MountedFolderResourceHandler extends BaseHandler {
    private final String filePath;
    private final String resourcePath;

    public MountedFolderResourceHandler(String filePath, String resourcePath) {
        this.filePath = filePath;
        this.resourcePath = resourcePath;
    }

    @Override
    public void doHead(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        serve(path, req, resp, true);
    }

    @Override
    public void doGet(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        serve(path, req, resp, false);
    }

    private void serve(PathParser path, HttpServletRequest req, HttpServletResponse resp, boolean headOnly) throws IOException {
        ResolvedFile resolved = resolve(path);
        if (resolved == null) {
            resp.sendError(404);
            return;
        }

        if (isNotModified(req, resolved.lastModified())) {
            resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }

        if (resolved.contentType() != null) {
            resp.setContentType(resolved.contentType());
        }
        resp.setDateHeader("Last-Modified", resolved.lastModified());
        resp.addHeader("Accept-Ranges", "bytes");

        List<Range> ranges = parseRanges(req.getHeader("Range"), resolved.length());
        if (ranges == null) {
            resp.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            resp.setHeader("Content-Range", "bytes */" + resolved.length());
            resp.setContentLength(0);
            return;
        }

        if (ranges.isEmpty()) {
            resp.setContentLengthLong(resolved.length());
            if (!headOnly) {
                try (OutputStream out = resp.getOutputStream()) {
                    Files.copy(resolved.path(), out);
                }
            }
            return;
        }

        if (ranges.size() == 1) {
            Range range = ranges.get(0);
            long length = range.end() - range.start() + 1;
            resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            resp.setHeader(
                    "Content-Range",
                    "bytes %d-%d/%d".formatted(range.start(), range.end(), resolved.length()));
            resp.setContentLengthLong(length);
            if (!headOnly) {
                try (OutputStream out = resp.getOutputStream()) {
                    writeRange(resolved.path(), range, out);
                }
            }
            return;
        }

        String boundary = "ignition-webdev-" + System.currentTimeMillis();
        byte[] multipart = encodeMultipartRanges(resolved, ranges, boundary);
        resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        resp.setContentType("multipart/byteranges; boundary=" + boundary);
        resp.setContentLength(multipart.length);
        if (!headOnly) {
            try (OutputStream out = resp.getOutputStream()) {
                out.write(multipart);
            }
        }
    }

    private ResolvedFile resolve(PathParser path) throws IOException {
        String urlPath = StringUtils.replaceOnce(path.getResource(), resourcePath, "");
        if (urlPath.startsWith("/")) {
            urlPath = urlPath.substring(1);
        }
        if (urlPath.isBlank()) {
            urlPath = "index.html";
        }

        Path basePath = Path.of(filePath).toAbsolutePath().normalize();
        if (!Files.isDirectory(basePath)) {
            return null;
        }

        Path resolvedPath = basePath.resolve(urlPath).normalize();
        if (!resolvedPath.startsWith(basePath)) {
            return null;
        }

        if (Files.isDirectory(resolvedPath)) {
            resolvedPath = resolvedPath.resolve("index.html");
        }

        File target = resolvedPath.toFile();
        if (!target.isFile() || !target.canRead()) {
            return null;
        }

        Path baseReal = basePath.toRealPath();
        Path resolvedReal = resolvedPath.toRealPath();
        if (!resolvedReal.startsWith(baseReal)) {
            return null;
        }

        String contentType = Files.probeContentType(resolvedReal);
        if (urlPath.endsWith(".js") && contentType == null) {
            contentType = "text/javascript";
        }

        return new ResolvedFile(
                resolvedReal,
                contentType,
                Files.size(resolvedReal),
                Files.getLastModifiedTime(resolvedReal).toMillis());
    }

    private static boolean isNotModified(HttpServletRequest req, long lastModified) {
        long since;
        try {
            since = req.getDateHeader("If-Modified-Since");
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        if (since < 0) {
            return false;
        }
        long rounded = (lastModified / 1000L) * 1000L;
        return rounded <= since;
    }

    private static void writeRange(Path path, Range range, OutputStream out) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            file.seek(range.start());
            long remaining = range.end() - range.start() + 1;
            byte[] buffer = new byte[8192];
            while (remaining > 0) {
                int read = file.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    break;
                }
                out.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    private static byte[] encodeMultipartRanges(ResolvedFile file, List<Range> ranges, String boundary) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] lineBreak = "\r\n".getBytes(StandardCharsets.UTF_8);
        String contentType = file.contentType() == null ? "application/octet-stream" : file.contentType();

        for (Range range : ranges) {
            out.write(("--" + boundary).getBytes(StandardCharsets.UTF_8));
            out.write(lineBreak);
            out.write(("Content-Type: " + contentType).getBytes(StandardCharsets.UTF_8));
            out.write(lineBreak);
            out.write(("Content-Range: bytes %d-%d/%d".formatted(range.start(), range.end(), file.length()))
                    .getBytes(StandardCharsets.UTF_8));
            out.write(lineBreak);
            out.write(lineBreak);
            writeRange(file.path(), range, out);
            out.write(lineBreak);
        }

        out.write(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
        out.write(lineBreak);
        return out.toByteArray();
    }

    private static List<Range> parseRanges(String header, long totalLength) {
        if (StringUtils.isBlank(header)) {
            return List.of();
        }

        if (!header.startsWith("bytes=")) {
            return null;
        }

        String raw = header.substring("bytes=".length());
        String[] tokens = raw.split(",");
        List<Range> valid = new ArrayList<>();

        for (String token : tokens) {
            String rangeValue = token.trim();
            String[] parts = rangeValue.split("-", 2);
            if (parts.length != 2) {
                continue;
            }

            try {
                long start;
                long end;

                if (parts[0].isBlank()) {
                    long suffixLength = Long.parseLong(parts[1]);
                    if (suffixLength <= 0) {
                        continue;
                    }
                    start = Math.max(totalLength - suffixLength, 0);
                    end = totalLength - 1;
                } else {
                    start = Long.parseLong(parts[0]);
                    end = parts[1].isBlank() ? totalLength - 1 : Long.parseLong(parts[1]);
                }

                if (start < 0 || end < 0 || start > end || start >= totalLength) {
                    continue;
                }

                end = Math.min(end, totalLength - 1);
                valid.add(new Range(start, end));
            } catch (NumberFormatException ignored) {
                // Skip malformed individual range values.
            }
        }

        return valid.isEmpty() ? null : valid;
    }

    private record ResolvedFile(Path path, String contentType, long length, long lastModified) {
    }

    private record Range(long start, long end) {
    }
}
