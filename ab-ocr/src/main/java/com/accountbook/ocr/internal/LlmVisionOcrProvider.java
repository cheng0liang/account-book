package com.accountbook.ocr.internal;

import com.accountbook.ocr.api.OcrResult;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Any OpenAI-compatible chat-completions endpoint with vision support. */
class LlmVisionOcrProvider implements OcrProvider {

    static final String PROMPT = """
            You read shopping receipts. Extract the receipt in the image and answer with ONE JSON object only, no prose.
            Schema: {"merchant":{"name":string,"country":ISO-3166 alpha-2|null,"city":string|null,"district":string|null,"address":string|null},
            "purchasedAt":"yyyy-MM-dd'T'HH:mm"|null,"currency":ISO-4217|null,"totalAmount":number|null,"taxAmount":number|null,
            "discountAmount":number|null,"paymentMethod":"CASH|CREDIT_CARD|DEBIT_CARD|E_MONEY|QR_PAY|OTHER"|null,
            "items":[{"name":string,"brand":string|null,"specText":string|null (e.g. "500ml","1kg"),"quantity":number,
            "unitPrice":number,"discount":number|null,"amount":number,"category":"FOOD|BEVERAGE|FRESH|DAILY|HOUSEHOLD|MEDICINE|BEAUTY|CLOTHING|ELECTRONICS|OTHER"}]}
            Amounts are plain numbers in the currency's major unit (no symbols or thousands separators). Keep item names in the
            language printed on the receipt. Use null for anything you cannot read; never invent values.""";

    private final OcrProperties.Llm cfg;
    private final RestClient client;

    LlmVisionOcrProvider(OcrProperties.Llm cfg) {
        this.cfg = cfg;
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(cfg.timeoutSeconds()));
        String base = cfg.baseUrl().endsWith("/") ? cfg.baseUrl().substring(0, cfg.baseUrl().length() - 1) : cfg.baseUrl();
        this.client = RestClient.builder().baseUrl(base).requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + cfg.apiKey()).build();
    }

    @Override
    public String name() {
        return "llm";
    }

    @Override
    @SuppressWarnings("unchecked")
    public OcrResult recognize(byte[] image, String contentType, String defaultCurrency) throws Exception {
        String dataUri = "data:" + (contentType == null ? "image/jpeg" : contentType) + ";base64,"
                + Base64.getEncoder().encodeToString(image);
        var body = Map.of(
                "model", cfg.model(),
                "max_tokens", cfg.maxTokens(),
                "temperature", 0,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", PROMPT),
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", "Default currency if not printed: " + defaultCurrency),
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUri))))));
        Map<String, Object> response = client.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(Map.class);
        var choices = (List<Map<String, Object>>) response.get("choices");
        var message = (Map<String, Object>) choices.get(0).get("message");
        return ReceiptJsonParser.parse((String) message.get("content"), name(), defaultCurrency);
    }
}
