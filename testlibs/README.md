# Test-only libraries

Files here are on the JVM test classpath only. They are never packaged into the APK and never staged under `libs/`,
which holds the host contract AARs of `locks/host-api-aars.lock`.

## Rhino for the JVM tests (lock `../locks/rhino-jar.lock`)

`org-mozilla-rhino-2_0_0-SNAPSHOT.jar` (3,547,677 B, SHA-256
`af6eb5bbeac672ad24f85d61d0e12e98247c8cb761194d6c3ebead1e3f5886b2`) is a byte-identical copy of the host's own
`libs/org-mozilla-rhino-2_0_0-SNAPSHOT.jar` (the AutoJs6 fork of Rhino, MPL-2.0), which last changed in host commit
`ac71b91fd3a4500c5f982569623dfea69728e5ca` (2026-11-17). It was staged on 2026-11-20 for roadmap P6.13: the JVM test
`ScriptGeneratorRhinoTest` compiles every generated Rhino script with it, in interpreted mode like the host, runs the
script against recording stubs of the `pdf` module and compares the recorded arguments with the tool request.

To update: copy the host's current `libs/org-mozilla-rhino-2_0_0-SNAPSHOT.jar` here, then set `rhino.sha256` and
`rhino.source` (the full hash of the last host commit that changed the jar, `git log -1 --format=%H -- <jar>`) in
`locks/rhino-jar.lock`. The build checks the hash before it compiles the tests.
