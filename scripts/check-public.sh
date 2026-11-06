#!/usr/bin/env bash
# This repository is public. Strategy code, rulebook values and market data live in the private
# optionsdesk-hq/strategies repo. Fail the build if any of it shows up here (INC-2026-004).
set -euo pipefail

fail=0

# 1. Market data and data stores: recorded quotes may not be republished.
if files=$(git ls-files | grep -E '\.(csv|csv\.gz|parquet|duckdb|sqlite)$'); then
  echo "::error::market data files in a public repo:"; echo "$files"; fail=1
fi

# 2. Strategy implementations and their data (the private plug-in's package and files).
if files=$(git ls-files | grep -E '(^|/)com/optionsdesk/strategies/|(^|/)rulebook/|strategy\.yaml$|golden/(strategies|straddle|calendar)'); then
  echo "::error::private strategy files in a public repo:"; echo "$files"; fail=1
fi
if hits=$(git grep -n -E '\b(StbtSignal|IntradayStraddle|DirectionalCalendar|ReentryGates|StraddleBook)\b' -- ':!scripts/check-public.sh'); then
  echo "::error::private strategy classes referenced in a public repo:"; echo "$hits"; fail=1
fi

if [ "$fail" -ne 0 ]; then
  echo "Move it to optionsdesk-hq/strategies (private). See docs/public-repo.md."
  exit 1
fi
echo "public-repo check: ok"
