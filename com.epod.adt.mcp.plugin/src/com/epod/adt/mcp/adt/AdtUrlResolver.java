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
package com.epod.adt.mcp.adt;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Maps ABAP object types to ADT REST API URL paths. */
public final class AdtUrlResolver {

	private AdtUrlResolver() {
		// utility class
	}

	private static final Map<String, String> TYPE_TO_URL = new LinkedHashMap<>();

	static {
		// OO
		TYPE_TO_URL.put("CLAS", "/sap/bc/adt/oo/classes/{name}");
		TYPE_TO_URL.put("CLASS", "/sap/bc/adt/oo/classes/{name}");
		TYPE_TO_URL.put("INTF", "/sap/bc/adt/oo/interfaces/{name}");
		TYPE_TO_URL.put("INTERFACE", "/sap/bc/adt/oo/interfaces/{name}");

		// Programs
		TYPE_TO_URL.put("PROG", "/sap/bc/adt/programs/programs/{name}");
		TYPE_TO_URL.put("PROGRAM", "/sap/bc/adt/programs/programs/{name}");

		// Function groups / modules
		TYPE_TO_URL.put("FUGR", "/sap/bc/adt/functions/groups/{name}");
		TYPE_TO_URL.put("FUNCTION_GROUP", "/sap/bc/adt/functions/groups/{name}");
		TYPE_TO_URL.put("FUNC", "/sap/bc/adt/functions/groups/{group}/fmodules/{name}");
		TYPE_TO_URL.put("FUNCTION", "/sap/bc/adt/functions/groups/{group}/fmodules/{name}");

		// DDIC
		TYPE_TO_URL.put("TABL", "/sap/bc/adt/ddic/tables/{name}");
		TYPE_TO_URL.put("TABLE", "/sap/bc/adt/ddic/tables/{name}");
		TYPE_TO_URL.put("STRU", "/sap/bc/adt/ddic/structures/{name}");
		TYPE_TO_URL.put("STRUCTURE", "/sap/bc/adt/ddic/structures/{name}");
		TYPE_TO_URL.put("DDLS", "/sap/bc/adt/ddic/ddl/sources/{name}");
		TYPE_TO_URL.put("CDS", "/sap/bc/adt/ddic/ddl/sources/{name}");
		TYPE_TO_URL.put("DTEL", "/sap/bc/adt/ddic/dataelements/{name}");
		TYPE_TO_URL.put("DATA_ELEMENT", "/sap/bc/adt/ddic/dataelements/{name}");
		TYPE_TO_URL.put("DOMA", "/sap/bc/adt/ddic/domains/{name}");
		TYPE_TO_URL.put("DOMAIN", "/sap/bc/adt/ddic/domains/{name}");

		// Service / CDS extensions
		TYPE_TO_URL.put("SRVD", "/sap/bc/adt/ddic/srvd/sources/{name}");
		TYPE_TO_URL.put("SERVICE_DEFINITION", "/sap/bc/adt/ddic/srvd/sources/{name}");
		TYPE_TO_URL.put("DDLX", "/sap/bc/adt/ddic/ddlx/sources/{name}");
		TYPE_TO_URL.put("METADATA_EXTENSION", "/sap/bc/adt/ddic/ddlx/sources/{name}");

		// Behavior definition (RAP)
		TYPE_TO_URL.put("BDEF", "/sap/bc/adt/acm/behaviorDefinitions/{name}");
		TYPE_TO_URL.put("BEHAVIOR_DEFINITION", "/sap/bc/adt/acm/behaviorDefinitions/{name}");
	}

	private static final String[] PRIMARY_TYPES = {
		"CLAS", "INTF", "PROG", "FUGR", "FUNC",
		"TABL", "STRU", "DDLS", "DTEL", "DOMA",
		"SRVD", "DDLX", "BDEF"
	};

	// -----------------------------------------------------------------------

	public static String resolveObjectUrl(String type, String name) {
		if (type == null || name == null) {
			return null;
		}
		String template = TYPE_TO_URL.get(type.toUpperCase());
		if (template == null) {
			return null;
		}
		String lowerName = name.toLowerCase();
		// For FUNC / FUNCTION the template contains {group} which cannot be resolved
		// without additional context. If {group} is still present, leave it as a
		// placeholder so the caller can substitute it.
		return template.replace("{name}", lowerName);
	}

	/** Resolve the ADT source URL (/source/main appended) for a given type and name. */
	public static String resolveSourceUrl(String type, String name) {
		String objectUrl = resolveObjectUrl(type, name);
		if (objectUrl == null) {
			return null;
		}
		return objectUrl + "/source/main";
	}

	public static JsonArray buildTypeEnumArray() {
		JsonArray arr = new JsonArray();
		for (String t : PRIMARY_TYPES) {
			arr.add(t);
		}
		return arr;
	}

	public static JsonObject buildTypeProperty() {
		JsonObject prop = new JsonObject();
		prop.addProperty("type", "string");
		prop.addProperty("description", "ABAP object type (e.g. CLAS, INTF, PROG, FUGR, FUNC, TABL, DDLS, DTEL, DOMA, SRVD, DDLX, BDEF)");
		prop.add("enum", buildTypeEnumArray());
		return prop;
	}

	public static JsonObject buildNameProperty() {
		JsonObject prop = new JsonObject();
		prop.addProperty("type", "string");
		prop.addProperty("description", "ABAP object name (e.g. ZCL_MY_CLASS, ZIF_MY_INTF, ZPROGRAM)");
		return prop;
	}

	public static boolean isFunctionModuleUrl(String url) {
		if (url == null) {
			return false;
		}
		return url.contains("/functions/groups/") && url.contains("/fmodules/");
	}
}
