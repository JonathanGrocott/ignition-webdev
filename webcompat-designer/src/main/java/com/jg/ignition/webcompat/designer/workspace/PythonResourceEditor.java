package com.jg.ignition.webcompat.designer.workspace;

import com.inductiveautomation.ignition.client.rpc.PlatformRpcInstances;
import com.inductiveautomation.ignition.client.util.gui.AbstractProfileOptionDropdown;
import com.inductiveautomation.ignition.client.util.gui.HeaderLabel;
import com.inductiveautomation.ignition.client.util.gui.Listen;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.JsonUtilities;
import com.inductiveautomation.ignition.common.TypeUtilities;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceBuilder;
import com.inductiveautomation.ignition.common.script.typing.ExtensionFunctionDescriptor;
import com.inductiveautomation.ignition.common.script.typing.TypeDescriptor;
import com.inductiveautomation.ignition.common.user.UserSourceMeta;
import com.inductiveautomation.ignition.designer.gui.EnablerItemListener;
import com.inductiveautomation.ignition.designer.gui.WatermarkTextField;
import com.inductiveautomation.ignition.designer.gui.tools.ExtensionFunctionPanel;
import com.inductiveautomation.ignition.designer.location.Locatable;
import com.inductiveautomation.ignition.designer.location.Location;
import com.jg.ignition.webcompat.common.MethodObject;
import com.jg.ignition.webcompat.common.PythonResource;
import com.jg.ignition.webcompat.common.WebCompatModule;
import com.jg.ignition.webcompat.designer.workspace.WebResourceEditor.WebDevResourceEditor;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.LayoutManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import net.miginfocom.swing.MigLayout;

public class PythonResourceEditor extends JPanel implements WebDevResourceEditor, Locatable {
    private final JsonObject config;

    private final JCheckBox enabledBox;
    private final JCheckBox https;
    private final JCheckBox useAuth;
    private final JSpinner maxRetryAttempts;
    private final JLabel maxRetryAttemptLabel;
    private final UserSourceDropdown userSource;
    private final WatermarkTextField requiredRoles;
    private final ExtensionFunctionPanel editor;
    private final JComboBox<String> methodComboBox;

    private String method;
    private final Map<String, MethodObject> methodProperties = new HashMap<>();

    PythonResourceEditor(JsonObject config) {
        super((LayoutManager) new MigLayout("ins 16, fill"));
        this.config = config;

        methodComboBox = new JComboBox<>(PythonResource.METHODS);
        methodComboBox.setEditable(false);

        enabledBox = new JCheckBox(BundleUtil.i18n("webcompat.enabled"));
        https = new JCheckBox(BundleUtil.i18n("webcompat.requireHttps"));
        useAuth = new JCheckBox(BundleUtil.i18n("webcompat.requireAuth"));

        maxRetryAttempts = new JSpinner(new SpinnerNumberModel(3, 1, 100, 1));
        maxRetryAttemptLabel = new JLabel(BundleUtil.i18n("webcompat.maxRetryAttempts"));

        userSource = new UserSourceDropdown();
        userSource.setEnabled(false);

        requiredRoles = new WatermarkTextField();
        requiredRoles.setWatermarkText(BundleUtil.i18n("webcompat.requiredRoles"));
        requiredRoles.setEnabled(false);

        EnablerItemListener.install(useAuth, false, userSource, requiredRoles, maxRetryAttempts, maxRetryAttemptLabel);

        editor = new ExtensionFunctionPanel(ExtensionFunctionPanel.GATEWAY_HINTS);
        editor.setEnabled(false);

        Listen.toStateChange(enabledBox, this::updateEnabledState);

        add((Component) new HeaderLabel(BundleUtil.i18n("webcompat.httpMethod")));
        add((Component) methodComboBox);
        add((Component) enabledBox, "gapleft 20px");
        add((Component) https, "gapleft 20px");
        add((Component) useAuth, "gapleft 20px");
        add((Component) userSource, "wmin 120px");
        add((Component) requiredRoles, "wmin 180px");
        add((Component) maxRetryAttempts, "wmin 40px, wmax 50px");
        add((Component) maxRetryAttemptLabel, "wmin 150px, pushx, wrap");
        add((Component) editor, "spanx 9, pushy, grow");

        Listen.toCombobox(methodComboBox, this::setMethod);

        for (String methodName : PythonResource.METHODS) {
            MethodObject methodObject = WebCompatModule.GSON.fromJson(
                    JsonUtilities.readObject(config, methodName).orElseGet(JsonObject::new),
                    MethodObject.class);
            methodProperties.put(methodName, methodObject);
        }

        setMethod("doGet");
    }

