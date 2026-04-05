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

public class SyntaxCheckTool extends AbstractMcpTool {

    public SyntaxCheckTool(AdtSessionBridge bridge) {
        super(bridge);
    }

    @Override
    public String getName() {
        return "sap_syntax_check";
    }

    @Override
    public String getDescription() {
        return "Run a syntax check on an ABAP object. Returns errors, warnings, and their line numbers.";
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

        String body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<chkrun:checkObjectList xmlns:chkrun=\"http://www.sap.com/adt/checkrun\""
                + " xmlns:adtcore=\"http://www.sap.com/adt/core\">\n"
                + "  <chkrun:checkObject adtcore:uri=\"" + escapeXml(objectUrl) + "\""
                + " chkrun:version=\"active\"/>\n"
                + "</chkrun:checkObjectList>";

        String response = bridge.post(
                "/sap/bc/adt/checkruns",
                body,
                "application/vnd.sap.adt.checkobjects+xml",
                "application/*");

        JsonObject result = AdtXmlParser.parseSyntaxCheckResults(response);
        return result.toString();
    }
}
