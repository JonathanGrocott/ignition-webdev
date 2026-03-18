# Ignition Web Dev Compatibility Module (8.3)

This repository builds a multi-scope Ignition module that directly replaces the built-in Web Dev servlet path at:

- `/system/webdev/*`

Current scope supports existing Web Dev project resources stored under:

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
- Resource type routing for:
  - `python-resource`
  - `file-resource`
  - `text-resource`
  - `mounted-folder-resource`
- File and mounted resources support byte ranges (`Range`) including open-ended, suffix, and multi-part requests.
- Static resources support `HEAD`; mounted-folder resources also emit `Last-Modified` and handle `If-Modified-Since` (`304`).
- Mounted-folder handler includes normalized-path and real-path checks to block traversal outside the mounted root.
- Per-method Python compilation from config + source files
- Legacy request dictionary keys (`scheme`, `remoteAddr`, `remoteHost`, `remainingPath`, `headers`, `params`, `servletRequest`, `servletResponse`, `context`, `data`, `postData`)
- Per-session `session` dict under `webdev-{project}-dict`
- Per-method auth settings (`require-auth`, `user-source`, `required-roles`, `max-retry-attempts`) with Basic auth challenge and role filtering
- Per-method HTTPS redirect (`require-https`)
- Response mapping keys (`file`, `bytes`, `html`, `json`, `response`, optional `contentType`)
- ScriptContext metadata with project, datasource/tag defaults, and tag reference location
- Legacy `data.bin` fallback deserialization path for Python resources
- Legacy `data.bin` fallback deserialization path for file/text/mounted resources

## Designer integration

- Resource workspace bound to `com.inductiveautomation.webdev/resources`
- Editors for:
  - Python resources (method selector + per-method auth/HTTPS config + scripts)
  - Text resources (`text`, `content-type`)
  - File resources (`file.bin`, `content-type`)
  - Mounted folder resources (`folder-path`)
- Python method editor includes:
  - method picker
  - per-method script editor
  - per-method settings: enabled, require HTTPS, require auth, user source, required roles, max retries
- Save format writes:
  - `config.json`
  - `doX.py` files (with `def doX(request, session):` wrappers)
- Find/replace provider indexes Python method bodies and opens the resource at the specific method location.
- Find/replace provider also indexes text content, content-type fields, and mounted folder paths.

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

## Live smoke test helper

Use the included script to validate an endpoint against a running gateway:

- `scripts/smoke_webdev.sh <gateway_base_url> <project> <resource_path> <method> <expected_status> [body_file] [basic_user] [basic_pass]`

Example:

- `scripts/smoke_webdev.sh http://localhost:8088 i3x_2026-03-06_1542 namespaces GET 200`

## Phase 2 live parity project (file/text/mounted)

This repo includes a ready-to-import project template plus mounted test content:

- `fixtures/live-parity/project-template`
- `fixtures/live-parity/mounted-content`

Prepare a local importable project with the mounted path injected:

- `scripts/prepare_live_parity_project.sh`

Then import the generated project folder into Ignition and run live parity checks:

- `scripts/parity_phase2_live.sh http://localhost:8088 webcompat_live_parity`

## License

MIT. See `LICENSE`.

## Release Process

See `RELEASE_CHECKLIST.md` for preflight checks, parity validation, and the tag/publish sequence.
