package com.accountbook.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.accountbook.AccountBookApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Whole stack on a real SQLite file. Nacos stays enabled but points at a dead port, so this also proves the
 * "no Nacos => fall back to application.yml" requirement.
 */
@SpringBootTest(classes = AccountBookApplication.class)
@AutoConfigureMockMvc
class ReceiptFlowTest {

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("ab.data-dir", () -> dataDir.toString());
        r.add("ab.ocr.provider", () -> "mock");
        r.add("spring.cloud.nacos.config.server-addr", () -> "127.0.0.1:1");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private JsonNode call(org.springframework.test.web.servlet.RequestBuilder rb, int expected) throws Exception {
        var res = mvc.perform(rb).andExpect(status().is(expected)).andReturn().getResponse();
        String body = res.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return body.isBlank() ? null : json.readTree(body);
    }

    private ObjectNode receipt(String merchant, String city, String at, String price) {
        var form = json.createObjectNode();
        form.putObject("merchant").put("name", merchant).put("country", "JP").put("city", city).put("type", "SUPERMARKET")
                .put("latitude", 35).put("longitude", 139);
        form.put("purchasedAt", at).put("currency", "JPY").put("taxAmount", 8);
        var item = form.putArray("items").addObject();
        item.putObject("product").put("name", "明治牛奶").put("brand", "明治").put("category", "BEVERAGE")
                .put("specText", "1L");
        item.put("quantity", 1).put("unitPrice", new java.math.BigDecimal(price)).put("amount", new java.math.BigDecimal(price));
        return form;
    }

    @Test
    void scanSaveCompareAndDelete() throws Exception {
        var meta = call(get("/api/meta"), 200);
        assertThat(meta.get("ocrProvider").asText()).isEqualTo("mock");
        assertThat(meta.get("merchantTypes").toString()).contains("SUPERMARKET");

        // recognise -> editable draft pre-linked to nothing yet
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        var draft = call(multipart("/api/receipts/recognize").file(new MockMvcFile("image", "r.png", "image/png", png).file()), 200);
        assertThat(draft.get("imageToken").asText()).isNotBlank();
        assertThat(draft.get("draft").get("items").size()).isGreaterThan(0);
        assertThat(draft.get("warnings").toString()).contains("ocr_mock_data");

        // unsupported image is a 400 with a localised message
        var bad = multipart("/api/receipts/recognize")
                .file(new MockMultipartFile("image", "x.txt", "text/plain", "hello world!".getBytes()))
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en");
        var err = call(bad, 400);
        assertThat(err.toString()).contains("Unsupported image format");

        // two receipts, same product, different merchants / prices
        var a = call(post("/api/receipts").contentType(MediaType.APPLICATION_JSON)
                .content(receipt("西友", "东京", "2026-10-03T10:00", "198").toString()), 201);
        var b = call(post("/api/receipts").contentType(MediaType.APPLICATION_JSON)
                .content(receipt("OK Store", "东京", "2026-11-02T18:30", "168").toString()), 201);
        long productId = a.get("items").get(0).get("product").get("id").asLong();
        assertThat(a.get("totalAmount").decimalValue()).isEqualByComparingTo("198");

        var list = call(get("/api/receipts"), 200);
        assertThat(list.get("total").asInt()).isEqualTo(2);

        // price comparison across merchants, served with an ETag that yields 304 until the data changes
        String url = "/api/analytics/price-compare?dimension=merchant&productId=" + productId;
        var first = mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse();
        String etag = first.getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotBlank();
        var chart = json.readTree(first.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(chart.get("labels").size()).isEqualTo(2);
        assertThat(chart.get("labels").get(0).asText()).isEqualTo("OK Store");
        assertThat(chart.get("series").get(0).get("data").get(0).decimalValue()).isEqualByComparingTo("168");
        mvc.perform(get(url).header(HttpHeaders.IF_NONE_MATCH, etag)).andExpect(status().isNotModified());

        var trend = call(get("/api/analytics/price-trend?granularity=month&productId=" + productId), 200);
        assertThat(trend.get("labels").toString()).contains("2026-10", "2026-11");
        var normalized = call(get("/api/analytics/price-trend?normalized=true&productId=" + productId), 200);
        assertThat(normalized.get("unit").asText()).isEqualTo("100ml");
        var table = call(get("/api/analytics/price-table?productId=" + productId), 200);
        assertThat(table.get("rows").get(0).get("merchantName").asText()).isEqualTo("OK Store");
        var spending = call(get("/api/analytics/spending?groupBy=month"), 200);
        assertThat(spending.get("total").decimalValue()).isEqualByComparingTo("366");
        assertThat(spending.get("count").asInt()).isEqualTo(2);
        var facets = call(get("/api/analytics/facets"), 200);
        assertThat(facets.get("brands").toString()).contains("明治");

        call(get("/api/analytics/price-compare?dimension=merchant"), 400);

        // edit: the old validator is stale and the chart reflects the new price
        var edited = receipt("OK Store", "东京", "2026-11-02T18:30", "150");
        call(put("/api/receipts/" + b.get("id").asLong()).contentType(MediaType.APPLICATION_JSON)
                .content(edited.toString()), 200);
        var second = mvc.perform(get(url).header(HttpHeaders.IF_NONE_MATCH, etag)).andExpect(status().isOk()).andReturn()
                .getResponse();
        assertThat(json.readTree(second.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("series").get(0).get("data").get(0).decimalValue())
                .isEqualByComparingTo("150");

        // delete: projection forgets the receipt
        call(delete("/api/receipts/" + a.get("id").asLong()), 204);
        var after = call(get(url), 200);
        assertThat(after.get("labels").size()).isEqualTo(1);
        call(get("/api/receipts/" + a.get("id").asLong()), 404);
    }

    /** Tiny helper so the multipart call above reads in one line. */
    private record MockMvcFile(String name, String filename, String type, byte[] bytes) {
        MockMultipartFile file() {
            return new MockMultipartFile(name, filename, type, bytes);
        }
    }
}
