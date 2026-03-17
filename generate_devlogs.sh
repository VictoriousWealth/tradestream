#!/usr/bin/env bash
set -euo pipefail

# =========================
# CONFIG DEFAULTS
# =========================
REPO="."
OUT_DIR="docs"
SKIP_EXISTING=false
FORCE=false
SINCE=""
UNTIL=""
NO_PATCH=false
MAX_PATCH_BYTES=200000

# Default exclude (noise reduction)
DEVLOG_EXCLUDE_REGEX='(^package-lock\.json$|^yarn\.lock$|^pnpm-lock\.yaml$|^dist/|^build/|^coverage/|^\.next/|^node_modules/|\.min\.js$)'

# Optional include filter (empty = include all)
DEVLOG_INCLUDE_REGEX=""

AI_COMMAND=""

# =========================
# ARG PARSING
# =========================
while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo) REPO="$2"; shift 2 ;;
    --out-dir) OUT_DIR="$2"; shift 2 ;;
    --skip-existing) SKIP_EXISTING=true; shift ;;
    --force) FORCE=true; shift ;;
    --since) SINCE="$2"; shift 2 ;;
    --until) UNTIL="$2"; shift 2 ;;
    --no-patch) NO_PATCH=true; shift ;;
    --max-patch-bytes) MAX_PATCH_BYTES="$2"; shift 2 ;;
    --ai-command) AI_COMMAND="$2"; shift 2 ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

cd "$REPO"

DAYS_DIR="$OUT_DIR/days"
PLAN_DIR="$OUT_DIR/_planning"

mkdir -p "$DAYS_DIR"
mkdir -p "$PLAN_DIR"

echo "📅 Generating activity map..."

# =========================
# ACTIVITY MAP
# =========================
GIT_RANGE_ARGS=()
[[ -n "$SINCE" ]] && GIT_RANGE_ARGS+=(--since="$SINCE")
[[ -n "$UNTIL" ]] && GIT_RANGE_ARGS+=(--until="$UNTIL")

mapfile -t DAYS < <(
  git log "${GIT_RANGE_ARGS[@]}" \
    --date=format:'%Y-%m-%d' \
    --pretty='%ad' | sort -u
)

ACTIVITY_MD="$PLAN_DIR/activity-map.md"
ACTIVITY_CSV="$PLAN_DIR/activity-map.csv"

echo "# Activity Map" > "$ACTIVITY_MD"
echo "date,commit_count" > "$ACTIVITY_CSV"

for DAY in "${DAYS[@]}"; do
  COUNT=$(git log "${GIT_RANGE_ARGS[@]}" \
    --since="$DAY 00:00:00" \
    --until="$DAY 23:59:59" \
    --pretty=oneline | wc -l)

  echo "- $DAY ($COUNT commits)" >> "$ACTIVITY_MD"
  echo "$DAY,$COUNT" >> "$ACTIVITY_CSV"
done

echo "✅ Activity map written"

# =========================
# PROCESS EACH DAY
# =========================
for DAY in "${DAYS[@]}"; do
  DAY_DIR="$DAYS_DIR/$DAY"
  mkdir -p "$DAY_DIR"

  DEVLOG_FILE="$DAY_DIR/devlog.md"

  if [[ "$SKIP_EXISTING" == true && -f "$DEVLOG_FILE" ]]; then
    echo "⏭️ Skipping $DAY (already exists)"
    continue
  fi

  if [[ "$FORCE" == true ]]; then
    rm -f "$DEVLOG_FILE"
  fi

  echo "⚙️ Processing $DAY..."

  # =========================
  # COMMITS
  # =========================
  git log "${GIT_RANGE_ARGS[@]}" \
    --reverse \
    --since="$DAY 00:00:00" \
    --until="$DAY 23:59:59" \
    --pretty=format:'%h %ad %an %s' \
    --date=iso \
    > "$DAY_DIR/commits.txt"

  git log "${GIT_RANGE_ARGS[@]}" \
    --reverse \
    --since="$DAY 00:00:00" \
    --until="$DAY 23:59:59" \
    --pretty=format:'%H' \
    > "$DAY_DIR/commit-hashes.txt"

  mapfile -t HASHES < "$DAY_DIR/commit-hashes.txt"

  if [[ "${#HASHES[@]}" -eq 0 ]]; then
    echo "No commits for $DAY"
    continue
  fi

  # =========================
  # FILES
  # =========================
  : > "$DAY_DIR/changed-files.txt"

  for H in "${HASHES[@]}"; do
    git show --name-only --pretty="" "$H" >> "$DAY_DIR/changed-files.txt"
  done

  sort -u "$DAY_DIR/changed-files.txt" -o "$DAY_DIR/changed-files.txt"

  # Apply filters
  if [[ -n "$DEVLOG_EXCLUDE_REGEX" ]]; then
    grep -Ev "$DEVLOG_EXCLUDE_REGEX" "$DAY_DIR/changed-files.txt" > "$DAY_DIR/tmp.txt" || true
    mv "$DAY_DIR/tmp.txt" "$DAY_DIR/changed-files.txt"
  fi

  if [[ -n "$DEVLOG_INCLUDE_REGEX" ]]; then
    grep -E "$DEVLOG_INCLUDE_REGEX" "$DAY_DIR/changed-files.txt" > "$DAY_DIR/tmp.txt" || true
    mv "$DAY_DIR/tmp.txt" "$DAY_DIR/changed-files.txt"
  fi

  # =========================
  # DIFF
  # =========================
  if [[ "$NO_PATCH" == false ]]; then
    : > "$DAY_DIR/diff.patch"

    for H in "${HASHES[@]}"; do
      {
        echo
        echo "===== COMMIT $H ====="
        git show --patch --stat "$H"
      } >> "$DAY_DIR/diff.patch"

      SIZE=$(wc -c < "$DAY_DIR/diff.patch")
      if (( SIZE > MAX_PATCH_BYTES )); then
        echo "⚠️ Patch truncated due to size limit"
        break
      fi
    done
  fi

  # =========================
  # CONTEXT
  # =========================
  {
    echo "Date: $DAY"
    echo "Commit count: ${#HASHES[@]}"
    echo
    echo "Commits:"
    cat "$DAY_DIR/commits.txt"
    echo
    echo "Changed files:"
    cat "$DAY_DIR/changed-files.txt"
  } > "$DAY_DIR/context.txt"

  # =========================
  # PROMPT FILE
  # =========================
  PROMPT_FILE="$DAY_DIR/prompt.txt"

  cat > "$PROMPT_FILE" <<EOF
You are analysing ONE day of development.

Date: $DAY

Use ONLY the provided files:
- context.txt
- commits.txt
- changed-files.txt
- diff.patch (if present)

STRICT RULES:
- Do NOT invent anything
- Do NOT summarise
- Be detailed and chronological
- Reference files and commits
- Explain evolution (before → after)

Write a full dev log.

EOF

  # =========================
  # AI CALL
  # =========================
  if [[ -n "$AI_COMMAND" ]]; then
    echo "🤖 Generating devlog for $DAY..."

    export DAY DAY_DIR REPO
    export CONTEXT_FILE="$DAY_DIR/context.txt"
    export COMMITS_FILE="$DAY_DIR/commits.txt"
    export CHANGED_FILES_FILE="$DAY_DIR/changed-files.txt"
    export DIFF_FILE="$DAY_DIR/diff.patch"
    export PROMPT_FILE
    export DEVLOG_FILE

    eval "$AI_COMMAND"
  fi

done

echo "🎉 Done"
