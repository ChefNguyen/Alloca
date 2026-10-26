#!/usr/bin/env bash
# Applies backend/scripts/dev-seed.sql to the database configured in backend/env (or backend/.env).
# Credentials are read from the file and passed to psql through PG* variables, never printed.
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE=""
for candidate in .env env; do
  [ -f "$candidate" ] && ENV_FILE="$candidate" && break
done
[ -n "$ENV_FILE" ] || { echo "backend/.env (or backend/env) not found" >&2; exit 1; }

PSQL="$(command -v psql || true)"
[ -n "$PSQL" ] || PSQL=/usr/local/opt/libpq/bin/psql
[ -x "$PSQL" ] || { echo "psql not found; install it with 'brew install libpq'" >&2; exit 1; }

eval "$(python3 -I - "$ENV_FILE" <<'PY'
import re, shlex, sys
from urllib.parse import urlparse

values = {}
for line in open(sys.argv[1], encoding="utf-8"):
    line = line.strip()
    if line and not line.startswith("#") and "=" in line:
        key, _, value = line.partition("=")
        values[key.strip()] = value.strip().strip('"').strip("'")

url = urlparse(re.sub(r"^jdbc:", "", values["SUPABASE_DB_URL"]))
exports = {
    "PGHOST": url.hostname,
    "PGPORT": str(url.port or 5432),
    "PGDATABASE": url.path.lstrip("/") or "postgres",
    "PGUSER": values["SUPABASE_DB_USERNAME"],
    "PGPASSWORD": values["SUPABASE_DB_PASSWORD"],
    "PGSSLMODE": "require",
}
for name, value in exports.items():
    print(f"export {name}={shlex.quote(value)}")
PY
)"

"$PSQL" -v ON_ERROR_STOP=1 -f scripts/dev-seed.sql
"$PSQL" -v ON_ERROR_STOP=1 -c "select username, role, is_active, length(password_hash) as hash_len from users order by username;"
