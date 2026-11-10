package com.jg.ignition.webcompat.designer.workspace;

import com.inductiveautomation.ignition.client.util.gui.ErrorUtil;
import com.inductiveautomation.ignition.common.Base64;
import com.inductiveautomation.ignition.common.ImmutableBytes;
import com.inductiveautomation.ignition.common.JsonUtilities;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceBuilder;
import com.inductiveautomation.ignition.common.resourcecollection.ResourcePath;
import com.inductiveautomation.ignition.common.util.ResourceUtil;
import com.inductiveautomation.ignition.designer.tabbedworkspace.TabbedResourceWorkspace;
import com.jg.ignition.webcompat.designer.workspace.WebResourceEditor.WebDevResourceEditor;
import java.awt.EventQueue;
import java.awt.LayoutManager;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.net.URLConnection;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import net.miginfocom.swing.MigLayout;

public class FileResourceEditor extends JPanel implements WebDevResourceEditor {
    private final JsonObject config;
    private final TabbedResourceWorkspace workspace;
    private final ResourcePath path;
    private final ContentTypePicker contentType;
    private final JLabel fileInfo;
    private byte[] bytes;

    FileResourceEditor(TabbedResourceWorkspace workspace, ResourcePath path, JsonObject config) {
        super((LayoutManager) new MigLayout("ins 16, fillx"));
        this.config = config;
        this.workspace = workspace;
        this.path = path;

        this.contentType = new ContentTypePicker(JsonUtilities.readString(config, "content-type", "application/octet-stream"));
        this.bytes = decodeBase64(JsonUtilities.readString(config, "bytes", ""));
        this.fileInfo = new JLabel();
        updatePreview();
        contentType.addActionListener(event -> updatePreview());

        JButton choose = new JButton("Choose...");
        choose.addActionListener(e -> pickFile());

        add(new JLabel("Content-Type"), "split 2");
        add(contentType, "growx, wrap");
        add(new JLabel("File"), "split 3");
        add(new JScrollPane(fileInfo), "growx, pushx, hmax 260");
        add(choose, "wrap");
    }

    private void pickFile() {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (selected != null && selected.isFile()) {
                try {
                    replaceFile(selected);
                } catch (Exception ex) {
                    ErrorUtil.showError(ex);
                }
            }
        }
    }

    private void replaceFile(File selected) throws Exception {
        String name = selected.getName();
        boolean rename = !path.getName().equals(name);
        var project = workspace.getContext().getProject();
        if (!ResourceUtil.isLegalName(name)) {
            throw new IOException("Invalid resource name: " + name);
        }
        ResourcePath destination = path.getParent().getChild(name);
        if (rename && project.getResource(destination).filter(resource -> !resource.getResourcePath().equals(path)).isPresent()) {
            throw new IOException("A resource named " + name + " already exists.");
        }

        byte[] replacement = Files.readAllBytes(selected.toPath());
        bytes = replacement;
        String guessed = URLConnection.guessContentTypeFromName(name);
        contentType.setSelectedItem(guessed == null ? "application/octet-stream" : guessed);
        updatePreview();
        workspace.commit(path);

        if (rename) {
            var savedBytes = project.getResource(path).flatMap(resource -> resource.getData("file.bin"));
            if (savedBytes.map(ImmutableBytes::getBytes).filter(saved -> Arrays.equals(saved, replacement)).isEmpty()) {
                throw new IOException("Unable to save replacement for " + path.getName());
            }
            project.renameResource(path, name);
            EventQueue.invokeLater(() -> workspace.open(destination));
        }
    }

    private void updatePreview() {
        fileInfo.setText(bytes.length + " bytes");
        fileInfo.setIcon(null);
        if (contentType.contentType().startsWith("image/")) {
            try {
                var image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (image != null) {
                    fileInfo.setIcon(new ImageIcon(image));
                }
            } catch (IOException ignored) {
                // A file can still be served if Java cannot preview it.
            }
        }
    }

    private byte[] decodeBase64(String raw) {
        try {
            if (raw == null || raw.isBlank()) {
                return new byte[0];
            }
            return Base64.decode(raw);
        } catch (Exception ignored) {
            return new byte[0];
        }
    }

    @Override
    public JsonObject getConfig() {
        config.addProperty("resource-type", "file-resource");
        config.addProperty("content-type", contentType.contentType());
        config.addProperty("bytes", Base64.encodeBytes(bytes));
        return config;
    }

    @Override
    public void beforeSave(ResourceBuilder builder, JsonObject config) {
        builder.putData("file.bin", bytes);
        config.remove("bytes");
    }
}
