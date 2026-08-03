#!/usr/bin/env python3
"""Greenfield rename: risk-platform / com.riskplatform → GiRisk technical IDs."""
from __future__ import annotations

import os
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SKIP_DIRS = {
    "node_modules",
    "target",
    ".git",
    "dist",
    "logs",
    ".idea",
    ".cursor",
}

# Longer / more specific first.
TEXT_REPLACEMENTS: list[tuple[str, str]] = [
    # packages & main class
    ("com.riskplatform", "com.girisk"),
    ("RiskPlatformApplication", "GiRiskApplication"),
    # topics
    ("gameline.risk.decision.v1", "girisk.decision.v1"),
    ("gameline.risk.config.v1", "girisk.config.v1"),
    ("gameline.trading.order.risk-check.post.v1", "girisk.trading.order.risk-check.post.v1"),
    ("gameline.trading.order.risk-check.v1", "girisk.trading.order.risk-check.v1"),
    ("gameline.sportsdata.fixture.match.summary", "girisk.sportsdata.fixture.match.summary"),
    ("football.order.risk.detail.result", "girisk.football.detail.result"),
    ("football.order.risk.summary.result", "girisk.football.summary.result"),
    ("football.order.risk.limit.result", "girisk.football.limit.result"),
    ("football.order.risk.business.result", "girisk.football.business.result"),
    ("risk.order.event", "girisk.order.event"),
    ("risk.decision.event", "girisk.decision.event"),
    # redis
    ("risk:view:", "girisk:view:"),
    # consumer / app ids
    ("risk-platform-flink-decision", "girisk-console-flink-decision"),
    ("risk-platform-stream", "girisk-console-stream"),
    ("risk-platform-jwt-secret", "girisk-jwt-secret"),
    ("risk-internal-api-key", "girisk-internal-api-key"),
    ("risk-platform:1.0.0", "girisk-console:1.0.0"),
    (".risk-platform.pid", ".girisk.pid"),
    ("riskplatform-mysql", "girisk-mysql"),
    ("riskplatform-kafka", "girisk-kafka"),
    ("risk_mysql_data", "girisk_mysql_data"),
    # maven modules / jars (before bare risk-platform)
    ("risk-flink-job", "girisk-engine"),
    ("risk-common", "girisk-common"),
    ("risk-app", "girisk-console"),
    ("artifactId>risk-platform<", "artifactId>girisk<"),
    ("<name>risk-platform</name>", "<name>girisk</name>"),
    ("name: risk-platform", "name: girisk-console"),
    ("risk-platform", "girisk"),
    # spring config prefix
    ('prefix = "risk.kafka"', 'prefix = "girisk.kafka"'),
    ('prefix = "risk.sports"', 'prefix = "girisk.sports"'),
    ("risk.demo-data", "girisk.demo-data"),
    ("risk.kafka.", "girisk.kafka."),
    ("risk.sports.", "girisk.sports."),
    ("risk.redis.", "girisk.redis."),
    ("${risk.", "${girisk."),
    ("name = \"risk.kafka.enabled\"", 'name = "girisk.kafka.enabled"'),
    ("name = \"risk.demo-data.enabled\"", 'name = "girisk.demo-data.enabled"'),
    # yaml root key — only exact line starts handled later
    # API + UI paths
    ("/api/v1/risk/", "/api/v1/girisk/"),
    ('"/api/v1/risk"', '"/api/v1/girisk"'),
    ("/api/v1/risk'", "/api/v1/girisk'"),
    ("POST /api/v1/risk/", "POST /api/v1/girisk/"),
    ("path: '/risk/", "path: '/girisk/"),
    ("path: '/risk'", "path: '/girisk'"),
    ('to="/risk', 'to="/girisk'),
    ("to='/risk", "to='/girisk"),
    ('Navigate to="/risk"', 'Navigate to="/girisk"'),
    ("navigate('/risk')", "navigate('/girisk')"),
    ("navigate(`/risk", "navigate(`/girisk"),
    ('path="/risk', 'path="/girisk'),
    ("path='/risk", "path='/girisk"),
    ('"/risk/', '"/girisk/'),
    ("'/risk/", "'/girisk/"),
    ('"/risk"', '"/girisk"'),
    ("'/risk'", "'/girisk'"),
    ("RISK_HOME = '/risk'", "RISK_HOME = '/girisk'"),
    ("key: '/risk'", "key: '/girisk'"),
    ("key: '/risk/", "key: '/girisk/"),
    ("RedirectView(\"/risk", "RedirectView(\"/girisk"),
    ('"/risk/**"', '"/girisk/**"'),
    ("/risk/**", "/girisk/**"),
    # flink main class path in docs already updated via package
    ("com.infras.flink.risk", "com.girisk.flink.risk"),
    ("flink-football-order-prod", "girisk-engine-prod"),
    ("flink-football-order", "girisk-engine"),
    ("team: risk-platform", "team: girisk"),
    ("RISK_PORT", "GIRISK_PORT"),
    ("RISK_DEMO_DATA", "GIRISK_DEMO_DATA"),
    ("RISK_TENANT", "GIRISK_TENANT"),
    ("RISK_REVIEW", "GIRISK_REVIEW"),
]


