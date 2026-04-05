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

import com.epod.adt.mcp.adt.AdtSessionBridge;
import com.epod.adt.mcp.adt.AdtUrlResolver;
import com.epod.adt.mcp.adt.AdtXmlParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Runs ATC checks: create worklist, run checks, retrieve results.
 */
public class AtcRunTool extends AbstractMcpTool {

    public AtcRunTool(AdtSessionBridge bridge) {
        super(bridge);
    }

    @Override
    public String getName() {
        return "sap_atc_run";
    }

    @Override
    public String getDescription() {
        return "Run ATC (ABAP Test Cockpit) checks on an object. Returns code quality findings.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("objectType");
        required.add("objectName");
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject args) {
        String objectUrl = resolveObjectUrlArg(args);
        String objectName = optString(args, "objectName");

        // Step 1: Create worklist (returns worklist ID as plain text)
        String worklistResponse = bridge.post(
                "/sap/bc/adt/atc/worklists?checkVariant=DEFAULT",
                "",
                "application/xml",
                "text/plain");

        String worklistId = worklistResponse != null ? worklistResponse.trim() : null;
        if (worklistId == null || worklistId.isEmpty()) {
            worklistId = extractWorklistId(worklistResponse);
        }
        if (worklistId == null || worklistId.isEmpty()) {
            throw new RuntimeException("Failed to create ATC worklist: could not extract worklist ID from response");
        }

        // Step 2: Run ATC checks
        String runBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<atc:run xmlns:atc=\"http://www.sap.com/adt/atc\" maximumVerdicts=\"100\">\n"
                + "  <objectSets xmlns:adtcore=\"http://www.sap.com/adt/core\">\n"
                + "    <objectSet kind=\"inclusive\">\n"
                + "      <adtcore:objectReferences>\n"
                + "        <adtcore:objectReference adtcore:uri=\"" + escapeXml(objectUrl) + "\""
                + " adtcore:name=\"" + escapeXml(objectName) + "\"/>\n"
                + "      </adtcore:objectReferences>\n"
                + "    </objectSet>\n"
                + "  </objectSets>\n"
                + "</atc:run>";

        bridge.post(
                "/sap/bc/adt/atc/runs?worklistId=" + urlEncode(worklistId),
                runBody,
                "application/xml",
                "application/xml");

        // Step 3: Retrieve results
        String resultsXml = bridge.get(
                "/sap/bc/adt/atc/worklists/" + urlEncode(worklistId),
                "application/atc.worklist.v1+xml");

        JsonArray findings = AdtXmlParser.parseAtcWorklist(resultsXml);
        return findings.toString();
    }

    private static String extractWorklistId(String xml) {
        if (xml == null || xml.isEmpty()) {
            return null;
        }

        // Try to find worklistId="..." or id="..." attribute in the XML
        String id = extractAttributeValue(xml, "worklistId");
        if (id != null) {
            return id;
        }
        id = extractAttributeValue(xml, "id");
        if (id != null) {
            return id;
        }

        // Fallback: look for <worklistId>...</worklistId> or <id>...</id> element
        id = extractElementValue(xml, "worklistId");
        if (id != null) {
            return id;
        }
        id = extractElementValue(xml, "id");
        return id;
    }

    private static String extractAttributeValue(String xml, String attrName) {
        // Match pattern: attrName="value"
        String pattern = attrName + "=\"";
        int idx = xml.indexOf(pattern);
        if (idx < 0) {
            return null;
        }
        int start = idx + pattern.length();
        int end = xml.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        String value = xml.substring(start, end).trim();
        return value.isEmpty() ? null : value;
    }

    private static String extractElementValue(String xml, String elementName) {
        String openTag = "<" + elementName + ">";
        int idx = xml.indexOf(openTag);
        if (idx < 0) {
            // Try with namespace prefix
            int nsIdx = xml.indexOf(":" + elementName + ">");
            if (nsIdx < 0) {
                return null;
            }
            // Find the start of this tag
            int tagStart = xml.lastIndexOf('<', nsIdx);
            if (tagStart < 0) {
                return null;
            }
            int start = nsIdx + (":" + elementName + ">").length();
            String closePattern = "</" ;
            int end = xml.indexOf(closePattern, start);
            if (end < 0) {
                return null;
            }
            String value = xml.substring(start, end).trim();
            return value.isEmpty() ? null : value;
        }
        int start = idx + openTag.length();
        String closeTag = "</" + elementName + ">";
        int end = xml.indexOf(closeTag, start);
        if (end < 0) {
            return null;
        }
        String value = xml.substring(start, end).trim();
        return value.isEmpty() ? null : value;
    }
}
