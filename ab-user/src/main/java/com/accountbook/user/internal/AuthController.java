package com.accountbook.user.internal;

import com.accountbook.user.api.CurrentUser;
import com.accountbook.user.internal.AuthService.SmsSent;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public endpoints (see {@link AuthInterceptor}); the optional bearer token only selects "bind" mode. */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService auth;
    private final OAuthService oauth;
    private final AuthProperties props;

    AuthController(AuthService auth, OAuthService oauth, AuthProperties props) {
        this.auth = auth;
        this.oauth = oauth;
        this.props = props;
    }

    record SmsSendRequest(String phone) {
    }

    record SmsLoginRequest(String phone, String code, LocationInput location) {
    }

    record TicketRequest(String ticket, LocationInput location) {
    }

    @GetMapping("/providers")
    Map<String, Object> providers() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sms", true);
        body.put("providers", oauth.available());
        body.put("requireLocation", props.requireLocation());
        return body;
    }

    @PostMapping("/sms/send")
    SmsSent send(@RequestBody SmsSendRequest req) {
        return auth.sendCode(req.phone());
    }

    @PostMapping("/sms/login")
    Views.Login smsLogin(@RequestBody SmsLoginRequest req, HttpServletRequest http) {
        return auth.loginWithSms(req.phone(), req.code(), req.location(), ClientInfo.of(http));
    }

    @GetMapping("/oauth/{provider}/authorize")
    Map<String, String> authorize(@PathVariable String provider, @RequestParam(required = false) String mockUser,
                                  HttpServletRequest http) {
        return Map.of("url", oauth.authorizeUrl(provider, CurrentUser.from(http).orElse(null), mockUser, http));
    }

    /** GET for most providers; POST for Apple's {@code response_mode=form_post}. */
    @GetMapping("/oauth/{provider}/callback")
    ResponseEntity<Void> callbackGet(@PathVariable String provider, @RequestParam Map<String, String> params,
                                     HttpServletRequest http) {
        return redirect(oauth.callback(provider, params, http));
    }

    @PostMapping("/oauth/{provider}/callback")
    ResponseEntity<Void> callbackPost(@PathVariable String provider, @RequestParam Map<String, String> params,
                                      HttpServletRequest http) {
        return redirect(oauth.callback(provider, params, http));
    }

    @PostMapping("/ticket")
    Views.Login ticket(@RequestBody TicketRequest req, HttpServletRequest http) {
        return oauth.exchangeTicket(req.ticket(), req.location(), ClientInfo.of(http));
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, location)
                .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }
}
