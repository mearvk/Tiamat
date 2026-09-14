#!/usr/bin/env bash
#
# verify-dragon-lore.sh
# =====================
# Verifies that the Tiamat software and its configuration/data files address
# "Tiamat, the lore of a powerful dragon" (Mesopotamian / Babylonian myth) and
# NOT the retired Captain-Marvell superhero subject matter.
#
# It checks:
#   1. training-data.json  (source + output copy) — the stat entries
#   2. search-engines.config (source + output copy) — queries + relevance keywords
#
# Exit 0 = all checks pass. Exit 1 = one or more checks failed.

set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PASS=0
FAIL=0

pass() { echo "  PASS: $1"; PASS=$((PASS+1)); }
fail() { echo "  FAIL: $1"; FAIL=$((FAIL+1)); }

# Files that must reflect the dragon lore (source of truth + built copy).
DATA_FILES=(
  "source/training/training-data.json"
  "output/production/Tiamat/training/training-data.json"
)
CONFIG_FILES=(
  "source/configuration/search-engines.config"
  "output/production/Tiamat/configuration/search-engines.config"
)

# Dragon-lore terms we REQUIRE to be present (at least these, case-insensitive).
REQUIRE_DATA=("dragon" "Mesopotamian" "Enuma Elish" "Marduk" "primordial" "chaos")
REQUIRE_CONFIG_QUERIES=("dragon" "Mesopotamian" "Enuma Elish")
REQUIRE_CONFIG_KEYWORDS=("dragon" "sea serpent" "Marduk" "primordial")

# Retired superhero terms that MUST NOT appear anywhere in the subject config/data.
FORBIDDEN=("carol danvers" "mar-vell" "ms marvel" "avengers" "shazam" "US Traditions" "American ideals" "USA Traditions")

echo "=== Verifying Tiamat addresses the powerful-dragon lore ==="
echo

echo "[1] Training data — required dragon-lore terms present:"
for f in "${DATA_FILES[@]}"; do
  path="$ROOT/$f"
  if [[ ! -f "$path" ]]; then fail "$f missing"; continue; fi
  for term in "${REQUIRE_DATA[@]}"; do
    if grep -qi -- "$term" "$path"; then pass "$f contains '$term'"; else fail "$f missing '$term'"; fi
  done
done
echo

echo "[2] Config — required query terms present:"
for f in "${CONFIG_FILES[@]}"; do
  path="$ROOT/$f"
  if [[ ! -f "$path" ]]; then fail "$f missing"; continue; fi
  qline="$(grep -E '^queries=' "$path" || true)"
  for term in "${REQUIRE_CONFIG_QUERIES[@]}"; do
    if echo "$qline" | grep -qi -- "$term"; then pass "$f queries contain '$term'"; else fail "$f queries missing '$term'"; fi
  done
  kline="$(grep -E '^image.relevance.keywords=' "$path" || true)"
  for term in "${REQUIRE_CONFIG_KEYWORDS[@]}"; do
    if echo "$kline" | grep -qi -- "$term"; then pass "$f keywords contain '$term'"; else fail "$f keywords missing '$term'"; fi
  done
done
echo

echo "[3] No retired superhero (Captain Marvell) terms remain:"
for f in "${DATA_FILES[@]}" "${CONFIG_FILES[@]}"; do
  path="$ROOT/$f"
  if [[ ! -f "$path" ]]; then fail "$f missing"; continue; fi
  for term in "${FORBIDDEN[@]}"; do
    if grep -qi -- "$term" "$path"; then fail "$f still contains forbidden term '$term'"; else pass "$f free of '$term'"; fi
  done
done
echo

echo "[4] Subject field declares the dragon lore:"
for f in "${DATA_FILES[@]}"; do
  path="$ROOT/$f"
  if grep -qi '"lore"[[:space:]]*:[[:space:]]*"the powerful dragon"' "$path"; then
    pass "$f declares lore = the powerful dragon"
  else
    fail "$f does not declare the powerful-dragon lore"
  fi
done
echo

echo "=== Result: $PASS passed, $FAIL failed ==="
if [[ $FAIL -gt 0 ]]; then
  echo "VERIFICATION FAILED"
  exit 1
fi
echo "VERIFICATION PASSED — software and config address Tiamat the powerful dragon."
exit 0
