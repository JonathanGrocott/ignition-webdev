package com.jg.ignition.webcompat.designer.workspace;

import com.inductiveautomation.ignition.client.icons.VectorIcons;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceBuilder;
import com.inductiveautomation.ignition.common.resourcecollection.ResourcePath;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.inductiveautomation.ignition.designer.tabbedworkspace.NewResourceAction;
import com.inductiveautomation.ignition.designer.tabbedworkspace.ResourceDescriptor;
import com.inductiveautomation.ignition.designer.tabbedworkspace.ResourceEditor;
import com.inductiveautomation.ignition.designer.tabbedworkspace.ResourceFolderNode;
import com.inductiveautomation.ignition.designer.tabbedworkspace.TabbedResourceWorkspace;
import com.inductiveautomation.ignition.designer.workspacewelcome.RecentlyModifiedTablePanel;
import com.inductiveautomation.ignition.designer.workspacewelcome.ResourceBuilderDelegate;
import com.inductiveautomation.ignition.designer.workspacewelcome.ResourceBuilderPanel;
import com.inductiveautomation.ignition.designer.workspacewelcome.WorkspaceWelcomePanel;
import com.jg.ignition.webcompat.common.MethodObject;
import com.jg.ignition.webcompat.common.PythonResource;
import com.jg.ignition.webcompat.common.WebCompatModule;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;

public class WebResourceWorkspace extends TabbedResourceWorkspace {
    private static final String WORKSPACE_KEY = "webcompat-workspace";

    private static final Icon RESOURCE_ICON = VectorIcons.get("script");

    public static final ResourceDescriptor DESCRIPTOR = ResourceDescriptor.builder()
            .resourceType(WebCompatModule.RESOURCE_TYPE)
            .nounKey("webcompat.noun")
            .scope(1)
            .icon(RESOURCE_ICON)
            .rootIcon(RESOURCE_ICON)
            .rootFolderText(BundleUtil.i18n("webcompat.module.name"))
            .build();

    private static final Consumer<ResourceBuilder> NEW_PYTHON_RESOURCE = builder -> {
        JsonObject prototype = new JsonObject();
        prototype.addProperty("resource-type", PythonResource.TYPE);

        for (String method : PythonResource.METHODS) {
            MethodObject props = new MethodObject();
            if ("doGet".equals(method)) {
                props.enabled = true;
                props.code = "\treturn {'html': '<html><body>Hello World</body></html>'}";
            }
            prototype.add(method, WebCompatModule.GSON.toJsonTree(props));
        }

        addPrototype(builder, prototype);
    };

    public WebResourceWorkspace(DesignerContext context) {
        super(context, DESCRIPTOR);
    }

    @Override
    public String getKey() {
        return WORKSPACE_KEY;
    }

    @Override
    protected ResourceEditor newResourceEditor(ResourcePath id) {
        return new WebResourceEditor(this, id);
    }

    @Override
    public void addNewResourceActions(ResourceFolderNode folder, JPopupMenu menu) {
        menu.add(new NewPythonResourceAction(this, folder));
    }

    @Override
    protected Optional<JComponent> createWorkspaceHomeTab() {
        return Optional.of(new WorkspaceWelcomePanel(BundleUtil.i18n("webcompat.module.name"), null, null) {
            @Override
            protected List<JComponent> createPanels() {
                return List.of(
                        new ResourceBuilderPanel(
                                context,
                                BundleUtil.i18n("webcompat.noun"),
                                WebCompatModule.RESOURCE_TYPE.rootPath(),
                                List.of(ResourceBuilderDelegate.build("webcompat.resources.python", RESOURCE_ICON, NEW_PYTHON_RESOURCE)),
                                WebResourceWorkspace.this::open),
                        new RecentlyModifiedTablePanel(
                                context,
                                WebCompatModule.RESOURCE_TYPE,
                                BundleUtil.i18n("webcompat.nouns"),
                                WebResourceWorkspace.this::open));
            }
        });
    }

    private static void addPrototype(ResourceBuilder builder, JsonObject prototype) {
        builder
                .setApplicationScope(1)
                .putData("config.json", WebCompatModule.GSON.toJson((JsonElement) prototype));
    }

    private static class NewPythonResourceAction extends NewResourceAction {
        NewPythonResourceAction(TabbedResourceWorkspace workspace, ResourceFolderNode folder) {
            super(workspace, folder, NEW_PYTHON_RESOURCE);
            putValue(NAME, BundleUtil.i18n("webcompat.python.new"));
            putValue(SMALL_ICON, RESOURCE_ICON);
        }

        @Override
        protected String newResourceName() {
            return "python";
        }
    }
}
