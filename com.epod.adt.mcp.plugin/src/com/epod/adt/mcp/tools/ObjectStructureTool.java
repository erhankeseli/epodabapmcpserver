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

public class ObjectStructureTool extends AbstractMcpTool {

    public ObjectStructureTool(AdtSessionBridge bridge) {
        super(bridge);
    }

    @Override
    public String getName() {
        return "sap_object_structure";
    }

    @Override
    public String getDescription() {
        return "Get the structure and metadata of an ABAP object including its includes, links, and source URLs.";
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
        String objectType = optString(args, "objectType");
        String objectName = optString(args, "objectName");

        if (objectType == null || objectType.isEmpty()) {
            throw new IllegalArgumentException("Parameter 'objectType' is required.");
        }
        if (objectName == null || objectName.isEmpty()) {
            throw new IllegalArgumentException("Parameter 'objectName' is required.");
        }

        String objectUrl = AdtUrlResolver.resolveObjectUrl(objectType, objectName);
        if (objectUrl == null) {
            throw new IllegalArgumentException("Unsupported object type: " + objectType);
        }

        String xml = bridge.get(objectUrl, "application/*");

        JsonObject structure = AdtXmlParser.parseObjectStructure(xml);
        return structure.toString();
    }
}
