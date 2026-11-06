/**
 * Books: one per strategy variant (e.g. {@code book-a}). A book turns a strategy's decisions into
 * orders (execution) and records (journal), one at a time on its own thread. A failure that would
 * leave the record and the market apart (an order rejected mid-position) halts the book and says
 * why; it never carries on guessing.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Books")
package com.optionsdesk.trading.book;
