package com.jg.ignition.webcompat.gateway.handlers;

import com.inductiveautomation.ignition.common.Base64;
import com.inductiveautomation.ignition.common.JsonUtilities;
import com.inductiveautomation.ignition.common.TypeUtilities;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.project.GlobalProps;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceCollectionNotFoundException;
import com.inductiveautomation.ignition.common.script.JythonExecException;
import com.inductiveautomation.ignition.common.script.ScriptContext;
import com.inductiveautomation.ignition.common.script.ScriptFunction;
import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.common.script.builtin.SystemUtilities;
import com.inductiveautomation.ignition.common.script.typing.ExtensionFunctionDescriptor;
import com.inductiveautomation.ignition.common.tags.config.model.TagReferenceLocation;
import com.inductiveautomation.ignition.common.user.AuthChallenge;
import com.inductiveautomation.ignition.common.user.AuthenticatedUser;
import com.inductiveautomation.ignition.common.user.SimpleAuthChallenge;
import com.inductiveautomation.ignition.common.util.LogUtil;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.jg.ignition.webcompat.common.MethodObject;
import com.jg.ignition.webcompat.common.PythonResource;
import com.jg.ignition.webcompat.common.WebCompatModule;
import com.jg.ignition.webcompat.gateway.WebDevTagReferenceLocation;
import com.jg.ignition.webcompat.gateway.servlets.PathParser;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.ParseException;
import org.apache.http.entity.ContentType;
import org.json.JSONException;
import org.python.core.Py;
import org.python.core.PyDictionary;
import org.python.core.PyException;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PyTuple;
import org.slf4j.MDC;

public class PythonResourceHandler extends BaseHandler {
    private static final Set<String> TEXT_TYPES = Set.of(
            "application/json",
            "application/javascript",
            "application/xml",
            "text/plain",
            "text/html",
            "text/css",
            "text/csv",
            "text/xml");

    private static final LoggerEx LOG = LogUtil.getLogger("WebCompat.PythonResource");

    private final GatewayContext context;
    private final String projectName;
    private final PathParser path;
    private final String remainingPath;
    private final Map<String, ScriptFunction> methodHandlers = new HashMap<>();
    private final Map<String, MethodObject> methodObjects = new HashMap<>();

    public PythonResourceHandler(
            GatewayContext context,
            String projectName,
            PathParser path,
            JsonObject config,
            String remainingPath) {

        this.context = context;
        this.projectName = projectName;
        this.path = path;
        this.remainingPath = remainingPath;

        ScriptManager manager = context.getProjectManager().getProjectScriptManager(projectName);
        assert manager != null;

        for (String method : PythonResource.METHODS) {
            MethodObject methodProps = WebCompatModule.GSON.fromJson(
                    JsonUtilities.readObject(config, method).orElseGet(JsonObject::new),
                    MethodObject.class);

            methodObjects.put(method, methodProps);
            if (!methodProps.enabled || StringUtils.isBlank(methodProps.code)) {
                continue;
            }

            String code = new ExtensionFunctionDescriptor.Builder(method)
                    .param("request")
                    .param("session")
                    .build()
                    .buildDef() + methodProps.code;

            Thread thread = Thread.currentThread();
            ClassLoader oldLoader = thread.getContextClassLoader();
            try {
                thread.setContextClassLoader(GatewayContext.class.getClassLoader());
                ScriptFunction function = manager.compileFunction(String.format("<%s>", toString(method)), method, code);
                methodHandlers.put(method, function);
            } catch (JythonExecException | ScriptManager.UndefinedFunctionException | PyException ex) {
                LOG.error("Parse Error in " + method + " script.", ex);
            } finally {
                thread.setContextClassLoader(oldLoader);
            }
        }
    }

    private String sessionKey(String purpose) {
        return String.format("webdev-%s-%s", projectName, purpose);
    }

