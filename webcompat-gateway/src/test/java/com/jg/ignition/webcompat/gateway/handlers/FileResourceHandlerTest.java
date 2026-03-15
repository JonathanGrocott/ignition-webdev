package com.jg.ignition.webcompat.gateway.handlers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jg.ignition.webcompat.gateway.servlets.PathParser;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class FileResourceHandlerTest {
    @Test
    void writesFullPayloadWhenNoRangeRequested() throws IOException {
        byte[] payload = "abcdef".getBytes();
        FileResourceHandler handler = new FileResourceHandler("text/plain", payload);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(req.getHeader("Range")).thenReturn(null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        when(resp.getOutputStream()).thenReturn(new MemoryOutputStream(out));

        handler.doGet(new PathParser("proj", "file"), req, resp);

        verify(resp).setContentLength(payload.length);
        assertArrayEquals(payload, out.toByteArray());
    }

    @Test
    void writesPartialPayloadForValidSingleRange() throws IOException {
        byte[] payload = "abcdef".getBytes();
        FileResourceHandler handler = new FileResourceHandler("text/plain", payload);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(req.getHeader("Range")).thenReturn("bytes=2-4");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        when(resp.getOutputStream()).thenReturn(new MemoryOutputStream(out));

        handler.doGet(new PathParser("proj", "file"), req, resp);

        verify(resp).setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        verify(resp).setHeader("Content-Range", "bytes 2-4/6");
        verify(resp).setContentLength(3);
        assertArrayEquals("cde".getBytes(), out.toByteArray());
    }

    @Test
    void returns416ForInvalidRange() throws IOException {
        byte[] payload = "abcdef".getBytes();
        FileResourceHandler handler = new FileResourceHandler("text/plain", payload);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(req.getHeader("Range")).thenReturn("bytes=100-200");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        when(resp.getOutputStream()).thenReturn(new MemoryOutputStream(out));

        handler.doGet(new PathParser("proj", "file"), req, resp);

        verify(resp).setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
        verify(resp).setHeader("Content-Range", "bytes */6");
        verify(resp).setContentLength(0);
        assertArrayEquals(new byte[0], out.toByteArray());
    }

    @Test
    void supportsOpenEndedAndSuffixRanges() throws IOException {
        byte[] payload = "abcdef".getBytes();
        FileResourceHandler handler = new FileResourceHandler("text/plain", payload);

        HttpServletRequest openReq = mock(HttpServletRequest.class);
        HttpServletResponse openResp = mock(HttpServletResponse.class);
        when(openReq.getHeader("Range")).thenReturn("bytes=3-");
        ByteArrayOutputStream openOut = new ByteArrayOutputStream();
        when(openResp.getOutputStream()).thenReturn(new MemoryOutputStream(openOut));
        handler.doGet(new PathParser("proj", "file"), openReq, openResp);
        assertArrayEquals("def".getBytes(), openOut.toByteArray());

        HttpServletRequest suffixReq = mock(HttpServletRequest.class);
        HttpServletResponse suffixResp = mock(HttpServletResponse.class);
        when(suffixReq.getHeader("Range")).thenReturn("bytes=-2");
        ByteArrayOutputStream suffixOut = new ByteArrayOutputStream();
        when(suffixResp.getOutputStream()).thenReturn(new MemoryOutputStream(suffixOut));
        handler.doGet(new PathParser("proj", "file"), suffixReq, suffixResp);
        assertArrayEquals("ef".getBytes(), suffixOut.toByteArray());
    }

    @Test
    void supportsMultipartRanges() throws IOException {
        byte[] payload = "abcdef".getBytes();
        FileResourceHandler handler = new FileResourceHandler("text/plain", payload);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(req.getHeader("Range")).thenReturn("bytes=0-1,4-5");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        when(resp.getOutputStream()).thenReturn(new MemoryOutputStream(out));

        handler.doGet(new PathParser("proj", "file"), req, resp);

        verify(resp).setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        verify(resp).setContentType(argThat(ct -> ct != null && ct.startsWith("multipart/byteranges; boundary=")));

        String multipart = out.toString();
        assertTrue(multipart.contains("Content-Range: bytes 0-1/6"));
        assertTrue(multipart.contains("Content-Range: bytes 4-5/6"));
    }

    private static class MemoryOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream delegate;

        MemoryOutputStream(ByteArrayOutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            // no-op for tests
        }

        @Override
        public void write(int b) {
            delegate.write(b);
        }
    }
}
