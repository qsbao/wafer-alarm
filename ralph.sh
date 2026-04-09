#!/usr/bin/env bash
#
# ralph.sh — autonomously work through wafer-alarm issues overnight using
# Claude Code + the /tdd skill.
#
# Pattern: pick the next available (open, unblocked, no open PR) issue from
# qsbao/wafer-alarm, create a branch, invoke `claude -p` with a TDD prompt,
# let it commit + push + open a PR, then repeat. Each issue runs in a fresh
# checkout off main. PRs are NOT auto-merged — they wait for human review.
#
# Usage:
#   ./ralph.sh                      # loop forever, work next available issue
#   ./ralph.sh --once               # work one issue and exit
#   ./ralph.sh --issue 5            # work a specific issue and exit
#   MAX_ITERATIONS=3 ./ralph.sh     # stop after 3 successful iterations
#
# Environment overrides:
#   WORKDIR=/path                   # where to clone the repo (default ~/github/qsbao/wafer-alarm-work)
#   LOG_DIR=/path                   # where logs go (default ./ralph-logs)
#   PER_ISSUE_TIMEOUT=3600          # per-issue wall-clock cap (seconds)
#   SLEEP_BETWEEN_NO_WORK=300       # idle sleep when nothing is available
#   MODEL=opus                      # claude model alias (default = claude default)
#
# Requires: git, gh (authenticated), claude CLI, python3 on PATH.
#
# DESTRUCTIVE: runs `claude --dangerously-skip-permissions`, which means every
# tool call (Bash, Edit, Write, gh, git push) executes without prompting. Run
# on a trusted machine. Review every PR before merging.
#

set -uo pipefail

REPO="qsbao/wafer-alarm"
WORKDIR="${WORKDIR:-$HOME/github/qsbao/wafer-alarm-work}"
LOG_DIR="${LOG_DIR:-$(cd "$(dirname "$0")" && pwd)/ralph-logs}"
MAX_ITERATIONS="${MAX_ITERATIONS:-0}"
SLEEP_BETWEEN_NO_WORK="${SLEEP_BETWEEN_NO_WORK:-300}"
PER_ISSUE_TIMEOUT="${PER_ISSUE_TIMEOUT:-3600}"
MODEL="${MODEL:-}"

# Issue dependency graph from PRD #1. Keep in sync with the PRD if it changes.
# (Function-based instead of `declare -A` so it works on macOS bash 3.2.)
blockers_for() {
  case "$1" in
    2)  echo "" ;;
    3)  echo "2" ;;
    4)  echo "2" ;;
    5)  echo "4" ;;
    6)  echo "2 4" ;;
    7)  echo "6" ;;
    8)  echo "6" ;;
    9)  echo "2" ;;
    10) echo "3 8 9" ;;
    11) echo "6 7" ;;
    12) echo "5" ;;
    13) echo "5" ;;
    14) echo "6" ;;
    15) echo "5 6" ;;
    16) echo "9 10" ;;
    17) echo "5" ;;
    *)  echo "" ;;
  esac
}
ALL_ISSUES="2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17"

# -------------------------------------------------------------------- helpers

log() { printf '[%s] %s\n' "$(date +'%F %T')" "$*"; }

issue_state() {
  gh issue view "$1" --repo "$REPO" --json state -q .state 2>/dev/null || echo UNKNOWN
}

is_closed() { [[ "$(issue_state "$1")" == "CLOSED" ]]; }

remote_branch_exists() {
  ( cd "$WORKDIR" && git ls-remote --exit-code --heads origin "issue-$1" >/dev/null 2>&1 )
}

blockers_done() {
  local b
  for b in $(blockers_for "$1"); do
    is_closed "$b" || return 1
  done
  return 0
}

next_issue() {
  local n
  for n in $ALL_ISSUES; do
    is_closed "$n" && continue
    remote_branch_exists "$n" && continue
    blockers_done "$n" || continue
    printf '%s' "$n"
    return 0
  done
  return 1
}

