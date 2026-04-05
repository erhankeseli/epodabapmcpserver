/*
 * Copyright 2025 Erhan Keseli
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epod.adt.mcp.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.epod.adt.mcp.tools.McpTool;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * MCP server (protocol version 2024-11-05) over Streamable HTTP transport.
 * Endpoints: POST /mcp (JSON-RPC), GET /mcp (SSE), DELETE /mcp (session close), GET /health.
 */
public class McpServer {

    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "epod-adt-mcp-server";
    private static final String SERVER_VERSION = "1.0.0";
    private static final String SESSION_HEADER = "Mcp-Session-Id";
    private static final int SSE_KEEPALIVE_INTERVAL_MS = 30_000;
    private static final String LOG_PREFIX = "[McpServer] ";

    private final int port;
    private final Gson gson;
    private final ConcurrentHashMap<String, SessionState> sessions;
    private final CopyOnWriteArrayList<McpTool> tools;
    private final AtomicBoolean running;

    private HttpServer httpServer;
    private ExecutorService executor;
    private IServerStatusListener statusListener;

    public interface IServerStatusListener {
        void onStatusChanged(boolean running, String message);
    }

    private static final class SessionState {
        final String id;
        final long createdAt;
        volatile boolean initialized;

        SessionState(String id) {
            this.id = id;
            this.createdAt = System.currentTimeMillis();
            this.initialized = false;
        }
    }


    public McpServer(int port) {
        this.port = (port > 0) ? port : 3000;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.sessions = new ConcurrentHashMap<>();
        this.tools = new CopyOnWriteArrayList<>();
        this.running = new AtomicBoolean(false);
    }

    /** Create a server on the default port (3000). */
    public McpServer() {
        this(3000);
    }

