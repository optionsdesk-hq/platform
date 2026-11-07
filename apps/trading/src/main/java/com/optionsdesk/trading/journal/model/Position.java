package com.optionsdesk.trading.journal.model;

import com.optionsdesk.shared.domain.Leg;
import java.util.List;
import java.util.Optional;

/** A position as recorded, with its legs at their entry prices. */
public record Position(
    long id,
    String bookId,
    String status,
    List<Leg> legs,
    double credit,
    double costs,
    Optional<Double> realisedPnl,
    Optional<String> exitReason) {}
