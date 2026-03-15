package com.jg.ignition.webcompat.gateway.servlets;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceCollectionChangeEvent;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceCollectionListener;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceFilter;
import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.project.ProjectManager;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

public class WebDevDispatch extends HttpServlet {
    private static final LoggerEx LOG = LogUtil.getLogger("WebCompat.Dispatch");

    private final Pattern basePathPattern;
    private final Pattern pathPattern;
    private final LoadingCache<String, ProjectDispatcher> mainCache;

    private final ResourceCollectionListener projectListener = new ResourceCollectionListener() {
        @Override
        public void collectionAdded(ResourceCollectionChangeEvent event) {
            // no-op
        }

        @Override
        public void collectionDeleted(ResourceCollectionChangeEvent event) {
            mainCache.invalidateAll();
        }

        @Override
        public void collectionUpdated(ResourceCollectionChangeEvent event) {
            ProjectManager manager = getContext().getProjectManager();
            manager.getNames().forEach(projectName -> manager
                    .find(projectName, ResourceFilter.NO_RESOURCES)
                    .ifPresent(project -> {
                        boolean updated = project.getInheritanceStructure().stream()
                                .anyMatch(namedManifest -> Objects.equals(namedManifest.name(), event.getName()));
                        if (updated) {
                            mainCache.invalidate(projectName);
                        }
                    }));
        }
    };

    public WebDevDispatch() {
        this.mainCache = CacheBuilder.newBuilder().build(new CacheLoader<>() {
            @Override
            public ProjectDispatcher load(String projectName) {
                return new ProjectDispatcher(WebDevDispatch.this, projectName);
            }
        });

        String projectPattern = "[A-Za-z_][A-Za-z0-9_-]*";
        this.basePathPattern = Pattern.compile(String.format("/%s/(%s)/?", "webdev", projectPattern));
        this.pathPattern = Pattern.compile(String.format("/%s/(%s)/([\\w\\-\\.\\/\\+\\%%\\ ]+).*", "webdev", projectPattern));
    }

    GatewayContext getContext() {
        return (GatewayContext) getServletContext().getAttribute(GatewayContext.SERVLET_CONTEXT_KEY);
    }

    @Override
    public void init() {
        getContext().getProjectManager().addListener(projectListener);
    }

    @Override
    public void destroy() {
        GatewayContext context = getContext();
        if (context != null && context.getProjectManager() != null) {
            context.getProjectManager().removeListener(projectListener);
        }
    }

    private PathParser parse(HttpServletRequest request) {
        return PathParser.fromRequest(basePathPattern, pathPattern, request);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            doPatch(req, resp);
            return;
        }
        super.service(req, resp);
    }

    private void dispatch(HttpServletRequest req, HttpServletResponse resp, String methodName) throws IOException, ServletException {
        LOG.tracef("%s(path=%s)", methodName, req.getPathInfo());

        PathParser path = parse(req);
        if (!path.isValid()) {
            resp.sendError(404);
            return;
        }

        try {
            ProjectDispatcher dispatcher = mainCache.get(path.project());
            var handler = dispatcher.get(path);
            switch (methodName) {
                case "GET" -> handler.doGet(path, req, resp);
                case "POST" -> handler.doPost(path, req, resp);
                case "PUT" -> handler.doPut(path, req, resp);
                case "DELETE" -> handler.doDelete(path, req, resp);
                case "HEAD" -> handler.doHead(path, req, resp);
                case "OPTIONS" -> handler.doOptions(path, req, resp);
                case "TRACE" -> handler.doTrace(path, req, resp);
                case "PATCH" -> handler.doPatch(path, req, resp);
                default -> resp.sendError(405);
            }
        } catch (ExecutionException ex) {
            throw new ServletException(ex);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        dispatch(req, resp, "GET");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        dispatch(req, resp, "POST");
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        dispatch(req, resp, "PUT");
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        dispatch(req, resp, "DELETE");
    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        dispatch(req, resp, "HEAD");
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        dispatch(req, resp, "OPTIONS");
    }

    @Override
    protected void doTrace(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        dispatch(req, resp, "TRACE");
    }

    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        dispatch(req, resp, "PATCH");
    }
}
