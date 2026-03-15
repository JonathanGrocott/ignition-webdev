package com.jg.ignition.webcompat.designer.workspace;

import com.inductiveautomation.ignition.common.Base64;
import com.inductiveautomation.ignition.common.JsonUtilities;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceBuilder;
import com.jg.ignition.webcompat.designer.workspace.WebResourceEditor.WebDevResourceEditor;
import java.awt.LayoutManager;
import java.io.File;
import java.nio.file.Files;
import java.net.URLConnection;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;

public class FileResourceEditor extends JPanel implements WebDevResourceEditor {
    private final JsonObject config;
    private final JTextField contentType;
    private final JLabel fileInfo;
    private byte[] bytes;

    FileResourceEditor(JsonObject config) {
        super((LayoutManager) new MigLayout("ins 16, fillx"));
        this.config = config;

        this.contentType = new JTextField(JsonUtilities.readString(config, "content-type", "application/octet-stream"));
        this.bytes = decodeBase64(JsonUtilities.readString(config, "bytes", ""));
        this.fileInfo = new JLabel(sizeText());

        JButton choose = new JButton("Choose...");
        choose.addActionListener(e -> pickFile());

        add(new JLabel("Content-Type"), "split 2");
        add(contentType, "growx, wrap");
        add(new JLabel("File"), "split 3");
        add(fileInfo, "growx, pushx");
        add(choose, "wrap");
    }

    private void pickFile() {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (selected != null && selected.isFile()) {
                try {
                    bytes = Files.readAllBytes(selected.toPath());
                    fileInfo.setText(selected.getName() + " (" + bytes.length + " bytes)");
                    String guessed = URLConnection.guessContentTypeFromName(selected.getName());
                    if (guessed != null && !guessed.isBlank()) {
                        contentType.setText(guessed);
                    }
                } catch (Exception ignored) {
                    // Leave previous bytes in place if loading fails.
                }
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

    private String sizeText() {
        return bytes.length + " bytes";
    }

    @Override
    public JsonObject getConfig() {
        config.addProperty("resource-type", "file-resource");
        config.addProperty("content-type", contentType.getText());
        config.addProperty("bytes", Base64.encodeBytes(bytes));
        return config;
    }

    @Override
    public void beforeSave(ResourceBuilder builder, JsonObject config) {
        builder.putData("file.bin", bytes);
        config.remove("bytes");
    }
}
