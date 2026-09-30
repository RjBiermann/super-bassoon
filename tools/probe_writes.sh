#!/usr/bin/env bash
# Live verification of upstream account-action endpoints.
# Token source: browser DevTools → Network → any upstream-api-host.example request → Authorization: Bearer <value>
# (Kinde JWT — not sender-bound; runs from any machine). Every write is reverted. ~2s spacing.
set -u
UA="Mozilla/5.0 (X11; Linux x86_64; rv:156.0) Gecko/20100101 Firefox/156.0"   # must match token's valid_agent
read -r -p "Paste auth token (long-lived, from POST /v2/auth): " TOKEN
[ -z "${TOKEN:-}" ] && { echo "no token, bye"; exit 1; }

H=(-s --http2 --compressed -A "$UA"
   -H "Accept: application/json"
   -H "Authorization: Bearer $TOKEN"
   -H "Referer: https://upstream-site.example/")
BASE="https://upstream-api-host.example"

step() { echo; echo "--- $1 ---"; sleep 2; }
# prints status code + 160 chars of body
probe() { # method url
  local code body
  body=$(curl "${H[@]}" -X "$1" -o /tmp/rg_probe.json -w "%{http_code}" "$2")
  echo "$1 $2"
  echo "  -> $body : $(head -c 160 /tmp/rg_probe.json)"
}

step "reads"
probe GET "$BASE/v2/likes"                                  # my liked gifs
probe GET "$BASE/v2/feeds/liked?page=1&count=5"             # liked feed
probe GET "$BASE/v2/niches/following"                       # followed niches
probe GET "$BASE/v2/user_profile"                           # my profile

step "pick test targets from trending"
curl "${H[@]}" "$BASE/v2/feeds/trending/popular?count=1" > /tmp/rg_probe.json
GID=$(python3 -c "import json;print(json.load(open('/tmp/rg_probe.json'))['gifs'][0]['id'])")
UN=$(python3 -c "import json;print(json.load(open('/tmp/rg_probe.json'))['gifs'][0]['userName'])")
echo "gif=$GID creator=$UN"

step "LIKE then UNLIKE  ($GID)"
probe PUT  "$BASE/v2/gifs/$GID/like"
probe GET  "$BASE/v2/likes"
probe DELETE "$BASE/v2/gifs/$GID/like"

step "FOLLOW then UNFOLLOW creator  ($UN)"
probe PUT    "$BASE/v1/me/follows/$UN"
probe GET    "$BASE/v1/me/followers/populated?count=5"
probe DELETE "$BASE/v1/me/follows/$UN"

step "NICHE SUBSCRIBE then UNSUBSCRIBE"
NICHE=$(python3 - <<'EOF'
import json
try:
    d = json.load(open('/tmp/rg_probe.json'))
    g = d['gifs'][0]
    n = (g.get('niches') or [None])[0]
    print(n['id'] if isinstance(n, dict) and n.get('id') else (n or ''))
except Exception:
    print('')
EOF
)
if [ -n "$NICHE" ]; then
  probe POST   "$BASE/v2/niches/$NICHE/subscription"
  probe GET    "$BASE/v2/niches/following"
  probe DELETE "$BASE/v2/niches/$NICHE/subscription"
else
  echo "no niche on test gif — fetch one: GET /v2/niches/search and subscribe manually"
fi

echo; echo "done — all writes reverted."
