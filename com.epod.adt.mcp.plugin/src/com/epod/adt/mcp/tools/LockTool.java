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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class LockTool extends AbstractMcpTool {

    private static final String NAME = "sap_lock";
    private static final String DESCRIPTION = "Lock an ABAP object for editing. Returns a lock handle.";

    /** Pattern to extract the lock handle from the ADT lock response XML. */
    private static final Pattern LOCK_HANDLE_PATTERN =
            Pattern.compile("<(?:[^:>]*:)?(?:lockHandle|LOCK_HANDLE)[^>]*>([^<]+)</(?:[^:>]*:)?(?:lockHandle|LOCK_HANDLE)>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public LockTool(AdtSessionBridge bridge) {
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
        objectType.addProperty("description", "ABAP object type (e.g. CLAS, INTF, PROG, FUGR, FUNC)");
        properties.add("objectType", objectType);

        JsonObject objectName = new JsonObject();
        objectName.addProperty("type", "string");
        objectName.addProperty("description", "ABAP object name");
        properties.add("objectName", objectName);

        JsonObject transport = new JsonObject();
        transport.addProperty("type", "string");
        transport.addProperty("description", "Transport request number (e.g. NPLK900001). Optional for local packages.");
        properties.add("transport", transport);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("objectType");
        required.add("objectName");
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject args) {
        try {
            String sourceUrl = resolveSourceUrlArg(args);
            String transport = optString(args, "transport");

            String lockUrl = sourceUrl + "?_action=LOCK&accessMode=MODIFY";
            if (transport != null && !transport.isEmpty()) {
                lockUrl += "&corrNr=" + urlEncode(transport);
            }
            String response = bridge.postEnqueue(lockUrl, "", "application/xml", "application/vnd.sap.as+xml", STATEFUL_HEADERS);

            String lockHandle = extractLockHandle(response);

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("lockHandle", lockHandle);
            if (transport != null && !transport.isEmpty()) {
                result.addProperty("transport", transport);
            }
            return result.toString();

        } catch (Exception e) {
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("error", e.getMessage());
            return error.toString();
        }
    }

    static String extractLockHandle(String xml) {
        if (xml == null || xml.isEmpty()) {
            throw new IllegalStateException("Empty lock response — no lock handle returned.");
        }
        Matcher matcher = LOCK_HANDLE_PATTERN.matcher(xml);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        // Fallback: try a namespace-agnostic approach
        Pattern fallback = Pattern.compile("<[^:>]*:?lockHandle[^>]*>([^<]+)</[^:>]*:?lockHandle>", Pattern.DOTALL);
        Matcher fallbackMatcher = fallback.matcher(xml);
        if (fallbackMatcher.find()) {
            return fallbackMatcher.group(1).trim();
        }
        throw new IllegalStateException("Could not extract lock handle from response: " + xml);
    }
}
