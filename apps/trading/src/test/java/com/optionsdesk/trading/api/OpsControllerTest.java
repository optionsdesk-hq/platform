package com.optionsdesk.trading.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.optionsdesk.trading.ops.DeployGuard;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer only: the controller's HTTP contract, with the guard mocked. */
@WebMvcTest(OpsController.class)
class OpsControllerTest {

  @Autowired MockMvc mvc;
  @MockBean DeployGuard guard;

  @Test
  void deployableWhenTheGuardAllows() throws Exception {
    Mockito.when(guard.blockedReason()).thenReturn(Optional.empty());
    mvc.perform(get("/ops/deployable"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deployable").value(true));
  }

  @Test
  void conflictWithTheReasonWhenBlocked() throws Exception {
    Mockito.when(guard.blockedReason()).thenReturn(Optional.of("market hours"));
    mvc.perform(get("/ops/deployable"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.deployable").value(false))
        .andExpect(jsonPath("$.reason").value("market hours"));
  }
}
