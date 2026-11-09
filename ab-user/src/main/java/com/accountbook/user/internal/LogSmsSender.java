package com.accountbook.user.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class LogSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LogSmsSender.class);

    private final AuthProperties props;

    LogSmsSender(AuthProperties props) {
        this.props = props;
    }

    @Override
    public void send(String phone, String code) {
        if (props.devMode()) {
            log.info("[dev] SMS code for {}: {}", phone, code);
        } else {
            log.warn("No SMS gateway configured; verification code for {} was not delivered", phone);
        }
    }
}
