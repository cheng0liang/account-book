package com.accountbook.user.internal;

/** Delivery of verification codes. Provide a bean (marked {@code @Primary}) wired to a real SMS gateway in production. */
public interface SmsSender {

    void send(String phone, String code);
}
