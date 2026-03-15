package com.jg.ignition.webcompat.gateway.handlers;

import com.jg.ignition.webcompat.gateway.servlets.PathParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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

        List<Range> ranges = parseRanges(req.getHeader("Range"), bytes.length);
        if (ranges == null) {
            resp.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            resp.setHeader("Content-Range", "bytes */" + bytes.length);
            resp.setContentLength(0);
            return;
        }

        try (ServletOutputStream out = resp.getOutputStream()) {
            if (ranges.isEmpty()) {
                resp.setContentLength(bytes.length);
                out.write(bytes);
                return;
            }

            if (ranges.size() == 1) {
                Range range = ranges.get(0);
                int length = range.end() - range.start() + 1;
                resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                resp.setHeader("Content-Range", "bytes %d-%d/%d".formatted(range.start(), range.end(), bytes.length));
                resp.setContentLength(length);
                out.write(bytes, range.start(), length);
                return;
            }

            String boundary = "ignition-webdev-" + System.currentTimeMillis();
            resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            resp.setContentType("multipart/byteranges; boundary=" + boundary);

            byte[] multipart = encodeMultipartRanges(ranges, boundary);
            resp.setContentLength(multipart.length);
            out.write(multipart);
        }
    }

    private byte[] encodeMultipartRanges(List<Range> ranges, String boundary) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] lineBreak = "\r\n".getBytes(StandardCharsets.UTF_8);

        for (Range range : ranges) {
            out.write(("--" + boundary).getBytes(StandardCharsets.UTF_8));
            out.write(lineBreak);
            out.write(("Content-Type: " + mimeType).getBytes(StandardCharsets.UTF_8));
            out.write(lineBreak);
            out.write(("Content-Range: bytes %d-%d/%d".formatted(range.start(), range.end(), bytes.length))
                    .getBytes(StandardCharsets.UTF_8));
            out.write(lineBreak);
            out.write(lineBreak);
            out.write(bytes, range.start(), range.end() - range.start() + 1);
            out.write(lineBreak);
        }

        out.write(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
        out.write(lineBreak);
        return out.toByteArray();
    }

    private static List<Range> parseRanges(String header, int totalLength) {
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
                int start;
                int end;

                if (parts[0].isBlank()) {
                    int suffixLength = Integer.parseInt(parts[1]);
                    if (suffixLength <= 0) {
                        continue;
                    }
                    start = Math.max(totalLength - suffixLength, 0);
                    end = totalLength - 1;
                } else {
                    start = Integer.parseInt(parts[0]);
                    end = parts[1].isBlank() ? totalLength - 1 : Integer.parseInt(parts[1]);
                }

                if (start < 0 || end < 0 || start > end || start >= totalLength) {
                    continue;
                }

                end = Math.min(end, totalLength - 1);
                valid.add(Range.of(start, end));
            } catch (NumberFormatException ignored) {
                // Skip malformed individual range values.
            }
        }

        return valid.isEmpty() ? null : valid;
    }

    private record Range(int start, int end) {
        static Range of(int start, int end) {
            return new Range(start, end);
        }
    }
}
