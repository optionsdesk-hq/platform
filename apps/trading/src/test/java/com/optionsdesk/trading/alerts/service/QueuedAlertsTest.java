package com.optionsdesk.trading.alerts.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class QueuedAlertsTest {

  static void await(java.util.function.BooleanSupplier done) throws InterruptedException {
    for (int i = 0; i < 200 && !done.getAsBoolean(); i++) {
      Thread.sleep(10);
    }
  }

  @Test
  void deliversInOrderWithTheLabel() throws Exception {
    List<String> got = new CopyOnWriteArrayList<>();
    try (var a = new QueuedAlerts(got::add, List.of(), "staging", 10)) {
      a.info("one");
      a.urgent("two");
      await(() -> got.size() == 2);
      assertThat(got).containsExactly("[staging] one", "[staging] ⚠️ two");
      assertThat(a.sent()).isEqualTo(2);
    }
    try (var a = new QueuedAlerts(got::add, List.of(), "", 10)) {
      a.info("plain");
      await(() -> got.size() == 3);
      assertThat(got.get(2)).isEqualTo("plain");
    }
  }

  @Test
  void retriesThenDelivers() throws Exception {
    var tries = new AtomicInteger();
    List<String> got = new CopyOnWriteArrayList<>();
    try (var a =
        new QueuedAlerts(
            t -> {
              if (tries.incrementAndGet() < 3) {
                throw new IllegalStateException("telegram down");
              }
              got.add(t);
            },
            List.of(Duration.ofMillis(5), Duration.ofMillis(5)),
            "",
            10)) {
      a.urgent("stop hit");
      await(() -> got.size() == 1);
      assertThat(tries.get()).isEqualTo(3);
      assertThat(a.dropped()).isZero();
    }
  }

  @Test
  void dropsAfterTheLastRetryAndCarriesOn() throws Exception {
    List<String> got = new CopyOnWriteArrayList<>();
    try (var a =
        new QueuedAlerts(
            t -> {
              if (t.contains("bad")) {
                throw new IllegalStateException("400");
              }
              got.add(t);
            },
            List.of(Duration.ofMillis(5)),
            "",
            10)) {
      a.info("bad message");
      a.info("good message");
      await(() -> got.size() == 1);
      assertThat(got).containsExactly("good message");
      assertThat(a.dropped()).isEqualTo(1);
    }
  }

  @Test
  void whenFullInfoIsDroppedButUrgentPushesIn() throws Exception {
    var gate = new CountDownLatch(1);
    List<String> got = new CopyOnWriteArrayList<>();
    try (var a =
        new QueuedAlerts(
            t -> {
              try {
                gate.await(5, TimeUnit.SECONDS); // the sender is stuck: the queue fills up
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              got.add(t);
            },
            List.of(),
            "",
            2)) {
      a.info("i0"); // taken by the worker, stuck in send
      await(() -> true);
      Thread.sleep(50);
      a.info("i1");
      a.info("i2");
      a.info("i3"); // queue full: dropped
      a.urgent("u"); // pushes out the oldest (i1)
      gate.countDown();
      await(() -> got.size() == 3);
      assertThat(got).containsExactly("i0", "i2", "⚠️ u");
      assertThat(a.dropped()).isEqualTo(2);
    }
  }
}