    public void registerTool(McpTool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("tool must not be null");
        }
        tools.add(tool);
        log("Registered tool: " + tool.getName());
    }

    public void registerTools(List<McpTool> toolList) {
        if (toolList == null) {
            throw new IllegalArgumentException("toolList must not be null");
        }
        for (McpTool tool : toolList) {
            registerTool(tool);
        }
    }

    public void setStatusListener(IServerStatusListener listener) {
        this.statusListener = listener;
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getPort() {
        return port;
    }

    public synchronized void start() throws IOException {
        if (running.get()) {
            log("Server is already running on port " + port);
            return;
        }

        executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "mcp-worker");
            t.setDaemon(true);
            return t;
        });

        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        httpServer.setExecutor(executor);

        httpServer.createContext("/mcp", this::handleMcp);
        httpServer.createContext("/health", this::handleHealth);

        httpServer.start();
        running.set(true);

        String msg = "MCP server started on http://127.0.0.1:" + port;
        log(msg);
        notifyStatus(true, msg);
    }

    /** Stop the server gracefully. Waits up to 5s for in-flight requests. */
    public synchronized void stop() {
        if (!running.get()) {
            log("Server is not running");
            return;
        }

        running.set(false);

        if (httpServer != null) {
            httpServer.stop(2); // allow 2 seconds for pending exchanges
            httpServer = null;
        }

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }

        sessions.clear();

        String msg = "MCP server stopped";
        log(msg);
        notifyStatus(false, msg);
    }

    private void handleHealth(HttpExchange exchange) {
        try {
            addCorsHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            JsonObject health = new JsonObject();
            health.addProperty("status", "ok");
            health.addProperty("server", SERVER_NAME);
            health.addProperty("version", SERVER_VERSION);
            health.addProperty("protocolVersion", MCP_PROTOCOL_VERSION);
            health.addProperty("activeSessions", sessions.size());
            health.addProperty("registeredTools", tools.size());

            sendJsonResponse(exchange, 200, health);
        } catch (Exception e) {
            logError("Error handling /health", e);
            trySendError(exchange, 500, "Internal Server Error");
        }
    }

    private void handleMcp(HttpExchange exchange) {
        try {
            addCorsHeaders(exchange);

            String method = exchange.getRequestMethod().toUpperCase();

            switch (method) {
                case "OPTIONS":
                    sendResponse(exchange, 204, "");
                    break;
                case "POST":
                    handleMcpPost(exchange);
                    break;
                case "GET":
                    handleMcpSse(exchange);
                    break;
                case "DELETE":
                    handleMcpDelete(exchange);
                    break;
                default:
                    sendResponse(exchange, 405, "Method Not Allowed");
                    break;
            }
        } catch (Exception e) {
            logError("Unhandled error on /mcp", e);
            trySendError(exchange, 500, "Internal Server Error");
        }
    }

    private void handleMcpPost(HttpExchange exchange) throws IOException {
        String body = readRequestBody(exchange);
        if (body == null || body.isBlank()) {
            sendJsonRpcError(exchange, null, -32700, "Parse error: empty body");
            return;
        }

        JsonObject request;
        try {
            request = JsonParser.parseString(body).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            sendJsonRpcError(exchange, null, -32700, "Parse error: invalid JSON");
            return;
        }

        JsonElement idElem = request.get("id");
        String rpcMethod = request.has("method") ? request.get("method").getAsString() : null;
        JsonObject params = request.has("params") && request.get("params").isJsonObject()
                ? request.getAsJsonObject("params")
                : new JsonObject();

        if (rpcMethod == null || rpcMethod.isEmpty()) {
            sendJsonRpcError(exchange, idElem, -32600, "Invalid Request: missing method");
            return;
        }

        log("JSON-RPC request: method=" + rpcMethod + (idElem != null ? ", id=" + idElem : " (notification)"));

        // Session handling: "initialize" creates a new session, all others
        // require an existing session (except notifications).
        String sessionId = exchange.getRequestHeaders().getFirst(SESSION_HEADER);

        switch (rpcMethod) {
            case "initialize":
                handleInitialize(exchange, idElem, params);
                break;
            case "notifications/initialized":
                handleNotificationsInitialized(exchange, sessionId);
                break;
            case "tools/list":
                requireSession(exchange, sessionId, idElem, () -> handleToolsList(exchange, idElem));
                break;
            case "tools/call":
                requireSession(exchange, sessionId, idElem, () -> handleToolsCall(exchange, idElem, params));
                break;
            default:
                sendJsonRpcError(exchange, idElem, -32601, "Method not found: " + rpcMethod);
                break;
        }
    }

    /** initialize → create session, return capabilities. */
    private void handleInitialize(HttpExchange exchange, JsonElement id, JsonObject params) throws IOException {
        String sessionId = UUID.randomUUID().toString();
        SessionState session = new SessionState(sessionId);
        sessions.put(sessionId, session);

        log("New session created: " + sessionId);

        // Build result
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", MCP_PROTOCOL_VERSION);

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", SERVER_NAME);
        serverInfo.addProperty("version", SERVER_VERSION);
        result.add("serverInfo", serverInfo);

        JsonObject capabilities = new JsonObject();
        JsonObject toolsCap = new JsonObject();
        toolsCap.addProperty("listChanged", true);
        capabilities.add("tools", toolsCap);
        result.add("capabilities", capabilities);

        // Send with session header
        exchange.getResponseHeaders().set(SESSION_HEADER, sessionId);
        sendJsonRpcResult(exchange, id, result);
    }

    /** notifications/initialized → mark session as fully initialized. */
    private void handleNotificationsInitialized(HttpExchange exchange, String sessionId) throws IOException {
        if (sessionId != null) {
            SessionState session = sessions.get(sessionId);
            if (session != null) {
                session.initialized = true;
                log("Session initialized: " + sessionId);
            }
        }
        // Notifications have no id; respond with 204 No Content.
        sendResponse(exchange, 204, "");
    }

    /** tools/list → enumerate registered tools. */
    private void handleToolsList(HttpExchange exchange, JsonElement id) throws IOException {
        JsonArray toolArray = new JsonArray();
        for (McpTool tool : tools) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", tool.getName());
            entry.addProperty("description", tool.getDescription());
            entry.add("inputSchema", tool.getInputSchema());
            toolArray.add(entry);
        }

        JsonObject result = new JsonObject();
        result.add("tools", toolArray);

        sendJsonRpcResult(exchange, id, result);
    }

    /** tools/call → execute a tool by name. */
    private void handleToolsCall(HttpExchange exchange, JsonElement id, JsonObject params) throws IOException {
        String toolName = params.has("name") ? params.get("name").getAsString() : null;
        if (toolName == null || toolName.isEmpty()) {
            sendJsonRpcError(exchange, id, -32602, "Invalid params: missing tool name");
            return;
        }

        JsonObject args = params.has("arguments") && params.get("arguments").isJsonObject()
                ? params.getAsJsonObject("arguments")
                : new JsonObject();

        McpTool target = null;
        for (McpTool tool : tools) {
            if (toolName.equals(tool.getName())) {
                target = tool;
                break;
            }
        }

        if (target == null) {
            sendJsonRpcError(exchange, id, -32602, "Tool not found: " + toolName);
            return;
        }

        log("Executing tool: " + toolName);

        String executionResult;
        boolean isError = false;
        try {
            executionResult = target.execute(args);
        } catch (Exception e) {
            logError("Tool execution failed: " + toolName, e);
            executionResult = "Error executing tool: " + e.getMessage();
            isError = true;
        }

        // Build MCP tool result
        JsonObject result = new JsonObject();
        JsonArray content = new JsonArray();

        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", executionResult != null ? executionResult : "");
        content.add(textContent);

        result.add("content", content);
        if (isError) {
            result.addProperty("isError", true);
        }

        sendJsonRpcResult(exchange, id, result);
    }

    private void handleMcpSse(HttpExchange exchange) throws IOException {
        String sessionId = exchange.getRequestHeaders().getFirst(SESSION_HEADER);
        if (sessionId == null || !sessions.containsKey(sessionId)) {
            sendResponse(exchange, 400, "Missing or invalid session");
            return;
        }

        log("SSE stream opened for session: " + sessionId);

        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0); // chunked

        OutputStream os = exchange.getResponseBody();

        try {
            // Send an initial comment so the client knows the connection is live
            os.write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
            os.flush();

            // Keep the connection alive with periodic pings until the session
            // is removed or the server is stopped.
            while (running.get() && sessions.containsKey(sessionId)) {
                try {
                    Thread.sleep(SSE_KEEPALIVE_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                try {
                    os.write(": ping\n\n".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                } catch (IOException e) {
                    // Client disconnected
                    break;
                }
            }
        } finally {
            try {
                os.close();
            } catch (IOException ignored) {
                // Best-effort close
            }
            log("SSE stream closed for session: " + sessionId);
        }
    }

    private void handleMcpDelete(HttpExchange exchange) throws IOException {
        String sessionId = exchange.getRequestHeaders().getFirst(SESSION_HEADER);
        if (sessionId == null) {
            sendResponse(exchange, 400, "Missing session header");
            return;
        }

        SessionState removed = sessions.remove(sessionId);
        if (removed != null) {
            log("Session closed: " + sessionId);
            sendResponse(exchange, 204, "");
        } else {
            sendResponse(exchange, 404, "Session not found");
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws IOException;
    }

    private void requireSession(HttpExchange exchange, String sessionId, JsonElement id,
                                CheckedRunnable action) throws IOException {
        if (sessionId == null || !sessions.containsKey(sessionId)) {
            sendJsonRpcError(exchange, id, -32600,
                    "Invalid Request: missing or unknown session. Send 'initialize' first.");
            return;
        }
        action.run();
    }

    private void addCorsHeaders(HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers",
                "Content-Type, " + SESSION_HEADER + ", Accept");
        headers.set("Access-Control-Expose-Headers", SESSION_HEADER);
    }

    private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (status == 204) {
            exchange.sendResponseHeaders(204, -1);
        } else {
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int status, JsonObject json) throws IOException {
        byte[] bytes = gson.toJson(json).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendJsonRpcResult(HttpExchange exchange, JsonElement id, JsonObject result) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (id != null) {
            response.add("id", id);
        }
        response.add("result", result);
        sendJsonResponse(exchange, 200, response);
    }

    private void sendJsonRpcError(HttpExchange exchange, JsonElement id, int code, String message)
            throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);

        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (id != null) {
            response.add("id", id);
        }
        response.add("error", error);

        sendJsonResponse(exchange, 200, response);
    }

    /** Best-effort error send; swallows exceptions for use in catch blocks. */
    private void trySendError(HttpExchange exchange, int status, String body) {
        try {
            sendResponse(exchange, status, body);
        } catch (IOException ignored) {
            // Nothing we can do
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void log(String message) {
        System.out.println(LOG_PREFIX + message);
    }

    private static void logError(String message, Throwable t) {
        System.err.println(LOG_PREFIX + "ERROR - " + message);
        if (t != null) {
            t.printStackTrace(System.err);
        }
    }

    private void notifyStatus(boolean isRunning, String message) {
        IServerStatusListener listener = this.statusListener;
        if (listener != null) {
            try {
                listener.onStatusChanged(isRunning, message);
            } catch (Exception e) {
                logError("Status listener threw an exception", e);
            }
        }
    }
}
