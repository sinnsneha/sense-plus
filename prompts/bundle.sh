#!/usr/bin/env bash
# Builds a ready-to-paste chat message: the task prompt + the CURRENT contents of the files it lists.
#
#   prompts/bundle.sh 00                 -> system prompt
#   prompts/bundle.sh 13                 -> segment 13 task + its files
#   prompts/bundle.sh T-bugfix a.java    -> template + any extra files you name
#   prompts/bundle.sh 13 --print         -> print instead of copying to the clipboard
#
# Always re-run it after committing the previous segment, so the model sees the latest code.
set -euo pipefail
cd "$(dirname "$0")/.."

[ $# -ge 1 ] || { sed -n '2,9p' "$0"; exit 1; }
id="$1"; shift
print=0; extra=()
for a in "$@"; do [ "$a" = "--print" ] && print=1 || extra+=("$a"); done

task=$(ls prompts/"$id".md prompts/"$id"-*.md 2>/dev/null | head -1 || true)
[ -n "$task" ] || { echo "No prompt file matching prompts/$id-*.md" >&2; exit 1; }

lang() { case "${1##*.}" in java) echo java;; xml) echo xml;; kts) echo kotlin;; md) echo markdown;; *) echo "";; esac; }

files=$(grep -m1 '^FILES:' "$task" | sed 's/^FILES://' || true)

out=$(
  if grep -q '^<!-- PASTE BELOW -->$' "$task"; then
    sed -n '/^<!-- PASTE BELOW -->$/,$p' "$task" | tail -n +2
  else
    cat "$task"
  fi
  all="$files ${extra[*]:-}"
  if [ -n "${all// /}" ]; then
    echo; echo "---"; echo "## Pasted files (current contents)"
    for f in $all; do
      if [ -f "$f" ]; then
        echo; echo "### $f"; echo "\`\`\`\`$(lang "$f")"; cat "$f"; echo; echo "\`\`\`\`"
      else
        echo; echo "### $f: does not exist yet"
      fi
    done
  fi
)

if [ "$print" = 1 ]; then printf '%s\n' "$out"; exit 0; fi
if [ -n "${WAYLAND_DISPLAY:-}" ] && command -v wl-copy >/dev/null; then printf '%s\n' "$out" | wl-copy
elif command -v xclip >/dev/null; then printf '%s\n' "$out" | xclip -selection clipboard
else printf '%s\n' "$out"; exit 0; fi
echo "Copied $(basename "$task") to clipboard: $(printf '%s\n' "$out" | wc -l) lines, $(printf '%s' "$out" | wc -c) chars."
for f in $files ${extra[@]+"${extra[@]}"}; do [ -f "$f" ] || echo "  note: $f does not exist yet"; done
