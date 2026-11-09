package com.accountbook.user.internal.oauth;

import com.accountbook.common.web.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class JdkHttpGateway implements HttpGateway {

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Override
    public String get(String url, Map<String, String> headers) {
        var b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET();
        headers.forEach(b::header);
        return send(b.build());
    }

    @Override
    public String postForm(String url, Map<String, String> form) {
        var req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(Urls.query(form))).build();
        return send(req);
    }

    private String send(HttpRequest req) {
        try {
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            // Providers report most failures in a 4xx JSON body, which the caller turns into a precise error.
            if (res.statusCode() >= 500) {
                throw upstream();
            }
            return res.body();
        } catch (IOException e) {
            throw upstream();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw upstream();
        }
    }

    private static ApiException upstream() {
        return ApiException.of(HttpStatus.BAD_GATEWAY, "BAD_GATEWAY", "auth.oauth.upstream");
    }
}
