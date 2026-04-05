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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Parses ADT REST API XML responses into Gson JSON structures. */
public final class AdtXmlParser {

	private AdtXmlParser() {
		// utility class
	}

	// -----------------------------------------------------------------------

	/** Parse ADT search results XML (objectReference elements). */
	public static JsonArray parseSearchResults(String xml) {
		JsonArray results = new JsonArray();
		if (xml == null || xml.isBlank()) {
			return results;
		}
		Document doc = parseDocument(xml);
		if (doc == null) {
			return results;
		}
		List<Element> refs = getElementsByLocalName(doc.getDocumentElement(), "objectReference");
		for (Element ref : refs) {
			JsonObject obj = new JsonObject();
			obj.addProperty("uri", attr(ref, "uri", ""));
			obj.addProperty("type", attr(ref, "type", ""));
			obj.addProperty("name", attr(ref, "name", ""));
			obj.addProperty("description", attr(ref, "description", ""));
			results.add(obj);
		}
		return results;
	}

	/** Parse syntax check / checkrun results. */
	public static JsonObject parseSyntaxCheckResults(String xml) {
		JsonObject result = new JsonObject();
		result.addProperty("errorCount", 0);
		result.addProperty("warningCount", 0);
		JsonArray findings = new JsonArray();
		result.add("findings", findings);

		if (xml == null || xml.isBlank()) {
			return result;
		}
		Document doc = parseDocument(xml);
		if (doc == null) {
			return result;
		}

		int errors = 0;
		int warnings = 0;

		// Try checkMessages or finding elements
		List<Element> messages = getElementsByLocalName(doc.getDocumentElement(), "checkMessage");
		if (messages.isEmpty()) {
			messages = getElementsByLocalName(doc.getDocumentElement(), "finding");
		}
		if (messages.isEmpty()) {
			messages = getElementsByLocalName(doc.getDocumentElement(), "message");
		}

		for (Element msg : messages) {
			JsonObject finding = new JsonObject();
			String severity = attr(msg, "severity", attr(msg, "type", ""));
			finding.addProperty("severity", severity);
			finding.addProperty("line", attr(msg, "line", childText(msg, "line", "0")));
			finding.addProperty("column", attr(msg, "column", childText(msg, "column", "0")));
			String text = attr(msg, "shortText", attr(msg, "text", childText(msg, "shortText", childText(msg, "text", ""))));
			finding.addProperty("message", text);
			findings.add(finding);

			String sev = severity.toLowerCase();
			if (sev.contains("error") || sev.equals("e")) {
				errors++;
			} else if (sev.contains("warn") || sev.equals("w")) {
				warnings++;
			}
		}

		result.addProperty("errorCount", errors);
		result.addProperty("warningCount", warnings);
		return result;
	}

	/** Parse activation response. */
	public static JsonObject parseActivationResult(String xml) {
		JsonObject result = new JsonObject();
		result.addProperty("success", true);
		JsonArray messages = new JsonArray();
		result.add("messages", messages);

		if (xml == null || xml.isBlank()) {
			return result;
		}
		Document doc = parseDocument(xml);
		if (doc == null) {
			return result;
		}

		Element root = doc.getDocumentElement();

		// Check for inactiveObjects or messages indicating failure
		List<Element> msgElements = getElementsByLocalName(root, "msg");
		if (msgElements.isEmpty()) {
			msgElements = getElementsByLocalName(root, "message");
		}
		if (msgElements.isEmpty()) {
			msgElements = getElementsByLocalName(root, "entry");
		}

		boolean hasError = false;
		for (Element msg : msgElements) {
			JsonObject m = new JsonObject();
			String severity = attr(msg, "severity", attr(msg, "type", childText(msg, "severity", "")));
			String text = attr(msg, "shortText", attr(msg, "text", childText(msg, "shortText", childText(msg, "text", getText(msg)))));
			m.addProperty("severity", severity);
			m.addProperty("message", text);
			messages.add(m);

			String sev = severity.toLowerCase();
			if (sev.contains("error") || sev.equals("e")) {
				hasError = true;
			}
		}

		if (hasError) {
			result.addProperty("success", false);
		}

		return result;
	}

