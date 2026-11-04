package com.employment.ads;
import com.employment.auth.AuthService;
import com.employment.global.common.Problem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class PortOneWebhookTests {
 @Test void v1WebhookRequestsAuthoritativeLookupAndNeverUsesSuppliedStatus()throws Exception{
  var service=mock(AdvertisingService.class);var controller=new AdvertisingController(service,mock(AuthService.class),new ObjectMapper());var request=new MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");String id="jb-ad-12345678-1234-1234-1234-123456789abc";
  controller.webhook("{\"merchant_uid\":\""+id+"\",\"imp_uid\":\"imp_1234\",\"status\":\"paid\"}",request);verify(service).webhook(id);
  reset(service);assertThatThrownBy(()->controller.webhook("{\"data\":{\"paymentId\":\""+id+"\"}}",request)).isInstanceOf(Problem.class);verifyNoInteractions(service);
 }
}
