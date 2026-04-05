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

public class SqlQueryTool extends AbstractMcpTool {

    private static final int DEFAULT_MAX_ROWS = 100;

    public SqlQueryTool(AdtSessionBridge bridge) {
        super(bridge);
    }

    @Override
    public String getName() {
        return "sap_sql_query";
    }

    @Override
    public String getDescription() {
        return "Execute an ABAP SQL query and return the results. Use standard ABAP SQL syntax.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject queryProp = new JsonObject();
        queryProp.addProperty("type", "string");
        queryProp.addProperty("description", "The ABAP SQL query to execute");
        properties.add("query", queryProp);

        JsonObject maxRowsProp = new JsonObject();
        maxRowsProp.addProperty("type", "integer");
        maxRowsProp.addProperty("description", "Maximum number of rows to return (default 100)");
        properties.add("maxRows", maxRowsProp);

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

        int maxRows = optInt(args, "maxRows", DEFAULT_MAX_ROWS);

        String path = "/sap/bc/adt/datapreview/freestyle?rowNumber=" + maxRows;

        String response = bridge.post(
                path,
                query,
                "text/plain",
                "application/vnd.sap.adt.datapreview.table.v1+xml");

        JsonObject result = AdtXmlParser.parseDataPreview(response);
        return result.toString();
    }
}
