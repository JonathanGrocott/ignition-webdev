package com.jg.ignition.webcompat.gateway.servlets;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.inductiveautomation.ignition.common.ImmutableBytes;
import com.inductiveautomation.ignition.common.JsonUtilities;
import com.inductiveautomation.ignition.common.config.PropertySet;
import com.inductiveautomation.ignition.common.resourcecollection.Resource;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceCollectionNotFoundException;
import com.inductiveautomation.ignition.common.resourcecollection.ResourcePath;
import com.inductiveautomation.ignition.common.resourcecollection.RuntimeResourceCollection;
import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.common.util.ResourceUtil;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.jg.ignition.webcompat.common.MethodObject;
import com.jg.ignition.webcompat.common.PythonResource;
import com.jg.ignition.webcompat.common.WebCompatModule;
import com.jg.ignition.webcompat.gateway.handlers.BaseHandler;
import com.jg.ignition.webcompat.gateway.handlers.ErrorMessageHandler;
import com.jg.ignition.webcompat.gateway.handlers.ProjectNotFoundHandler;
import com.jg.ignition.webcompat.gateway.handlers.PythonResourceHandler;
import com.jg.ignition.webcompat.gateway.handlers.ResourceNotFoundHandler;

import jakarta.servlet.ServletException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

class ProjectDispatcher {
    private static final LoggerEx LOG = LogUtil.getLogger("WebCompat.Dispatch");

    private final WebDevDispatch webDevDispatch;
    private final String projectName;
    private final LoadingCache<PathParser, BaseHandler> cache;

    ProjectDispatcher(WebDevDispatch webDevDispatch, String projectName) {
        this.webDevDispatch = webDevDispatch;
        this.projectName = projectName;
        this.cache = CacheBuilder.newBuilder().build(new CacheLoader<>() {
            @Override
            public BaseHandler load(PathParser path) {
                return loadHandler(path);
            }
        });
    }

    private BaseHandler loadHandler(PathParser path) {
        RuntimeResourceCollection project;
        try {
            project = webDevDispatch.getContext()
                    .getProjectManager()
                    .find(projectName, 1)
                    .orElseThrow(() -> new ResourceCollectionNotFoundException(projectName))
                    .validateOrThrow();
        } catch (Exception ex) {
            return new ProjectNotFoundHandler(projectName);
        }

        String requestedResource = path.isRootPath() ? "index.html" : path.resource();
        String resolvedResource = requestedResource;

        Resource resource = findResource(project, resolvedResource).orElse(null);
        while (resource == null && resolvedResource.indexOf('/') != -1) {
            resolvedResource = resolvedResource.substring(0, resolvedResource.lastIndexOf('/'));
            resource = findResource(project, resolvedResource).orElse(null);
        }

        if (resource == null) {
            return new ResourceNotFoundHandler(path);
        }

        return route(path, requestedResource, resolvedResource, resource);
    }

    private Optional<Resource> findResource(RuntimeResourceCollection project, String resourceName) {
        return project.getResource(new ResourcePath(WebCompatModule.RESOURCE_TYPE, resourceName));
    }

    private BaseHandler route(PathParser path, String requestedResource, String resolvedResource, Resource resource) {
        var configOpt = decodeConfig(resource);
        if (configOpt.isEmpty() || configOpt.get().isEmpty()) {
            return new ErrorMessageHandler(String.format("No data found for resource '%s'", requestedResource));
        }

        var config = configOpt.get();
        String resourceType = JsonUtilities.readString(config, "resource-type", "");

        if (PythonResource.TYPE.equals(resourceType)) {
            for (String dataKey : resource.getDataKeys()) {
                if ("config.json".equals(dataKey)) {
                    continue;
                }

                String methodName = PythonResource.getMethodName(dataKey);
                ImmutableBytes bytes = resource.getData(dataKey).orElse(ImmutableBytes.EMPTY);
                JsonUtilities.readObject(config, methodName).ifPresent(element -> {
                    MethodObject methodObject = WebCompatModule.GSON.fromJson(element, MethodObject.class);
                    methodObject.code = PythonResource.maybeRemoveDef(bytes.getBytesAsString());
                    config.add(methodName, WebCompatModule.GSON.toJsonTree(methodObject));
                });
            }

            String remainingPath = requestedResource.substring(resolvedResource.length());
            return new PythonResourceHandler(webDevDispatch.getContext(), projectName, path, config, remainingPath);
        }

        return new ErrorMessageHandler("Unsupported resource type in V1: " + resourceType);
    }

    private Optional<com.inductiveautomation.ignition.common.gson.JsonObject> decodeConfig(Resource resource) {
        try {
            GatewayContext context = webDevDispatch.getContext();
            if (resource.getDataKeys().contains("data.bin")) {
                PropertySet legacySet = ResourceUtil.decodeOrNull(resource, context.createDeserializer(), PropertySet.class);
                if (legacySet != null) {
                    return Optional.of(com.jg.ignition.webcompat.common.LegacyResourceUtils.configFromPropertySet(legacySet));
                }
            }

            var bytes = resource.getData("config.json").orElse(ImmutableBytes.EMPTY);
            var config = WebCompatModule.GSON.fromJson(bytes.getBytesAsReader(), com.inductiveautomation.ignition.common.gson.JsonObject.class);
            return Optional.ofNullable(config);
        } catch (Exception ex) {
            LOG.error("Deserializing resource failed.", ex);
            return Optional.empty();
        }
    }

    BaseHandler get(PathParser path) throws ServletException {
        try {
            return cache.get(path);
        } catch (ExecutionException ex) {
            throw new ServletException(ex.getCause());
        }
    }
}
