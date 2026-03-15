package com.jg.ignition.webcompat.designer.workspace;

import com.inductiveautomation.ignition.common.Base64;
import com.google.common.collect.ImmutableSet;
import com.inductiveautomation.ignition.common.ImmutableBytes;
import com.inductiveautomation.ignition.common.JsonUtilities;
import com.inductiveautomation.ignition.common.config.PropertySet;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.resourcecollection.Resource;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceBuilder;
import com.inductiveautomation.ignition.common.resourcecollection.ResourcePath;
import com.inductiveautomation.ignition.common.util.ResourceUtil;
import com.inductiveautomation.ignition.common.xmlserialization.deserialization.XMLDeserializer;
import com.inductiveautomation.ignition.designer.location.Locatable;
import com.inductiveautomation.ignition.designer.location.Location;
import com.inductiveautomation.ignition.designer.tabbedworkspace.ResourceEditor;
import com.inductiveautomation.ignition.designer.tabbedworkspace.TabbedResourceWorkspace;
import com.jg.ignition.webcompat.common.LegacyResourceUtils;
import com.jg.ignition.webcompat.common.MethodObject;
import com.jg.ignition.webcompat.common.PythonResource;
import com.jg.ignition.webcompat.common.WebCompatModule;
import java.awt.Component;
import java.awt.LayoutManager;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;

public class WebResourceEditor extends ResourceEditor<JsonObject> implements Locatable {
    private WebDevResourceEditor editor;

    WebResourceEditor(TabbedResourceWorkspace workspace, ResourcePath id) {
        super(workspace, id);
    }

    @Override
    protected void init(JsonObject config) {
        String resourceType = getResourceType(config);

        removeAll();
        setLayout((LayoutManager) new MigLayout("fill, ins 0"));

        switch (resourceType) {
            case PythonResource.TYPE -> editor = new PythonResourceEditor(config);
            case "file-resource" -> editor = new FileResourceEditor(config);
            case "text-resource" -> editor = new TextResourceEditor(config);
            case "mounted-folder-resource" -> editor = new MountedFolderResourceEditor(config);
            default -> editor = new EmptyEditor(config);
        }

        add((JComponent) editor, "push, grow");
    }

    @Override
    protected JsonObject getObjectForSave() {
        return editor.getConfig();
    }

    @Override
    public void locate(Location location) {
        Locatable.forward(editor, location);
    }

    @Override
    protected JsonObject deserialize(Resource resource) throws Exception {
        PropertySet oldValues = ResourceUtil.decodeOrNull(resource, (XMLDeserializer) context.createDeserializer(), PropertySet.class);
        if (oldValues != null) {
            return LegacyResourceUtils.configFromPropertySet(oldValues);
        }

        ImmutableSet<String> dataKeys = resource.getDataKeys();
        ImmutableBytes jsonBytes = resource.getData("config.json").orElse(null);
        if (jsonBytes == null) {
            return new JsonObject();
        }

        JsonObject config = WebCompatModule.GSON.fromJson(jsonBytes.getBytesAsReader(), JsonObject.class);

        if (PythonResource.TYPE.equals(getResourceType(config))) {
            for (String method : PythonResource.METHODS) {
                MethodObject methodObject = WebCompatModule.GSON.fromJson(
                        JsonUtilities.readObject(config, method).orElseGet(JsonObject::new),
                        MethodObject.class);

                String dataKey = PythonResource.getDataKey(method);
                if (dataKeys.contains(dataKey)) {
                    ImmutableBytes data = resource.getData(dataKey).orElse(ImmutableBytes.EMPTY);
                    methodObject.code = PythonResource.maybeRemoveDef(data.getBytesAsString());
                }

                config.add(method, WebCompatModule.GSON.toJsonTree(methodObject));
            }
        } else if ("file-resource".equals(getResourceType(config))) {
            ImmutableBytes data = resource.getData("file.bin").orElse(null);
            if (data != null) {
                config.addProperty("bytes", Base64.encodeBytes(data.getBytes()));
            }
        }

        return config;
    }

    @Override
    protected void serializeResource(ResourceBuilder builder, JsonObject config) {
        builder.removeData("data.bin");
        editor.beforeSave(builder, config);
        builder.putData("config.json", WebCompatModule.GSON.toJson((JsonElement) config));
    }

    private static String getResourceType(JsonObject config) {
        return JsonUtilities.readString(config, "resource-type", "");
    }

    @FunctionalInterface
    public interface WebDevResourceEditor {
        JsonObject getConfig();

        default void beforeSave(ResourceBuilder builder, JsonObject config) {
        }
    }

    private static class EmptyEditor extends JPanel implements WebDevResourceEditor {
        private final JsonObject config;

        EmptyEditor(JsonObject config) {
            super((LayoutManager) new MigLayout("fill"));
            this.config = config;

            JLabel label = new JLabel();
            label.setText("Unknown resource type: " + String.valueOf(config.get("resource-type")));
            add((Component) label, "push, ax 50%, ay 50%");
        }

        @Override
        public JsonObject getConfig() {
            return config;
        }
    }
}