	/** Parse ABAP unit test run results. */
	public static JsonObject parseUnitTestResults(String xml) {
		JsonObject result = new JsonObject();
		JsonArray programs = new JsonArray();
		result.add("programs", programs);

		if (xml == null || xml.isBlank()) {
			return result;
		}
		Document doc = parseDocument(xml);
		if (doc == null) {
			return result;
		}

		Element root = doc.getDocumentElement();

		// Program elements
		List<Element> programElements = getElementsByLocalName(root, "program");
		for (Element progEl : programElements) {
			JsonObject prog = new JsonObject();
			prog.addProperty("name", attr(progEl, "name", attr(progEl, "uri", "")));

			JsonArray testClasses = new JsonArray();

			List<Element> classElements = getElementsByLocalName(progEl, "testClass");
			for (Element clsEl : classElements) {
				JsonObject testClass = new JsonObject();
				testClass.addProperty("name", attr(clsEl, "name", attr(clsEl, "uri", "")));

				JsonArray testMethods = new JsonArray();

				List<Element> methodElements = getElementsByLocalName(clsEl, "testMethod");
				for (Element methEl : methodElements) {
					JsonObject testMethod = new JsonObject();
					testMethod.addProperty("name", attr(methEl, "name", attr(methEl, "uri", "")));
					testMethod.addProperty("executionTime", attr(methEl, "executionTime", "0"));

					// Unit field (pass/fail/skip)
					String unit = attr(methEl, "unit", "");
					testMethod.addProperty("unit", unit);

					JsonArray alerts = new JsonArray();
					List<Element> alertElements = getElementsByLocalName(methEl, "alert");
					for (Element alertEl : alertElements) {
						JsonObject alert = new JsonObject();
						alert.addProperty("kind", attr(alertEl, "kind", attr(alertEl, "severity", "")));
						alert.addProperty("severity", attr(alertEl, "severity", attr(alertEl, "kind", "")));

						// Alert title / details
						String title = childText(alertEl, "title", "");
						alert.addProperty("title", title);

						String details = childText(alertEl, "details", childText(alertEl, "detail", ""));
						alert.addProperty("details", details);

						// Stack trace
						JsonArray stack = new JsonArray();
						List<Element> stackEntries = getElementsByLocalName(alertEl, "stackEntry");
						for (Element entry : stackEntries) {
							JsonObject stackItem = new JsonObject();
							stackItem.addProperty("uri", attr(entry, "uri", ""));
							stackItem.addProperty("description", attr(entry, "description", getText(entry)));
							stack.add(stackItem);
						}
						alert.add("stack", stack);
						alerts.add(alert);
					}
					testMethod.add("alerts", alerts);
					testMethods.add(testMethod);
				}
				testClass.add("testMethods", testMethods);
				testClasses.add(testClass);
			}
			prog.add("testClasses", testClasses);
			programs.add(prog);
		}

		return result;
	}

	/** Parse object structure / metadata response. */
	public static JsonObject parseObjectStructure(String xml) {
		JsonObject result = new JsonObject();
		result.addProperty("objectUrl", "");
		result.addProperty("sourceUrl", "");
		JsonArray links = new JsonArray();
		JsonArray includes = new JsonArray();
		result.add("links", links);
		result.add("includes", includes);

		if (xml == null || xml.isBlank()) {
			return result;
		}
		Document doc = parseDocument(xml);
		if (doc == null) {
			return result;
		}

		Element root = doc.getDocumentElement();

		// Object URL from root attributes
		String objectUrl = attr(root, "uri", attr(root, "objectUrl", ""));
		result.addProperty("objectUrl", objectUrl);

		// Parse links
		List<Element> linkElements = getElementsByLocalName(root, "link");
		String sourceUrl = "";
		for (Element link : linkElements) {
			JsonObject l = new JsonObject();
			String rel = attr(link, "rel", "");
			String href = attr(link, "href", "");
			String type = attr(link, "type", "");
			l.addProperty("rel", rel);
			l.addProperty("href", href);
			l.addProperty("type", type);
			links.add(l);

			if ("source".equalsIgnoreCase(rel) || "http://www.sap.com/adt/relations/source".equals(rel)) {
				sourceUrl = href;
			}
		}

		// Parse includes (class includes, etc.)
		List<Element> includeElements = getElementsByLocalName(root, "include");
		if (includeElements.isEmpty()) {
			includeElements = getElementsByLocalName(root, "classInclude");
		}
		for (Element incl : includeElements) {
			JsonObject inc = new JsonObject();
			inc.addProperty("name", attr(incl, "name", ""));
			inc.addProperty("type", attr(incl, "type", attr(incl, "includeType", "")));
			inc.addProperty("uri", attr(incl, "uri", ""));

			// Nested links inside includes
			List<Element> inclLinks = getElementsByLocalName(incl, "link");
			for (Element iLink : inclLinks) {
				String rel = attr(iLink, "rel", "");
				String href = attr(iLink, "href", "");
				if (("source".equalsIgnoreCase(rel)
						|| "http://www.sap.com/adt/relations/source".equals(rel))
						&& sourceUrl.isEmpty()) {
					sourceUrl = href;
				}
			}

			includes.add(inc);
		}

		result.addProperty("sourceUrl", sourceUrl);
		return result;
	}

