#!/usr/bin/env bash
# Every active parameter line in tuned config files must be in a block (lines without a blank line
# between them) that starts with a "[MANUAL CHANGE – Marom]" comment.
set -euo pipefail
cd "$(dirname "$0")/.."
rc=0
check() { # file commentChar
  awk -v f="$1" -v c="$2" '
    { line=$0; sub(/^[ \t]+/,"",line) }
    line=="" { marked=0; next }
    index(line,c)==1 { if (line ~ /\[MANUAL CHANGE – Marom\]/) marked=1; next }
    line ~ /^\[[a-z]+\]$/ { next }
    !marked { printf "✖ %s:%d: parameter without [MANUAL CHANGE – Marom] comment: %s\n", f, NR, line; bad=1 }
    END { exit bad }' "$1" || rc=1
}
check infra/postgres/postgresql.conf "#"
check infra/pgbouncer/pgbouncer.ini ";"
exit $rc
