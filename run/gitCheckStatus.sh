#!/usr/bin/env bash
set -euo pipefail

OUT_DIR="$1"

# Resolve both paths before changing directory.
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
REPO_DIR="$(git rev-parse --show-toplevel)"

cd "$REPO_DIR"

COMMIT="$(git rev-parse HEAD)"
DIRTY=false

if [[ -n "$(git status --porcelain)" ]]; then
    DIRTY=true
fi

# Append metadata to Settings.ini.
cat >> "$OUT_DIR/Settings.ini" <<EOF
GIT_COMMIT=$COMMIT
GIT_DIRTY=$DIRTY
EOF

if [[ "$DIRTY" == true ]]; then

    # Include both staged and unstaged modifications to tracked files.
    git diff HEAD --binary > "$OUT_DIR/GitChanges.patch"

    # Remove an empty patch.
    if [[ ! -s "$OUT_DIR/GitChanges.patch" ]]; then
        rm "$OUT_DIR/GitChanges.patch"
    fi

    # Archive untracked files, excluding ignored files.
    # Avoid including large forcing datasets in the source record.
    git ls-files --others --exclude-standard -z \
        | python3 -c '
import sys, tarfile

output = sys.argv[1]
paths = [p.decode("utf-8") for p in sys.stdin.buffer.read().split(b"\0") if p]

# Preserve untracked source and configuration files.
extensions = (".java", ".sh", ".ini", ".xml", ".properties", ".md")
paths = [p for p in paths if p.endswith(extensions)]

if paths:
    with tarfile.open(output, "w:gz") as archive:
        for path in paths:
            archive.add(path, arcname=path)
' "$OUT_DIR/GitUntracked.tar.gz"

fi