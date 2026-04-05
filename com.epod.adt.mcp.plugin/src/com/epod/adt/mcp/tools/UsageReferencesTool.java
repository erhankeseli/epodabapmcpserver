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

public class UsageReferencesTool extends AbstractMcpTool {

    private static final String CONTENT_TYPE_REQUEST =
            "application/vnd.sap.adt.repository.usagereferences.request.v1+xml";
    private static final String CONTENT_TYPE_RESULT =
            "application/vnd.sap.adt.repository.usagereferences.result.v1+xml";

    public UsageReferencesTool(AdtSessionBridge bridge) {
        super(bridge);
    }

    @Override
    public String getName() {
        return "sap_usage_references";
    }

    @Override
    public String getDescription() {
        return "Find where an ABAP object is used (where-used list).";
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

        String requestBody =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<usagereferences:usageReferenceRequest "
                + "xmlns:usagereferences=\"http://www.sap.com/adt/ris/usageReferences\"/>";

        String path = "/sap/bc/adt/repository/informationsystem/usageReferences?uri="
                + urlEncode(objectUrl);

        String xml = bridge.post(path, requestBody, CONTENT_TYPE_REQUEST,
                CONTENT_TYPE_RESULT);

        return xml;
    }
}
