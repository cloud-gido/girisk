# Disclaimer

## Software warranty

GiRisk is provided under the Apache License 2.0 **on an "AS IS" BASIS**,
without warranties or conditions of any kind. See [LICENSE](LICENSE) §§7–8.

## Not legal, compliance, or licensing advice

This software implements **technical risk controls** (stake limits, exposure
estimation, decision logging). It does **not**:

- constitute legal, regulatory, tax, or gambling-licence advice;
- certify that a deployment is lawful in any jurisdiction;
- replace responsible gaming, KYC/AML, sanctions screening, or operator
  licence obligations.

Operators and integrators remain solely responsible for obtaining all
required licences and for complying with applicable laws and regulations
where they offer services.

## No facilitation of illegal gambling

Contributors and copyright holders do not intend this project to be used to
operate gambling services in jurisdictions where such activity is unlawful.
You must ensure your use is lawful before deploying GiRisk in production.

## Demo data and credentials

Sample orders, default passwords (`admin` / `admin123`, etc.), and local
demo profiles are for **development and demonstration only**. Do not expose
them on public networks. Rotate secrets before any shared or production use.

## Decision authority

Production sports bet decisions are intended to be authoritative in
**GiRisk Engine** (Flink). Console HTTP decide paths and offline replays are
for ops, audit, and local demos unless you explicitly configure otherwise.
Misconfiguration can cause financial loss; validate thoroughly before go-live.
