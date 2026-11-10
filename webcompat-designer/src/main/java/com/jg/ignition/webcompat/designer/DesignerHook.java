package com.jg.ignition.webcompat.designer;

import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceId;
import com.inductiveautomation.ignition.common.resourcecollection.ResourcePath;
import com.inductiveautomation.ignition.designer.location.Locatable;
import com.inductiveautomation.ignition.designer.location.Location;
import com.inductiveautomation.ignition.designer.location.LocationRenderingInfo;
import com.inductiveautomation.ignition.designer.model.AbstractDesignerModuleHook;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.inductiveautomation.ignition.designer.model.SaveContext;
import com.jg.ignition.webcompat.common.WebCompatModule;
import com.jg.ignition.webcompat.designer.eventstream.HttpSourceDesignDelegate;
import com.jg.ignition.webcompat.designer.search.WebDevSearchProvider;
import com.jg.ignition.webcompat.designer.workspace.WebResourceWorkspace;
import javax.swing.Icon;

public class DesignerHook extends AbstractDesignerModuleHook implements Locatable {
    private WebResourceWorkspace workspace;
    private Runnable unregisterHttpSource;

    @Override
    public void startup(DesignerContext context, LicenseState activationState) {
        BundleUtil.get().addBundle("webcompat", DesignerHook.class, "webcompat");

        workspace = new WebResourceWorkspace(context);
        context.registerResourceWorkspace(workspace);
        context.registerSearchProvider(new WebDevSearchProvider(context, workspace));
        if (context.getModule(WebCompatModule.EVENT_STREAM_MODULE_ID) != null) {
            unregisterHttpSource = HttpSourceDesignDelegate.register(context);
        }
    }

    @Override
    public void shutdown() {
        if (unregisterHttpSource != null) {
            unregisterHttpSource.run();
            unregisterHttpSource = null;
        }
    }

    @Override
    public void notifyProjectSaveStart(SaveContext saveContext) {
        if (workspace != null) {
            workspace.commitAll();
        }
    }

    @Override
    public String getResourceCategoryKey(ResourceId resourceId) {
        if (resourceId.getResourcePath().isResourceTypeFolder()) {
            return "webcompat.qualifiedNouns";
        }
        return null;
    }

    @Override
    public String getResourceDisplayName(ResourceId resourceId) {
        if (resourceId.getResourcePath().isResourceTypeFolder()) {
            return BundleUtil.i18n("webcompat.module.name");
        }
        return null;
    }

    @Override
    public Icon getResourceIcon(ResourceId resourceId) {
        if (workspace == null) {
            return null;
        }

        ResourcePath resourcePath = resourceId.getResourcePath();
        if (resourcePath.isResourceTypeFolder()) {
            return workspace.getDescriptor().getNavRootIcon();
        }

        return WebCompatModule.RESOURCE_TYPE.equals(resourcePath.getResourceType())
                ? workspace.getDescriptor().getSmallIcon()
                : null;
    }

    @Override
    public void updateLocationRenderer(LocationRenderingInfo renderer, Location location) {
        if (workspace == null || location.getResourceType() == null) {
            return;
        }

        if (WebCompatModule.RESOURCE_TYPE.equals(location.getResourceType()) && location.containsKey("script")) {
            renderer.addPathElement(location.get("script")).setIcon(workspace.getDescriptor().getSmallIcon());
        }
    }

    @Override
    public void locate(Location location) {
        if (workspace != null) {
            workspace.locate(location);
        }
    }
}
