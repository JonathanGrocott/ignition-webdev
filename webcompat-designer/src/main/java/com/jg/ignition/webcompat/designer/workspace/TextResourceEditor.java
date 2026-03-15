package com.jg.ignition.webcompat.designer.workspace;

import com.inductiveautomation.ignition.common.JsonUtilities;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.jg.ignition.webcompat.designer.workspace.WebResourceEditor.WebDevResourceEditor;
import java.awt.LayoutManager;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;

public class TextResourceEditor extends JPanel implements WebDevResourceEditor {
    private final JsonObject config;
    private final JTextField contentType;
    private final JTextArea textArea;

    TextResourceEditor(JsonObject config) {
        super((LayoutManager) new MigLayout("ins 16, fill"));
        this.config = config;

        contentType = new JTextField(JsonUtilities.readString(config, "content-type", "text/plain"));
        textArea = new JTextArea(JsonUtilities.readString(config, "text", ""));

        add(new JLabel("Content-Type"), "split 2");
        add(contentType, "growx, wrap");
        add(new JScrollPane(textArea), "span, grow, push");
    }

    @Override
    public JsonObject getConfig() {
        config.addProperty("resource-type", "text-resource");
        config.addProperty("content-type", contentType.getText());
        config.addProperty("text", textArea.getText());
        return config;
    }
}
