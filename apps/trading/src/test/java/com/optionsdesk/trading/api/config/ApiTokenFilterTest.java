package com.optionsdesk.trading.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiTokenFilterTest {

  static MockHttpServletResponse run(ApiTokenFilter f, String method, String auth)
      throws Exception {
    var req = new MockHttpServletRequest(method, "/api/kill");
    if (auth != null) {
      req.addHeader("Authorization", auth);
    }
    var res = new MockHttpServletResponse();
    f.doFilter(req, res, new MockFilterChain());
    return res;
  }

  @Test
  void withNoTokenConfiguredEveryWriteIsRefused() throws Exception {
    var f = new ApiTokenFilter("");
    assertThat(run(f, "POST", "Bearer ").getStatus()).isEqualTo(403);
    assertThat(run(f, "POST", null).getContentAsString()).contains("no API token is configured");
    assertThat(run(new ApiTokenFilter(null), "DELETE", "Bearer x").getStatus()).isEqualTo(403);
  }

  @Test
  void readsPassWritesNeedTheExactToken() throws Exception {
    var f = new ApiTokenFilter("abc");
    assertThat(run(f, "GET", null).getStatus()).isEqualTo(200);
    assertThat(run(f, "POST", "Bearer abc").getStatus()).isEqualTo(200);
    assertThat(run(f, "POST", "Bearer abcd").getStatus()).isEqualTo(401);
    assertThat(run(f, "POST", "Basic abc").getStatus()).isEqualTo(401);
    assertThat(run(f, "POST", "Bearer abc").getContentAsString()).doesNotContain("abc");
  }
}
