package com.jg.ignition.webcompat.gateway.handlers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jg.ignition.webcompat.gateway.servlets.PathParser;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MountedFolderResourceHandlerTest {
    @Test
    void servesMountedFile() throws IOException {
        Path dir = Files.createTempDirectory("webcompat-mounted");
        Path file = dir.resolve("hello.txt");
        Files.writeString(file, "hello");

        MountedFolderResourceHandler handler = new MountedFolderResourceHandler(dir.toString(), "assets");

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        when(resp.getOutputStream()).thenReturn(new MemoryOutputStream(out));

        handler.doGet(new PathParser("proj", "assets/hello.txt"), req, resp);

        assertArrayEquals("hello".getBytes(), out.toByteArray());
        verify(resp).setContentLengthLong(5);
    }

    @Test
    void servesIndexFileAtMountedRoot() throws IOException {
        Path dir = Files.createTempDirectory("webcompat-mounted");
        Files.writeString(dir.resolve("index.html"), "root");

        MountedFolderResourceHandler handler = new MountedFolderResourceHandler(dir.toString(), "assets");

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        when(resp.getOutputStream()).thenReturn(new MemoryOutputStream(out));

        handler.doGet(new PathParser("proj", "assets"), req, resp);

        assertArrayEquals("root".getBytes(), out.toByteArray());
    }

    @Test
    void returns304WhenIfModifiedSinceMatches() throws IOException {
        Path dir = Files.createTempDirectory("webcompat-mounted");
        Path file = dir.resolve("hello.txt");
        Files.writeString(file, "hello");

        MountedFolderResourceHandler handler = new MountedFolderResourceHandler(dir.toString(), "assets");

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getDateHeader("If-Modified-Since")).thenReturn(System.currentTimeMillis() + 60_000L);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        handler.doGet(new PathParser("proj", "assets/hello.txt"), req, resp);

        verify(resp).setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        verify(resp, never()).getOutputStream();
    }

    @Test
    void supportsSingleRangeRequests() throws IOException {
        Path dir = Files.createTempDirectory("webcompat-mounted");
        Path file = dir.resolve("hello.txt");
        Files.writeString(file, "abcdef");

        MountedFolderResourceHandler handler = new MountedFolderResourceHandler(dir.toString(), "assets");

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Range")).thenReturn("bytes=2-4");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        when(resp.getOutputStream()).thenReturn(new MemoryOutputStream(out));

        handler.doGet(new PathParser("proj", "assets/hello.txt"), req, resp);

        verify(resp).setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        verify(resp).setHeader("Content-Range", "bytes 2-4/6");
        verify(resp).setContentLengthLong(3);
        assertArrayEquals("cde".getBytes(), out.toByteArray());
    }

    @Test
    void headReturnsHeadersWithoutWritingBody() throws IOException {
        Path dir = Files.createTempDirectory("webcompat-mounted");
        Path file = dir.resolve("hello.txt");
        Files.writeString(file, "abcdef");

        MountedFolderResourceHandler handler = new MountedFolderResourceHandler(dir.toString(), "assets");

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Range")).thenReturn("bytes=1-2");
        HttpServletResponse resp = mock(HttpServletResponse.class);

        handler.doHead(new PathParser("proj", "assets/hello.txt"), req, resp);

        verify(resp).setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        verify(resp).setHeader("Content-Range", "bytes 1-2/6");
        verify(resp).setContentLengthLong(2);
        verify(resp, never()).getOutputStream();
    }

    @Test
    void blocksPathTraversalOutsideMountedRoot() throws IOException {
        Path dir = Files.createTempDirectory("webcompat-mounted");
        MountedFolderResourceHandler handler = new MountedFolderResourceHandler(dir.toString(), "assets");

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        handler.doGet(new PathParser("proj", "assets/../../etc/passwd"), req, resp);

        verify(resp).sendError(404);
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
            // no-op
        }

        @Override
        public void write(int b) {
            delegate.write(b);
        }
    }
}
