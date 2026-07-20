#!/usr/bin/env bash
# 将「可公开」子集同步到 GitHub（完整 monorepo 仍推 GitLab）。
#
# 为什么不用 .gitignore？
#   ignore 对所有 remote 生效；Engine 一旦 ignore，GitLab 也跟踪不到。
#
# 用法:
#   export GITHUB_REMOTE=git@github.com:<org>/girisk.git
#   bash scripts/sync-github.sh              # dry-run：只生成导出目录
#   bash scripts/sync-github.sh --push       # 导出并 force-with-lease 推到 GitHub main
#
# 首次:
#   git remote add github "$GITHUB_REMOTE"   # 可选；也可用环境变量直接 push

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

EXCLUDE_FILE="$ROOT/scripts/github-export.exclude"
PUSH=false
BRANCH="${GITHUB_SYNC_BRANCH:-main}"
EXPORT_DIR="${GIRISK_GITHUB_EXPORT_DIR:-/tmp/girisk-github-export}"

for arg in "$@"; do
  case "$arg" in
    --push) PUSH=true ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
  esac
done

if [[ ! -f "$EXCLUDE_FILE" ]]; then
  echo "missing $EXCLUDE_FILE" >&2
  exit 1
fi

rm -rf "$EXPORT_DIR"
mkdir -p "$EXPORT_DIR"

echo ">>> rsync public tree → $EXPORT_DIR"
rsync -a \
  --exclude-from="$EXCLUDE_FILE" \
  "$ROOT/" "$EXPORT_DIR/"

# Public-facing pom: drop engine module so GitHub clone builds cleanly
python3 - <<'PY' "$EXPORT_DIR/pom.xml"
from pathlib import Path
import sys
p = Path(sys.argv[1])
text = p.read_text()
text2 = text.replace("        <module>girisk-engine</module>\n", "")
text2 = text2.replace(
    "Console + contracts + internal Engine (GitLab full; GitHub sync excludes Engine)",
    "GiRisk Console + contracts (public); Engine lives on internal GitLab only",
)
p.write_text(text2)
print("rewrote public pom.xml (no girisk-engine module)")
PY

# Strip Engine-only wording from root README if present (best-effort)
if [[ -f "$EXPORT_DIR/README.md" ]]; then
  python3 - <<'PY' "$EXPORT_DIR/README.md"
from pathlib import Path
import sys
p = Path(sys.argv[1])
t = p.read_text()
# Keep README as-is if already console-focused; ensure no local path to private sibling
t = t.replace("/Users/felixzhu/IdeaProjects/girisk-engine", "（内部 GitLab 同仓 girisk-engine/，不同步到 GitHub）")
p.write_text(t)
PY
fi

echo ">>> export ready: $EXPORT_DIR"
echo "    excluded per scripts/github-export.exclude"

if [[ "$PUSH" != true ]]; then
  echo "dry-run only. Re-run with --push to publish."
  exit 0
fi

REMOTE_URL="${GITHUB_REMOTE:-}"
if [[ -z "$REMOTE_URL" ]]; then
  REMOTE_URL="$(git remote get-url github 2>/dev/null || true)"
fi
if [[ -z "$REMOTE_URL" ]]; then
  echo "Set GITHUB_REMOTE=git@github.com:<org>/girisk.git or: git remote add github <url>" >&2
  exit 1
fi

echo ">>> commit + push → $REMOTE_URL ($BRANCH)"
cd "$EXPORT_DIR"
if [[ ! -d .git ]]; then
  git init -b "$BRANCH"
fi
git checkout -B "$BRANCH" >/dev/null 2>&1 || true
git add -A
if git diff --cached --quiet; then
  echo "nothing to commit in export"
else
  git -c user.email="${GIT_AUTHOR_EMAIL:-girisk-sync@local}" \
      -c user.name="${GIT_AUTHOR_NAME:-girisk-sync}" \
      commit -m "sync: public Console + contracts from internal monorepo"
fi
git remote remove github 2>/dev/null || true
git remote add github "$REMOTE_URL"
# Export is an unrelated root commit (filtered tree); lease cannot apply — force update public main.
git fetch github "$BRANCH" 2>/dev/null || true
git push --force github "HEAD:${BRANCH}"
echo ">>> GitHub sync done"
