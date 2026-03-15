package com.jg.ignition.webcompat.gateway.handlers;

import com.jg.ignition.webcompat.gateway.servlets.PathParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class BaseHandler extends HttpServlet {
    public void doGet(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

    public void doHead(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doHead(req, resp);
    }

    public void doPost(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    public void doPut(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPut(req, resp);
    }

    public void doDelete(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDelete(req, resp);
    }

    public void doOptions(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doOptions(req, resp);
    }

    public void doTrace(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doTrace(req, resp);
    }

    public void doPatch(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.sendError(501);
    }
}
