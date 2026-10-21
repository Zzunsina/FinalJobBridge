package com.employment.auth;

public interface SmsGateway {
    void send(String phone, String code);
}
