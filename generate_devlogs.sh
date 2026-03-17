#!/usr/bin/env bash
set -euo pipefail

# =========================================================
# DEFAULT CONFIG
# =========================================================
REPO="."
OUT_DIR="docs"
SKIP_EXISTING=false
FORCE=false
SINCE=""
UNTIL=""
NO_PATCH=false

# Chunking controls
MAX_PATCH_BYTES=180000
MAX_COMMITS_PER_CHUNK=8
MAX_FILES_PER_CHUNK=40

# Noise reduction: exclude common generated / low-signal files
DEFAULT_EXCLUDE_REGEX='(^package-lock\.json$|^yarn\.lock$|^pnpm-lock\.yaml$|^bun\.lockb$|^Cargo\.lock$|^composer\.lock$|^Gemfile\.lock$|^poetry\.lock$|^Pipfile\.lock$|^dist/|^build/|^coverage/|^\.next/|^node_modules/|^out/|^target/|^tmp/|^temp/|^vendor/|^bin/|^obj/|\.min\.js$|\.map$|^storybook-static/|^public/build/|^public/dist/|^\.turbo/|^\.cache/|^__pycache__/|\.pyc$)'

DEVLOG_EXCLUDE_REGEX="${DEVLOG_EXCLUDE_REGEX:-$DEFAULT_EXCLUDE_REGEX}"
DEVLOG_INCLUDE_REGEX="${DEVLOG_INCLUDE_REGEX:-}"

AI_COMMAND=""

# =========================================================
# HELP
# =========================================================
usage() {
  cat <<EOF
Usage: $0 [options]

Options:
  --repo PATH                  Repository path (default: .)
  --out-dir PATH               Output docs dir (default: docs)
  --skip-existing              Skip chunk devlogs that already exist
  --force                      Remove existing generated files before regenerating
  --since DATE                 Only include commits since date
  --until DATE                 Only include commits until date
  --no-patch                   Do not generate diff.patch files
  --max-patch-bytes N          Max bytes of patch content per chunk (default: 180000)
  --max-commits-per-chunk N    Max commits per chunk (default: 8)
  --max-files-per-chunk N      Max unique filtered files per chunk (default: 40)
  --ai-command CMD             Run CMD once per chunk (uses exported env vars)
  --help                       Show this help

Environment variables:
  DEVLOG_EXCLUDE_REGEX         Override default exclude regex
  DEVLOG_INCLUDE_REGEX         Include only matching files

Example:
  ./generate_devlogs.sh --repo . --out-dir docs

Example with AI:
  ./generate_devlogs.sh \\
    --ai-command 'cat "\$PROMPT_FILE" "\$CONTEXT_FILE" "\$DIFF_FILE" | my-ai-cli > "\$DEVLOG_FILE"'
EOF
}

# =========================================================
# ARG PARSING
# =========================================================
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
    --max-commits-per-chunk) MAX_COMMITS_PER_CHUNK="$2"; shift 2 ;;
    --max-files-per-chunk) MAX_FILES_PER_CHUNK="$2"; shift 2 ;;
    --ai-command) AI_COMMAND="$2"; shift 2 ;;
    --help) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 1 ;;
  esac
done

# =========================================================
# CHECKS
# =========================================================
if ! command -v git >/dev/null 2>&1; then
  echo "Error: git is required." >&2
  exit 1
fi

cd "$REPO"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Error: '$REPO' is not a git repository." >&2
  exit 1
fi

# =========================================================
# PATHS
# =========================================================
DAYS_DIR="$OUT_DIR/days"
PLAN_DIR="$OUT_DIR/_planning"

mkdir -p "$DAYS_DIR" "$PLAN_DIR"

# =========================================================
# UTILITIES
# =========================================================
git_range_args=()
[[ -n "$SINCE" ]] && git_range_args+=(--since="$SINCE")
[[ -n "$UNTIL" ]] && git_range_args+=(--until="$UNTIL")

