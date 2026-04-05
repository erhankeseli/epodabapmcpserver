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

public class RunUnitTestTool extends AbstractMcpTool {

    public RunUnitTestTool(AdtSessionBridge bridge) {
        super(bridge);
    }

    @Override
    public String getName() {
        return "sap_run_unit_test";
    }

    @Override
    public String getDescription() {
        return "Run ABAP Unit tests for an object. Returns test results with pass/fail status.";
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
                + "<aunit:runConfiguration xmlns:aunit=\"http://www.sap.com/adt/aunit\">\n"
                + "  <external>\n"
                + "    <coverage active=\"false\"/>\n"
                + "  </external>\n"
                + "  <options>\n"
                + "    <uriType value=\"semantic\"/>\n"
                + "    <testDeterminationStrategy sameProgram=\"true\" assignedTests=\"false\"/>\n"
                + "    <testRiskLevels harmless=\"true\" dangerous=\"true\" critical=\"true\"/>\n"
                + "    <testDurations short=\"true\" medium=\"true\" long=\"true\"/>\n"
                + "    <withNavigationUri enabled=\"false\"/>\n"
                + "  </options>\n"
                + "  <adtcore:objectSets xmlns:adtcore=\"http://www.sap.com/adt/core\">\n"
                + "    <objectSet kind=\"inclusive\">\n"
                + "      <adtcore:objectReferences>\n"
                + "        <adtcore:objectReference adtcore:uri=\"" + escapeXml(objectUrl) + "\"/>\n"
                + "      </adtcore:objectReferences>\n"
                + "    </objectSet>\n"
                + "  </adtcore:objectSets>\n"
                + "</aunit:runConfiguration>";

        String response = bridge.post(
                "/sap/bc/adt/abapunit/testruns",
                body,
                "application/xml",
                "application/*");

        JsonObject result = AdtXmlParser.parseUnitTestResults(response);
        return result.toString();
    }
}
