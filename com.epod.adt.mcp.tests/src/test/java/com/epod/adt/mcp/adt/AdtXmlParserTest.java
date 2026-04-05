package com.epod.adt.mcp.adt;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

class AdtXmlParserTest {

    @Test
    void searchResultsAreExtracted() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <objectReferences xmlns:adtcore="http://www.sap.com/adt/core">
                  <objectReference adtcore:uri="/sap/bc/adt/oo/classes/zcl_foo"
                      adtcore:type="CLAS/OC" adtcore:name="ZCL_FOO"
                      adtcore:description="Test class"/>
                  <objectReference adtcore:uri="/sap/bc/adt/oo/classes/zcl_bar"
                      adtcore:type="CLAS/OC" adtcore:name="ZCL_BAR"
                      adtcore:description="Another class"/>
                </objectReferences>
                """;
        JsonArray results = AdtXmlParser.parseSearchResults(xml);
        assertEquals(2, results.size());
        assertEquals("ZCL_FOO", results.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("ZCL_BAR", results.get(1).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void searchResultsHandleBadInput() {
        // null, blank, broken XML should all return empty
        assertEquals(0, AdtXmlParser.parseSearchResults(null).size());
        assertEquals(0, AdtXmlParser.parseSearchResults("").size());
        assertEquals(0, AdtXmlParser.parseSearchResults("   ").size());
        assertEquals(0, AdtXmlParser.parseSearchResults("<broken").size());
    }

    /* Syntax check parsing */

    @Nested
    class SyntaxCheckParsing {

        @Test
        void cleanSource_noFindings() {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <chkrun:checkRunReports xmlns:chkrun="http://www.sap.com/adt/checkrun"/>
                    """;
            JsonObject result = AdtXmlParser.parseSyntaxCheckResults(xml);
            assertEquals(0, result.get("errorCount").getAsInt());
            assertEquals(0, result.get("warningCount").getAsInt());
            assertEquals(0, result.getAsJsonArray("findings").size());
        }

        @Test
        void errorsAndWarningsAreCounted() {
            String checkXml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <checkMessages>
                      <checkMessage severity="error" line="42" column="5"
                          shortText="Variable LV_FOO is not defined"/>
                      <checkMessage severity="warning" line="10" column="1"
                          shortText="MOVE is obsolete"/>
                    </checkMessages>
                    """;
            JsonObject result = AdtXmlParser.parseSyntaxCheckResults(checkXml);
            assertEquals(1, result.get("errorCount").getAsInt());
            assertEquals(1, result.get("warningCount").getAsInt());

            JsonArray findings = result.getAsJsonArray("findings");
            assertEquals(2, findings.size());
            assertEquals("42", findings.get(0).getAsJsonObject().get("line").getAsString());
        }

        @Test
        void emptyXml_returnsDefaults() {
            JsonObject result = AdtXmlParser.parseSyntaxCheckResults(null);
            assertEquals(0, result.get("errorCount").getAsInt());
            assertNotNull(result.getAsJsonArray("findings"));
        }
    }

    @Test
    void activationSuccess() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <adtcore:objectReferences xmlns:adtcore="http://www.sap.com/adt/core"/>
                """;
        JsonObject result = AdtXmlParser.parseActivationResult(xml);
        assertTrue(result.get("success").getAsBoolean());
    }

    @Test
    void activationWithErrors() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <messages>
                  <msg severity="error" shortText="Activation failed: syntax error in line 5"/>
                </messages>
                """;
        JsonObject result = AdtXmlParser.parseActivationResult(xml);
        assertFalse(result.get("success").getAsBoolean());
        assertTrue(result.getAsJsonArray("messages").size() > 0);
    }

    @Test
    void activationEmptyInputMeansSuccess() {
        // empty body = no errors reported by ADT
        JsonObject result = AdtXmlParser.parseActivationResult("");
        assertTrue(result.get("success").getAsBoolean());
    }

    @Test
    void unitTestResultsSinglePassing() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit">
                  <program name="ZCL_FOO========CP">
                    <testClass name="LTC_TEST">
                      <testMethod name="TEST_HAPPY_PATH" executionTime="12" unit="s"/>
                    </testClass>
                  </program>
                </aunit:runResult>
                """;
        JsonObject result = AdtXmlParser.parseUnitTestResults(xml);
        JsonArray programs = result.getAsJsonArray("programs");
        assertEquals(1, programs.size());

        JsonObject prog = programs.get(0).getAsJsonObject();
        assertEquals("ZCL_FOO========CP", prog.get("name").getAsString());

        JsonArray testClasses = prog.getAsJsonArray("testClasses");
        assertEquals(1, testClasses.size());
        assertEquals("LTC_TEST",
                testClasses.get(0).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void unitTestWithFailedAssertion() {
        String adtResponse = """
                <?xml version="1.0" encoding="UTF-8"?>
                <aunit:runResult xmlns:aunit="http://www.sap.com/adt/aunit">
                  <program name="ZCL_FOO========CP">
                    <testClass name="LTC_TEST">
                      <testMethod name="TEST_FAIL" executionTime="5" unit="s">
                        <alert kind="failedAssertion" severity="critical">
                          <title>Expected 1 but got 2</title>
                          <stackEntry uri="/sap/bc/adt/oo/classes/zcl_foo" description="line 42"/>
                        </alert>
                      </testMethod>
                    </testClass>
                  </program>
                </aunit:runResult>
                """;
        JsonObject result = AdtXmlParser.parseUnitTestResults(adtResponse);
        JsonObject method = result.getAsJsonArray("programs")
                .get(0).getAsJsonObject()
                .getAsJsonArray("testClasses")
                .get(0).getAsJsonObject()
                .getAsJsonArray("testMethods")
                .get(0).getAsJsonObject();

        assertEquals("TEST_FAIL", method.get("name").getAsString());
        JsonArray alerts = method.getAsJsonArray("alerts");
        assertEquals(1, alerts.size());
        assertEquals("failedAssertion", alerts.get(0).getAsJsonObject().get("kind").getAsString());
    }

    @Test
    void emptyUnitTestInput() {
        JsonObject result = AdtXmlParser.parseUnitTestResults(null);
        assertEquals(0, result.getAsJsonArray("programs").size());
    }

    @Disabled("TODO: need real multi-program response XML from a system with multiple test includes")
    @Test
    void multipleProgramsInRunResult() {
        // would need XML with 2+ <program> nodes
    }

    @Test
    void objectStructureWithSourceLink() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <class:abapClass xmlns:class="http://www.sap.com/adt/oo/classes"
                    xmlns:adtcore="http://www.sap.com/adt/core"
                    adtcore:uri="/sap/bc/adt/oo/classes/zcl_foo">
                  <link rel="http://www.sap.com/adt/relations/source"
                        href="/sap/bc/adt/oo/classes/zcl_foo/source/main"
                        type="text/plain"/>
                  <include includeType="testclasses"
                        uri="/sap/bc/adt/oo/classes/zcl_foo/includes/testclasses"/>
                </class:abapClass>
                """;
        JsonObject result = AdtXmlParser.parseObjectStructure(xml);
        assertEquals("/sap/bc/adt/oo/classes/zcl_foo", result.get("objectUrl").getAsString());
        assertEquals("/sap/bc/adt/oo/classes/zcl_foo/source/main", result.get("sourceUrl").getAsString());
        assertTrue(result.getAsJsonArray("links").size() > 0);
    }

    @Test
    void objectStructureEmptyInput() {
        JsonObject result = AdtXmlParser.parseObjectStructure("");
        assertEquals("", result.get("objectUrl").getAsString());
        assertEquals("", result.get("sourceUrl").getAsString());
    }

    @Test
    void atcFindingsAreParsed() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <worklist>
                  <finding priority="1" messageTitle="Missing exception class"
                      uri="/sap/bc/adt/oo/classes/zcl_foo" checkId="CHECK_01"
                      checkTitle="Best Practices"/>
                </worklist>
                """;
        JsonArray results = AdtXmlParser.parseAtcWorklist(xml);
        assertEquals(1, results.size());
        JsonObject finding = results.get(0).getAsJsonObject();
        assertEquals("1", finding.get("priority").getAsString());
        assertTrue(finding.get("message").getAsString().contains("Missing exception"));
    }

    @Test
    void atcEmptyWorklist() {
        assertEquals(0, AdtXmlParser.parseAtcWorklist(null).size());
    }

    @Test
    void inactiveObjectsParsed() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <inactiveObjects>
                  <entry name="ZCL_FOO" type="CLAS"
                      uri="/sap/bc/adt/oo/classes/zcl_foo" user="DEVELOPER"/>
                </inactiveObjects>
                """;
        JsonArray results = AdtXmlParser.parseInactiveObjects(xml);
        assertEquals(1, results.size());
        assertEquals("ZCL_FOO", results.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("DEVELOPER", results.get(0).getAsJsonObject().get("user").getAsString());
    }

    @Test
    void dataPreviewTransposesColumnsToRows() {
        // ADT returns data in column-oriented format (one <columns> per field),
        // but rows make more sense for downstream consumption
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <dataPreview:dataPreview xmlns:dataPreview="http://www.sap.com/adt/dataPreview">
                  <columns>
                    <metadata name="CARRID" type="C" description="Carrier ID"/>
                    <dataSet>
                      <data>LH</data>
                      <data>AA</data>
                    </dataSet>
                  </columns>
                  <columns>
                    <metadata name="CONNID" type="N" description="Connection"/>
                    <dataSet>
                      <data>0400</data>
                      <data>0017</data>
                    </dataSet>
                  </columns>
                </dataPreview:dataPreview>
                """;
        JsonObject result = AdtXmlParser.parseDataPreview(xml);

        JsonArray columns = result.getAsJsonArray("columns");
        assertEquals(2, columns.size());
        assertEquals("CARRID", columns.get(0).getAsJsonObject().get("name").getAsString());

        JsonArray rows = result.getAsJsonArray("rows");
        assertEquals(2, rows.size(), "should have one row per data entry");
        assertEquals("LH", rows.get(0).getAsJsonObject().get("CARRID").getAsString());
        assertEquals("0017", rows.get(1).getAsJsonObject().get("CONNID").getAsString());
    }

    @Test
    void dataPreviewEmptyInput() {
        JsonObject result = AdtXmlParser.parseDataPreview(null);
        assertEquals(0, result.getAsJsonArray("columns").size());
        assertEquals(0, result.getAsJsonArray("rows").size());
    }

    @Test
    void attr_findsNamespacedAttribute() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root xmlns:adtcore="http://www.sap.com/adt/core"
                    adtcore:name="ZCL_TEST"/>
                """;
        var doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xml.getBytes()));
        String val = AdtXmlParser.attr(doc.getDocumentElement(), "name", "default");
        assertEquals("ZCL_TEST", val);
    }

    @Test
    void attr_returnsDefault_whenMissing() throws Exception {
        String xml = "<root/>";
        var doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xml.getBytes()));
        assertEquals("fallback", AdtXmlParser.attr(doc.getDocumentElement(), "missing", "fallback"));
    }

    @Test
    void attr_nullElement() {
        assertEquals("safe", AdtXmlParser.attr(null, "anything", "safe"));
    }
}