safe_wc_bytes() {
  local file="$1"
  if [[ -f "$file" ]]; then
    wc -c < "$file" | tr -d ' '
  else
    echo 0
  fi
}

safe_wc_lines() {
  local file="$1"
  if [[ -f "$file" ]]; then
    wc -l < "$file" | tr -d ' '
  else
    echo 0
  fi
}

filter_file_list() {
  local input_file="$1"
  local output_file="$2"

  cp "$input_file" "$output_file"

  if [[ -n "$DEVLOG_EXCLUDE_REGEX" ]]; then
    grep -Ev "$DEVLOG_EXCLUDE_REGEX" "$output_file" > "${output_file}.tmp" || true
    mv "${output_file}.tmp" "$output_file"
  fi

  if [[ -n "$DEVLOG_INCLUDE_REGEX" ]]; then
    grep -E "$DEVLOG_INCLUDE_REGEX" "$output_file" > "${output_file}.tmp" || true
    mv "${output_file}.tmp" "$output_file"
  fi
}

build_filtered_files_for_commit() {
  local commit_hash="$1"
  local out_file="$2"
  local tmp_file
  tmp_file="$(mktemp)"

  git show --name-only --pretty="" "$commit_hash" | sed '/^$/d' | sort -u > "$tmp_file"
  filter_file_list "$tmp_file" "$out_file"

  rm -f "$tmp_file"
}

build_patch_for_commit() {
  local commit_hash="$1"
  git show --patch --stat --format=fuller "$commit_hash"
}

merge_unique_files() {
  local base_file="$1"
  local add_file="$2"
  local out_file="$3"

  {
    [[ -f "$base_file" ]] && cat "$base_file"
    [[ -f "$add_file" ]] && cat "$add_file"
  } | sed '/^$/d' | sort -u > "$out_file"
}

# =========================================================
# ACTIVITY MAP
# =========================================================
echo "📅 Generating activity map..."

mapfile -t DAYS < <(
  git log "${git_range_args[@]}" \
    --date=format:'%Y-%m-%d' \
    --pretty='%ad' | sort -u
)

ACTIVITY_MD="$PLAN_DIR/activity-map.md"
ACTIVITY_CSV="$PLAN_DIR/activity-map.csv"

{
  echo "# Activity Map"
  echo
  echo "Generated from git history."
  echo
} > "$ACTIVITY_MD"

echo "date,commit_count" > "$ACTIVITY_CSV"

for DAY in "${DAYS[@]}"; do
  COUNT=$(git log "${git_range_args[@]}" \
    --since="$DAY 00:00:00" \
    --until="$DAY 23:59:59" \
    --pretty=oneline | wc -l | tr -d ' ')

  echo "- $DAY ($COUNT commits)" >> "$ACTIVITY_MD"
  echo "$DAY,$COUNT" >> "$ACTIVITY_CSV"
done

echo "✅ Activity map written"

# =========================================================
# FORCE CLEANUP
# =========================================================
if [[ "$FORCE" == true ]]; then
  echo "🧹 Force mode: cleaning generated daily outputs..."
  find "$DAYS_DIR" -type f \
    \( -name 'commits.txt' \
    -o -name 'commit-hashes.txt' \
    -o -name 'changed-files.txt' \
    -o -name 'context.txt' \
    -o -name 'diff.patch' \
    -o -name 'prompt.txt' \
    -o -name 'devlog.md' \
    -o -name 'index.md' \) -delete 2>/dev/null || true
fi

