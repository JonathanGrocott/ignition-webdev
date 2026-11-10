# WebDevCompat for Ignition 8.3

WebDevCompat serves project files and mounted folders, runs Python HTTP endpoints, and can receive HTTP requests for Event Streams. Use Ignition Designer to create and edit resources.

## Install and verify

1. In the Gateway, open **Platform > System > Modules**, select **Install or Upgrade Module**, and choose the signed `webdev-compat.modl` file.
2. Review the signing certificate if prompted, complete the installation, and verify that WebDevCompat is active.
3. In Designer, open a project and create a Text Resource named `health.txt` under **Web Dev**. Set its content type to `text/plain`, enter `OK`, and save the project.
4. Open `/system/webdev/{project}/health.txt` on the Gateway or select **Tools > Launch > Launch Resource** with the resource open.

WebDevCompat registers `/system/webdev/*`. With Event Streams installed and enabled, it also registers `/system/eventstream/*`. Ensure the applicable routes are available before enabling WebDevCompat.

## Runtime and resource formats

- Servlet key: `webdev` (registered via `WebResourceManager.addServlet`)
- URL surface: `GET|POST|PUT|DELETE|HEAD|OPTIONS|TRACE|PATCH /system/webdev/{project}/{resource...}`
- Python resource files: `config.json` and `doX.py` (`doGet` through `doPatch`)
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
- Uploaded files also coalesce overlapping ranges and honor the legacy `Request-Range` multipart content type.
- Static resources support `HEAD`; mounted-folder resources also emit `Last-Modified` and handle `If-Modified-Since` (`304`).
- Mounted directories with an `index.html` redirect to a URL ending in `/` so relative links resolve inside the directory.
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

- The Web Dev workspace manages resources within the current project.
- Launch Resource opens the selected resource in a browser. Copy Mounted Path copies its server-relative URL from the project tree.
- Import Text Resource reads a UTF-8 file into an editable text resource. File and text editors offer MIME presets. The file editor previews supported images and renames a resource when its replacement has a different filename.
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

## Event Streams

With the optional Event Streams module installed and enabled, create a stream in Designer and select **HTTP Endpoint** as its source. Configure HTTPS and authentication, then save the stream. Send a POST or PUT request to `/system/eventstream/{project}/{resourcePath}`. The source forwards the request body and metadata to the stream and supports user sources, required roles, and retry limits.

## Build and test

From repo root:

- `./gradlew test`
- `./gradlew build`
- `./scripts/sign-module.sh`

Module artifact:

- unsigned intermediate: `webcompat-build/build/webdev-compat.unsigned.modl`
- signed module: `webcompat-build/build/webdev-compat.modl`

If signing material does not exist yet, `./scripts/sign-module.sh` creates a simple self-signed certificate under `certs/`; those files are ignored by git. Keep the same generated files if you want future builds to use the same certificate, so Ignition users only need to accept that certificate once.

Verify the module contains the signing payload:

- `unzip -l webcompat-build/build/webdev-compat.modl | grep -E 'certificates.p7b|signatures.properties'`

## Live smoke test helper

Use the included script to validate an endpoint against a running gateway:

- `scripts/smoke_webdev.sh <gateway_base_url> <project> <resource_path> <method> <expected_status> [body_file] [basic_user] [basic_pass]`

Example:

- `scripts/smoke_webdev.sh http://localhost:8088 myProject health.txt GET 200`

## Hosted-file smoke test

This repo includes a ready-to-import project template plus mounted test content:

- `fixtures/live-parity/project-template`
- `fixtures/live-parity/mounted-content`

Prepare a local importable project with the mounted path injected:

- `scripts/prepare_live_parity_project.sh`

Then import the generated project folder into Ignition and run the smoke test.

- `scripts/parity_phase2_live.sh http://localhost:8088 webcompat_live_parity`

The live check uses `curl` and Python 3 to follow the mounted directory redirect and fetch the relative asset linked from the index page.

## License

MIT. See `LICENSE`.

## Release Process

See [the release checklist](RELEASE_CHECKLIST.md) for build, verification, and publishing steps.
