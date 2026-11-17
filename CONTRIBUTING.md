# Contributing

Thanks for helping. Before a pull request:

- `./gradlew assembleDebug lintDebug` passes, and `scripts/test.sh` on a
  phone and a tablet emulator (see `README.md`).
- A change to what the user sees comes with a screenshot.
- Commit subjects follow conventional commits, short: `fix(sync): …`,
  `feat(sidebar): …`.
- The app never writes the chain's sidebar, never lists tabs of private
  spaces for other devices, and contacts a site only when one of its pages
  is opened. A change must keep that true.
- What both apps share lives in
  [magnetite-mobile-core](https://github.com/magnetite-browser/magnetite-mobile-core);
  the iOS app is [magnetite-ios](https://github.com/magnetite-browser/magnetite-ios).
  A feature should work the same way on both.
- No telemetry, no third-party services, no account. A new dependency needs
  a reason in the pull request.
- Security issues go to security@magnetitebrowser.org, not to public issues.

`README.md` has the rest: how to build and how to run the tests.

CI builds the app with the core and runs Android's lint for every pull request.
CodeRabbit reviews pull requests automatically (`.coderabbit.yaml`);
maintainers decide what gets merged.

Contributions come in under the EUPL-1.2 (inbound = outbound); you keep your
copyright. There is no CLA.
