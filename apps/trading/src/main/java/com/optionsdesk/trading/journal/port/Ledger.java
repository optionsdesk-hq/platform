package com.optionsdesk.trading.journal.port;

import com.optionsdesk.trading.execution.model.Execution;
import com.optionsdesk.trading.journal.repository.PositionJournal;
import java.util.List;
import java.util.Optional;

/** Where books record positions. {@link PositionJournal} is the Postgres implementation. */
public interface Ledger {

  /** An opened position from its fills; returns its id. */
  long opened(
      String bookId, List<Execution> fills, Optional<String> eventTag, Optional<Long> signalId);

  /** An open that failed: what filled, the unwind, and why. */
  long failed(String bookId, List<Execution> fills, List<Execution> unwind, String reason);

  /** A position's close. */
  void closed(long positionId, List<Execution> fills, String reason);
}
