package com.jg.ignition.webcompat.gateway.servlets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class PathParserTest {
    @Test
    void parsesRootResourcePath() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getPathInfo()).thenReturn("/webdev/MyProject/");

        PathParser path = PathParser.fromRequest(req);
        assertTrue(path.isValid());
        assertTrue(path.isRootPath());
        assertEquals("MyProject", path.getProject());
    }

    @Test
    void parsesNestedResourcePath() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getPathInfo()).thenReturn("/webdev/MyProject/api/v1/devices");

        PathParser path = PathParser.fromRequest(req);
        assertTrue(path.isValid());
        assertFalse(path.isRootPath());
        assertEquals("MyProject", path.getProject());
        assertEquals("api/v1/devices", path.getResource());
    }

    @Test
    void preservesPunctuationAndUnicodeInResourcePath() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getPathInfo()).thenReturn("/webdev/MyProject/assets/café! (v2).js");

        assertEquals("assets/café! (v2).js", PathParser.fromRequest(req).getResource());
    }

    @Test
    void rejectsInvalidPath() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getPathInfo()).thenReturn("/system/other/path");

        PathParser path = PathParser.fromRequest(req);
        assertFalse(path.isValid());
    }
}
