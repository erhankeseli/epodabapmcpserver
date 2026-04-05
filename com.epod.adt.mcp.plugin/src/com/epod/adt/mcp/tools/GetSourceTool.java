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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class GetSourceTool extends AbstractMcpTool {

    public GetSourceTool(AdtSessionBridge bridge) {
        super(bridge);
    }

    @Override
    public String getName() {
        return "sap_get_source";
    }

    @Override
    public String getDescription() {
        return "Read source code of an ABAP object. Provide objectType + objectName, or objectSourceUrl directly.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());

        JsonObject sourceUrlProp = new JsonObject();
        sourceUrlProp.addProperty("type", "string");
        sourceUrlProp.addProperty("description",
                "Direct ADT source URL (e.g. /sap/bc/adt/oo/classes/zcl_example/source/main). "
                + "If provided, objectType and objectName are ignored.");
        properties.add("objectSourceUrl", sourceUrlProp);

        JsonObject versionProp = new JsonObject();
        versionProp.addProperty("type", "string");
        versionProp.addProperty("description", "Source version to retrieve: 'active' or 'inactive'");
        JsonArray versionEnum = new JsonArray();
        versionEnum.add("active");
        versionEnum.add("inactive");
        versionProp.add("enum", versionEnum);
        properties.add("version", versionProp);

        schema.add("properties", properties);

        // No hard "required" — the resolver will validate at runtime
        return schema;
    }

    @Override
    public String execute(JsonObject args) {
        String sourceUrl = resolveSourceUrlArg(args);

        String version = optString(args, "version");
        if (version != null && !version.isEmpty()) {
            String separator = sourceUrl.contains("?") ? "&" : "?";
            sourceUrl = sourceUrl + separator + "version=" + urlEncode(version);
        }

        String source = bridge.get(sourceUrl, "text/plain");

        // If SAP returned XML instead of plain text, extract the ABAP source
        if (source != null && source.trim().startsWith("<")) {
            source = extractSourceFromXml(source);
        }

        return source;
    }

    private static String extractSourceFromXml(String xml) {
        try {
            javax.xml.parsers.DocumentBuilderFactory factory =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            org.w3c.dom.Document doc = factory.newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            String text = doc.getDocumentElement().getTextContent();
            return (text != null && !text.isBlank()) ? text : xml;
        } catch (Exception e) {
            // Not valid XML, return as-is
            return xml;
        }
    }
}
