package com.epod.adt.mcp.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.epod.adt.mcp.tools.McpTool;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class McpServerTest {

    private McpServer server;
    private int port;
    private HttpClient http;

    @BeforeEach
    void setUp() throws IOException {
        // pick a random high port to avoid collisions
        port = 19000 + (int) (Math.random() * 1000);
        server = new McpServer(port);
        server.start();
        http = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private McpTool dummyTool(String name, String desc) {
        return new McpTool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return desc; }
            @Override public JsonObject getInputSchema() { return new JsonObject(); }
            @Override public String execute(JsonObject args) { return "ok"; }
        };
    }

    // --- /health ---

    @Test
    void healthEndpoint() throws Exception {
        var resp = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals("ok", body.get("status").getAsString());
        assertEquals("epod-adt-mcp-server", body.get("server").getAsString());
    }

    @Test
    void healthRejectsPost() throws Exception {
        var resp = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/health"))
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(405, resp.statusCode());
    }

    @Test
    void initializeCreatesSession() throws Exception {
        JsonObject req = jsonRpc("initialize", 1, new JsonObject());
        var resp = postMcp(req, null);

        assertEquals(200, resp.statusCode());
        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertNotNull(body.get("result"));

        String sessionId = resp.headers().firstValue("Mcp-Session-Id").orElse(null);
        assertNotNull(sessionId, "should return session ID in header");
        assertFalse(sessionId.isBlank());

        // result should have protocol version and capabilities
        JsonObject result = body.getAsJsonObject("result");
        assertEquals("2024-11-05", result.get("protocolVersion").getAsString());
        assertNotNull(result.getAsJsonObject("capabilities"));
    }

    /* session enforcement */

    @Test
    void toolsListRequiresSession() throws Exception {
        JsonObject req = jsonRpc("tools/list", 2, new JsonObject());
        var resp = postMcp(req, null);

        assertEquals(200, resp.statusCode());
        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertNotNull(body.get("error"), "should reject without session");
    }

    @Test
    void toolsListWorksWithSession() throws Exception {
        String sessionId = doInitialize();

        server.registerTool(dummyTool("test_tool", "a test"));

        JsonObject req = jsonRpc("tools/list", 2, new JsonObject());
        var resp = postMcp(req, sessionId);

        assertEquals(200, resp.statusCode());
        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonArray tools = body.getAsJsonObject("result").getAsJsonArray("tools");
        assertTrue(tools.size() >= 1);

        // find our tool
        boolean found = false;
        for (int i = 0; i < tools.size(); i++) {
            if ("test_tool".equals(tools.get(i).getAsJsonObject().get("name").getAsString())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "registered tool should appear in tools/list");
    }

    @Test
    void callToolReturnsResult() throws Exception {
        server.registerTool(new McpTool() {
            @Override public String getName() { return "echo"; }
            @Override public String getDescription() { return "echoes input"; }
            @Override public JsonObject getInputSchema() { return new JsonObject(); }
            @Override public String execute(JsonObject args) {
                return "hello " + args.get("msg").getAsString();
            }
        });

        String session = doInitialize();

        JsonObject params = new JsonObject();
        params.addProperty("name", "echo");
        JsonObject arguments = new JsonObject();
        arguments.addProperty("msg", "world");
        params.add("arguments", arguments);

        var resp = postMcp(jsonRpc("tools/call", 3, params), session);
        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonObject result = body.getAsJsonObject("result");

        JsonArray content = result.getAsJsonArray("content");
        assertEquals("hello world", content.get(0).getAsJsonObject().get("text").getAsString());
    }

    @Test
    void callUnknownToolReturnsError() throws Exception {
        String session = doInitialize();

        JsonObject params = new JsonObject();
        params.addProperty("name", "nonexistent");

        var resp = postMcp(jsonRpc("tools/call", 4, params), session);
        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertNotNull(body.get("error"));
        assertTrue(body.getAsJsonObject("error").get("message").getAsString().contains("not found"));
    }

    @Test
    void callToolThatThrowsReturnsIsError() throws Exception {
        server.registerTool(new McpTool() {
            @Override public String getName() { return "broken"; }
            @Override public String getDescription() { return "always fails"; }
            @Override public JsonObject getInputSchema() { return new JsonObject(); }
            @Override public String execute(JsonObject args) {
                throw new RuntimeException("boom");
            }
        });

        String session = doInitialize();
        JsonObject params = new JsonObject();
        params.addProperty("name", "broken");

        var resp = postMcp(jsonRpc("tools/call", 5, params), session);
        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonObject result = body.getAsJsonObject("result");
        assertTrue(result.get("isError").getAsBoolean());
    }

    @Test
    void unknownMethodReturnsError() throws Exception {
        JsonObject req = jsonRpc("bogus/method", 99, new JsonObject());
        var resp = postMcp(req, null);

        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals(-32601, body.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    void invalidJsonReturnsParseError() throws Exception {
        var resp = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/mcp"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{not valid"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals(-32700, body.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    void emptyBodyReturnsParseError() throws Exception {
        var resp = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/mcp"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(""))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals(-32700, body.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    void deleteSession() throws Exception {
        String session = doInitialize();

        var resp = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/mcp"))
                        .header("Mcp-Session-Id", session)
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(204, resp.statusCode());

        // subsequent request with that session should fail
        var resp2 = postMcp(jsonRpc("tools/list", 10, new JsonObject()), session);
        JsonObject body = JsonParser.parseString(resp2.body()).getAsJsonObject();
        assertNotNull(body.get("error"), "deleted session should be rejected");
    }

    @Test
    void optionsReturnsCorsHeaders() throws Exception {
        var resp = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/mcp"))
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(204, resp.statusCode());
        assertTrue(resp.headers().firstValue("Access-Control-Allow-Origin").isPresent());
    }

    @Test
    void startIsIdempotent() throws Exception {
        assertTrue(server.isRunning());
        server.start(); // should not throw
        assertTrue(server.isRunning());
    }

    @Test
    void stopTwiceIsNoOp() {
        server.stop();
        assertFalse(server.isRunning());
        server.stop(); // should not throw
    }

    @Test
    void registerNullToolThrows() {
        assertThrows(IllegalArgumentException.class, () -> server.registerTool(null));
    }

    @Test
    void statusListenerGetsNotified() throws IOException {
        McpServer srv = new McpServer(port + 1);
        boolean[] called = {false};
        srv.setStatusListener((running, msg) -> called[0] = true);
        srv.start();
        assertTrue(called[0]);
        srv.stop();
    }

    private JsonObject jsonRpc(String method, int id, JsonObject params) {
        JsonObject req = new JsonObject();
        req.addProperty("jsonrpc", "2.0");
        req.addProperty("method", method);
        req.addProperty("id", id);
        req.add("params", params);
        return req;
    }

    private HttpResponse<String> postMcp(JsonObject body, String sessionId) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(baseUrl() + "/mcp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String doInitialize() throws Exception {
        JsonObject req = jsonRpc("initialize", 1, new JsonObject());
        var resp = postMcp(req, null);
        return resp.headers().firstValue("Mcp-Session-Id").orElseThrow();
    }
}