	/** Parse ATC worklist findings. */
	public static JsonArray parseAtcWorklist(String xml) {
		JsonArray results = new JsonArray();
		if (xml == null || xml.isBlank()) {
			return results;
		}
		Document doc = parseDocument(xml);
		if (doc == null) {
			return results;
		}

		Element root = doc.getDocumentElement();

		List<Element> findingElements = getElementsByLocalName(root, "finding");
		if (findingElements.isEmpty()) {
			findingElements = getElementsByLocalName(root, "atcFinding");
		}

		for (Element finding : findingElements) {
			JsonObject f = new JsonObject();
			f.addProperty("priority", attr(finding, "priority", childText(finding, "priority", "")));
			f.addProperty("message", attr(finding, "messageTitle",
					attr(finding, "message", childText(finding, "messageTitle",
							childText(finding, "message", "")))));
			f.addProperty("uri", attr(finding, "uri", attr(finding, "location", "")));
			f.addProperty("checkId", attr(finding, "checkId",
					attr(finding, "checkTitle", childText(finding, "checkId", ""))));
			f.addProperty("checkTitle", attr(finding, "checkTitle", childText(finding, "checkTitle", "")));
			results.add(f);
		}

		return results;
	}

	/** Parse inactive objects list. */
	public static JsonArray parseInactiveObjects(String xml) {
		JsonArray results = new JsonArray();
		if (xml == null || xml.isBlank()) {
			return results;
		}
		Document doc = parseDocument(xml);
		if (doc == null) {
			return results;
		}

		Element root = doc.getDocumentElement();

		List<Element> entries = getElementsByLocalName(root, "entry");
		if (entries.isEmpty()) {
			entries = getElementsByLocalName(root, "inode");
		}
		if (entries.isEmpty()) {
			entries = getElementsByLocalName(root, "inactiveObject");
		}
		if (entries.isEmpty()) {
			entries = getElementsByLocalName(root, "objectReference");
		}

		for (Element entry : entries) {
			JsonObject obj = new JsonObject();
			obj.addProperty("name", attr(entry, "name", childText(entry, "name", "")));
			obj.addProperty("type", attr(entry, "type", childText(entry, "type", "")));
			obj.addProperty("uri", attr(entry, "uri", childText(entry, "uri", "")));
			obj.addProperty("user", attr(entry, "user", attr(entry, "owner", childText(entry, "user", ""))));
			results.add(obj);
		}

		return results;
	}

