-- The trading journal: every book, position, leg, order, fill, mark, signal and audit event.
-- Flyway applies this once, in order; never edit an applied migration, add V2__… instead.

CREATE TABLE book (
    id           TEXT PRIMARY KEY,                       -- e.g. 'spread-a', 'intraday-b'
    strategy_id  TEXT        NOT NULL,
    mode         TEXT        NOT NULL CHECK (mode IN ('PAPER', 'LIVE')),
    lots         INTEGER     NOT NULL CHECK (lots > 0),
    state        TEXT        NOT NULL CHECK (state IN ('ACTIVE', 'PAUSED', 'KILLED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE signal (
    id        BIGSERIAL PRIMARY KEY,
    day       DATE        NOT NULL,
    rule      TEXT        NOT NULL,                        -- e.g. 'spread-a'
    decision  TEXT        NOT NULL,                        -- e.g. 'BULL_PUT', 'BEAR_CALL', 'NONE'
    inputs    JSONB       NOT NULL,                        -- the numbers the decision used
    at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (day, rule)
);

CREATE TABLE position (
    id            BIGSERIAL PRIMARY KEY,
    book_id       TEXT        NOT NULL REFERENCES book (id),
    status        TEXT        NOT NULL CHECK (status IN ('PROPOSED', 'OPENING', 'OPEN', 'ADJUSTING', 'CLOSING', 'CLOSED', 'REJECTED', 'FAILED')),
    opened_at     TIMESTAMPTZ,
    closed_at     TIMESTAMPTZ,
    credit        NUMERIC(14, 2),                          -- ₹ received at entry (negative = debit)
    costs         NUMERIC(14, 2) NOT NULL DEFAULT 0,       -- all charges so far
    realised_pnl  NUMERIC(14, 2),
    exit_reason   TEXT,
    event_tag     TEXT,                                    -- e.g. 'RBI policy (2026-12-04)'
    signal_id     BIGINT REFERENCES signal (id)
);
CREATE INDEX position_book_status ON position (book_id, status);

CREATE TABLE leg (
    id             BIGSERIAL PRIMARY KEY,
    position_id    BIGINT        NOT NULL REFERENCES position (id),
    trading_symbol TEXT          NOT NULL,
    underlying     TEXT          NOT NULL,
    expiry         DATE          NOT NULL,
    strike         NUMERIC(10, 2) NOT NULL,
    opt_right      TEXT          NOT NULL CHECK (opt_right IN ('CE', 'PE')),
    lots           INTEGER       NOT NULL CHECK (lots <> 0), -- negative = short
    lot_size       INTEGER       NOT NULL CHECK (lot_size > 0),
    entry_price    NUMERIC(10, 2) NOT NULL,
    exit_price     NUMERIC(10, 2)
);

CREATE TABLE "order" (
    id          BIGSERIAL PRIMARY KEY,
    leg_id      BIGINT      NOT NULL REFERENCES leg (id),
    side        TEXT        NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity    INTEGER     NOT NULL CHECK (quantity > 0),
    type        TEXT        NOT NULL CHECK (type IN ('LIMIT', 'MARKET')),
    limit_price NUMERIC(10, 2),
    state       TEXT        NOT NULL CHECK (state IN ('NEW', 'SENT', 'PARTIAL', 'FILLED', 'REJECTED', 'CANCELLED')),
    tag         TEXT        NOT NULL UNIQUE,               -- idempotency key sent to the broker
    source      TEXT        NOT NULL DEFAULT 'rule' CHECK (source IN ('rule', 'manual')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fill (
    id        BIGSERIAL PRIMARY KEY,
    order_id  BIGINT         NOT NULL REFERENCES "order" (id),
    price     NUMERIC(10, 2) NOT NULL,
    quantity  INTEGER        NOT NULL CHECK (quantity > 0),
    charges   NUMERIC(10, 2) NOT NULL DEFAULT 0,
    at        TIMESTAMPTZ    NOT NULL
);

CREATE TABLE mark (                                        -- one row per position per tick (partition monthly later)
    position_id  BIGINT        NOT NULL REFERENCES position (id),
    at           TIMESTAMPTZ   NOT NULL,
    spot         NUMERIC(10, 2) NOT NULL,
    pnl          NUMERIC(14, 2) NOT NULL,
    sane         BOOLEAN       NOT NULL,                   -- false = prices failed the sanity check; not acted on
    delta        DOUBLE PRECISION,
    gamma        DOUBLE PRECISION,
    theta        DOUBLE PRECISION,
    vega         DOUBLE PRECISION,
    PRIMARY KEY (position_id, at)
);

CREATE TABLE audit_event (                                 -- who did what: manual actions, kill switch, exits
    id       BIGSERIAL PRIMARY KEY,
    at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    module   TEXT        NOT NULL,
    type     TEXT        NOT NULL,
    payload  JSONB       NOT NULL
);

CREATE TABLE job_run (
    id        BIGSERIAL PRIMARY KEY,
    job       TEXT        NOT NULL,
    started   TIMESTAMPTZ NOT NULL,
    finished  TIMESTAMPTZ,
    status    TEXT        NOT NULL CHECK (status IN ('RUNNING', 'OK', 'FAILED')),
    rows      BIGINT,
    error     TEXT
);