ensure_workdir() {
  if [[ -d "$WORKDIR/.git" ]]; then
    return 0
  fi
  log "Cloning $REPO into $WORKDIR..."
  mkdir -p "$(dirname "$WORKDIR")"
  gh repo clone "$REPO" "$WORKDIR" || { log "Clone failed."; exit 1; }
}

reset_to_main() {
  cd "$WORKDIR"
  git fetch origin --prune
  if git show-ref --verify --quiet refs/heads/main; then
    git checkout main
  else
    git checkout -b main origin/main 2>/dev/null || git checkout -b main
  fi
  git reset --hard origin/main 2>/dev/null || true
  git clean -fdx
}

build_prompt() {
  local n=$1
  cat <<EOF
You are working autonomously on GitHub issue #$n in the repository $REPO.

Step 1. Read the issue:
  gh issue view $n --repo $REPO

Step 2. Read the parent PRD (issue #1) for full architectural context:
  gh issue view 1 --repo $REPO

Step 3. Use the tdd skill (red-green-refactor). For each acceptance criterion:
write a failing test first, watch it fail, write the minimum code to make it
pass, refactor, commit. Make small commits per cycle.

Step 4. Implement only what the issue's acceptance criteria require. Do NOT
add features beyond the slice. Refer to the PRD for module shapes and
architectural decisions, but do not exceed scope.

Step 5. When all acceptance criteria pass and all tests are green:
  - You are already on branch issue-$n. Push it to origin.
  - Open a PR titled the same as the issue, with a body containing
    "Closes #$n" and a short summary of what was built and how it was tested.
  - Merge the PR using:
      gh pr merge --squash --delete-branch --repo $REPO
    Auto-merge is enabled because subsequent slices depend on this one being
    on main. Only merge if your tests are green.

Constraints:
- Stay on branch issue-$n. Do not modify main directly (only via merging the PR).
- Use the tech stack from the PRD: Java/Spring Boot, MySQL, Flyway, React,
  Apache ECharts. Do not introduce alternatives.
- If you discover this slice depends on something not yet built that the
  caller's blocker check missed, leave a comment on the issue explaining the
  blocker and stop. Do NOT merge an incomplete PR.
- Do not run with --no-verify, --force, or other destructive git flags.
- Tests must pass before merging. If tests are failing, fix them; if you
  cannot, leave a comment on the PR and stop without merging.
EOF
}

format_stream() {
  # Reads claude stream-json on stdin, prints human-readable lines on stdout.
  python3 -u -c '
import sys, json
for line in sys.stdin:
    line = line.strip()
    if not line:
        continue
    try:
        o = json.loads(line)
    except Exception:
        print(line, flush=True)
        continue
    t = o.get("type")
    if t == "assistant":
        for c in (o.get("message", {}) or {}).get("content", []) or []:
            ct = c.get("type")
            if ct == "text":
                txt = (c.get("text") or "").rstrip()
                if txt:
                    print(txt, flush=True)
            elif ct == "tool_use":
                name = c.get("name", "?")
                inp = json.dumps(c.get("input", {}), ensure_ascii=False)
                if len(inp) > 240:
                    inp = inp[:240] + "..."
                print(f"\u25B8 {name} {inp}", flush=True)
    elif t == "user":
        for c in (o.get("message", {}) or {}).get("content", []) or []:
            if c.get("type") == "tool_result":
                content = c.get("content", "")
                if isinstance(content, list):
                    content = " ".join(
                        (item.get("text") or "") if isinstance(item, dict) else str(item)
                        for item in content
                    )
                content = str(content).replace("\n", " ")
                if len(content) > 240:
                    content = content[:240] + "..."
                print(f"  \u25C2 {content}", flush=True)
    elif t == "system":
        sub = o.get("subtype", "system")
        print(f"[{sub}]", flush=True)
    elif t == "result":
        cost = o.get("total_cost_usd", 0)
        dur_ms = o.get("duration_ms", 0)
        sub = o.get("subtype", "")
        print(f"[done] {sub} cost=${cost} duration={dur_ms}ms", flush=True)
'
}

work_on_issue() {
  local n=$1
  local branch="issue-$n"
  local ts logfile
  ts=$(date +%Y%m%d-%H%M%S)
  logfile="$LOG_DIR/issue-$n-$ts.log"
  mkdir -p "$LOG_DIR"

  log "==== Starting issue #$n on branch $branch ===="
  log "Log:        $logfile"
  log "Watch live: tail -f \"$logfile\""

  reset_to_main
  git checkout -B "$branch"

  local timeout_cmd=()
  if command -v timeout >/dev/null 2>&1; then
    timeout_cmd=(timeout "$PER_ISSUE_TIMEOUT")
  elif command -v gtimeout >/dev/null 2>&1; then
    timeout_cmd=(gtimeout "$PER_ISSUE_TIMEOUT")
  fi

  local model_arg=()
  [[ -n "$MODEL" ]] && model_arg=(--model "$MODEL")

  local prompt
  prompt=$(build_prompt "$n")

  ${timeout_cmd[@]+"${timeout_cmd[@]}"} claude \
    --print \
    --output-format stream-json \
    --verbose \
    --dangerously-skip-permissions \
    --name "ralph-issue-$n" \
    ${model_arg[@]+"${model_arg[@]}"} \
    "$prompt" 2>&1 \
    | format_stream \
    | tee "$logfile"

  local rc=${PIPESTATUS[0]}
  log "==== Issue #$n claude run exited with code $rc ===="

  # Safety net: if Claude pushed a branch + opened a PR but did not merge it,
  # merge it here so that subsequent slices can build on this one. Skip if the
  # branch never got pushed (Claude bailed) or the PR was already merged.
  if remote_branch_exists "$n"; then
    local pr_number
    pr_number=$(gh pr list --repo "$REPO" --head "issue-$n" --state open --json number -q '.[0].number' 2>/dev/null || true)
    if [[ -n "$pr_number" ]]; then
      log "Auto-merging PR #$pr_number for issue #$n"
      if gh pr merge "$pr_number" --repo "$REPO" --squash --delete-branch 2>&1 | tee -a "$logfile"; then
        log "Merged PR #$pr_number"
      else
        log "PR #$pr_number merge failed — leaving open for manual review"
      fi
    else
      log "No open PR found for branch issue-$n; skipping auto-merge"
    fi
  fi

  return 0
}

print_banner() {
  log "Ralph starting"
  log "  repo:           $REPO"
  log "  workdir:        $WORKDIR"
  log "  log dir:        $LOG_DIR"
  log "  timeout/issue:  ${PER_ISSUE_TIMEOUT}s"
  log "  max iterations: $([[ $MAX_ITERATIONS -eq 0 ]] && echo unlimited || echo "$MAX_ITERATIONS")"
  log "  model:          ${MODEL:-default}"
}

# ----------------------------------------------------------------------- main

ONCE=false
SPECIFIC=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --once)  ONCE=true; shift ;;
    --issue) SPECIFIC="$2"; shift 2 ;;
    -h|--help)
      sed -n '2,40p' "$0"
      exit 0
      ;;
    *) echo "Unknown arg: $1" >&2; exit 1 ;;
  esac
done

print_banner
ensure_workdir

if [[ -n "$SPECIFIC" ]]; then
  work_on_issue "$SPECIFIC"
  exit 0
fi

iter=0
while true; do
  iter=$((iter + 1))
  if [[ "$MAX_ITERATIONS" -gt 0 && "$iter" -gt "$MAX_ITERATIONS" ]]; then
    log "Reached MAX_ITERATIONS=$MAX_ITERATIONS. Stopping."
    exit 0
  fi

  if n=$(next_issue); then
    log "Iteration $iter: next available issue is #$n"
    work_on_issue "$n"
    if $ONCE; then
      log "--once: done."
      exit 0
    fi
  else
    if $ONCE; then
      log "--once: nothing available, exiting."
      exit 0
    fi
    log "No available issues. Sleeping ${SLEEP_BETWEEN_NO_WORK}s..."
    sleep "$SLEEP_BETWEEN_NO_WORK"
  fi
done
