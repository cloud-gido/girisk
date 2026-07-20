# Legal & compliance checklist (Apache-style)

GiRisk aims to operate like a mature Apache-licensed open-source project
(process-wise). Use this checklist before publishing the repository or cutting
a release.

## 1. License stack (required)

| Artifact | Status |
|----------|--------|
| Root [LICENSE](../LICENSE) — Apache License 2.0 full text | Required |
| Root [NOTICE](../NOTICE) — attribution / trademarks | Required |
| [DISCLAIMER.md](../DISCLAIMER.md) — regulatory & AS-IS | Required for this domain |
| Source headers on **new** files (see `docs/legal/`) | Required going forward |
| Retroactive headers on entire history | Optional / gradual |

**Not ASF unless donated:** do not claim “Apache GiRisk” or ASF endorsement
without a formal ASF incubation / donation process.

## 2. Contribution legality

| Control | Mechanism |
|---------|-----------|
| Origin of code | DCO sign-off on every commit ([CONTRIBUTING.md](../CONTRIBUTING.md)) |
| Conduct | [CODE_OF_CONDUCT.md](../CODE_OF_CONDUCT.md) |
| Security reports | [SECURITY.md](../SECURITY.md) private channel |
| No secrets in git | `.gitignore` + PR review; rotate if leaked |

Optional later: Corporate CLA / ICLA if a foundation or company requires it.

## 3. Dependency / third-party license review

Before each release:

1. `mvn -pl girisk-console,girisk-engine -am dependency:list` — flag GPL/AGPL（公开同步前确认 Engine 未进 GitHub）
   or unknown licenses if you redistribute binaries.
2. `cd girisk-console/frontend && npm ls` — same for npm.
3. Prefer Apache-2.0 / MIT / BSD. Avoid bundling GPL into releases without legal review.
4. Only put **bundled** third-party notices into `NOTICE` / `LICENSE` appendices;
   Maven/npm resolved deps are not “bundled” in the source tree.

## 4. Privacy & data

- Do not commit real customer betting data, PII, or production order dumps.
- Demo CSVs (e.g. Germany vs Paraguay) must be synthetic or rights-cleared.
- Logs may contain order IDs / user IDs — document retention for deployers.

## 5. Regulated-industry (gambling) compliance

Engineering cannot satisfy licence law alone. Deployers must address:

- Local gambling / gaming licence
- KYC / AML / sanctions
- Responsible gaming and advertising rules
- Cross-border offering restrictions

Point readers to [DISCLAIMER.md](../DISCLAIMER.md). Do not market GiRisk as
“licence compliant” or “regulator approved.”

## 6. Security hygiene before going public

- [ ] Change default JWT secret / internal API key documentation to placeholders
- [ ] Confirm no production Kafka bootstrap or passwords in tracked files
- [ ] Demo accounts documented as non-production
- [ ] `SECURITY.md` reporting path works (GitHub advisories enabled recommended)

## 7. Export / sanctions awareness

Encryption and dual-use rules rarely block this stack, but distributors should
still follow their jurisdiction’s export and sanctions rules when shipping
binaries to restricted parties.

## 8. Release gate (maintainers)

- [ ] `LICENSE` + `NOTICE` unchanged or correctly updated
- [ ] Changelog lists breaking Kafka/API changes
- [ ] No known critical CVEs in locked dependency versions (best effort)
- [ ] Tag signed if the project adopts signing (`git tag -s` / sigstore)

## 9. If pursuing true ASF donation later

Additional ASF steps (not done here): ICLA/CCLA on file, IP clearance,
incubation, mailing lists, release voting, `NOTICE` ASF attribution line,
distribution via ASF infra. Track separately; do not pretend ASF status early.