    private void doRequest(PathParser path, HttpServletRequest req, HttpServletResponse resp, String method)
            throws IOException {

        ScriptFunction func = methodHandlers.get(method);
        if (func == null) {
            resp.sendError(501);
            return;
        }

        MethodObject methodConfig = methodObjects.get(method);

        if (methodConfig.requireHttps && !req.isSecure()) {
            String redirectUrl = String.format(
                    "https://%s:%s%s",
                    req.getServerName(),
                    context.getWebResourceManager().getHttpsPort(),
                    req.getRequestURI());

            if (req.getQueryString() != null) {
                redirectUrl += "?" + req.getQueryString();
            }

            resp.sendRedirect(redirectUrl);
            return;
        }

        String dictKey = sessionKey("dict");
        PyDictionary session = (PyDictionary) req.getSession(true).getAttribute(dictKey);
        if (session == null) {
            session = new PyDictionary();
            req.getSession().setAttribute(dictKey, session);
        }

        Optional<GlobalProps> projectProps = Optional.of(context.getProjectManager()).map(pm -> {
            try {
                return pm.getProjectProps(projectName);
            } catch (ResourceCollectionNotFoundException ex) {
                return null;
            }
        });

        if (methodConfig.requireAuth) {
            AuthenticatedUser user;
            String authKey = sessionKey("user");
            user = (AuthenticatedUser) req.getSession(true).getAttribute(authKey);

            if (user == null) {
                String auth = req.getHeader("Authorization");
                if (StringUtils.isNotBlank(auth) && auth.toUpperCase().startsWith("BASIC ")) {
                    String userPassEncoded = auth.substring(6);
                    String decoded = new String(Base64.decode(userPassEncoded), StandardCharsets.UTF_8);
                    String[] split = decoded.split(":");
                    if (split.length != 2) {
                        resp.sendError(403, "Malformed auth request.");
                        return;
                    }

                    SimpleAuthChallenge challenge = new SimpleAuthChallenge(split[0], split[1]);
                    String userSource = methodConfig.userSource;
                    if (StringUtils.isBlank(userSource)) {
                        userSource = projectProps.map(GlobalProps::getAuthProfileName).orElse("");
                    }

                    if (StringUtils.isBlank(userSource)) {
                        resp.sendError(500, "No user source for project.");
                        return;
                    }

                    try {
                        var profile = context.getUserSourceManager().getProfile(userSource);
                        user = profile != null ? profile.authenticate((AuthChallenge) challenge) : null;

                        if (user == null) {
                            int retryAttempts = session.get("retryAttempts") != null
                                    ? TypeUtilities.toInteger(session.get("retryAttempts"))
                                    : 0;

                            if (retryAttempts <= methodConfig.maxRetryAttempts) {
                                resp.setHeader("WWW-Authenticate", String.format("BASIC realm=\"%s\"", path.getProject()));
                                resp.sendError(401, "Access denied.");
                                session.put(Py.java2py("retryAttempts"), Py.java2py(++retryAttempts));
                                return;
                            }

                            resp.sendError(403, "Access denied.");
                            return;
                        }

                        String roles = methodConfig.requiredRoles;
                        if (StringUtils.isNotBlank(roles)) {
                            String[] roleArray = StringUtils.split(roles, ',');
                            boolean found = false;
                            for (String role : roleArray) {
                                if (user.getRoles().contains(role.trim())) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                resp.sendError(403, "Access denied.");
                                return;
                            }
                        }
                    } catch (Exception ex) {
                        LOG.debug("Error authenticating.", ex);
                        resp.sendError(500, "Error authenticating.");
                        return;
                    }
                } else {
                    resp.setHeader("WWW-Authenticate", String.format("BASIC realm=\"%s\"", path.getProject()));
                    resp.sendError(401);
                    return;
                }
            }

            session.put(Py.java2py("user"), Py.java2py(user));
        }

        PyDictionary request = wrapRequest(req, resp);
        ScriptManager manager = context.getProjectManager().getProjectScriptManager(projectName);
        if (manager == null) {
            LOG.errorf("Unable to retrieve script manager for project %s", projectName);
            resp.sendError(500);
            return;
        }

        Thread thread = Thread.currentThread();
        ClassLoader oldLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(GatewayContext.class.getClassLoader());

        try (ScriptContext.ContextCloseable ignored = ScriptContext.push(ctx -> {
            ctx.project(projectName)
                    .description(String.format("WebDev - %s:%s:%s", path.getProject(), path.getResource(), method))
                    .tagReferenceLocation((TagReferenceLocation) new WebDevTagReferenceLocation(
                            path.getProject(),
                            path.getResource(),
                            method));
            projectProps.ifPresent(props -> ctx
                    .datasource(props.getDefaultDatasourceName())
                    .tagProvider(props.getDefaultSQLTagsProviderName()));
        })) {

            PyObject retVal = func.invoke(new PyObject[]{request, session});
            if (retVal == null || Py.None == retVal) {
                LOG.tracef("%s() returned None", toString(method));
                return;
            }

            if (!retVal.isMappingType()) {
                LOG.warnf("Return value should be a dictionary, not a: %s", retVal);
                resp.sendError(500, "Bad return value from dynamic web target.");
                return;
            }

            String filePath = extract(retVal, "file", String.class);
            if (filePath != null) {
                File file = new File(filePath);
                if (!file.exists()) {
                    resp.sendError(404, filePath);
                    return;
                }

                String contentType = extract(retVal, "contentType", String.class);
                if (contentType == null) {
                    contentType = Files.probeContentType(file.toPath());
                }

                resp.setContentType(contentType);
                resp.setContentLengthLong(file.length());
                try (FileInputStream fis = new FileInputStream(file)) {
                    IOUtils.copy(fis, resp.getOutputStream());
                }
                return;
            }

            byte[] responseBytes = extract(retVal, "bytes", byte[].class);
            if (responseBytes != null) {
                resp.setContentType(getContentType(retVal, "application/octet-stream"));
                IOUtils.write(responseBytes, resp.getOutputStream());
                return;
            }

            String html = extract(retVal, "html", String.class);
            String userType = getContentType(retVal, null);
            Charset charset = extractCharset(userType);
            resp.setCharacterEncoding(charset.name());

            try (PrintWriter writer = resp.getWriter()) {
                if (html != null) {
                    resp.setContentType(getContentType(retVal, "text/html"));
                    writer.write(html);
                    return;
                }

                if (retVal.__contains__(Py.newStringOrUnicode("json"))) {
                    PyObject json = retVal.__getitem__(Py.newStringOrUnicode("json"));
                    resp.setContentType(getContentType(retVal, "application/json"));
                    if (json instanceof PyString) {
                        writer.write(json.toString());
                    } else {
                        writer.write(SystemUtilities.jsonEncode(json));
                    }
                    return;
                }

                String response = extract(retVal, "response", String.class);
                if (response == null) {
                    resp.sendError(500, "No response from " + toString(method));
                    return;
                }

                resp.setContentType(getContentType(retVal, "text/plain"));
                writer.write(response);
            }
        } catch (Exception ex) {
            LOG.errorf("Error executing %s", toString(method), ex);
            resp.sendError(500, ex.getMessage());
        } finally {
            thread.setContextClassLoader(oldLoader);
        }
    }

