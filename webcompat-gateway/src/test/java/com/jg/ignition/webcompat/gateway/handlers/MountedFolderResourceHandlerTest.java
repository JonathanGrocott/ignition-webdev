package com.jg.ignition.webcompat.gateway.handlers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
