package com.accountbook.user.internal;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code ab.auth.*}. {@code devMode} exposes SMS codes in API responses and lets providers without credentials run
 * a local stand-in flow; it must be off in production.
 */
@ConfigurationProperties(prefix = "ab.auth")
public record AuthProperties(
        @DefaultValue("false") boolean devMode,
        @DefaultValue("true") boolean requireLocation,
        @DefaultValue("30") int sessionDays,
        String publicBaseUrl,
        @DefaultValue Sms sms,
        @DefaultValue Map<String, Client> oauth) {

    /**
     * {@code fixedCode}: when non-blank, this code is accepted for every phone and no SMS is generated. A stand-in
     * until an SMS gateway is wired in; it must be blank in any deployment reachable by others.
     */
    public record Sms(@DefaultValue("300") int codeTtlSeconds, @DefaultValue("60") int resendSeconds,
                      @DefaultValue("5") int maxAttempts, @DefaultValue("") String fixedCode) {

        public boolean hasFixedCode() {
            return fixedCode != null && !fixedCode.isBlank();
        }
    }

    /** Credentials of one provider; Apple additionally uses teamId/keyId/privateKey (PKCS#8 PEM). */
    public record Client(String clientId, String clientSecret, String teamId, String keyId, String privateKey) {

        public boolean hasCredentials() {
            return clientId != null && !clientId.isBlank();
        }
    }

    public Client client(String provider) {
        Client c = oauth.get(provider);
        return c == null ? new Client(null, null, null, null, null) : c;
    }
}