    private Charset extractCharset(String contentType) {
        if (contentType != null) {
            try {
                ContentType parsedType = ContentType.parse(contentType);
                if (parsedType.getCharset() != null) {
                    return parsedType.getCharset();
                }
            } catch (ParseException | UnsupportedCharsetException ex) {
                LOG.warnf("Unable to parse provided Content-Type value, serving content as UTF-8", ex);
                return StandardCharsets.UTF_8;
            }
        }

        return StandardCharsets.UTF_8;
    }

    private static String getContentType(PyObject dict, String defaultValue) {
        if (dict.__contains__(Py.newStringOrUnicode("contentType"))) {
            return extract(dict, "contentType", String.class);
        }
        return defaultValue;
    }

    private static <T> T extract(PyObject dict, String key, Class<T> type) {
        PyString pyKey = Py.newStringOrUnicode(key);
        if (dict.__contains__(pyKey)) {
            PyObject obj = dict.__getitem__(pyKey);
            return (T) TypeUtilities.coerceNullSafe(obj.__tojava__(Object.class), type);
        }
        return null;
    }

    private PyDictionary wrapRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<PyObject, PyObject> requestMap = new HashMap<>();

        requestMap.put(Py.java2py("scheme"), Py.java2py(req.getScheme()));
        requestMap.put(Py.java2py("remoteAddr"), Py.java2py(req.getRemoteAddr()));
        requestMap.put(Py.java2py("remoteHost"), Py.java2py(req.getRemoteHost()));
        requestMap.put(Py.java2py("remainingPath"), Py.java2py(remainingPath));

