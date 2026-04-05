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

public class SearchObjectTool extends AbstractMcpTool {

    public SearchObjectTool(AdtSessionBridge bridge) {
        super(bridge);
    }

    @Override
    public String getName() {
        return "sap_search_object";
    }

    @Override
    public String getDescription() {
        return "Search for ABAP objects in the SAP system. Returns matching object names, types, and URIs.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject queryProp = new JsonObject();
        queryProp.addProperty("type", "string");
        queryProp.addProperty("description", "Search pattern (e.g. ZCL_*, BAPI_MATERIAL*)");
        properties.add("query", queryProp);

        JsonObject objTypeProp = new JsonObject();
        objTypeProp.addProperty("type", "string");
        objTypeProp.addProperty("description", "Filter by object type (e.g. CLAS, PROG, FUGR, TABL, DDLS)");
        properties.add("objType", objTypeProp);

        JsonObject maxResultsProp = new JsonObject();
        maxResultsProp.addProperty("type", "integer");
        maxResultsProp.addProperty("description", "Maximum number of results to return (default 100)");
        properties.add("maxResults", maxResultsProp);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("query");
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject args) {
        String query = optString(args, "query");
        if (query == null || query.isEmpty()) {
            throw new IllegalArgumentException("Parameter 'query' is required.");
        }

        String objType = optString(args, "objType");
        int maxResults = optInt(args, "maxResults", 100);

        StringBuilder path = new StringBuilder();
        path.append("/sap/bc/adt/repository/informationsystem/search?operation=quickSearch&query=");
        path.append(urlEncode(query));
        path.append("&maxResults=");
        path.append(maxResults);

        if (objType != null && !objType.isEmpty()) {
            path.append("&objectType=");
            path.append(urlEncode(objType));
        }

        String xml = bridge.get(path.toString(), "application/xml");

        JsonArray results = AdtXmlParser.parseSearchResults(xml);
        return results.toString();
    }
}
