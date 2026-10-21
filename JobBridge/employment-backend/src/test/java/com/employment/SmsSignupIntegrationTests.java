package com.employment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.employment.global.common.Database.*;
import com.employment.auth.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SmsSignupIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SmsService sms;
    @MockitoBean SmsGateway gateway;
    final AtomicReference<String> code = new AtomicReference<>();
    @BeforeEach void captureSms() {
        doAnswer(call -> { code.set(call.getArgument(1)); return null; }).when(gateway).send(anyString(), anyString());
    }
    @Test void signupRequiresProofForSamePhoneAndConsumesOnlyAfterSuccess() throws Exception {
        String phone="01087654321";
        var body=fields("loginId", "sms"+UUID.randomUUID().toString().replace("-", "").substring(0,12),
            "password", "TestPassword123!", "name", "문자 인증 테스트", "email", UUID.randomUUID()+"@test.local",
            "phone", phone, "birthDate", "20000123", "gender", "FEMALE",
            "termsAccepted", true, "privacyAccepted", true, "agreementVersion", "2026-10-21",
            "companyName", "인증 테스트 기업", "businessNumber", "bad");
        mvc.perform(post("/api/auth/signup/company").with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
        var sent=mvc.perform(post("/api/auth/sms/send").with(csrf()).contentType("application/json").content(json.writeValueAsString(fields("phone", "010-8765-4321"))))
            .andExpect(status().isOk()).andReturn();
        var sentJson=json.readTree(sent.getResponse().getContentAsString());
        assertThat(sentJson.has("code")).isFalse();
        var verified=mvc.perform(post("/api/auth/sms/verify").with(csrf()).contentType("application/json").content(json.writeValueAsString(fields("phone", phone,"challengeId",sentJson.get("challengeId").asText(),"code", code.get()))))
            .andExpect(status().isOk()).andReturn();
        String token=json.readTree(verified.getResponse().getContentAsString()).get("verificationToken").asText();
        body.put("smsVerificationToken",token); body.put("phone","01087654322");
        mvc.perform(post("/api/auth/signup/company").with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("fieldErrors[0].field").value("phone"));
        body.put("phone",phone);
        mvc.perform(post("/api/auth/signup/company").with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
        sms.requireVerified(phone,token); // Failed DB transaction retains proof for correction.
        body.put("businessNumber","8765432109");
        mvc.perform(post("/api/auth/signup/company").with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isCreated());
        assertThatThrownBy(() -> sms.requireVerified(phone,token)).isInstanceOf(com.employment.global.common.Problem.class);
        mvc.perform(post("/api/auth/sms/send").with(csrf()).contentType("application/json").content(json.writeValueAsString(fields("phone",phone))))
            .andExpect(status().isConflict());
    }
}