        Enumeration<String> headerNames = req.getHeaderNames();
        PyDictionary headers = new PyDictionary();
        while (headerNames.hasMoreElements()) {
            String header = headerNames.nextElement();
            ArrayList<String> values = Collections.list(req.getHeaders(header));

            if (values.size() == 1) {
                headers.__setitem__(header, Py.java2py(values.get(0)));
            } else if (values.size() > 1) {
                PyObject[] pyValues = new PyObject[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    pyValues[i] = Py.java2py(values.get(i));
                }
                headers.__setitem__(header, new PyTuple(pyValues));
            }
        }
        requestMap.put(Py.java2py("headers"), headers);

        Enumeration<String> paramNames = req.getParameterNames();
        PyDictionary params = new PyDictionary();
        while (paramNames.hasMoreElements()) {
            String param = paramNames.nextElement();
            String[] values = req.getParameterValues(param);

            if (values != null && values.length == 1) {
                params.__setitem__(param, Py.java2py(values[0]));
            } else if (values != null && values.length > 1) {
                PyObject[] pyValues = new PyObject[values.length];
                for (int i = 0; i < values.length; i++) {
                    pyValues[i] = Py.java2py(values[i]);
                }
                params.__setitem__(param, new PyTuple(pyValues));
            }
        }
        requestMap.put(Py.java2py("params"), params);

        requestMap.put(Py.java2py("servletRequest"), Py.java2py(req));
        requestMap.put(Py.java2py("servletResponse"), Py.java2py(resp));
        requestMap.put(Py.java2py("context"), Py.java2py(context));

        Object data;
        String contentType = StringUtils.defaultString(req.getContentType());
        if (contentType.startsWith("application/json")) {
            String jsonString = readAll(req);
            try {
                data = SystemUtilities.jsonDecode(jsonString);
            } catch (JSONException ex) {
                LOG.warn("Unable to parse JSON POST data.", ex);
                data = jsonString;
            }
        } else if (StringUtils.isNotBlank(contentType)
                && (TEXT_TYPES.contains(contentType) || contentType.startsWith("text/"))) {
            data = readAll(req);
        } else {
            try (ServletInputStream is = req.getInputStream()) {
                data = IOUtils.toByteArray(is);
            }
        }

        if (data != null) {
            if ("POST".equalsIgnoreCase(req.getMethod())) {
                requestMap.put(Py.java2py("postData"), Py.java2py(data));
            }
            requestMap.put(Py.java2py("data"), Py.java2py(data));
        }

        return new PyDictionary(requestMap);
    }

    private static String readAll(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private String toString(String method) {
        return String.format("%s/%s:%s", path.getProject(), path.getResource(), method);
    }

    private Closeable mdcSet(String method, PathParser path) {
        MDC.put("method", method);
        MDC.put("project", path.getProject());
        MDC.put("route-path", path.getResource());
        return () -> {
            MDC.remove("method");
            MDC.remove("project");
            MDC.remove("route-path");
        };
    }

    @Override
    public void doGet(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (Closeable ignored = mdcSet("GET", path)) {
            doRequest(path, req, resp, "doGet");
        }
    }

    @Override
    public void doPost(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (Closeable ignored = mdcSet("POST", path)) {
            doRequest(path, req, resp, "doPost");
        }
    }

    @Override
    public void doPut(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (Closeable ignored = mdcSet("PUT", path)) {
            doRequest(path, req, resp, "doPut");
        }
    }

    @Override
    public void doDelete(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (Closeable ignored = mdcSet("DELETE", path)) {
            doRequest(path, req, resp, "doDelete");
        }
    }

    @Override
    public void doHead(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (Closeable ignored = mdcSet("HEAD", path)) {
            doRequest(path, req, resp, "doHead");
        }
    }

    @Override
    public void doOptions(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (Closeable ignored = mdcSet("OPTIONS", path)) {
            doRequest(path, req, resp, "doOptions");
        }
    }

    @Override
    public void doTrace(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (Closeable ignored = mdcSet("TRACE", path)) {
            doRequest(path, req, resp, "doTrace");
        }
    }

    @Override
    public void doPatch(PathParser path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (Closeable ignored = mdcSet("PATCH", path)) {
            doRequest(path, req, resp, "doPatch");
        }
    }
}
