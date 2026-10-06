# HeadlessMc Web

A web GUI for [HeadlessMc](https://github.com/headlesshq/headlessmc): a Quarkus server that runs HeadlessMc
in-process and a Vue + TypeScript frontend that exposes everything the HeadlessMc commands can do,
plus a console with tab completion.

> [!WARNING]
> The web UI can execute any HeadlessMc command and launch processes. It only listens on `127.0.0.1` by default
> (`quarkus.http.host`), do not expose it without putting authentication in front of it.

## Getting started

```shell
git clone --recurse-submodules <this repository>
# or, in an existing clone: git submodule update --init

cd src/main/webui && npm install && cd -
./gradlew quarkusDev        # http://localhost:8080, hot reload for backend and frontend
```

Production build: `./gradlew quarkusBuild` (also builds the frontend), then
`java -jar build/quarkus-app/quarkus-run.jar`.

In dev mode HeadlessMc places its files in `./HeadlessMC` (`%dev.hmc.files.location`), otherwise the usual
HeadlessMc locations are used. All HeadlessMc config properties (`hmc.*`) work as system properties,
environment variables or in `application.properties`.

## Features

| Page         | What it does                                                                                   |
|--------------|------------------------------------------------------------------------------------------------|
| Dashboard    | Directories, selected account, launching anything (`launch`), running processes, recent commands |
| Accounts     | Log in (Microsoft device code, credentials, offline), select, refresh, remove                    |
| Versions     | Installed versions, install versions, browse remote versions, create profiles                   |
| Profiles     | Add, edit, launch and remove profiles, list their mods                                           |
| Servers      | Add, launch and remove servers, read/accept the EULA                                             |
| Mods         | Search, add, list and remove mods/resource packs/shaders/datapacks, list worlds                  |
| Java         | Installed Java runtimes, install from distributions, remove                                      |
| Config       | View and edit all HeadlessMc config properties                                                  |
| Processes    | Live output of launched clients/servers, send stdin (server commands), stop/kill                 |
| All commands | A form for every HeadlessMc command and option, generated from the picocli model                 |
| Console      | A HeadlessMc shell: tab completion, history, colored output, answering prompts                   |

Commands that ask for input (logins, `config set` without a value, `profile edit`, ...) show a dialog,
or prompt inline in the console. Download progress is shown in the bottom right corner.

## How it works

The web app consumes the `headlessmc/` git submodule as a Gradle composite build (`includeBuild 'headlessmc'`),
so changes to HeadlessMc are picked up directly. No changes to HeadlessMc were necessary,
the web layer plugs into HeadlessMc's CDI extension points:

* **Commands** run on a single worker thread (HeadlessMc is a single user shell), as *jobs*
  (`/api/jobs`, `JobService`). Every GUI action executes a HeadlessMc command line, so the GUI behaves exactly
  like the command line.
* **Console**: `WebConsoleProvider` is a HeadlessMc `ConsoleProvider` with the highest priority. While a job
  runs, everything HeadlessMc writes goes to the job and the browser, and reads (`read`, `readPassword`,
  `ConsoleExtensions.edit`) become prompts in the browser.
* **Logs**: HeadlessMc's log records are forwarded to the running job (`WebLogHandler`).
* **Progress bars**: `WebProgressbarService` is a HeadlessMc `ProgressbarService`.
* **Launching**: `WebLifecycleService` replaces HeadlessMc's `LifecycleService` (CDI alternative). Clients and
  servers are launched with piped IO and handed to the `GameProcessManager` instead of blocking HeadlessMc,
  their output is streamed to the browser and input can be sent to them.
* **Completions** use HeadlessMc's own picocli completion code (`PicocliCompletions`, `/api/complete`).
* **Command model**: `/api/commands` describes the picocli command tree, the frontend generates forms from it.
* Live updates are pushed to the browser over a websocket (`/ws/events`).

## Tests

```shell
./gradlew test                       # backend (Quarkus tests)
cd src/main/webui
npm test                             # frontend unit/component tests
npm run typecheck
HMC_BACKEND=http://localhost:8080 npm run test:integration   # against a running backend
```

## Notes

* `quarkus-quinoa` is pinned to 2.8.2, newer versions require a newer Quarkus than HeadlessMc's (3.35.3).
* Keep `quarkusVersion` in `gradle.properties` aligned with `headlessmc/gradle/libs.versions.toml`.
* `build.gradle` adds the extensions' conditional dev dependencies to the `quarkusDev` configuration,
  the Quarkus Gradle plugin drops them in this composite build and `quarkusDev` would fail otherwise.
