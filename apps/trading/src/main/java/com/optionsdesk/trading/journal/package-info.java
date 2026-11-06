/**
 * The journal: PostgreSQL tables for books, positions, legs, orders, fills, marks, signals and
 * audit events. The schema is owned by Flyway migrations in {@code db/migration} (never edit an
 * applied one; add the next version). Repositories arrive with the engine (P3).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Journal")
package com.optionsdesk.trading.journal;
