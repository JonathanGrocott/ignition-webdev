package com.jg.ignition.webcompat.designer.search;

import com.google.common.collect.ImmutableSet;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.ImmutableBytes;
import com.inductiveautomation.ignition.common.JsonUtilities;
import com.inductiveautomation.ignition.common.config.PropertySet;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.gui.progress.TaskProgressListener;
import com.inductiveautomation.ignition.common.resourcecollection.Resource;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceBuilder;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceId;
import com.inductiveautomation.ignition.common.util.ResourceUtil;
import com.inductiveautomation.ignition.common.xmlserialization.deserialization.XMLDeserializer;
import com.inductiveautomation.ignition.designer.findreplace.AbstractSearchProvider;
import com.inductiveautomation.ignition.designer.findreplace.DefaultSearchObject;
import com.inductiveautomation.ignition.designer.findreplace.SearchObject;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.inductiveautomation.ignition.designer.project.DesignableProject;
import com.jg.ignition.webcompat.common.LegacyResourceUtils;
import com.jg.ignition.webcompat.common.MethodObject;
import com.jg.ignition.webcompat.common.PythonResource;
import com.jg.ignition.webcompat.common.WebCompatModule;
import com.jg.ignition.webcompat.designer.workspace.WebResourceWorkspace;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class WebDevSearchProvider extends AbstractSearchProvider {
    private final DesignerContext context;
    private final WebResourceWorkspace workspace;

    public WebDevSearchProvider(DesignerContext context, WebResourceWorkspace workspace) {
        this.context = context;
        this.workspace = workspace;
    }

    @Override
    public String getName() {
        return BundleUtil.i18n("webcompat.search.name");
    }

    @Override
    public String getId() {
        return WebCompatModule.MODULE_ID;
    }

    @Override
    public List<Object> getCategories() {
        return Collections.emptyList();
    }

    @Override
    public boolean hasSelectableObjects() {
        return false;
    }

    @Override
    public void selectObjects(SelectedObjectsHandler selectedObjectsHandler) {
        // no-op
    }

    @Override
    protected String getSelectableObjectBaseKey() {
        return null;
    }

    @Override
    public Iterator<SearchObject> retrieveSearchableObjects(
            Collection<Object> categories,
            List<Object> selectedObjects,
            TaskProgressListener progress) {

        progress.setIndeterminate(true);

        DesignableProject project = context.getProject();
        if (project == null) {
            return Collections.emptyIterator();
        }

        List<SearchObject> searchObjects = new ArrayList<>();
        for (Resource resource : project.getResourcesOfType(WebCompatModule.RESOURCE_TYPE)) {
            Optional<JsonObject> configOpt = deserializeConfig(resource);
            if (configOpt.isEmpty()) {
                continue;
            }

            JsonObject config = configOpt.get();
            if (!PythonResource.TYPE.equals(JsonUtilities.readString(config, "resource-type", ""))) {
                continue;
            }

            for (String methodName : PythonResource.METHODS) {
                String dataKey = PythonResource.getDataKey(methodName);
                String code = resource.getData(dataKey)
                        .map(ImmutableBytes::getBytesAsString)
                        .orElseGet(() -> JsonUtilities.readString(
                                JsonUtilities.readObject(config, methodName).orElseGet(JsonObject::new),
                                "code",
                                ""));
                String userCode = PythonResource.maybeRemoveDef(code);

                searchObjects.add(DefaultSearchObject.newBuilder()
                        .setPropertyName(methodName)
                        .setLocation("WebDev/" + resource.getResourcePath().getFolderPath())
                        .setValue(userCode)
                        .setMutable(project.isLocal(resource.getResourcePath()))
                        .onLocate(() -> workspace.locate(new com.inductiveautomation.ignition.designer.location.Location(
                                new ResourceId(project.getName(), WebCompatModule.RESOURCE_TYPE, resource.getResourcePath().getFolderPath()),
                                Map.of("script", methodName))))
                        .whenAltered(newCode -> updateMethodCode(project, resource, config, methodName, dataKey, newCode))
                        .build());
            }
        }

        return searchObjects.iterator();
    }

    private void updateMethodCode(
            DesignableProject project,
            Resource resource,
            JsonObject oldConfig,
            String methodName,
            String dataKey,
            String newCode) {

        Resource updated = resource.copy((ResourceBuilder builder) -> {
            builder.removeData("data.bin");

            JsonObject newConfig = oldConfig.deepCopy();
            builder.putData(dataKey, MethodObject.getCodeToSave(methodName, newCode));
            JsonUtilities.readObject(newConfig, methodName).ifPresent(methodObject -> methodObject.remove("code"));
            builder.putData("config.json", WebCompatModule.GSON.toJson(newConfig));
        });

        project.createOrModify(updated);
    }

    private Optional<JsonObject> deserializeConfig(Resource resource) {
        try {
            ImmutableSet<String> keys = resource.getDataKeys();
            if (keys.contains("data.bin")) {
                XMLDeserializer deserializer = context.createDeserializer();
                PropertySet ps = ResourceUtil.decodeOrNull(resource, deserializer, PropertySet.class);
                if (ps != null) {
                    return Optional.of(LegacyResourceUtils.configFromPropertySet(ps));
                }
            }

            if (keys.contains("config.json")) {
                ImmutableBytes jsonBytes = resource.getData("config.json").orElse(null);
                if (jsonBytes != null) {
                    return Optional.of(WebCompatModule.GSON.fromJson(jsonBytes.getBytesAsReader(), JsonObject.class));
                }
            }
        } catch (Exception ignored) {
            // Ignore bad resources during find/replace enumeration.
        }

        return Optional.empty();
    }
}
