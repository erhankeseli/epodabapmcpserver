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

public class CreateObjectTool extends AbstractMcpTool {

    private static final String NAME = "sap_create_object";
    private static final String DESCRIPTION =
            "Create a new ABAP object (program, class, interface, function group, data element, or table).";

    /** Maximum allowed length for the object description. */
    private static final int MAX_DESCRIPTION_LENGTH = 60;

    public CreateObjectTool(AdtSessionBridge bridge) {
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
        objectType.addProperty("description", "ABAP object type to create");
        JsonArray typeEnum = new JsonArray();
        typeEnum.add("PROG");
        typeEnum.add("CLAS");
        typeEnum.add("INTF");
        typeEnum.add("FUGR");
        typeEnum.add("DTEL");
        typeEnum.add("TABL");
        typeEnum.add("STRU");
        objectType.add("enum", typeEnum);
        properties.add("objectType", objectType);

        JsonObject objectName = new JsonObject();
        objectName.addProperty("type", "string");
        objectName.addProperty("description", "ABAP object name (e.g. ZCL_MY_CLASS)");
        properties.add("objectName", objectName);

        JsonObject packageName = new JsonObject();
        packageName.addProperty("type", "string");
        packageName.addProperty("description", "ABAP package name (e.g. ZPACKAGE)");
        properties.add("packageName", packageName);

        JsonObject description = new JsonObject();
        description.addProperty("type", "string");
        description.addProperty("description", "Short description of the object (max 60 characters)");
        description.addProperty("maxLength", MAX_DESCRIPTION_LENGTH);
        properties.add("description", description);

        JsonObject transport = new JsonObject();
        transport.addProperty("type", "string");
        transport.addProperty("description", "Transport request number (e.g. NPLK900001)");
        properties.add("transport", transport);

        // Optional DTEL-specific properties
        JsonObject domainName = new JsonObject();
        domainName.addProperty("type", "string");
        domainName.addProperty("description", "Domain name to base the data element on (e.g. MANDT). Only for DTEL.");
        properties.add("domainName", domainName);

        JsonObject dataType = new JsonObject();
        dataType.addProperty("type", "string");
        dataType.addProperty("description", "Predefined ABAP type (e.g. CHAR, NUMC, INT4). Only for DTEL. Ignored if domainName is set.");
        properties.add("dataType", dataType);

        JsonObject dataTypeLength = new JsonObject();
        dataTypeLength.addProperty("type", "integer");
        dataTypeLength.addProperty("description", "Length of the data type. Only for DTEL. Defaults to 1.");
        properties.add("dataTypeLength", dataTypeLength);

        JsonObject dataTypeDecimals = new JsonObject();
        dataTypeDecimals.addProperty("type", "integer");
        dataTypeDecimals.addProperty("description", "Decimal places. Only for DTEL. Defaults to 0.");
        properties.add("dataTypeDecimals", dataTypeDecimals);

        JsonObject shortFieldLabel = new JsonObject();
        shortFieldLabel.addProperty("type", "string");
        shortFieldLabel.addProperty("description", "Short field label (max 10 chars). Only for DTEL.");
        shortFieldLabel.addProperty("maxLength", 10);
        properties.add("shortFieldLabel", shortFieldLabel);

        JsonObject mediumFieldLabel = new JsonObject();
        mediumFieldLabel.addProperty("type", "string");
        mediumFieldLabel.addProperty("description", "Medium field label (max 20 chars). Only for DTEL.");
        mediumFieldLabel.addProperty("maxLength", 20);
        properties.add("mediumFieldLabel", mediumFieldLabel);

        JsonObject longFieldLabel = new JsonObject();
        longFieldLabel.addProperty("type", "string");
        longFieldLabel.addProperty("description", "Long field label (max 40 chars). Only for DTEL.");
        longFieldLabel.addProperty("maxLength", 40);
        properties.add("longFieldLabel", longFieldLabel);

        JsonObject headingFieldLabel = new JsonObject();
        headingFieldLabel.addProperty("type", "string");
        headingFieldLabel.addProperty("description", "Heading field label (max 55 chars). Only for DTEL.");
        headingFieldLabel.addProperty("maxLength", 55);
        properties.add("headingFieldLabel", headingFieldLabel);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("objectType");
        required.add("objectName");
        required.add("packageName");
        required.add("description");
        required.add("transport");
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject args) {
        try {
            String objectType = optString(args, "objectType");
            String objectName = optString(args, "objectName");
            String packageName = optString(args, "packageName");
            String description = optString(args, "description");
            String transport = optString(args, "transport");

            // Optional DTEL-specific parameters
            String domainName = optString(args, "domainName");
            String dataType = optString(args, "dataType");
            int dataTypeLength = optInt(args, "dataTypeLength", 1);
            int dataTypeDecimals = optInt(args, "dataTypeDecimals", 0);
            String shortLabel = optString(args, "shortFieldLabel");
            String mediumLabel = optString(args, "mediumFieldLabel");
            String longLabel = optString(args, "longFieldLabel");
            String headingLabel = optString(args, "headingFieldLabel");

            // Validate required parameters
            if (objectType == null || objectType.isEmpty()) {
                throw new IllegalArgumentException("'objectType' is required.");
            }
            if (objectName == null || objectName.isEmpty()) {
                throw new IllegalArgumentException("'objectName' is required.");
            }
            if (packageName == null || packageName.isEmpty()) {
                throw new IllegalArgumentException("'packageName' is required.");
            }
            if (description == null || description.isEmpty()) {
                throw new IllegalArgumentException("'description' is required.");
            }
            if (transport == null || transport.isEmpty()) {
                throw new IllegalArgumentException("'transport' is required.");
            }

            // Truncate description to max length
            if (description.length() > MAX_DESCRIPTION_LENGTH) {
                description = description.substring(0, MAX_DESCRIPTION_LENGTH);
            }

            // Normalize to uppercase
            String type = objectType.toUpperCase();
            String name = objectName.toUpperCase();
            String pkg = packageName.toUpperCase();

            // Determine endpoint and XML body
            String endpoint;
            String xmlBody;
            String contentType = "application/xml";
            String acceptType = "application/vnd.sap.as+xml";

            switch (type) {
                case "PROG":
                    endpoint = "/sap/bc/adt/programs/programs";
                    xmlBody = buildProgXml(name, pkg, description);
                    break;
                case "CLAS":
                    endpoint = "/sap/bc/adt/oo/classes";
                    xmlBody = buildClasXml(name, pkg, description);
                    break;
                case "INTF":
                    endpoint = "/sap/bc/adt/oo/interfaces";
                    xmlBody = buildIntfXml(name, pkg, description);
                    break;
                case "FUGR":
                    endpoint = "/sap/bc/adt/functions/groups";
                    xmlBody = buildFugrXml(name, pkg, description);
                    break;
                case "DTEL":
                    endpoint = "/sap/bc/adt/ddic/dataelements";
                    contentType = "application/vnd.sap.adt.dataelements.v2+xml";
                    acceptType = "application/vnd.sap.adt.dataelements.v1+xml, application/vnd.sap.adt.dataelements.v2+xml";
                    xmlBody = buildDtelXml(name, pkg, description,
                            domainName, dataType, dataTypeLength, dataTypeDecimals,
                            shortLabel, mediumLabel, longLabel, headingLabel);
                    break;
                case "TABL":
                    endpoint = "/sap/bc/adt/ddic/tables";
                    contentType = "application/vnd.sap.adt.tables.v2+xml";
                    acceptType = "application/vnd.sap.adt.blues.v1+xml, application/vnd.sap.adt.tables.v2+xml";
                    xmlBody = buildTablXml(name, pkg, description);
                    break;
                case "STRU":
                    endpoint = "/sap/bc/adt/ddic/structures";
                    contentType = "application/vnd.sap.adt.structures.v2+xml";
                    acceptType = "application/vnd.sap.adt.blues.v1+xml, application/vnd.sap.adt.structures.v2+xml";
                    xmlBody = buildStruXml(name, pkg, description);
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unsupported object type: " + type
                            + ". Supported types: PROG, CLAS, INTF, FUGR, DTEL, TABL, STRU.");
            }

            // POST to ADT
            String url = endpoint + "?corrNr=" + urlEncode(transport);

            String response = bridge.post(url, xmlBody, contentType, acceptType, STATEFUL_HEADERS);

            // Build result
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("objectType", type);
            result.addProperty("objectName", name);
            result.addProperty("packageName", pkg);
            result.addProperty("description", description);
            result.addProperty("transport", transport);
            if (response != null && !response.isEmpty()) {
                result.addProperty("response", response);
            }
            return result.toString();

        } catch (Exception e) {
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("error", e.getMessage());
            return error.toString();
        }
    }

    private static String buildProgXml(String name, String pkg, String description) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<program:abapProgram xmlns:program=\"http://www.sap.com/adt/programs/programs\""
                + " xmlns:adtcore=\"http://www.sap.com/adt/core\""
                + " adtcore:type=\"PROG/P\""
                + " adtcore:description=\"" + escapeXml(description) + "\""
                + " adtcore:name=\"" + escapeXml(name) + "\">\n"
                + "  <adtcore:packageRef adtcore:name=\"" + escapeXml(pkg) + "\"/>\n"
                + "</program:abapProgram>";
    }

    private static String buildClasXml(String name, String pkg, String description) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<class:abapClass xmlns:class=\"http://www.sap.com/adt/oo/classes\""
                + " xmlns:adtcore=\"http://www.sap.com/adt/core\""
                + " adtcore:type=\"CLAS/OC\""
                + " adtcore:description=\"" + escapeXml(description) + "\""
                + " adtcore:name=\"" + escapeXml(name) + "\">\n"
                + "  <adtcore:packageRef adtcore:name=\"" + escapeXml(pkg) + "\"/>\n"
                + "</class:abapClass>";
    }

    private static String buildIntfXml(String name, String pkg, String description) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<intf:abapInterface xmlns:intf=\"http://www.sap.com/adt/oo/interfaces\""
                + " xmlns:adtcore=\"http://www.sap.com/adt/core\""
                + " adtcore:type=\"INTF/OI\""
                + " adtcore:description=\"" + escapeXml(description) + "\""
                + " adtcore:name=\"" + escapeXml(name) + "\">\n"
                + "  <adtcore:packageRef adtcore:name=\"" + escapeXml(pkg) + "\"/>\n"
                + "</intf:abapInterface>";
    }

    private static String buildFugrXml(String name, String pkg, String description) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<group:functionGroup xmlns:group=\"http://www.sap.com/adt/functions/groups\""
                + " xmlns:adtcore=\"http://www.sap.com/adt/core\""
                + " adtcore:type=\"FUGR/F\""
                + " adtcore:description=\"" + escapeXml(description) + "\""
                + " adtcore:name=\"" + escapeXml(name) + "\">\n"
                + "  <adtcore:packageRef adtcore:name=\"" + escapeXml(pkg) + "\"/>\n"
                + "</group:functionGroup>";
    }

    private static String buildTablXml(String name, String pkg, String description) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<blue:blueSource xmlns:blue=\"http://www.sap.com/wbobj/blue\""
                + " xmlns:adtcore=\"http://www.sap.com/adt/core\""
                + " adtcore:type=\"TABL/DT\""
                + " adtcore:description=\"" + escapeXml(description) + "\""
                + " adtcore:name=\"" + escapeXml(name) + "\""
                + " adtcore:masterLanguage=\"EN\">\n"
                + "  <adtcore:packageRef adtcore:name=\"" + escapeXml(pkg) + "\"/>\n"
                + "</blue:blueSource>";
    }

    private static String buildStruXml(String name, String pkg, String description) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<blue:blueSource xmlns:blue=\"http://www.sap.com/wbobj/blue\""
                + " xmlns:adtcore=\"http://www.sap.com/adt/core\""
                + " adtcore:type=\"TABL/DS\""
                + " adtcore:description=\"" + escapeXml(description) + "\""
                + " adtcore:name=\"" + escapeXml(name) + "\""
                + " adtcore:masterLanguage=\"EN\">\n"
                + "  <adtcore:packageRef adtcore:name=\"" + escapeXml(pkg) + "\"/>\n"
                + "</blue:blueSource>";
    }

    private static String buildDtelXml(String name, String pkg, String description,
            String domainName, String dataType, int dataTypeLength, int dataTypeDecimals,
            String shortLabel, String mediumLabel, String longLabel, String headingLabel) {

        // Determine typeKind and type details
        String typeKind;
        String typeName;
        String resolvedDataType;

        if (domainName != null && !domainName.isEmpty()) {
            typeKind = "domain";
            typeName = domainName.toUpperCase();
            resolvedDataType = "";
        } else if (dataType != null && !dataType.isEmpty()) {
            typeKind = "predefinedAbapType";
            typeName = "";
            resolvedDataType = dataType.toUpperCase();
        } else {
            typeKind = "predefinedAbapType";
            typeName = "";
            resolvedDataType = "CHAR";
            dataTypeLength = 1;
            dataTypeDecimals = 0;
        }

        String sLabel = (shortLabel != null) ? shortLabel : "";
        String mLabel = (mediumLabel != null) ? mediumLabel : "";
        String lLabel = (longLabel != null) ? longLabel : "";
        String hLabel = (headingLabel != null) ? headingLabel : "";

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<blue:wbobj xmlns:blue=\"http://www.sap.com/wbobj/dictionary/dtel\"");
        sb.append(" xmlns:adtcore=\"http://www.sap.com/adt/core\"");
        sb.append(" adtcore:name=\"").append(escapeXml(name)).append("\"");
        sb.append(" adtcore:type=\"DTEL/DE\"");
        sb.append(" adtcore:description=\"").append(escapeXml(description)).append("\"");
        sb.append(" adtcore:language=\"EN\"");
        sb.append(" adtcore:masterLanguage=\"EN\">\n");
        sb.append("  <adtcore:packageRef adtcore:name=\"").append(escapeXml(pkg)).append("\"/>\n");
        sb.append("  <dtel:dataElement xmlns:dtel=\"http://www.sap.com/adt/dictionary/dataelements\">\n");
        sb.append("    <dtel:typeKind>").append(escapeXml(typeKind)).append("</dtel:typeKind>\n");
        sb.append("    <dtel:typeName>").append(escapeXml(typeName)).append("</dtel:typeName>\n");
        sb.append("    <dtel:dataType>").append(escapeXml(resolvedDataType)).append("</dtel:dataType>\n");
        sb.append("    <dtel:dataTypeLength>").append(dataTypeLength).append("</dtel:dataTypeLength>\n");
        sb.append("    <dtel:dataTypeDecimals>").append(dataTypeDecimals).append("</dtel:dataTypeDecimals>\n");
        sb.append("    <dtel:shortFieldLabel>").append(escapeXml(sLabel)).append("</dtel:shortFieldLabel>\n");
        sb.append("    <dtel:shortFieldLength>10</dtel:shortFieldLength>\n");
        sb.append("    <dtel:shortFieldMaxLength>10</dtel:shortFieldMaxLength>\n");
        sb.append("    <dtel:mediumFieldLabel>").append(escapeXml(mLabel)).append("</dtel:mediumFieldLabel>\n");
        sb.append("    <dtel:mediumFieldLength>20</dtel:mediumFieldLength>\n");
        sb.append("    <dtel:mediumFieldMaxLength>20</dtel:mediumFieldMaxLength>\n");
        sb.append("    <dtel:longFieldLabel>").append(escapeXml(lLabel)).append("</dtel:longFieldLabel>\n");
        sb.append("    <dtel:longFieldLength>40</dtel:longFieldLength>\n");
        sb.append("    <dtel:longFieldMaxLength>40</dtel:longFieldMaxLength>\n");
        sb.append("    <dtel:headingFieldLabel>").append(escapeXml(hLabel)).append("</dtel:headingFieldLabel>\n");
        sb.append("    <dtel:headingFieldLength>55</dtel:headingFieldLength>\n");
        sb.append("    <dtel:headingFieldMaxLength>55</dtel:headingFieldMaxLength>\n");
        sb.append("    <dtel:searchHelp></dtel:searchHelp>\n");
        sb.append("    <dtel:searchHelpParameter></dtel:searchHelpParameter>\n");
        sb.append("    <dtel:setGetParameter></dtel:setGetParameter>\n");
        sb.append("    <dtel:defaultComponentName></dtel:defaultComponentName>\n");
        sb.append("    <dtel:deactivateInputHistory>false</dtel:deactivateInputHistory>\n");
        sb.append("    <dtel:changeDocument>false</dtel:changeDocument>\n");
        sb.append("    <dtel:leftToRightDirection>false</dtel:leftToRightDirection>\n");
        sb.append("    <dtel:deactivateBIDIFiltering>false</dtel:deactivateBIDIFiltering>\n");
        sb.append("  </dtel:dataElement>\n");
        sb.append("</blue:wbobj>");

        return sb.toString();
    }
}
