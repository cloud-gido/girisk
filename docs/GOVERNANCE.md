# GiRisk Governance (lightweight, Apache-style)

This document describes how decisions are made while the project is run as an
open-source community project under the Apache License 2.0. It is inspired by
ASF practices but **does not** imply ASF membership.

## Roles

| Role | Rights |
|------|--------|
| **Contributor** | Opens Issues / PRs; must DCO sign-off |
| **Committer** | Merge rights to `main`; reviews PRs |
| **Maintainer** (PMC-like) | Releases, security triage, governance changes |

Initial maintainers: list repository owners / org admins on GitHub until a
`MAINTAINERS` file is published.

## Decision process

- Day-to-day: lazy consensus on PRs (approve + no sustained objection).
- Contested design: discuss in Issue; maintainers decide with rationale.
- Breaking public Kafka / API contracts: require Issue + explicit maintainer ACK.
- License / trademark / security policy changes: maintainer vote (majority).

## Releases

1. Version bump + changelog (`CHANGELOG.md` or GitHub Release notes).
2. `LICENSE` + `NOTICE` present at repo root (and in release artifacts).
3. Tag `vX.Y.Z`; attach console jar / engine jar checksums when publishing binaries.
4. Do not ship demo passwords as production defaults in release notes without a warning.

## Code of Conduct

Enforcement contacts: repository Maintainers (private email or GitHub security
advisory). See [CODE_OF_CONDUCT.md](../CODE_OF_CONDUCT.md).

## Trademark

See [NOTICE](../NOTICE). Downstream forks should rename if they imply official
GiRisk / gido affiliation without permission.
