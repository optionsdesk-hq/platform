package com.optionsdesk.trading.alerts.service;

import com.optionsdesk.trading.alerts.port.Alerts;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Alerts queued and delivered by one background thread, with retries: each message is tried
 * immediately, then after each delay in {@code backoff}; after the last it is dropped and logged.
 * The queue is bounded: when full (a long outage), new info messages are dropped but urgent ones
 * still push in by dropping the oldest. Every message is also logged, so nothing is lost from the
 * record. A label (e.g. "staging") prefixes each text.
 */
public final class QueuedAlerts implements Alerts, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(QueuedAlerts.class);

  private record Message(String text, boolean urgent) {}

  private final Consumer<String> sender;
  private final List<Duration> backoff;
  private final String label;
  private final BlockingQueue<Message> queue;
  private final Thread worker;
  private final AtomicLong sent = new AtomicLong();
  private final AtomicLong dropped = new AtomicLong();

  public QueuedAlerts(Consumer<String> sender, List<Duration> backoff, String label, int capacity) {
    this.sender = sender;
    this.backoff = List.copyOf(backoff);
    this.label = label == null || label.isBlank() ? "" : "[" + label + "] ";
    this.queue = new LinkedBlockingQueue<>(capacity);
    this.worker = Thread.ofVirtual().name("alerts").start(this::run);
  }

  @Override
  public void info(String text) {
    log.info("alert: {}", text);
    if (!queue.offer(new Message(label + text, false))) {
      dropped.incrementAndGet();
    }
  }

  @Override
  public void urgent(String text) {
    log.warn("ALERT: {}", text);
    var m = new Message(label + "⚠️ " + text, true);
    while (!queue.offer(m)) {
      if (queue.poll() != null) {
        dropped.incrementAndGet();
      }
    }
  }

  private void run() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        deliver(queue.take());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void deliver(Message m) throws InterruptedException {
    for (int attempt = 0; ; attempt++) {
      try {
        sender.accept(m.text());
        sent.incrementAndGet();
        return;
      } catch (RuntimeException e) {
        if (attempt >= backoff.size()) {
          dropped.incrementAndGet();
          log.error(
              "alert not delivered after {} tries: {} ({})", attempt + 1, m.text(), e.getMessage());
          return;
        }
        Thread.sleep(backoff.get(attempt));
      }
    }
  }

  public long sent() {
    return sent.get();
  }

  public long dropped() {
    return dropped.get();
  }

  @Override
  public void close() {
    worker.interrupt();
  }
}
