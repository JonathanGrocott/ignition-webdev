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
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TextResourceHandlerTest {
    @Test
    void writesUtf8BodyWithContentLength() throws IOException {
        TextResourceHandler handler = new TextResourceHandler("hello", "text/plain");

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        when(resp.getOutputStream()).thenReturn(new MemoryOutputStream(out));

        handler.doGet(new PathParser("proj", "text"), req, resp);

        verify(resp).setContentType("text/plain");
        verify(resp).setCharacterEncoding("UTF-8");
        verify(resp).setContentLength(5);
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), out.toByteArray());
    }

    @Test
    void headSetsHeadersWithoutWritingBody() throws IOException {
        TextResourceHandler handler = new TextResourceHandler("hello", "text/plain");

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        handler.doHead(new PathParser("proj", "text"), req, resp);

        verify(resp).setContentType("text/plain");
        verify(resp).setCharacterEncoding("UTF-8");
        verify(resp).setContentLength(5);
        verify(resp, never()).getOutputStream();
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