TEXT_EXTS = {
    ".java",
    ".xml",
    ".yml",
    ".yaml",
    ".md",
    ".sh",
    ".ts",
    ".tsx",
    ".json",
    ".html",
    ".css",
    ".properties",
    ".sql",
    ".env",
    ".example",
    ".gitignore",
    ".dockerignore",
    ".iml",
    ".txt",
    ".Dockerfile",
}


def should_skip(path: Path) -> bool:
    parts = set(path.parts)
    return bool(parts & SKIP_DIRS)


def rewrite_file(path: Path) -> bool:
    if path.suffix.lower() not in TEXT_EXTS and path.name not in {
        "Dockerfile",
        "Dockerfile.full",
        "start.sh",
        "stop.sh",
        "start-dev.sh",
        ".env.example",
        ".gitignore",
        ".dockerignore",
    }:
        return False
    try:
        text = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, IsADirectoryError):
        return False
    orig = text
    for old, new in TEXT_REPLACEMENTS:
        text = text.replace(old, new)
    # YAML root `risk:` → `girisk:` (line-start only)
    lines = []
    for line in text.splitlines(keepends=True):
        if line.startswith("risk:"):
            line = "girisk:" + line[len("risk:") :]
        elif line.startswith("  risk:"):  # nested unlikely
            line = "  girisk:" + line[len("  risk:") :]
        lines.append(line)
    text = "".join(lines)
    if text != orig:
        path.write_text(text, encoding="utf-8")
        return True
    return False


def move_tree(src: Path, dst: Path) -> None:
    if not src.exists():
        return
    dst.parent.mkdir(parents=True, exist_ok=True)
    if dst.exists():
        shutil.rmtree(dst)
    shutil.move(str(src), str(dst))


