package com.jg.ignition.webcompat.gateway.servlets;

import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.jg.ignition.webcompat.common.WebCompatModule;
import com.jg.ignition.webcompat.gateway.GatewayHook;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class EventStreamDispatch extends HttpServlet {
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        PathParser path = PathParser.fromRequest(request, WebCompatModule.EVENT_STREAM_SERVLET_KEY);
        GatewayContext context = (GatewayContext) getServletContext().getAttribute(GatewayContext.SERVLET_CONTEXT_KEY);
        GatewayHook hook = (GatewayHook) context.getModule(WebCompatModule.MODULE_ID);
        if (path.isValid() && !path.isRootPath() && hook != null
                && hook.eventStreamRoutes().dispatch(path, request, response)) {
            return;
        }
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @FunctionalInterface
    public interface Handler {
        void receive(HttpServletRequest request, HttpServletResponse response) throws IOException;
    }

    public static final class Routes {
        private final ConcurrentMap<PathParser, Handler> handlers = new ConcurrentHashMap<>();

        public void register(PathParser path, Handler handler) {
            handlers.put(path.lowercase(), handler);
        }

        public void unregister(PathParser path, Handler handler) {
            handlers.remove(path.lowercase(), handler);
        }

        public void clear() {
            handlers.clear();
        }

        boolean dispatch(PathParser path, HttpServletRequest request, HttpServletResponse response) throws IOException {
            Handler handler = handlers.get(path.lowercase());
            if (handler == null) {
                return false;
            }
            handler.receive(request, response);
            return true;
        }
    }
}