	/**
	 * Parse SQL / data preview results. ADT returns column-oriented XML;
	 * this transposes it to row-oriented JSON with columns[] and rows[].
	 */
	public static JsonObject parseDataPreview(String xml) {
		JsonObject result = new JsonObject();
		JsonArray columns = new JsonArray();
		JsonArray rows = new JsonArray();
		result.add("columns", columns);
		result.add("rows", rows);

		if (xml == null || xml.isBlank()) {
			return result;
		}
		Document doc = parseDocument(xml);
		if (doc == null) {
			return result;
		}

		Element root = doc.getDocumentElement();

		// Each <columns> element holds <metadata .../> and <dataSet><data>...</data>...</dataSet>
		List<Element> colElements = getElementsByLocalName(root, "columns");

		List<String> colNames = new ArrayList<>();
		List<List<String>> colData = new ArrayList<>();

		for (Element col : colElements) {
			// Extract metadata attributes
			List<Element> metaList = getElementsByLocalName(col, "metadata");
			String name = "";
			String type = "";
			String description = "";
			if (!metaList.isEmpty()) {
				Element meta = metaList.get(0);
				name = attr(meta, "name", "");
				type = attr(meta, "type", "");
				description = attr(meta, "description", "");
			}
			colNames.add(name);

			JsonObject c = new JsonObject();
			c.addProperty("name", name);
			c.addProperty("type", type);
			c.addProperty("description", description);
			columns.add(c);

			// Extract data values for this column
			List<String> values = new ArrayList<>();
			List<Element> dataSetList = getElementsByLocalName(col, "dataSet");
			if (!dataSetList.isEmpty()) {
				List<Element> dataElements = getElementsByLocalName(dataSetList.get(0), "data");
				for (Element d : dataElements) {
					values.add(getText(d));
				}
			}
			colData.add(values);
		}

		// Transpose column-oriented data to row-oriented
		int rowCount = 0;
		for (List<String> values : colData) {
			rowCount = Math.max(rowCount, values.size());
		}

		for (int r = 0; r < rowCount; r++) {
			JsonObject row = new JsonObject();
			for (int c = 0; c < colNames.size(); c++) {
				String colName = colNames.get(c);
				List<String> values = colData.get(c);
				String value = r < values.size() ? values.get(r) : "";
				row.addProperty(colName, value);
			}
			rows.add(row);
		}

		return result;
	}

	// -----------------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------------

	/** Read an attribute, trying direct name then common ADT namespace prefixes. */
	public static String attr(Element el, String name, String defaultVal) {
		if (el == null) {
			return defaultVal;
		}
		// Try direct attribute
		if (el.hasAttribute(name)) {
			return el.getAttribute(name);
		}
		// Try common ADT namespace prefixes
		String[] prefixes = {"adtcore:", "chkrun:", "atom:", "atc:", "aunit:", "program:"};
		for (String prefix : prefixes) {
			if (el.hasAttribute(prefix + name)) {
				return el.getAttribute(prefix + name);
			}
		}
		// Try namespace-aware access with wildcard
		String val = el.getAttributeNS("*", name);
		if (val != null && !val.isEmpty()) {
			return val;
		}
		// Try iterating all attributes for local name match
		for (int i = 0; i < el.getAttributes().getLength(); i++) {
			Node attrNode = el.getAttributes().item(i);
			if (name.equals(attrNode.getLocalName())) {
				return attrNode.getNodeValue();
			}
		}
		return defaultVal;
	}

	public static String childText(Element parent, String tag, String defaultVal) {
		if (parent == null) {
			return defaultVal;
		}
		List<Element> children = getElementsByLocalName(parent, tag);
		if (!children.isEmpty()) {
			String text = getText(children.get(0));
			if (!text.isEmpty()) {
				return text;
			}
		}
		return defaultVal;
	}

	public static String childAttr(Element parent, String childTag, String attrName, String defaultVal) {
		if (parent == null) {
			return defaultVal;
		}
		List<Element> children = getElementsByLocalName(parent, childTag);
		if (!children.isEmpty()) {
			return attr(children.get(0), attrName, defaultVal);
		}
		return defaultVal;
	}

	/** Parse an XML string into a DOM Document. Returns null on failure. */
	private static Document parseDocument(String xml) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			// Disable external entities for security
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			return null;
		}
	}

	private static List<Element> getElementsByLocalName(Element parent, String localName) {
		List<Element> result = new ArrayList<>();
		if (parent == null) {
			return result;
		}
		// Try namespace-agnostic search first
		NodeList nl = parent.getElementsByTagNameNS("*", localName);
		for (int i = 0; i < nl.getLength(); i++) {
			if (nl.item(i) instanceof Element) {
				result.add((Element) nl.item(i));
			}
		}
		// If nothing found, try without namespace
		if (result.isEmpty()) {
			nl = parent.getElementsByTagName(localName);
			for (int i = 0; i < nl.getLength(); i++) {
				if (nl.item(i) instanceof Element) {
					result.add((Element) nl.item(i));
				}
			}
		}
		return result;
	}

	private static String getText(Element el) {
		if (el == null) {
			return "";
		}
		String text = el.getTextContent();
		return text != null ? text.trim() : "";
	}

}
