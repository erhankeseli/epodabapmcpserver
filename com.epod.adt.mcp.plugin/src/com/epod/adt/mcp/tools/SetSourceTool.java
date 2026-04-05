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
package com.epod.adt.mcp.tools;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.epod.adt.mcp.adt.AdtSessionBridge;
import com.epod.adt.mcp.adt.AdtUrlResolver;
import com.epod.adt.mcp.adt.AdtXmlParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Writes ABAP source via LOCK → WRITE → UNLOCK → ACTIVATE lifecycle.
 * Retries lock acquisition up to 3 times on HTTP 423.
 */
public class SetSourceTool extends AbstractMcpTool {

    private static final String NAME = "sap_set_source";
    private static final String DESCRIPTION =
            "Write source code to an ABAP object. Handles locking, writing, unlocking, and activation automatically.";

    /** Maximum number of lock-acquisition retries on HTTP 423. */
    private static final int LOCK_MAX_RETRIES = 3;
    private static final long LOCK_RETRY_DELAY_MS = 500;

    /** Pattern to extract the lock handle from the ADT lock response XML. */
    private static final Pattern LOCK_HANDLE_PATTERN =
            Pattern.compile("<(?:[^:>]*:)?(?:lockHandle|LOCK_HANDLE)[^>]*>([^<]+)</(?:[^:>]*:)?(?:lockHandle|LOCK_HANDLE)>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    /** Pattern to extract the transport (CORRNR) from the ADT lock response XML. */
    private static final Pattern CORRNR_PATTERN =
            Pattern.compile("<(?:[^:>]*:)?CORRNR[^>]*>([^<]+)</(?:[^:>]*:)?CORRNR>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public SetSourceTool(AdtSessionBridge bridge) {
        super(bridge);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject objectType = new JsonObject();
        objectType.addProperty("type", "string");
        objectType.addProperty("description",
                "ABAP object type (e.g. CLAS, INTF, PROG, FUGR, FUNC)");
        properties.add("objectType", objectType);

        JsonObject objectName = new JsonObject();
        objectName.addProperty("type", "string");
        objectName.addProperty("description", "ABAP object name");
        properties.add("objectName", objectName);

        JsonObject objectSourceUrl = new JsonObject();
        objectSourceUrl.addProperty("type", "string");
        objectSourceUrl.addProperty("description",
                "Optional ADT source URL. If provided, objectType/objectName are used only for activation.");
        properties.add("objectSourceUrl", objectSourceUrl);

        JsonObject source = new JsonObject();
        source.addProperty("type", "string");
        source.addProperty("description", "The ABAP source code to write");
        properties.add("source", source);

        JsonObject transport = new JsonObject();
        transport.addProperty("type", "string");
        transport.addProperty("description", "Transport request number (e.g. NPLK900001)");
        properties.add("transport", transport);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("source");
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject args) {
        String lockHandle = null;
        String sourceUrl = null;

        try {
            sourceUrl = resolveSourceUrlArg(args);
            String source = optString(args, "source");
            String transport = optString(args, "transport");

            if (source == null || source.isEmpty()) {
                throw new IllegalArgumentException("'source' parameter is required and must not be empty.");
            }

            // 1. LOCK
            String lockCorrNr = null;
            try {
                String[] lockResult = acquireLock(sourceUrl, transport);
                lockHandle = lockResult[0];
                lockCorrNr = lockResult[1]; // CORRNR from lock response (may be null)
            } catch (Exception e) {
                throw new RuntimeException("[LOCK] " + e.getMessage(), e);
            }

            // 2. WRITE — use CORRNR from lock response if available, else user-specified transport
            String effectiveTransport = (lockCorrNr != null && !lockCorrNr.isEmpty()) ? lockCorrNr : transport;
            String writeUrl = sourceUrl + "?lockHandle=" + urlEncode(lockHandle);
            if (effectiveTransport != null && !effectiveTransport.isEmpty()) {
                writeUrl += "&corrNr=" + urlEncode(effectiveTransport);
            }

            // Sanitize function module source if applicable
            String body = source;
            if (AdtUrlResolver.isFunctionModuleUrl(sourceUrl)) {
                body = sanitizeFmSource(body);
            }

            try {
                bridge.put(writeUrl, body, "text/plain; charset=utf-8", "text/plain", null);
            } catch (Exception e) {
                throw new RuntimeException("[WRITE] " + e.getMessage(), e);
            }

            // 3. UNLOCK (in finally)
            // (handled below)

            // 4. ACTIVATE
            String objectUrl = resolveObjectUrlArg(args);
            String objectName = optString(args, "objectName");
            if (objectName == null || objectName.isEmpty()) {
                // Derive name from URL as fallback
                objectName = deriveNameFromUrl(objectUrl);
            }

            JsonObject activationResult = activate(objectUrl, objectName.toUpperCase());

            // Build result
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", "Source written and activation requested.");
            result.add("activation", activationResult);
            return result.toString();

        } catch (Exception e) {
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("error", e.getMessage());
            return error.toString();

        } finally {
            // 3. UNLOCK (always)
            if (lockHandle != null && sourceUrl != null) {
                try {
                    String unlockUrl = sourceUrl + "?_action=UNLOCK&lockHandle=" + urlEncode(lockHandle);
                    bridge.postEnqueue(unlockUrl, "", "application/xml", "application/vnd.sap.as+xml", STATEFUL_HEADERS);
                } catch (Exception ignored) {
                    // Best-effort unlock
                }
            }
        }
    }

    /**
     * Acquires an ADT lock and returns {lockHandle, corrNr}.
     * corrNr is the transport returned by SAP in the lock response (may differ from requested).
     */
    private String[] acquireLock(String sourceUrl, String transport) throws Exception {
        String lockUrl = sourceUrl + "?_action=LOCK&accessMode=MODIFY";
        if (transport != null && !transport.isEmpty()) {
            lockUrl += "&corrNr=" + urlEncode(transport);
        }
        Exception lastException = null;

        for (int attempt = 1; attempt <= LOCK_MAX_RETRIES; attempt++) {
            try {
                String response = bridge.postEnqueue(lockUrl, "", "application/xml", "application/vnd.sap.as+xml", STATEFUL_HEADERS);
                String handle = extractLockHandle(response);
                String corrNr = extractCorrNr(response);
                return new String[] { handle, corrNr };
            } catch (Exception e) {
                lastException = e;
                // Check if this is an HTTP 423 (Locked) — the message or
                // exception type from ResourceException typically contains "423"
                String msg = e.getMessage();
                if (msg != null && msg.contains("423")) {
                    if (attempt < LOCK_MAX_RETRIES) {
                        try {
                            Thread.sleep(LOCK_RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Lock acquisition interrupted", ie);
                        }
                        continue;
                    }
                }
                throw e;
            }
        }

        throw lastException != null
                ? lastException
                : new RuntimeException("Failed to acquire lock after " + LOCK_MAX_RETRIES + " attempts.");
    }

    private JsonObject activate(String objectUrl, String objectName) {
        try {
            String activationBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<adtcore:objectReferences xmlns:adtcore=\"http://www.sap.com/adt/core\">\n"
                    + "  <adtcore:objectReference adtcore:uri=\"" + escapeXml(objectUrl)
                    + "\" adtcore:name=\"" + escapeXml(objectName) + "\"/>\n"
                    + "</adtcore:objectReferences>";

            String response = bridge.postEnqueue(
                    "/sap/bc/adt/activation?method=activate&preauditRequested=true",
                    activationBody,
                    "application/xml",
                    "application/vnd.sap.as+xml",
                    STATEFUL_HEADERS);

            return AdtXmlParser.parseActivationResult(response);

        } catch (Exception e) {
            JsonObject fallback = new JsonObject();
            fallback.addProperty("success", false);
            fallback.addProperty("error", "Activation failed: " + e.getMessage());
            return fallback;
        }
    }

    private static String extractLockHandle(String xml) {
        if (xml == null || xml.isEmpty()) {
            throw new IllegalStateException("Empty lock response — no lock handle returned.");
        }
        Matcher matcher = LOCK_HANDLE_PATTERN.matcher(xml);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        // Fallback: namespace-agnostic extraction
        Pattern fallback = Pattern.compile(
                "<[^:>]*:?lockHandle[^>]*>([^<]+)</[^:>]*:?lockHandle>", Pattern.DOTALL);
        Matcher fallbackMatcher = fallback.matcher(xml);
        if (fallbackMatcher.find()) {
            return fallbackMatcher.group(1).trim();
        }
        throw new IllegalStateException("Could not extract lock handle from response: " + xml);
    }

    /** Extracts the CORRNR (transport) from the lock response XML. Returns null if absent. */
    private static String extractCorrNr(String xml) {
        if (xml == null || xml.isEmpty()) {
            return null;
        }
        Matcher matcher = CORRNR_PATTERN.matcher(xml);
        if (matcher.find()) {
            String value = matcher.group(1).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    private static String deriveNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "UNKNOWN";
        }
        // Strip trailing slashes and /source/main
        String clean = toObjectUrl(url);
        int lastSlash = clean.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < clean.length() - 1) {
            return clean.substring(lastSlash + 1).toUpperCase();
        }
        return "UNKNOWN";
    }
}
