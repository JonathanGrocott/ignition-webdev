package com.jg.ignition.webcompat.designer.workspace;

import com.inductiveautomation.ignition.client.icons.VectorIcons;
import com.inductiveautomation.ignition.client.gateway_interface.GatewayConnectionManager;
import com.inductiveautomation.ignition.client.util.BrowserLauncher;
import com.inductiveautomation.ignition.client.util.ClipboardUtil;
import com.inductiveautomation.ignition.client.util.action.BaseAction;
import com.inductiveautomation.ignition.client.util.gui.ErrorUtil;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.ImmutableBytes;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.resourcecollection.Resource;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceBuilder;
import com.inductiveautomation.ignition.common.resourcecollection.ResourcePath;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.inductiveautomation.ignition.designer.model.menu.JMenuMerge;
import com.inductiveautomation.ignition.designer.model.menu.MenuBarMerge;
import com.inductiveautomation.ignition.designer.navtree.model.AbstractNavTreeNode;
import com.inductiveautomation.ignition.designer.tabbedworkspace.NewResourceAction;
import com.inductiveautomation.ignition.designer.tabbedworkspace.ResourceDescriptor;
import com.inductiveautomation.ignition.designer.tabbedworkspace.ResourceEditor;
import com.inductiveautomation.ignition.designer.tabbedworkspace.ResourceFolderNode;
import com.inductiveautomation.ignition.designer.tabbedworkspace.ResourceNode;
import com.inductiveautomation.ignition.designer.tabbedworkspace.TabbedResourceWorkspace;
import com.inductiveautomation.ignition.designer.workspacewelcome.DefaultResourceBuilderDelegate;
import com.inductiveautomation.ignition.designer.workspacewelcome.RecentlyModifiedTablePanel;
import com.inductiveautomation.ignition.designer.workspacewelcome.ResourceBuilderDelegate;
import com.inductiveautomation.ignition.designer.workspacewelcome.ResourceBuilderPanel;
import com.inductiveautomation.ignition.designer.workspacewelcome.WorkspaceWelcomePanel;
import com.jg.ignition.webcompat.common.MethodObject;
import com.jg.ignition.webcompat.common.PythonResource;
import com.jg.ignition.webcompat.common.WebCompatModule;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPopupMenu;
import javax.swing.tree.TreePath;
import org.apache.commons.lang3.StringUtils;

public class WebResourceWorkspace extends TabbedResourceWorkspace {
    private static final String WORKSPACE_KEY = "webcompat-workspace";

    private static final Icon PYTHON_ICON = VectorIcons.get("script");
    private static final Icon FILE_ICON = VectorIcons.get("file");
    private static final Icon TEXT_ICON = VectorIcons.get("file-text");
    private static final Icon FOLDER_ICON = VectorIcons.get("tree-folder");
    private static final Icon RESOURCE_ICON = PYTHON_ICON;
    private final MenuBarMerge menu = new MenuBarMerge(WORKSPACE_KEY);
    private final BaseAction launchResource = new BaseAction("webcompat.actions.launchResource") {
        @Override
        public void actionPerformed(ActionEvent event) {
            ResourceEditor editor = getSelectedEditor();
            if (editor != null) {
                String gateway = GatewayConnectionManager.getInstance().getGatewayWebURL();
                try {
                    BrowserLauncher.openURL(StringUtils.removeEnd(gateway, "/")
                            + mountedPath(editor.getResourcePath()));
                } catch (IOException | URISyntaxException ex) {
                    ErrorUtil.showError(ex);
                }
            }
        }

        @Override
        protected boolean evaluateEnabled() {
            return getSelectedEditor() != null;
        }
    };

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

    private static final Consumer<ResourceBuilder> NEW_TEXT_RESOURCE = builder -> {
        JsonObject prototype = new JsonObject();
        prototype.addProperty("resource-type", "text-resource");
        prototype.addProperty("content-type", "text/html");
        prototype.addProperty("text", "");
        addPrototype(builder, prototype);
    };

    public WebResourceWorkspace(DesignerContext context) {
        super(context, DESCRIPTOR);
        JMenuMerge tools = new JMenuMerge("tools");
        JMenuMerge launch = new JMenuMerge("launch", "webcompat.actions.launch");
        launch.add(launchResource);
        tools.add(40000, launch);
        menu.add(2147483547, tools);
        getModel().addChangeListener(event -> launchResource.updateEnabled());
        launchResource.updateEnabled();
    }

    @Override
    public MenuBarMerge getMenu() {
        return menu;
    }

    @Override
    protected AbstractNavTreeNode createRootNavTreeNode() {
        return new WebResourceFolder();
    }

