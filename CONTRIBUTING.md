# Contributing to GiRisk

Thanks for your interest in contributing. This project follows **Apache-style**
open-source practices (Apache License 2.0 + DCO). It is not necessarily an
Apache Software Foundation project; see [NOTICE](NOTICE).

Please also read:

- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
- [DISCLAIMER.md](DISCLAIMER.md)
- [SECURITY.md](SECURITY.md) (for vulnerabilities)
- [docs/COMPLIANCE.md](docs/COMPLIANCE.md)
- [docs/GOVERNANCE.md](docs/GOVERNANCE.md)

## Developer Certificate of Origin (DCO)

By contributing, you certify that each commit is covered by the
[Developer Certificate of Origin](https://developercertificate.org/) v1.1.

**Sign off every commit:**

```bash
git commit -s -m "feat: short description"
```

This adds a `Signed-off-by: Your Name <email@example.com>` trailer. Pull
requests without DCO sign-off may be rejected.

We do **not** currently require a separate CLA. If that changes, it will be
announced in GOVERNANCE.md.

## How to contribute

1. Open an Issue describing the bug or proposal (unless trivial docs fix).
2. Fork and create a feature branch from `main`.
3. Keep changes focused; match existing code style.
4. Add / update tests when changing Engine gates or Console APIs.
5. Ensure builds pass locally:

   ```bash
   mvn -pl girisk-console,girisk-engine -am -DskipTests package
   cd girisk-console/frontend && npm run build
   ```

6. Open a Pull Request using the template; link related Issues.

## Source file license headers

New or substantially modified source files should include the Apache-2.0
boilerplate header. Templates:

- Java: [docs/legal/header-java.txt](docs/legal/header-java.txt)
- TypeScript / TSX: [docs/legal/header-ts.txt](docs/legal/header-ts.txt)

Do **not** add personal copyright lines in file headers; attribution belongs
in git history and [NOTICE](NOTICE).

## What we will not accept

- Exploit PoCs targeting third-party production systems
- Hard-coded secrets, production credentials, or customer PII
- Features whose primary purpose is to evade gambling regulation or AML/KYC
- Large unrelated refactors bundled with feature PRs

## Commit message style

Prefer short, imperative subjects:

```text
feat(console): allow league-level limit overrides
fix(engine): honor seed when group is cold-started
docs: add Apache-style compliance checklist
```

## Questions

Use GitHub Discussions / Issues for product and design questions. For security,
follow [SECURITY.md](SECURITY.md).
