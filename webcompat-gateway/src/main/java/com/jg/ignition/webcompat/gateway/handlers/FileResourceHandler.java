package com.jg.ignition.webcompat.gateway.handlers;

import com.jg.ignition.webcompat.gateway.servlets.PathParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;

public class FileResourceHandler extends BaseHandler {
    private final String mimeType;
    private final byte[] bytes;

    public FileResourceHandler(String mimeType, byte[] data) {
        this.mimeType = mimeType;
        this.bytes = data == null ? new byte[0] : data;
    }

    @Override
    public void doHead(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.addHeader("Accept-Ranges", "bytes");
        super.doHead(path, req, resp);
    }

    @Override
    public void doGet(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType(mimeType);
        resp.addHeader("Accept-Ranges", "bytes");

        Range range = parseRange(req.getHeader("Range"), bytes.length);
        if (range.invalid()) {
            resp.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            resp.setHeader("Content-Range", "bytes */" + bytes.length);
            resp.setContentLength(0);
            return;
        }

        try (ServletOutputStream out = resp.getOutputStream()) {
            if (!range.hasRange()) {
                resp.setContentLength(bytes.length);
                out.write(bytes);
                return;
            }

            int length = range.end() - range.start() + 1;
            resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            resp.setHeader("Content-Range", "bytes %d-%d/%d".formatted(range.start(), range.end(), bytes.length));
            resp.setContentLength(length);
            out.write(bytes, range.start(), length);
        }
    }

    private static Range parseRange(String header, int totalLength) {
        if (StringUtils.isBlank(header)) {
            return Range.none();
        }

        if (!header.startsWith("bytes=")) {
            return Range.invalidRange();
        }

        String rangeValue = header.substring("bytes=".length()).trim();
        if (rangeValue.contains(",")) {
            rangeValue = rangeValue.substring(0, rangeValue.indexOf(','));
        }

        String[] parts = rangeValue.split("-", 2);
        if (parts.length != 2) {
            return Range.invalidRange();
        }

        try {
            int start;
            int end;

            if (parts[0].isBlank()) {
                int suffixLength = Integer.parseInt(parts[1]);
                if (suffixLength <= 0) {
                    return Range.invalidRange();
                }
                start = Math.max(totalLength - suffixLength, 0);
                end = totalLength - 1;
            } else {
                start = Integer.parseInt(parts[0]);
                end = parts[1].isBlank() ? totalLength - 1 : Integer.parseInt(parts[1]);
            }

            if (start < 0 || end < 0 || start > end || start >= totalLength) {
                return Range.invalidRange();
            }

            end = Math.min(end, totalLength - 1);
            return Range.of(start, end);
        } catch (NumberFormatException ex) {
            return Range.invalidRange();
        }
    }

    private record Range(boolean hasRange, boolean invalid, int start, int end) {
        static Range none() {
            return new Range(false, false, 0, 0);
        }

        static Range invalidRange() {
            return new Range(false, true, 0, 0);
        }

        static Range of(int start, int end) {
            return new Range(true, false, start, end);
        }
    }
}
