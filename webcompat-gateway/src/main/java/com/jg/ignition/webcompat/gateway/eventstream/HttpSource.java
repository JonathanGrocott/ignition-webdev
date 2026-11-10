package com.jg.ignition.webcompat.gateway.eventstream;

import com.inductiveautomation.eventstream.EventPayload;
import com.inductiveautomation.eventstream.SourceDescriptor;
import com.inductiveautomation.eventstream.gateway.EventStreamManager;
import com.inductiveautomation.eventstream.gateway.api.EventStreamContext;
import com.inductiveautomation.eventstream.gateway.api.EventStreamSource;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceCollectionNotFoundException;
import com.inductiveautomation.ignition.common.user.AuthenticatedUser;
import com.inductiveautomation.ignition.common.user.SimpleAuthChallenge;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.jg.ignition.webcompat.common.WebCompatModule;
import com.jg.ignition.webcompat.common.eventstream.HttpSourceConfig;
import com.jg.ignition.webcompat.gateway.servlets.EventStreamDispatch;
import com.jg.ignition.webcompat.gateway.servlets.PathParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.StringUtils;

public final class HttpSource implements EventStreamSource {
    private final GatewayContext context;
    private final EventStreamDispatch.Routes routes;
    private final EventStreamDispatch.Handler handler = this::receive;
    private final PathParser path;
    private final HttpSourceConfig config;
    private final String sessionKey;
    private final UUID generation = UUID.randomUUID();
    private volatile Subscriber subscriber;

    public static Runnable register(GatewayContext context, EventStreamDispatch.Routes routes) {
        var registry = EventStreamManager.get(context).getSourceRegistry();
        Factory factory = new Factory() {
            @Override
            public SourceDescriptor getDescriptor() {
                return new SourceDescriptor(WebCompatModule.EVENT_STREAM_SOURCE_ID, "HTTP Endpoint",
                        "Receive HTTP requests as events.");
            }

            @Override
            public EventStreamSource create(EventStreamContext stream, JsonObject config) {
                return new HttpSource(context, routes, new PathParser(stream.project(), stream.path()),
                        HttpSourceConfig.fromJson(config));
            }
        };
        registry.register(factory);
        return () -> {
            if (registry.get(factory.getTypeId()) == factory) {
                registry.unregister(factory);
            }
        };
    }

    HttpSource(GatewayContext context, EventStreamDispatch.Routes routes, PathParser path, HttpSourceConfig config) {
        this.context = context;
        this.routes = routes;
        this.path = path;
        this.config = config;
        sessionKey = WebCompatModule.EVENT_STREAM_SOURCE_ID + ":" + path.lowercase();
    }

    @Override
    public synchronized void onStartup(Subscriber subscriber) {
        this.subscriber = subscriber;
        routes.register(path, handler);
    }

    @Override
    public synchronized void onShutdown() {
        routes.unregister(path, handler);
        subscriber = null;
    }

    void receive(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Subscriber current = subscriber;
        if (current == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (config.requireHttps() && !request.isSecure()) {
            String query = request.getQueryString();
            response.sendRedirect("https://" + request.getServerName() + ":"
                    + context.getWebResourceManager().getHttpsPort() + request.getRequestURI()
                    + (query == null ? "" : "?" + query));
            return;
        }

        if (config.requireAuth() && !authenticate(request, response)) {
            return;
        }

        EventPayload event = EventPayload.builder(request.getInputStream().readAllBytes())
                .withMetadata(metadata -> {
                    metadata.add("method", request.getMethod());
                    metadata.add("scheme", request.getScheme());
                    metadata.add("remoteAddr", request.getRemoteAddr());
                    metadata.add("remoteHost", request.getRemoteHost());
                    metadata.add("headers", headers -> request.getHeaderNames().asIterator()
                            .forEachRemaining(name -> headers.add(name, request.getHeader(name))));
                    metadata.add("parameters", parameters -> request.getParameterMap().forEach((name, values) -> {
                        if (values.length == 1) {
                            parameters.add(name, values[0]);
                        } else {
                            parameters.add(name, values);
                        }
                    }));
                })
                .build();
        current.submitEvent(event);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private boolean authenticate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthState state = authState(request.getSession());
        if (state.authenticated) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (!StringUtils.startsWithIgnoreCase(auth, "Basic ")) {
            challenge(response);
            return false;
        }

        String[] credentials;
        try {
            credentials = new String(Base64.getDecoder().decode(auth.substring(6)), StandardCharsets.UTF_8).split(":", 2);
        } catch (IllegalArgumentException ignored) {
            credentials = new String[0];
        }
        if (credentials.length != 2) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Malformed auth request.");
            return false;
        }

        AuthenticatedUser user;
        try {
            String userSource = config.userSource();
            if (StringUtils.isBlank(userSource)) {
                userSource = context.getProjectManager().getProjectProps(path.project()).getAuthProfileName();
            }
            if (StringUtils.isBlank(userSource)) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No user source for project.");
                return false;
            }
            user = context.getUserSourceManager().getProfile(userSource)
                    .authenticate(new SimpleAuthChallenge(credentials[0], credentials[1]));
        } catch (ResourceCollectionNotFoundException ignored) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No user source for project.");
            return false;
        } catch (Exception ignored) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error authenticating.");
            return false;
        }

        if (user == null) {
            if (state.attempts.getAndUpdate(count -> count < config.maxRetryAttempts() ? count + 1 : count)
                    < config.maxRetryAttempts()) {
                challenge(response);
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied.");
            }
            return false;
        }

        if (StringUtils.isNotBlank(config.requiredRoles())
                && Arrays.stream(config.requiredRoles().split(",")).map(String::trim).noneMatch(user.getRoles()::contains)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied.");
            return false;
        }
        state.authenticated = true;
        return true;
    }

    private void challenge(HttpServletResponse response) throws IOException {
        response.setHeader("WWW-Authenticate", "Basic realm=\"" + path.project() + "\"");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private AuthState authState(HttpSession session) {
        synchronized (session) {
            if (session.getAttribute(sessionKey) instanceof AuthState state && generation.equals(state.generation)) {
                return state;
            }
            AuthState state = new AuthState(generation);
            session.setAttribute(sessionKey, state);
            return state;
        }
    }

    private static final class AuthState implements Serializable {
        private static final long serialVersionUID = 1L;
        private final UUID generation;
        private final AtomicInteger attempts = new AtomicInteger();
        private volatile boolean authenticated;

        AuthState(UUID generation) {
            this.generation = generation;
        }
    }
}
