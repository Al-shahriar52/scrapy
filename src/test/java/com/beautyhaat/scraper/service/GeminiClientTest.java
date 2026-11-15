package com.beautyhaat.scraper.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the client against a tiny local fake of the Gemini API - no key and no internet needed. */
class GeminiClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final GeminiClient.ProductFacts FACTS = new GeminiClient.ProductFacts(
            "Skin Cafe Brightening Serum – 30 ml", "skin cafe", "Skin", "Skin Lightening",
            List.of("Serums", "Free Delivery"), "30 ml", "38466", "A light serum with 2% Alpha Arbutin.");

    private HttpServer server;
    private int status;
    private String reply;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastKey = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastKey.set(ex.getRequestHeaders().getFirst("x-goog-api-key"));
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private GeminiClient client(String key) {
        GeminiProperties p = new GeminiProperties();
        p.setApiKey(key);
        p.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta");
        p.setModel("test-model");
        return new GeminiClient(p);
    }

    private static String answer(String innerJson) throws Exception {
        var root = JSON.createObjectNode();
        var cand = root.putArray("candidates").addObject();
        cand.put("finishReason", "STOP");
        cand.putObject("content").putArray("parts").addObject().put("text", innerJson);
        return root.toString();
    }

    private static final String GOOD = """
            {"seoName":"Skin Cafe Brightening Serum 30ml with Alpha Arbutin",
             "metaDescription":"Light brightening serum with 2% Alpha Arbutin and Hyaluronic Acid. Order from BeautyHaat today.",
             "description":"A light serum.\\n\\nSecond paragraph.",
             "benefitsBn":["১. ত্বক উজ্জ্বল দেখাতে সাহায্য করে","- ত্বককে আর্দ্র রাখে","", "২% অ্যালফা আরবুটিন দিয়ে তৈরি","দাগছোপ হালকা করতে সহায়ক"],
             "imageAlt":"Skin Cafe Brightening Serum bottle"}
            """;

    @Test
    void parsesGoodAnswerAndSendsKeyAndSchema() throws Exception {
        status = 200;
        reply = answer(GOOD);
        GeminiClient.SeoContent c = client("KEY123").generateSeo(FACTS);

        assertEquals("Skin Cafe Brightening Serum 30ml with Alpha Arbutin", c.seoName());
        assertEquals(4, c.benefitsBn().size()); // blank line dropped
        assertEquals("ত্বক উজ্জ্বল দেখাতে সাহায্য করে", c.benefitsBn().get(0)); // "১." numbering removed
        assertEquals("ত্বককে আর্দ্র রাখে", c.benefitsBn().get(1)); // "- " removed
        assertEquals("২% অ্যালফা আরবুটিন দিয়ে তৈরি", c.benefitsBn().get(2)); // a leading quantity is NOT numbering
        assertTrue(c.description().contains("Second paragraph."));

        assertEquals("KEY123", lastKey.get());
        JsonNode sent = JSON.readTree(lastBody.get());
        assertEquals("application/json", sent.path("generationConfig").path("responseMimeType").asText());
        assertEquals("OBJECT", sent.path("generationConfig").path("responseSchema").path("type").asText());
        String prompt = sent.path("contents").path(0).path("parts").path(0).path("text").asText();
        assertTrue(prompt.contains("Brand: skin cafe"));
        assertTrue(prompt.contains("Tags: Serums, Free Delivery"));
    }

    @Test
    void acceptsJsonWrappedInCodeFence() throws Exception {
        status = 200;
        reply = answer("```json\n" + GOOD + "\n```");
        assertEquals(4, client("k").generateSeo(FACTS).benefitsBn().size());
    }

    @Test
    void blockedOrIncompleteAnswerOnlyAffectsThisProduct() throws Exception {
        status = 200;
        reply = "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}";
        GeminiException e = assertThrows(GeminiException.class, () -> client("k").generateSeo(FACTS));
        assertEquals(GeminiException.Kind.OTHER, e.kind());
        assertFalse(e.isFatal());

        reply = answer("{\"seoName\":\"X\",\"description\":\"\",\"benefitsBn\":[],\"metaDescription\":\"\",\"imageAlt\":\"\"}");
        e = assertThrows(GeminiException.class, () -> client("k").generateSeo(FACTS));
        assertEquals(GeminiException.Kind.OTHER, e.kind());
    }

    @Test
    void wrongKeyIsFatal() {
        status = 400;
        reply = "{\"error\":{\"code\":400,\"message\":\"API key not valid. Please pass a valid API key.\"}}";
        GeminiException e = assertThrows(GeminiException.class, () -> client("bad").generateSeo(FACTS));
        assertEquals(GeminiException.Kind.AUTH, e.kind());
        assertTrue(e.isFatal());
    }

    @Test
    void dailyQuotaStopsImmediately() {
        status = 429;
        reply = "{\"error\":{\"code\":429,\"message\":\"Quota exceeded\",\"details\":[{\"violations\":[{\"quotaId\":\"GenerateRequestsPerDayPerProjectPerModel-FreeTier\"}]}]}}";
        GeminiException e = assertThrows(GeminiException.class, () -> client("k").generateSeo(FACTS));
        assertEquals(GeminiException.Kind.DAILY_QUOTA, e.kind());
    }

    @Test
    void overloadedModelIsRetriedThenReportedAsBusyAndNotFatal() {
        status = 503;
        reply = "{\"error\":{\"code\":503,\"message\":\"This model is currently experiencing high demand.\"}}";
        GeminiProperties p = new GeminiProperties();
        p.setApiKey("k");
        p.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta");
        p.setFallbackModel("");
        p.setServerRetryBaseMs(5); // keep the test fast
        GeminiException e = assertThrows(GeminiException.class, () -> new GeminiClient(p).generateSeo(FACTS));
        assertEquals(GeminiException.Kind.BUSY, e.kind());
        assertFalse(e.isFatal());
        assertTrue(e.getMessage().contains("overloaded"));
    }

    @Test
    void missingKeyIsReportedClearly() {
        GeminiException e = assertThrows(GeminiException.class, () -> client("").generateSeo(FACTS));
        assertEquals(GeminiException.Kind.AUTH, e.kind());
        assertTrue(e.getMessage().contains("GEMINI_API_KEY"));
    }

    @Test
    void listsOnlyModelsThatCanGenerateText() throws Exception {
        status = 200;
        reply = "{\"models\":[{\"name\":\"models/gemini-a\",\"supportedGenerationMethods\":[\"generateContent\"]},"
                + "{\"name\":\"models/embed-b\",\"supportedGenerationMethods\":[\"embedContent\"]}]}";
        assertEquals(List.of("gemini-a"), client("k").listModels());
    }
}
