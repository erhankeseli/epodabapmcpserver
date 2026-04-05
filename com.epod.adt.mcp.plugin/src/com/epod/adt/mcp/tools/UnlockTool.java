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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class UnlockTool extends AbstractMcpTool {

    private static final String NAME = "sap_unlock";
    private static final String DESCRIPTION = "Unlock a previously locked ABAP object.";

    public UnlockTool(AdtSessionBridge bridge) {
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

        JsonObject lockHandle = new JsonObject();
        lockHandle.addProperty("type", "string");
        lockHandle.addProperty("description", "Lock handle obtained from sap_lock");
        properties.add("lockHandle", lockHandle);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("objectType");
        required.add("objectName");
        required.add("lockHandle");
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject args) {
        try {
            String sourceUrl = resolveSourceUrlArg(args);
            String handle = optString(args, "lockHandle");

            if (handle == null || handle.isEmpty()) {
                // Nothing to unlock — still report success
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("message", "No lock handle provided; nothing to unlock.");
                return result.toString();
            }

            String unlockUrl = sourceUrl + "?_action=UNLOCK&lockHandle=" + urlEncode(handle);
            bridge.postEnqueue(unlockUrl, "", "application/xml", "application/vnd.sap.as+xml", STATEFUL_HEADERS);

        } catch (Exception e) {
            // Errors are silently caught — still return success
        }

        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        return result.toString();
    }
}
