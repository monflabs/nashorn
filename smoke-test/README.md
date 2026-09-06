# Smoke test — verifying the published jars

A small, **independent** Maven project (no `<parent>`, not a reactor module) that
depends on the released artifacts

- `org.monflabs.nashorn:nashorn-core`
- `org.monflabs.nashorn:nashorn-debugger`
- `org.monflabs.nashorn:nashorn-node`

and checks they resolve from your **local repository (`~/.m2`)** and actually
work: the engine runs ES2017, the host/fetch libraries install through the
builder, the Node `path`/`os`/`buffer` modules resolve, and the debugger attaches.

## Run it

First install the jars into `~/.m2` from the repository root (or after a real
release, they come from Maven Central):

```bash
cd ..
mvn -DskipTests install          # puts nashorn-* 2021.0.0 in ~/.m2
```

Then, from this folder:

```bash
mvn test
```

Green means the published jars load and run as a downstream consumer sees them.

## Notes

- It reads `nashorn.version` (default `2021.0.0`) from its own `pom.xml`; bump it
  to test a different release.
- It uses the **class path** (the common consumption path). A modular consumer
  would put the jars on the module path instead; the APIs used here work either way.
- This project is deliberately outside the reactor, so `mvn` at the repo root
  never builds it — run it here, by hand, against installed/released artifacts.
