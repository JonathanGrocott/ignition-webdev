package com.jg.ignition.webcompat.designer.workspace;

import java.util.Objects;
import javax.swing.JComboBox;

final class ContentTypePicker extends JComboBox<String> {
    private static final String[] TYPES = {
            "application/json", "application/javascript", "application/octet-stream",
            "application/pdf", "application/xml", "application/zip", "application/gzip",
            "image/gif", "image/jpeg", "image/png",
            "text/css", "text/csv", "text/html", "text/plain", "text/xml"
    };

    ContentTypePicker(String selected) {
        super(TYPES);
        setEditable(true);
        setSelectedItem(selected);
    }

    String contentType() {
        return Objects.toString(getEditor().getItem(), "");
    }
}