    private String mountedPath(ResourcePath path) throws URISyntaxException {
        String decoded = "/system/" + WebCompatModule.SERVLET_KEY + "/"
                + context.getProject().getName() + "/" + path.getFolderPath();
        return new URI(null, null, decoded, null).toASCIIString();
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
        menu.add(new ImportTextResourceAction(this, folder));
        menu.addSeparator();
        menu.add(new NewPythonResourceAction(this, folder));
        menu.add(new NewFileResourceAction(this, folder));
        menu.add(new NewTextResourceAction(this, folder));
        menu.add(new NewMountedFolderResourceAction(this, folder));
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
                                List.of(
                                        ResourceBuilderDelegate.build("webcompat.resources.python", PYTHON_ICON, NEW_PYTHON_RESOURCE),
                                        new NewFileResourceBuilderDelegate(),
                                        ResourceBuilderDelegate.build("webcompat.resources.text", TEXT_ICON, NEW_TEXT_RESOURCE),
                                        new NewMountedFolderResourceBuilderDelegate()),
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

    private class WebResourceFolder extends ResourceFolderNode {
        WebResourceFolder() {
            super(WebResourceWorkspace.this.context, WebResourceWorkspace.this, true);
        }

        WebResourceFolder(Resource resource) {
            super(WebResourceWorkspace.this.context, WebResourceWorkspace.this, resource);
        }

        @Override
        protected AbstractNavTreeNode createChildNode(Resource resource) {
            if (!getResourceType().equals(resource.getResourceType())) {
                return null;
            }
            return resource.isFolder() ? new WebResourceFolder(resource) : new WebResourceNode(resource);
        }
    }

    private class WebResourceNode extends ResourceNode {
        private final PopupAction copyPath = new PopupAction("webcompat.actions.copyMountedPath", VectorIcons.get("copy-path")) {
            @Override
            public void actionPerformed(ActionEvent event) {
                try {
                    ClipboardUtil.doCopyTransferableToClipboard(new StringSelection(mountedPath(getResourcePath())));
                } catch (URISyntaxException ex) {
                    ErrorUtil.showError(ex);
                }
            }
        };

        WebResourceNode(Resource resource) {
            super(WebResourceWorkspace.this.context, WebResourceWorkspace.this, resource);
        }

        @Override
        protected void addEditActions(JPopupMenu menu) {
            super.addEditActions(menu);
            menu.add(copyPath);
        }

        @Override
        protected void setupEditActions(TreePath[] paths, List<AbstractNavTreeNode> selection) {
            super.setupEditActions(paths, selection);
            copyPath.setEnabled(selection.size() == 1);
        }
    }

    private static class ImportTextResourceAction extends NewResourceAction {
        private File source;

        ImportTextResourceAction(TabbedResourceWorkspace workspace, ResourceFolderNode folder) {
            super(workspace, folder);
            putValue(NAME, BundleUtil.i18n("webcompat.text.import"));
            putValue(SMALL_ICON, TEXT_ICON);
        }

        @Override
        protected void configureResource(ResourceBuilder builder) throws IOException {
            JFileChooser chooser = new JFileChooser();
            canceled = chooser.showOpenDialog((JComponent) workspace.getWorkspace()) != JFileChooser.APPROVE_OPTION;
            if (canceled) {
                return;
            }
            source = chooser.getSelectedFile();
            String mimeType = URLConnection.guessContentTypeFromName(source.getName());
            JsonObject prototype = new JsonObject();
            prototype.addProperty("resource-type", "text-resource");
            prototype.addProperty("content-type", mimeType == null ? "text/plain" : mimeType);
            prototype.addProperty("text", Files.readString(source.toPath()));
            addPrototype(builder, prototype);
        }

        @Override
        protected String newResourceName() {
            return source == null ? "TextImport" : source.getName();
        }
    }

    private static class NewPythonResourceAction extends NewResourceAction {
        NewPythonResourceAction(TabbedResourceWorkspace workspace, ResourceFolderNode folder) {
            super(workspace, folder, NEW_PYTHON_RESOURCE);
            putValue(NAME, BundleUtil.i18n("webcompat.python.new"));
            putValue(SMALL_ICON, PYTHON_ICON);
        }

        @Override
        protected String newResourceName() {
            return "python";
        }
    }

    private static class NewTextResourceAction extends NewResourceAction {
        NewTextResourceAction(TabbedResourceWorkspace workspace, ResourceFolderNode folder) {
            super(workspace, folder, NEW_TEXT_RESOURCE);
            putValue(NAME, BundleUtil.i18n("webcompat.text.new"));
            putValue(SMALL_ICON, TEXT_ICON);
        }

        @Override
        protected String newResourceName() {
            return "newfile.txt";
        }
    }

    private static class NewFileResourceAction extends NewResourceAction {
        private File selectedFile;

        NewFileResourceAction(TabbedResourceWorkspace workspace, ResourceFolderNode folder) {
            super(workspace, folder);
            putValue(NAME, BundleUtil.i18n("webcompat.file.new"));
            putValue(SMALL_ICON, FILE_ICON);
        }

        @Override
        protected void configureResource(ResourceBuilder builder) {
            JFileChooser chooser = new JFileChooser();
            int result = chooser.showOpenDialog((JComponent) workspace.getWorkspace());
            if (result != JFileChooser.APPROVE_OPTION) {
                canceled = true;
                return;
            }

            selectedFile = chooser.getSelectedFile();
            if (selectedFile == null || !selectedFile.isFile()) {
                canceled = true;
                return;
            }

            if (!configureFileResource(builder, selectedFile)) {
                canceled = true;
            }
        }

        @Override
        protected String newResourceName() {
            return selectedFile != null ? selectedFile.getName() : "FileResource";
        }
    }

    private static class NewMountedFolderResourceAction extends NewResourceAction {
        private File selectedFolder;

        NewMountedFolderResourceAction(TabbedResourceWorkspace workspace, ResourceFolderNode folder) {
            super(workspace, folder);
            putValue(NAME, BundleUtil.i18n("webcompat.folder.new"));
            putValue(SMALL_ICON, FOLDER_ICON);
        }

        @Override
        protected void configureResource(ResourceBuilder builder) {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);

            int result = chooser.showOpenDialog((JComponent) workspace.getWorkspace());
            if (result != JFileChooser.APPROVE_OPTION) {
                canceled = true;
                return;
            }

            selectedFolder = chooser.getSelectedFile();
            if (selectedFolder == null || !Files.isDirectory(selectedFolder.toPath())) {
                canceled = true;
                return;
            }

            if (!configureMountedFolderResource(builder, selectedFolder)) {
                canceled = true;
            }
        }

        @Override
        protected String newResourceName() {
            return selectedFolder != null ? selectedFolder.getName() : "MountedResource";
        }
    }

