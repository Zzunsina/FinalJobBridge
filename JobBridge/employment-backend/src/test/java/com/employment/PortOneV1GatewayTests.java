package com.employment.ads;

import com.fasterxml.jackson.databind.*;
import com.employment.global.common.Problem;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PortOneV1GatewayTests {
 private final ObjectMapper json=new ObjectMapper();
 private JsonNode raw(String status,int cancelled)throws Exception{return json.readTree("{\"merchant_uid\":\"jb-ad-test\",\"imp_uid\":\"imp_123456789\",\"pg_provider\":\"html5_inicis\",\"sandbox\":true,\"currency\":\"KRW\",\"amount\":242000,\"cancel_amount\":"+cancelled+",\"status\":\""+status+"\"}");}
 @Test void v1TokenLookupAndFullCancellationUseOfficialProtocol()throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var tokenCalls=new AtomicInteger();var cancelled=new AtomicBoolean();var errors=new java.util.concurrent.CopyOnWriteArrayList<String>();
  server.createContext("/",exchange->{try{
   String path=exchange.getRequestURI().getPath();String payload;
   if(path.equals("/users/getToken")){
    tokenCalls.incrementAndGet();var credentials=json.readTree(exchange.getRequestBody());
    if(!credentials.path("imp_key").asText().equals("test-api-key")||!credentials.path("imp_secret").asText().equals("test-api-secret"))errors.add("wrong credentials");
    payload="{\"code\":0,\"response\":{\"access_token\":\"test-access-token\",\"expired_at\":"+(Instant.now().getEpochSecond()+1800)+"}}";
   }else{
    if(!"test-access-token".equals(exchange.getRequestHeaders().getFirst("Authorization")))errors.add("wrong auth header");
    if(path.equals("/payments/cancel")){
     var body=json.readTree(exchange.getRequestBody());if(!body.path("imp_uid").asText().equals("imp_123456789")||body.path("checksum").asInt()!=242000||body.path("amount").asInt()!=242000)errors.add("wrong cancellation");cancelled.set(true);
    }else if(!path.equals("/payments/find/jb-ad-test")&&!path.equals("/payments/imp_123456789"))errors.add("wrong lookup path");
    if(!path.equals("/payments/cancel")&&!"include_sandbox=true".equals(exchange.getRequestURI().getQuery()))errors.add("sandbox lookup missing");
    payload="{\"code\":0,\"response\":"+raw(cancelled.get()?"cancelled":"paid",cancelled.get()?242000:0)+"}";
   }
   byte[] bytes=payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
  }catch(Exception e){errors.add(e.toString());exchange.sendResponseHeaders(500,-1);}finally{exchange.close();}});
  server.start();try{
   var gateway=new PortOneGateway(json,"imp12345678","channel-test","test-api-key","test-api-secret",URI.create("http://127.0.0.1:"+server.getAddress().getPort()),HttpClient.newHttpClient());
   var paid=gateway.payment("jb-ad-test");gateway.validate(paid,"jb-ad-test",242000);assertThat(paid.path("status").asText()).isEqualTo("PAID");
   var direct=gateway.paymentByProviderId("imp_123456789");gateway.validate(direct,"jb-ad-test",242000);assertThat(direct).isEqualTo(paid);
   gateway.cancel("jb-ad-test","전액 취소",242000);assertThat(gateway.payment("jb-ad-test").path("status").asText()).isEqualTo("CANCELLED");assertThat(tokenCalls).hasValue(1);assertThat(errors).isEmpty();assertThat(gateway.configuration()).doesNotContainKeys("apiKey","secret","apiSecret");
  }finally{server.stop(0);}
 }
 @Test void v1TestProviderAmountAndMerchantMustMatchAndPartialCancellationIsNotPaid()throws Exception{
  var gateway=new PortOneGateway(json,"imp12345678","channel-test","test-api-key","test-api-secret");
  var payment=gateway.normalize(raw("paid",0));gateway.validate(payment,"jb-ad-test",242000);
  assertThatThrownBy(()->gateway.validate(payment,"different-order",242000)).isInstanceOf(Problem.class);
  assertThatThrownBy(()->gateway.validate(payment,"jb-ad-test",1)).isInstanceOf(Problem.class);
  var live=(com.fasterxml.jackson.databind.node.ObjectNode)raw("paid",0);live.put("sandbox",false);assertThatThrownBy(()->gateway.validate(gateway.normalize(live),"jb-ad-test",242000)).isInstanceOf(Problem.class);
  live.remove("sandbox");assertThatThrownBy(()->gateway.validate(gateway.normalize(live),"jb-ad-test",242000)).isInstanceOf(Problem.class);
  assertThat(gateway.normalize(raw("cancelled",1000)).path("status").asText()).isEqualTo("PARTIAL_CANCELLED");assertThat(gateway.normalize(raw("paid",1000)).path("status").asText()).isEqualTo("PARTIAL_CANCELLED");
 }
 @Test void legacyOrdersMatchOnlyTheirExactFortyCharacterMerchantId()throws Exception {
  var gateway=new PortOneGateway(json,"imp12345678","channel-test","test-api-key","test-api-secret");
  String old="jb-ad-83c6d2cc-8834-4900-892b-e3ba987596e0";
  var r=(com.fasterxml.jackson.databind.node.ObjectNode)raw("paid",0);r.put("merchant_uid",old.substring(0,40));
  gateway.validate(gateway.normalize(r),old,242000);
  r.put("merchant_uid",old.substring(0,39));assertThatThrownBy(()->gateway.validate(gateway.normalize(r),old,242000)).isInstanceOf(Problem.class);
  assertThat(PortOneGateway.merchantId("jb-ad-83c6d2cc88344900892be3ba987596e0")).hasSize(38);
 } @Test void failedRetryDoesNotHideEarlierPaidTransaction()throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",e->{try{
   String path=e.getRequestURI().getPath();String body;
   if(path.equals("/users/getToken"))body="{\"code\":0,\"response\":{\"access_token\":\"token\",\"expired_at\":"+(Instant.now().getEpochSecond()+1800)+"}}";
   else body="{\"code\":0,\"response\":"+raw(path.endsWith("/paid")?"paid":"failed",0)+"}";
   var bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);
  }catch(Exception ex){e.sendResponseHeaders(500,-1);}finally{e.close();}});server.start();
  try{var gateway=new PortOneGateway(json,"imp12345678","channel-test","key","secret",URI.create("http://127.0.0.1:"+server.getAddress().getPort()),HttpClient.newHttpClient());assertThat(gateway.payment("jb-ad-test").path("status").asText()).isEqualTo("PAID");}finally{server.stop(0);}
 }}
