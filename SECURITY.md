# Security Policy

## Supported versions

| Version / branch | Security fixes |
|------------------|----------------|
| `main` (latest)  | Yes            |
| Older tags       | Best effort    |

## Reporting a vulnerability

**Do not** open a public GitHub Issue for security vulnerabilities.

Please report privately:

1. Prefer GitHub **Security Advisories** → *Report a vulnerability* on this repository (if enabled); or
2. Email the maintainers listed in [GOVERNANCE.md](docs/GOVERNANCE.md) with subject `[GiRisk SECURITY]`.

Include:

- Affected component (`girisk-console` / `girisk-engine` / `girisk-common`)
- Description and impact (auth bypass, injection, data leak, DoS, etc.)
- Reproduction steps or PoC (non-destructive preferred)
- Your contact for follow-up

We aim to acknowledge within **72 hours** and to provide a remediation plan or fix timeline within **14 days** for confirmed issues.

## Scope (examples)

In scope:

- Authentication / authorization flaws on Console APIs
- Injection into decision or config paths
- Secrets leakage via logs, default configs, or demo endpoints
- Privilege escalation across tenant / operator boundaries (when enabled)

Out of scope (unless chained to a real bug):

- Denial of service via legitimate high-volume betting traffic without a clear amplification bug
- Issues solely in third-party dependencies (please report upstream; we will bump versions when feasible)
- Misconfiguration of Kafka / Redis / Flink by deployers

## Hardening expectations for deployers

- Change all default JWT secrets, API keys, and passwords
- Disable demo / exposure-demo profiles in production
- Restrict Kafka, Redis, and admin UI to private networks
- Enable TLS and proper authn/z at the edge
- Do not commit `.env`, keystores, or production `kafka-client.properties`

See also [DISCLAIMER.md](DISCLAIMER.md) and [docs/COMPLIANCE.md](docs/COMPLIANCE.md).
