# Ignition Web Dev Compatibility Module (8.3)

This repository builds a multi-scope Ignition module that directly replaces the built-in Web Dev servlet path at:

- `/system/webdev/*`

V1 scope is **Python-resource parity first** for existing Web Dev project resources stored under:

- `com.inductiveautomation.webdev/resources`

## Compatibility Contract

- Servlet key: `webdev` (registered via `WebResourceManager.addServlet`)
- URL surface: `GET|POST|PUT|DELETE|HEAD|OPTIONS|TRACE|PATCH /system/webdev/{project}/{resource...}`
- Resource format: `config.json` + `doX.py` files (`doGet` ... `doPatch`)
- Python signature: `def doX(request, session): ...`

### Runtime behavior implemented

- Root path fallback to `index.html`
- Parent-path fallback with `remainingPath`
- Project dispatcher cache with invalidation on project collection updates
- Per-method Python compilation from config + source files
- Legacy request dictionary keys (`scheme`, `remoteAddr`, `remoteHost`, `remainingPath`, `headers`, `params`, `servletRequest`, `servletResponse`, `context`, `data`, `postData`)
- Per-session `session` dict under `webdev-{project}-dict`
- Per-method auth settings (`require-auth`, `user-source`, `required-roles`, `max-retry-attempts`) with Basic auth challenge and role filtering
- Per-method HTTPS redirect (`require-https`)
- Response mapping keys (`file`, `bytes`, `html`, `json`, `response`, optional `contentType`)
- ScriptContext metadata with project, datasource/tag defaults, and tag reference location
- Legacy `data.bin` fallback deserialization path for Python resources

## Designer integration

- Resource workspace bound to `com.inductiveautomation.webdev/resources`
- Python method editor with:
  - method picker
  - per-method script editor
  - per-method settings: enabled, require HTTPS, require auth, user source, required roles, max retries
- Save format writes:
  - `config.json`
  - `doX.py` files (with `def doX(request, session):` wrappers)
- Find/replace provider indexes Python method bodies and opens the resource at the specific method location.

## Important rollout rule

This module intentionally owns `/system/webdev/*`.

- **Disable/uninstall the built-in Web Dev module before enabling this module in production**.
- Running both modules together causes path conflicts by design.

## Build and test

From repo root:

- `./gradlew test`
- `./gradlew build`

Module artifact:

- `webcompat-build/build/webdev-compat.unsigned.modl`
