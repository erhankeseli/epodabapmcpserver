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

import java.io.BufferedReader;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import com.epod.adt.mcp.adt.AdtSessionBridge;
import com.epod.adt.mcp.adt.AdtUrlResolver;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

public abstract class AbstractMcpTool implements McpTool {

    protected final AdtSessionBridge bridge;

    public static final Map<String, String> STATEFUL_HEADERS =
            Map.of("X-sap-adt-sessiontype", "stateful");

    protected AbstractMcpTool(AdtSessionBridge bridge) {
        this.bridge = bridge;
    }

    protected static String optString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key) instanceof JsonNull) {
            return null;
        }
        return obj.get(key).getAsString();
    }

    protected static int optInt(JsonObject obj, String key, int defaultVal) {
        if (obj == null || !obj.has(key) || obj.get(key) instanceof JsonNull) {
            return defaultVal;
        }
        return obj.get(key).getAsInt();
    }

    /**
     * Resolves the ADT source URL from tool arguments. Uses "objectSourceUrl"
     * directly if provided, otherwise resolves from "objectType" + "objectName".
     */
    protected String resolveSourceUrlArg(JsonObject args) {
        String sourceUrl = optString(args, "objectSourceUrl");
        if (sourceUrl != null && !sourceUrl.isEmpty()) {
            return sourceUrl;
        }

        String objectType = optString(args, "objectType");
        String objectName = optString(args, "objectName");
        if (objectType != null && objectName != null) {
            return AdtUrlResolver.resolveSourceUrl(objectType, objectName);
        }

        throw new IllegalArgumentException(
                "Either 'objectSourceUrl' or both 'objectType' and 'objectName' must be provided.");
    }

    /**
     * Resolves the ADT object URL from tool arguments. Uses "objectSourceUrl"
     * directly if provided, otherwise resolves from "objectType" + "objectName".
     */
    protected String resolveObjectUrlArg(JsonObject args) {
        String sourceUrl = optString(args, "objectSourceUrl");
        if (sourceUrl != null && !sourceUrl.isEmpty()) {
            return sourceUrl;
        }

        String objectType = optString(args, "objectType");
        String objectName = optString(args, "objectName");
        if (objectType != null && objectName != null) {
            return AdtUrlResolver.resolveObjectUrl(objectType, objectName);
        }

        throw new IllegalArgumentException(
                "Either 'objectSourceUrl' or both 'objectType' and 'objectName' must be provided.");
    }

    // ------------------------------------------------------------------
    // URL / text helpers
    // ------------------------------------------------------------------

    /**
     * Converts a source URL to its parent object URL by stripping
     * the /source/main or /source/ suffix.
     */
    protected static String toObjectUrl(String sourceUrl) {
        if (sourceUrl == null) {
            return null;
        }
        int idx = sourceUrl.indexOf("/source/");
        if (idx >= 0) {
            return sourceUrl.substring(0, idx);
        }
        return sourceUrl;
    }

    protected static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // Text helpers
    // ------------------------------------------------------------------

    /** Strips ADT-injected *" comment lines from function module source. */
    protected static String sanitizeFmSource(String source) {
        if (source == null) {
            return null;
        }
        return new BufferedReader(new StringReader(source))
                .lines()
                .filter(line -> !line.startsWith("*\""))
                .collect(Collectors.joining("\n"));
    }

    protected static String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
