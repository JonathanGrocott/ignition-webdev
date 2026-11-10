package com.jg.ignition.webcompat.gateway;

import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.model.ModuleState;
import com.jg.ignition.webcompat.common.WebCompatModule;
import com.jg.ignition.webcompat.gateway.eventstream.HttpSource;
import com.jg.ignition.webcompat.gateway.servlets.EventStreamDispatch;
import com.jg.ignition.webcompat.gateway.servlets.WebDevDispatch;

public class GatewayHook extends AbstractGatewayModuleHook {
    private GatewayContext context;
    private final EventStreamDispatch.Routes eventStreamRoutes = new EventStreamDispatch.Routes();
    private Runnable unregisterHttpSource;

    @Override
    public void setup(GatewayContext gatewayContext) {
        this.context = gatewayContext;
        var eventStreams = context.getModuleManager().getModule(WebCompatModule.EVENT_STREAM_MODULE_ID);
        if (eventStreams != null && (eventStreams.getState() == ModuleState.PENDING || eventStreams.getState() == ModuleState.ACTIVE)) {
            unregisterHttpSource = HttpSource.register(context, eventStreamRoutes);
        }
    }

    @Override
    public void startup(LicenseState activationState) {
        context.getWebResourceManager().addServlet(WebCompatModule.SERVLET_KEY, WebDevDispatch.class);
        if (unregisterHttpSource != null) {
            context.getWebResourceManager().addServlet(WebCompatModule.EVENT_STREAM_SERVLET_KEY, EventStreamDispatch.class);
        }
    }

    @Override
    public void shutdown() {
        if (context != null) {
            context.getWebResourceManager().removeServlet(WebCompatModule.SERVLET_KEY);
            if (unregisterHttpSource != null) {
                context.getWebResourceManager().removeServlet(WebCompatModule.EVENT_STREAM_SERVLET_KEY);
                eventStreamRoutes.clear();
                unregisterHttpSource.run();
                unregisterHttpSource = null;
            }
        }
    }

    public EventStreamDispatch.Routes eventStreamRoutes() {
        return eventStreamRoutes;
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
