---
name: kmp-lsp
description: 'Kotlin/Java/Swift code navigation for this repo via the kmp-lsp CLI (no JVM, no Gradle sync). Use instead of Grep when finding a declaration, checking references, or getting type/signature info for a symbol in sharedLogic/sharedUI/androidApp/iosApp.'
metadata:
  version: "0.26.0"
---

# kmp-lsp — CLI code navigation for hikyaku-mobile

This repo pins CLAUDE.md's "prefer LSP over Grep" rule to the `kmp-lsp` CLI binary
(there is no editor/MCP `lsp` tool wired into Claude Code here — invoke it via the shell).

## Prerequisites

- `kmp-lsp` on `PATH` — see the [install methods](https://github.com/Hessesian/kmp-lsp#install)
  for your OS (cargo, cargo-binstall, the one-liner installers, mise/aqua). Verify with
  `kmp-lsp --version`.
- `ripgrep` (`rg`) on `PATH` — required for `refs` and `--fast` mode. Verify with `rg --version`.
  Without it, `refs` silently returns `No references found` instead of erroring — see the caveat
  below before concluding a symbol truly has no references.
- Optionally `fd`/`fdfind` for faster file discovery (falls back to a slower walk otherwise).

None of this is committed to the repo or specific to any one machine — each developer installs
these locally per the README, on whatever OS they're on (Windows, macOS, Linux all supported).

## Commands

```
kmp-lsp find <Name> --root .                       # locate a declaration (file:line:col)
kmp-lsp refs <Name> --exclude-imports --root .      # find references — SEE CAVEAT BELOW
kmp-lsp hover <file> <line> <col> --root .          # signature / doc info at a position
kmp-lsp complete <file> <line> [col] --dot --root . # completion candidates
kmp-lsp check <file|dir>                            # instant syntax check, no index needed
kmp-lsp diagnose <file> --root .                    # call-arg / missing-import diagnostics
kmp-lsp index --root .                              # build/refresh the cache (run after large refactors)
kmp-lsp sources --root .                            # list resolved source roots
kmp-lsp extract-sources <pattern...> --dry-run       # materialize Gradle *-sources.jar into ~/.kmp-lsp/sources
```

`find`/`refs` accept a bare name (`ScanPackagesOverlay`) or dotted FQN — both work. Run from the
repo root, or pass `--root <path-to-hikyaku-mobile>`. This repo has 10 source roots across
`androidApp`, `sharedLogic/{androidMain,commonMain,iosMain,*Test}`, and
`sharedUI/{androidMain,commonMain,commonTest}` — `kmp-lsp sources --root .` lists them.

##️ If `refs` comes back empty, verify `rg` first — don't trust it blindly

`refs` shells out to `rg` internally. If the `rg` it finds is missing, broken, or an unexpected
version (e.g. a stale install from an old `PATH` entry shadowing the one you think is active), it
returns a plain `No references found` — not an error — indistinguishable from a real empty result.
This has produced false negatives even for symbols with confirmed real usages, in shell sessions
where `PATH` had been patched together by hand across several commands. Verified working correctly
from both PowerShell and a normal Git Bash (MINGW64) terminal, so it isn't tied to a particular
shell — the actual cause is whatever `rg` that specific session's `PATH` resolves to.

Before trusting an empty `refs` result: run `rg --version` in the *exact same shell*, and if you've
been exporting/editing `PATH` manually earlier in that session, open a fresh shell and retry rather
than layering more `PATH` edits on top. `find` (pure index lookup, no `rg` subprocess) isn't
affected by any of this.

## If `find`/`hover` come back empty for a third-party symbol — don't extract the compiled jar

`find`/`hover` resolve library symbols from `-sources.jar` files that `kmp-lsp extract-sources` has
already unpacked into `~/.kmp-lsp/sources`. Two things commonly make that step come up empty, and
neither is a reason to unzip the compiled `.jar`/`.aar` by hand (that gives you decompiled bytecode,
not real source, and CLAUDE.md's Null Safety / doc conventions won't be visible in it anyway):

1. **The sources jar was never downloaded.** Gradle only fetches the compiled `.jar` for a
   dependency by default — `-sources.jar` is a separate classified artifact it does *not* pull down
   on a normal build/sync. Check first:
   `find "$USERPROFILE/.gradle/caches/modules-2/files-2.1/<group>/<artifact>" -iname "*sources*.jar"`
   (or `Get-ChildItem -Recurse -Filter *sources*.jar` in PowerShell). If nothing's there, force a
   download with a throwaway detached configuration via an init script (doesn't touch the project's
   own build files, so nothing to revert):

   ```kotlin
   gradle.rootProject {
       tasks.register("downloadSources") {
           doLast {
               val coords = listOf("<group>:<artifact>:<version>") // one entry per dependency
               val cfg = configurations.detachedConfiguration(
                   *coords.map { dependencies.create("$it:sources") }.toTypedArray()
               )
               cfg.isTransitive = false
               cfg.resolve().forEach { println("Resolved: $it") }
           }
       }
   }
   ```

   Then: `./gradlew --init-script <path-to-script> downloadSources`. Get the exact
   `group:artifact:version` from the jar already sitting in the Gradle cache (or from
   `gradle/libs.versions.toml` — note KMP artifacts publish per-target, e.g.
   `io.github.jan-tennert.supabase:auth-kt-jvm:3.8.0`, not the bare module name from the catalog).
   Confirm the sources artifact actually exists upstream first if unsure — e.g.
   `curl -I https://repo1.maven.org/maven2/<group-path>/<artifact>/<version>/<artifact>-<version>-sources.jar`
   should return `200`.

   (The classic Gradle `idea` plugin's `downloadSources = true` + `ideaModule` task, which is the
   usual trick for plain Java projects, does **not** work here — it doesn't understand Kotlin
   Multiplatform source sets and silently fetches nothing for KMP dependencies.)

2. **`kmp-lsp extract-sources` hasn't been run since the jar landed in the cache.** Once the
   `-sources.jar` exists in `~/.gradle/caches`, run
   `kmp-lsp extract-sources <pattern> --dry-run` to confirm it's found, then drop `--dry-run` to
   unpack it into `~/.kmp-lsp/sources`. `find`/`hover` pick it up on the next invocation — verify
   with `kmp-lsp find <SomeTypeFromThatLibrary> --root .` and confirm the result path is under
   `~/.kmp-lsp/sources/...`, not a manually-extracted temp dir.

## What's NOT exposed here

- No `goToImplementation` / `rename` / `workspaceSymbol` / `documentSymbol` CLI subcommands exist —
  those are LSP-protocol-only features (see `kmp-lsp --help`); this repo has no editor/MCP LSP client
  wired up, so they aren't reachable from Claude Code. `find` covers most goToDefinition-style needs.
