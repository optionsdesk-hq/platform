package com.optionsdesk.trading.journal.model;

/**
 * A book: one strategy variant's account in the journal.
 *
 * @param state ACTIVE, PAUSED (no new sessions) or KILLED
 */
public record Book(String id, String strategyId, String mode, int lots, String state) {}
