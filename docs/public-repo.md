# What may live in this public repository

This repo is public: the engine, the risk framework, execution, the broker adapter, the journal, pricing and the
interfaces strategies plug into.

**Never here** (they live in the private `optionsdesk-hq/strategies` repo):
- strategy implementations, their parameters and the rulebook values;
- research results (₹ per trade, win rates, which variant won), in code, docs, commit messages or PR descriptions;
- market data of any kind (recorded quotes, candles, EOD files), including test fixtures: exchange data may not be
  republished;
- account, position or P&L data; secrets of course.

Tests here use made-up numbers. Golden and replay tests that need real data or real strategies run in the private
repo, which builds against this one.

CI enforces the obvious cases (`scripts/check-public.sh`); the rest is on review.
