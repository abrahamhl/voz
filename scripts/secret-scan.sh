#!/usr/bin/env bash
# Fails if tracked files look like they contain credentials. Run before every push; CI runs it too.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
patterns=(
  'AIza[0-9A-Za-z_-]{35}'                 # Google API key
  'sk-[A-Za-z0-9_-]{20,}'                 # generic "sk-" API keys
  'gh[pousr]_[A-Za-z0-9]{30,}'            # GitHub tokens
  'xox[baprs]-[A-Za-z0-9-]{10,}'          # Slack tokens
  '-----BEGIN [A-Z ]*PRIVATE KEY-----'    # private keys
  'AKIA[0-9A-Z]{16}'                      # AWS access key id
  '(api[_-]?key|secret|password|passwd|token)["'"'"']?\s*[:=]\s*["'"'"'][^"'"'"'$ ]{12,}["'"'"']'
)
status=0
for p in "${patterns[@]}"; do
  if git grep --untracked -nIE -- "$p" -- ':!scripts/secret-scan.sh' ; then
    echo "::error::possible secret matching: $p"
    status=1
  fi
done
if git ls-files | grep -Ei '\.(jks|keystore|p12|pem)$|(^|/)\.env|local\.properties$' ; then
  echo "::error::sensitive file is tracked"
  status=1
fi
[ "$status" -eq 0 ] && echo "secret scan: clean"
exit "$status"
