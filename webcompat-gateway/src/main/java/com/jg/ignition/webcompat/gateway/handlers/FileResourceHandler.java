package com.jg.ignition.webcompat.gateway.handlers;

import com.jg.ignition.webcompat.gateway.servlets.PathParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.eclipse.jetty.http.ByteRange;

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
        PreparedResponse prepared = prepareResponse(req.getHeaders("Range"), req.getHeader("Request-Range") != null);

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
                out.write(bytes, (int) prepared.range().first(), (int) prepared.range().getLength());
                return;
            }

            out.write(bytes);
        }
    }

    private PreparedResponse prepareResponse(Enumeration<String> rangeHeaders, boolean legacyMultipart) throws IOException {
        if (rangeHeaders == null || !rangeHeaders.hasMoreElements()) {
            return new PreparedResponse(HttpServletResponse.SC_OK, mimeType, null, bytes.length, null, null);
        }

        List<ByteRange> ranges = ByteRange.parse(Collections.list(rangeHeaders), bytes.length);
        if (ranges.isEmpty()) {
            return new PreparedResponse(
                    HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE,
                    null,
                    ByteRange.toNonSatisfiableHeaderValue(bytes.length),
                    0,
                    null,
                    null);
        }

        if (ranges.size() == 1) {
            ByteRange range = ranges.get(0);
            return new PreparedResponse(
                    HttpServletResponse.SC_PARTIAL_CONTENT,
                    mimeType,
                    range.toHeaderValue(bytes.length),
                    (int) range.getLength(),
                    range,
                    null);
        }

        String boundary = "ignition-webdev-" + System.currentTimeMillis();
        byte[] multipart = encodeMultipartRanges(ranges, boundary);
        String multipartType = legacyMultipart ? "multipart/x-byteranges" : "multipart/byteranges";
        return new PreparedResponse(
                HttpServletResponse.SC_PARTIAL_CONTENT,
                multipartType + "; boundary=" + boundary,
                null,
                multipart.length,
                null,
                multipart);
    }

    private byte[] encodeMultipartRanges(List<ByteRange> ranges, String boundary) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] lineBreak = "\r\n".getBytes(StandardCharsets.UTF_8);

        for (ByteRange range : ranges) {
            out.write(("--" + boundary).getBytes(StandardCharsets.UTF_8));
            out.write(lineBreak);
            out.write(("Content-Type: " + mimeType).getBytes(StandardCharsets.UTF_8));
            out.write(lineBreak);
            out.write(("Content-Range: " + range.toHeaderValue(bytes.length)).getBytes(StandardCharsets.UTF_8));
            out.write(lineBreak);
            out.write(lineBreak);
            out.write(bytes, (int) range.first(), (int) range.getLength());
            out.write(lineBreak);
        }

        out.write(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
        out.write(lineBreak);
        return out.toByteArray();
    }

    private record PreparedResponse(
            int status,
            String contentType,
            String contentRange,
            int contentLength,
            ByteRange range,
            byte[] multipartBody) {
    }
}
