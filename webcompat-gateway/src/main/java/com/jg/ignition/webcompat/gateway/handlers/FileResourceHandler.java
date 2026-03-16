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
        writeResponse(req, resp, true);
    }

    @Override
    public void doGet(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        writeResponse(req, resp, false);
    }

    private void writeResponse(HttpServletRequest req, HttpServletResponse resp, boolean headOnly) throws IOException {
        PreparedResponse prepared = prepareResponse(req.getHeader("Range"));

        resp.addHeader("Accept-Ranges", "bytes");
        if (prepared.contentType() != null) {
            resp.setContentType(prepared.contentType());
        }
        if (prepared.status() != HttpServletResponse.SC_OK) {
            resp.setStatus(prepared.status());
        }
        if (prepared.contentRange() != null) {
            resp.setHeader("Content-Range", prepared.contentRange());
        }
        resp.setContentLength(prepared.contentLength());

        if (headOnly || prepared.status() == HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE) {
            return;
        }

        try (ServletOutputStream out = resp.getOutputStream()) {
            if (prepared.multipartBody() != null) {
                out.write(prepared.multipartBody());
                return;
            }

            if (prepared.range() != null) {
                int length = prepared.range().end() - prepared.range().start() + 1;
                out.write(bytes, prepared.range().start(), length);
                return;
            }

            out.write(bytes);
        }
    }

    private PreparedResponse prepareResponse(String rangeHeader) throws IOException {
        List<Range> ranges = parseRanges(rangeHeader, bytes.length);
        if (ranges == null) {
            return new PreparedResponse(
                    HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE,
                    null,
                    "bytes */" + bytes.length,
                    0,
                    null,
                    null);
        }

        if (ranges.isEmpty()) {
            return new PreparedResponse(HttpServletResponse.SC_OK, mimeType, null, bytes.length, null, null);
        }

        if (ranges.size() == 1) {
            Range range = ranges.get(0);
            int length = range.end() - range.start() + 1;
            return new PreparedResponse(
                    HttpServletResponse.SC_PARTIAL_CONTENT,
                    mimeType,
                    "bytes %d-%d/%d".formatted(range.start(), range.end(), bytes.length),
                    length,
                    range,
                    null);
        }

        String boundary = "ignition-webdev-" + System.currentTimeMillis();
        byte[] multipart = encodeMultipartRanges(ranges, boundary);
        return new PreparedResponse(
                HttpServletResponse.SC_PARTIAL_CONTENT,
                "multipart/byteranges; boundary=" + boundary,
                null,
                multipart.length,
                null,
                multipart);
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

    private record PreparedResponse(
            int status,
            String contentType,
            String contentRange,
            int contentLength,
            Range range,
            byte[] multipartBody) {
    }
}
