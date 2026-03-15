package com.jg.ignition.webcompat.gateway.servlets;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.inductiveautomation.ignition.common.Base64;
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
import com.jg.ignition.webcompat.gateway.handlers.FileResourceHandler;
import com.jg.ignition.webcompat.gateway.handlers.MountedFolderResourceHandler;
import com.jg.ignition.webcompat.gateway.handlers.ProjectNotFoundHandler;
import com.jg.ignition.webcompat.gateway.handlers.PythonResourceHandler;
import com.jg.ignition.webcompat.gateway.handlers.ResourceNotFoundHandler;
import com.jg.ignition.webcompat.gateway.handlers.TextResourceHandler;

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
        var decodedOpt = decodeConfig(resource);
        if (decodedOpt.isEmpty() || decodedOpt.get().config().isEmpty()) {
            return new ErrorMessageHandler(String.format("No data found for resource '%s'", requestedResource));
        }

        var decoded = decodedOpt.get();
        var config = decoded.config();
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

        if ("file-resource".equals(resourceType)) {
            String mimeType = JsonUtilities.readString(config, "content-type", "application/octet-stream");
            byte[] fileData = decoded.legacySet() != null
                    ? decoded.legacySet().getNonNull(com.jg.ignition.webcompat.common.LegacyProperties.BYTES, new byte[0])
                    : resource.getData("file.bin").map(ImmutableBytes::getBytes).orElseGet(
                            () -> decodeBase64Bytes(JsonUtilities.readString(config, "bytes", "")));
            return new FileResourceHandler(mimeType, fileData);
        }

        if ("text-resource".equals(resourceType)) {
            String text = JsonUtilities.readString(config, "text", "");
            String contentType = JsonUtilities.readString(config, "content-type", "text/plain");
            return new TextResourceHandler(text, contentType);
        }

        if ("mounted-folder-resource".equals(resourceType)) {
            String folderPath = JsonUtilities.readString(config, "folder-path", "");
            return new MountedFolderResourceHandler(folderPath, resolvedResource);
        }

        return new ErrorMessageHandler("Unknown resource factory: " + resourceType);
    }

    private byte[] decodeBase64Bytes(String base64) {
        try {
            if (base64 == null || base64.isBlank()) {
                return new byte[0];
            }
            return Base64.decode(base64);
        } catch (Exception ignored) {
            return new byte[0];
        }
    }

    private Optional<DecodedConfig> decodeConfig(Resource resource) {
        try {
            GatewayContext context = webDevDispatch.getContext();
            if (resource.getDataKeys().contains("data.bin")) {
                PropertySet legacySet = ResourceUtil.decodeOrNull(resource, context.createDeserializer(), PropertySet.class);
                if (legacySet != null) {
                    return Optional.of(new DecodedConfig(
                            com.jg.ignition.webcompat.common.LegacyResourceUtils.configFromPropertySet(legacySet),
                            legacySet));
                }
            }

            var bytes = resource.getData("config.json").orElse(ImmutableBytes.EMPTY);
            var config = WebCompatModule.GSON.fromJson(bytes.getBytesAsReader(), com.inductiveautomation.ignition.common.gson.JsonObject.class);
            return Optional.ofNullable(config).map(cfg -> new DecodedConfig(cfg, null));
        } catch (Exception ex) {
            LOG.error("Deserializing resource failed.", ex);
            return Optional.empty();
        }
    }

    private record DecodedConfig(com.inductiveautomation.ignition.common.gson.JsonObject config, PropertySet legacySet) {
    }

    BaseHandler get(PathParser path) throws ServletException {
        try {
            return cache.get(path);
        } catch (ExecutionException ex) {
            throw new ServletException(ex.getCause());
        }
    }
}
