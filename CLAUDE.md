# CLAUDE.md

## API Contracts

### Swagger Docs
Generate API calls using gradle's swagger plugin instead of hand wiring. 

## Coding Style

### Null Safety
Frequent use of !! usually reveals bad state management or poor API design. 

Instead of forcing a value to be non-null, provide a fallback safe default value

```
val nonNullData = checkNotNull(data) { "Initialization failed: Data packet cannot be null." }
val id = nonNullData.id
```

DO NOT use !!

```
val id = data!!.id
```

### Code Intelligence

Prefer LSP over Grep/Read for code navigation — it's faster, precise, and avoids reading entire files:
- `workspaceSymbol` to find where something is defined
- `findReferences` to see all usages across the codebase
- `goToDefinition` / `goToImplementation` to jump to source
- `hover` for type info without reading the file

Use Grep only when LSP isn't available or for text/pattern searches (comments, strings, config).

After writing or editing code, check LSP diagnostics and fix errors before proceeding.


Prefer Java Docs MCP over Grep/Read for versioned documentation for the artifact. 