    private void setMethod(String methodName) {
        if (methodName == null) {
            return;
        }

        save();

        MethodObject methodObject = methodProperties.get(methodName);
        if (methodObject == null) {
            methodObject = new MethodObject();
            methodProperties.put(methodName, methodObject);
        }

        editor.setEnabled(true);
        editor.setDescriptor(buildDescriptor(methodName));
        editor.setUserScript(methodObject.code);

        enabledBox.setSelected(methodObject.enabled);
        https.setSelected(methodObject.requireHttps);
        useAuth.setSelected(methodObject.requireAuth);
        userSource.setSelectedItem(methodObject.userSource);
        userSource.refresh(false);
        requiredRoles.setText(methodObject.requiredRoles);
        maxRetryAttempts.setValue(methodObject.maxRetryAttempts);

        this.method = methodName;
        updateEnabledState(methodObject.enabled);
    }

    @Override
    public void locate(Location location) {
        String methodName = location.get("script");
        if (methodName != null) {
            methodComboBox.setSelectedItem(methodName);
            EventQueue.invokeLater(() -> editor.requestFocusInWindow());
        }
    }

    private void updateEnabledState(boolean state) {
        https.setEnabled(state);
        useAuth.setEnabled(state);
        userSource.setEnabled(state && useAuth.isSelected());
        requiredRoles.setEnabled(state && useAuth.isSelected());
        maxRetryAttempts.setEnabled(state && useAuth.isSelected());
        maxRetryAttemptLabel.setEnabled(state && useAuth.isSelected());
        editor.setEnabled(state);
    }

    private void save() {
        if (method != null) {
            methodProperties.computeIfPresent(method, (s, methodObject) -> {
                methodObject.code = editor.getUserScript();
                methodObject.enabled = enabledBox.isSelected();
                methodObject.requireHttps = https.isSelected();
                methodObject.requireAuth = useAuth.isSelected();
                methodObject.userSource = userSource.getSelectedItem();
                methodObject.requiredRoles = requiredRoles.getText();
                methodObject.maxRetryAttempts = TypeUtilities.toInteger(maxRetryAttempts.getValue());
                return methodObject;
            });
        }

        methodProperties.forEach((name, props) -> config.add(name, WebCompatModule.GSON.toJsonTree(props)));
    }

    @Override
    public JsonObject getConfig() {
        save();
        return config;
    }

    @Override
    public void beforeSave(ResourceBuilder builder, JsonObject config) {
        save();

        for (String methodName : PythonResource.METHODS) {
            MethodObject methodProps = WebCompatModule.GSON.fromJson(
                    JsonUtilities.readObject(config, methodName).orElseGet(JsonObject::new),
                    MethodObject.class);

            builder.putData(PythonResource.getDataKey(methodName), methodProps.getCodeToSave(methodName));
            methodProps.code = null;
            config.add(methodName, WebCompatModule.GSON.toJsonTree(methodProps));
        }
    }

    private ExtensionFunctionDescriptor buildDescriptor(String methodName) {
        return new ExtensionFunctionDescriptor.Builder(methodName)
                .description("Respond to an incoming HTTP request and return a dictionary containing response values.")
                .param(
                        "request",
                        "Dictionary with metadata and input payload.",
                        TypeDescriptor.Dictionary.toBuilder()
                                .addItem("scheme", "Request scheme, such as http or https.", () -> TypeDescriptor.String)
                                .addItem("remoteAddr", "Client IP address.", () -> TypeDescriptor.String)
                                .addItem("remoteHost", "Client host name.", () -> TypeDescriptor.String)
                                .addItem("remainingPath", "Remaining path after the mounted resource.", () -> TypeDescriptor.String)
                                .addItem("headers", "Header name/value dictionary.", () -> TypeDescriptor.Dictionary)
                                .addItem("params", "Query/form parameter dictionary.", () -> TypeDescriptor.Dictionary)
                                .addItem("servletRequest", "Underlying Java HttpServletRequest object.")
                                .addItem("servletResponse", "Underlying Java HttpServletResponse object.")
                                .addItem("context", "Gateway context object.")
                                .addItem("data", "Request body payload (json/text/bytes).")
                                .addItem("postData", "POST payload mirror for compatibility.")
                                .build())
                .param("session", "Per-session dictionary persisted per browser session.", TypeDescriptor.Dictionary)
                .build();
    }

    private static class UserSourceDropdown extends AbstractProfileOptionDropdown {
        UserSourceDropdown() {
            super(false);
            setNoneOptionText(getNoSelectionText());
            setAllowNone(true);
        }

        @Override
        public boolean isLoadInBackground() {
            return true;
        }

        @Override
        protected List<String> getOptions() throws Exception {
            return PlatformRpcInstances.USERS_RPC
                    .getUserSources()
                    .stream()
                    .map(UserSourceMeta::getName)
                    .collect(Collectors.toList());
        }
    }
}
