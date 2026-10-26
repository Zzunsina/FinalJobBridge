package com.employment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import com.employment.global.common.Problem;

class UnexpectedFailureRegressionTests {
    @RestController
    static class Broken {
        @GetMapping("/broken")
        String broken() { throw new NullPointerException("private implementation details"); }
    }
    @Test
    void unexpectedFailureStaysGeneric500WithoutLeakingDetails() throws Exception {
        MockMvcBuilders.standaloneSetup(new Broken()).setControllerAdvice(new Problem.Handler()).build()
            .perform(get("/broken")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("HTTP_500"))
            .andExpect(jsonPath("$.message").value("서버 처리 중 오류가 발생했습니다."))
            .andExpect(jsonPath("$.traceId").isNotEmpty())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private implementation"))));
    }
}