def main() -> None:
    # 1) content rewrite while paths still old
    changed = 0
    for dirpath, dirnames, filenames in os.walk(ROOT):
        p = Path(dirpath)
        if should_skip(p):
            dirnames[:] = []
            continue
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for name in filenames:
            fp = p / name
            if rewrite_file(fp):
                changed += 1
    print(f"rewrote {changed} files")

    # 2) move java packages
    moves = [
        (
            ROOT / "risk-common/src/main/java/com/riskplatform",
            ROOT / "risk-common/src/main/java/com/girisk",
        ),
        (
            ROOT / "risk-app/src/main/java/com/riskplatform",
            ROOT / "risk-app/src/main/java/com/girisk",
        ),
        (
            ROOT / "risk-app/src/test/java/com/riskplatform",
            ROOT / "risk-app/src/test/java/com/girisk",
        ),
        (
            ROOT / "risk-flink-job/src/main/java/com/riskplatform",
            ROOT / "risk-flink-job/src/main/java/com/girisk",
        ),
        (
            ROOT / "risk-flink-job/src/test/java/com/riskplatform",
            ROOT / "risk-flink-job/src/test/java/com/girisk",
        ),
    ]
    for src, dst in moves:
        if src.exists():
            print(f"move {src.relative_to(ROOT)} -> {dst.relative_to(ROOT)}")
            move_tree(src, dst)
            # drop empty com/ if needed
            com = src.parent
            if com.name == "com" and com.exists() and not any(com.iterdir()):
                com.rmdir()

    # 3) rename application class file if still old name
    old_app = ROOT / "risk-app/src/main/java/com/girisk/RiskPlatformApplication.java"
    new_app = ROOT / "risk-app/src/main/java/com/girisk/GiRiskApplication.java"
    if old_app.exists():
        move_tree(old_app, new_app)
    # after content rewrite it should already be GiRiskApplication.java content under possibly old filename
    maybe = ROOT / "risk-app/src/main/java/com/girisk/GiRiskApplication.java"
    alt = ROOT / "risk-app/src/main/java/com/girisk/RiskPlatformApplication.java"
    if alt.exists() and not maybe.exists():
        text = alt.read_text(encoding="utf-8")
        alt.write_text(text.replace("RiskPlatformApplication", "GiRiskApplication"), encoding="utf-8")
        alt.rename(maybe)
    elif alt.exists() and maybe.exists():
        alt.unlink()

    # 4) rename module directories
    module_moves = [
        ("risk-common", "girisk-common"),
        ("risk-app", "girisk-console"),
        ("risk-flink-job", "girisk-engine"),
    ]
    for old, new in module_moves:
        src, dst = ROOT / old, ROOT / new
        if src.exists():
            print(f"module {old} -> {new}")
            if dst.exists():
                shutil.rmtree(dst)
            src.rename(dst)

    # 5) fix parent pom modules (should already be rewritten) + groupId
    parent = ROOT / "pom.xml"
    text = parent.read_text(encoding="utf-8")
    text = text.replace("<groupId>com.riskplatform</groupId>", "<groupId>com.girisk</groupId>")
    text = text.replace("com.riskplatform", "com.girisk")
    # ensure modules list
    text = text.replace("<module>risk-common</module>", "<module>girisk-common</module>")
    text = text.replace("<module>risk-app</module>", "<module>girisk-console</module>")
    text = text.replace("<module>risk-flink-job</module>", "<module>girisk-engine</module>")
    text = text.replace("<artifactId>risk-platform</artifactId>", "<artifactId>girisk</artifactId>")
    text = text.replace("<name>risk-platform</name>", "<name>girisk</name>")
    text = text.replace("risk.platform.version", "girisk.version")
    parent.write_text(text, encoding="utf-8")

    # 6) child poms parent refs
    for pom in [
        ROOT / "girisk-common/pom.xml",
        ROOT / "girisk-console/pom.xml",
        ROOT / "girisk-engine/pom.xml",
    ]:
        if not pom.exists():
            continue
        t = pom.read_text(encoding="utf-8")
        t = t.replace("com.riskplatform", "com.girisk")
        t = t.replace("<artifactId>risk-platform</artifactId>", "<artifactId>girisk</artifactId>")
        t = t.replace("<artifactId>risk-common</artifactId>", "<artifactId>girisk-common</artifactId>")
        t = t.replace("<artifactId>risk-app</artifactId>", "<artifactId>girisk-console</artifactId>")
        t = t.replace("<artifactId>risk-flink-job</artifactId>", "<artifactId>girisk-engine</artifactId>")
        t = t.replace("<name>risk-common</name>", "<name>girisk-common</name>")
        t = t.replace("<name>risk-app</name>", "<name>girisk-console</name>")
        t = t.replace("<name>risk-flink-job</name>", "<name>girisk-engine</name>")
        pom.write_text(t, encoding="utf-8")

    # 7) rename pid file if present
    old_pid = ROOT / ".risk-platform.pid"
    new_pid = ROOT / ".girisk.pid"
    if old_pid.exists():
        old_pid.rename(new_pid)

    print("done")


if __name__ == "__main__":
    main()
