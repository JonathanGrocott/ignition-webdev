package com.jg.ignition.webcompat.designer.eventstream;

import com.inductiveautomation.eventstream.designer.EventStreamManager;
import com.inductiveautomation.eventstream.designer.api.EventStreamContext;
import com.inductiveautomation.eventstream.designer.api.source.EventStreamSourceDesignDelegate;
import com.inductiveautomation.eventstream.designer.api.source.SourceEditor;
import com.inductiveautomation.ignition.client.util.gui.ErrorUtil;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.jg.ignition.webcompat.common.WebCompatModule;
import com.jg.ignition.webcompat.common.eventstream.HttpSourceConfig;
import java.util.Objects;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import net.miginfocom.swing.MigLayout;

public final class HttpSourceDesignDelegate implements EventStreamSourceDesignDelegate {
    public static Runnable register(DesignerContext context) {
        var manager = EventStreamManager.get(context);
        if (manager == null) {
            return () -> {};
        }
        var registry = manager.getSourceRegistry();
        var delegate = new HttpSourceDesignDelegate();
        registry.register(delegate);
        return () -> registry.unregister(delegate.getType());
    }

    @Override
    public String getType() {
        return WebCompatModule.EVENT_STREAM_SOURCE_ID;
    }

    @Override
    public SourceEditor getEditor(EventStreamContext context) {
        return new HttpSourceEditor();
    }

    private static final class HttpSourceEditor extends SourceEditor {
        private final JCheckBox https = new JCheckBox(BundleUtil.i18n("webcompat.requireHttps"));
        private final JCheckBox auth = new JCheckBox(BundleUtil.i18n("webcompat.requireAuth"));
        private final JComboBox<String> userSource = new JComboBox<>();
        private final JTextField roles = new JTextField();
        private final JSpinner retries = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));

        HttpSourceEditor() {
            setLayout(new MigLayout("ins 0, fillx, wrap 2", "[][grow]"));
            add(https, "span");
            add(auth, "span");
            add(new JLabel(BundleUtil.i18n("webcompat.userSource")));
            add(userSource, "growx");
            add(new JLabel(BundleUtil.i18n("webcompat.requiredRoles")));
            add(roles, "growx");
            add(new JLabel(BundleUtil.i18n("webcompat.maxRetryAttempts")));
            add(retries);
            auth.addItemListener(event -> updateAuthFields());
        }

        @Override
        public void initialize(EventStreamContext context, JsonObject json) {
            HttpSourceConfig config = HttpSourceConfig.fromJson(json);
            https.setSelected(config.requireHttps());
            auth.setSelected(config.requireAuth());
            roles.setText(config.requiredRoles());
            retries.setValue(Math.max(0, config.maxRetryAttempts()));
            userSource.removeAllItems();
            userSource.addItem("");
            try {
                var sources = context.listUserSources();
                for (String source : sources) {
                    userSource.addItem(source);
                }
            } catch (Exception ex) {
                ErrorUtil.showError(ex);
            }
            if (!config.userSource().isEmpty() && !containsUserSource(config.userSource())) {
                userSource.addItem(config.userSource());
            }
            userSource.setSelectedItem(config.userSource());
            updateAuthFields();
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            if (auth != null) {
                updateAuthFields();
            }
        }

        private boolean containsUserSource(String name) {
            for (int i = 0; i < userSource.getItemCount(); i++) {
                if (name.equals(userSource.getItemAt(i))) {
                    return true;
                }
            }
            return false;
        }

        private void updateAuthFields() {
            boolean enabled = isEnabled() && auth.isSelected();
            userSource.setEnabled(enabled);
            roles.setEnabled(enabled);
            retries.setEnabled(enabled);
        }

        @Override
        public JsonObject getConfig() {
            return new HttpSourceConfig(https.isSelected(), auth.isSelected(),
                    Objects.toString(userSource.getSelectedItem(), ""), roles.getText(),
                    ((Number) retries.getValue()).intValue()).toJson();
        }
    }
}
