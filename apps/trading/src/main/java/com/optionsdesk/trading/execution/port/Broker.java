package com.optionsdesk.trading.execution.port;

import com.optionsdesk.trading.execution.model.Execution;
import com.optionsdesk.trading.execution.model.OrderRequest;

/** Sends one order and returns its fill, or throws {@link OrderRejected}. */
public interface Broker {

  Execution execute(OrderRequest request) throws OrderRejected;

  /** The broker refused the order, or it couldn't be priced. Nothing filled. */
  final class OrderRejected extends Exception {
    private static final long serialVersionUID = 1L;

    private final transient OrderRequest request;

    public OrderRejected(OrderRequest request, String reason) {
      super(
          request.side()
              + " "
              + request.quantity()
              + " "
              + request.contract().tradingSymbol()
              + ": "
              + reason);
      this.request = request;
    }

    public OrderRequest request() {
      return request;
    }
  }
}
