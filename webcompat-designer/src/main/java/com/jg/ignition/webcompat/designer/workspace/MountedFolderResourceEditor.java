package com.jg.ignition.webcompat.designer.workspace;

import com.inductiveautomation.ignition.common.JsonUtilities;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.jg.ignition.webcompat.designer.workspace.WebResourceEditor.WebDevResourceEditor;
import java.awt.LayoutManager;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;

public class MountedFolderResourceEditor extends JPanel implements WebDevResourceEditor {
    private final JsonObject config;
    private final JTextField folderPath;

    MountedFolderResourceEditor(JsonObject config) {
        super((LayoutManager) new MigLayout("ins 16, fillx"));
        this.config = config;

        folderPath = new JTextField(JsonUtilities.readString(config, "folder-path", ""));
        JButton browse = new JButton("Choose...");
        browse.addActionListener(e -> pickFolder());

        add(new JLabel("Folder Path"), "split 3");
        add(folderPath, "growx, pushx");
        add(browse, "wrap");
    }

    private void pickFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (selected != null) {
                folderPath.setText(selected.getAbsolutePath());
            }
        }
    }

    @Override
    public JsonObject getConfig() {
        config.addProperty("resource-type", "mounted-folder-resource");
        config.addProperty("folder-path", folderPath.getText());
        return config;
    }
}
