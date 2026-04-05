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
import com.epod.adt.mcp.adt.AdtXmlParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class ActivateTool extends AbstractMcpTool {

    private static final String NAME = "sap_activate";
    private static final String DESCRIPTION = "Activate an ABAP object.";

    public ActivateTool(AdtSessionBridge bridge) {
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
            String objectUrl = resolveObjectUrlArg(args);
            String objectName = optString(args, "objectName");

            if (objectName == null || objectName.isEmpty()) {
                throw new IllegalArgumentException("'objectName' is required.");
            }

            String activationBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<adtcore:objectReferences xmlns:adtcore=\"http://www.sap.com/adt/core\">\n"
                    + "  <adtcore:objectReference adtcore:uri=\"" + escapeXml(objectUrl)
                    + "\" adtcore:name=\"" + escapeXml(objectName.toUpperCase()) + "\"/>\n"
                    + "</adtcore:objectReferences>";

            String response = bridge.postEnqueue(
                    "/sap/bc/adt/activation?method=activate&preauditRequested=true",
                    activationBody,
                    "application/xml",
                    "application/vnd.sap.as+xml",
                    STATEFUL_HEADERS);

            JsonObject result = AdtXmlParser.parseActivationResult(response);
            return result.toString();

        } catch (Exception e) {
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("error", e.getMessage());
            return error.toString();
        }
    }
}