# =========================================================
# DAY PROCESSING
# =========================================================
for DAY in "${DAYS[@]}"; do
  echo "⚙️ Processing $DAY..."

  DAY_DIR="$DAYS_DIR/$DAY"
  CHUNKS_DIR="$DAY_DIR/chunks"
  mkdir -p "$DAY_DIR" "$CHUNKS_DIR"

  mapfile -t DAY_HASHES < <(
    git log "${git_range_args[@]}" \
      --reverse \
      --since="$DAY 00:00:00" \
      --until="$DAY 23:59:59" \
      --pretty=format:'%H'
  )

  if [[ "${#DAY_HASHES[@]}" -eq 0 ]]; then
    echo "  No commits found for $DAY, skipping."
    continue
  fi

  {
    echo "Date: $DAY"
    echo "Commit count: ${#DAY_HASHES[@]}"
    echo
    echo "Commits:"
    git log "${git_range_args[@]}" \
      --reverse \
      --since="$DAY 00:00:00" \
      --until="$DAY 23:59:59" \
      --pretty=format:'%h %ad %an %s' \
      --date=iso
  } > "$DAY_DIR/context.txt"

  chunk_index=1
  chunk_commit_count=0
  chunk_patch_bytes=0
  chunk_hashes_file="$(mktemp)"
  chunk_files_seen_file="$(mktemp)"
  : > "$chunk_hashes_file"
  : > "$chunk_files_seen_file"

  write_current_chunk() {
    if [[ "$chunk_commit_count" -eq 0 ]]; then
      return
    fi

    CHUNK_ID=$(printf "%03d" "$chunk_index")
    CHUNK_DIR="$CHUNKS_DIR/$CHUNK_ID"
    mkdir -p "$CHUNK_DIR"

    COMMITS_FILE="$CHUNK_DIR/commits.txt"
    HASHES_FILE="$CHUNK_DIR/commit-hashes.txt"
    CHANGED_FILES_FILE="$CHUNK_DIR/changed-files.txt"
    CONTEXT_FILE="$CHUNK_DIR/context.txt"
    DIFF_FILE="$CHUNK_DIR/diff.patch"
    PROMPT_FILE="$CHUNK_DIR/prompt.txt"
    DEVLOG_FILE="$CHUNK_DIR/devlog.md"

    : > "$COMMITS_FILE"
    cp "$chunk_hashes_file" "$HASHES_FILE"
    : > "$CHANGED_FILES_FILE"
    [[ "$NO_PATCH" == false ]] && : > "$DIFF_FILE"

    while IFS= read -r H; do
      [[ -z "$H" ]] && continue
      git show --quiet --format='%h %ad %an %s' --date=iso "$H" >> "$COMMITS_FILE"

      tmp_files="$(mktemp)"
      build_filtered_files_for_commit "$H" "$tmp_files"
      cat "$tmp_files" >> "$CHANGED_FILES_FILE"
      rm -f "$tmp_files"

      if [[ "$NO_PATCH" == false ]]; then
        {
          echo
          echo "===== COMMIT $H ====="
          build_patch_for_commit "$H"
        } >> "$DIFF_FILE"
      fi
    done < "$chunk_hashes_file"

    sort -u "$CHANGED_FILES_FILE" -o "$CHANGED_FILES_FILE"

    {
      echo "Date: $DAY"
      echo "Chunk: $CHUNK_ID"
      echo "Chunk commit count: $chunk_commit_count"
      echo "Chunk unique filtered files: $(safe_wc_lines "$CHANGED_FILES_FILE")"
      if [[ "$NO_PATCH" == false ]]; then
        echo "Chunk patch bytes: $(safe_wc_bytes "$DIFF_FILE")"
      else
        echo "Chunk patch bytes: skipped (--no-patch)"
      fi
      echo
      echo "Commits:"
      cat "$COMMITS_FILE"
      echo
      echo "Changed files:"
      cat "$CHANGED_FILES_FILE"
    } > "$CONTEXT_FILE"

    cat > "$PROMPT_FILE" <<EOF
You are analysing ONE chronological chunk from ONE day of repository history.

Date: $DAY
Chunk: $CHUNK_ID

Use ONLY these files as evidence:
- context.txt
- commits.txt
- changed-files.txt
- diff.patch $( [[ "$NO_PATCH" == false ]] && echo "(present)" || echo "(not present for this run)" )

Strict requirements:
- Do NOT invent anything
- Do NOT summarise
- Be detailed and chronological
- Ground every claim in visible evidence
- Reference specific files and commit hashes
- Explain code evolution in before → after terms
- If evidence is ambiguous, state uncertainty explicitly
- Treat debugging, breakages, refactors, and repeated edits as first-class parts of the narrative
- Do not inspect unrelated history

Write the output to devlog.md for this chunk only.
EOF

    if [[ -n "$AI_COMMAND" ]]; then
      if [[ "$SKIP_EXISTING" == true && -f "$DEVLOG_FILE" ]]; then
        echo "  ⏭️ Skipping chunk $CHUNK_ID for $DAY (devlog.md exists)"
      else
        echo "  🤖 Generating devlog for $DAY chunk $CHUNK_ID..."
        export DAY CHUNK_ID CHUNK_DIR REPO DAY_DIR
        export CONTEXT_FILE COMMITS_FILE CHANGED_FILES_FILE HASHES_FILE PROMPT_FILE DEVLOG_FILE
        if [[ "$NO_PATCH" == false ]]; then
          export DIFF_FILE
        else
          export DIFF_FILE=""
        fi
        eval "$AI_COMMAND"
      fi
    fi

    chunk_index=$((chunk_index + 1))
    chunk_commit_count=0
    chunk_patch_bytes=0
    : > "$chunk_hashes_file"
    : > "$chunk_files_seen_file"
  }

  for H in "${DAY_HASHES[@]}"; do
    commit_patch_file="$(mktemp)"
    commit_files_filtered="$(mktemp)"
    prospective_files_seen="$(mktemp)"

    if [[ "$NO_PATCH" == false ]]; then
      {
        echo
        echo "===== COMMIT $H ====="
        build_patch_for_commit "$H"
      } > "$commit_patch_file"
      commit_patch_bytes=$(wc -c < "$commit_patch_file" | tr -d ' ')
    else
      : > "$commit_patch_file"
      commit_patch_bytes=0
    fi

    build_filtered_files_for_commit "$H" "$commit_files_filtered"
    merge_unique_files "$chunk_files_seen_file" "$commit_files_filtered" "$prospective_files_seen"
    prospective_files_count=$(safe_wc_lines "$prospective_files_seen")

    would_exceed_patch=false
    would_exceed_commits=false
    would_exceed_files=false

    if [[ "$chunk_commit_count" -gt 0 ]]; then
      if [[ "$NO_PATCH" == false ]] && (( chunk_patch_bytes + commit_patch_bytes > MAX_PATCH_BYTES )); then
        would_exceed_patch=true
      fi

      if (( chunk_commit_count + 1 > MAX_COMMITS_PER_CHUNK )); then
        would_exceed_commits=true
      fi

      if (( prospective_files_count > MAX_FILES_PER_CHUNK )); then
        would_exceed_files=true
      fi
    fi

    if [[ "$would_exceed_patch" == true || "$would_exceed_commits" == true || "$would_exceed_files" == true ]]; then
      write_current_chunk
    fi

    echo "$H" >> "$chunk_hashes_file"
    cp "$prospective_files_seen" "$chunk_files_seen_file"
    chunk_commit_count=$((chunk_commit_count + 1))
    chunk_patch_bytes=$((chunk_patch_bytes + commit_patch_bytes))

    rm -f "$commit_patch_file" "$commit_files_filtered" "$prospective_files_seen"
  done

  write_current_chunk

  {
    echo "# $DAY"
    echo
    echo "This day is split into chronological chunks."
    echo
    for dir in "$CHUNKS_DIR"/*; do
      [[ -d "$dir" ]] || continue
      base="$(basename "$dir")"
      echo "- Chunk $base"
    done
  } > "$DAY_DIR/index.md"

  rm -f "$chunk_hashes_file" "$chunk_files_seen_file"
done

echo "🎉 Done"
echo
echo "Generated:"
echo "  - $PLAN_DIR/activity-map.md"
echo "  - $PLAN_DIR/activity-map.csv"
echo "  - $DAYS_DIR/YYYY-MM-DD/chunks/NNN/{commits.txt,commit-hashes.txt,changed-files.txt,context.txt,diff.patch,prompt.txt}"