    private class NewFileResourceBuilderDelegate extends DefaultResourceBuilderDelegate {
        private File selectedFile;

        NewFileResourceBuilderDelegate() {
            super("webcompat.resources.file", FILE_ICON);
        }

        @Override
        public Optional<ResourcePath> createResource(DesignerContext context, ResourcePath path) {
            JFileChooser chooser = new JFileChooser();
            int result = chooser.showOpenDialog((JComponent) getWorkspace());
            if (result != JFileChooser.APPROVE_OPTION) {
                return Optional.empty();
            }

            selectedFile = chooser.getSelectedFile();
            if (selectedFile == null || !selectedFile.isFile()) {
                return Optional.empty();
            }

            return super.createResource(context, path);
        }

        @Override
        protected void configureResource(ResourceBuilder builder) {
            if (!configureFileResource(builder, selectedFile)) {
                throw new IllegalStateException("Unable to configure file resource");
            }
        }
    }

    private class NewMountedFolderResourceBuilderDelegate extends DefaultResourceBuilderDelegate {
        private File selectedFolder;

        NewMountedFolderResourceBuilderDelegate() {
            super("webcompat.resources.folder", FOLDER_ICON);
        }

        @Override
        public Optional<ResourcePath> createResource(DesignerContext context, ResourcePath path) {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);

            int result = chooser.showOpenDialog((JComponent) getWorkspace());
            if (result != JFileChooser.APPROVE_OPTION) {
                return Optional.empty();
            }

            selectedFolder = chooser.getSelectedFile();
            if (selectedFolder == null || !Files.isDirectory(selectedFolder.toPath())) {
                return Optional.empty();
            }

            return super.createResource(context, path);
        }

        @Override
        protected void configureResource(ResourceBuilder builder) {
            if (!configureMountedFolderResource(builder, selectedFolder)) {
                throw new IllegalStateException("Unable to configure mounted folder resource");
            }
        }
    }

    private static boolean configureFileResource(ResourceBuilder builder, File selectedFile) {
        try {
            String contentType = URLConnection.guessContentTypeFromName(selectedFile.getName());
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }

            JsonObject prototype = new JsonObject();
            prototype.addProperty("resource-type", "file-resource");
            prototype.addProperty("content-type", contentType);
            addPrototype(builder, prototype);
            builder.putData("file.bin", ImmutableBytes.fromFile(selectedFile.toPath()));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean configureMountedFolderResource(ResourceBuilder builder, File selectedFolder) {
        try {
            JsonObject prototype = new JsonObject();
            prototype.addProperty("resource-type", "mounted-folder-resource");
            prototype.addProperty("folder-path", selectedFolder.getAbsolutePath());
            addPrototype(builder, prototype);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
