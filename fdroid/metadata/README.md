# Per-app metadata

Put each app's F-Droid metadata YAML here, named `<package-id>.yml`
(for example `org.hikyaku.mobile.yml`). CI copies this directory into the
indexer's `data/metadata/` before every run, so edits here are what the
F-Droid client shows to users - see section 5 of [FDROID.md](../../FDROID.md).

The indexer creates a blank file here the first time it sees a new package
(via `fdroid update --create-metadata`), but only inside its own working
copy, not in this git-tracked directory. To pick up a first-run default, run
the indexer locally once (`docker compose up -d` in `fdroid/`), copy the
generated `data/metadata/<package-id>.yml` here, fill in the fields, and
commit it. After that, CI preserves your edits on every push - it never
overwrites a file that already exists here.
