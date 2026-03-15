package com.jg.ignition.webcompat.gateway;

import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.jg.ignition.webcompat.common.WebCompatModule;
import com.jg.ignition.webcompat.gateway.servlets.WebDevDispatch;

public class GatewayHook extends AbstractGatewayModuleHook {
    private GatewayContext context;

    @Override
    public void setup(GatewayContext gatewayContext) {
        this.context = gatewayContext;
    }

    @Override
    public void startup(LicenseState activationState) {
        context.getWebResourceManager().addServlet(WebCompatModule.SERVLET_KEY, WebDevDispatch.class);
    }

    @Override
    public void shutdown() {
        if (context != null) {
            context.getWebResourceManager().removeServlet(WebCompatModule.SERVLET_KEY);
        }
    }

    @Override
    public boolean isMakerEditionCompatible() {
        return true;
    }

    @Override
    public boolean isFreeModule() {
        return true;
    }
}
