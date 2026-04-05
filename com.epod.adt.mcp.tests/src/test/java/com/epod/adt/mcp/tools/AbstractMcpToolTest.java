package com.epod.adt.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import com.epod.adt.mcp.adt.AdtSessionBridge;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

class AbstractMcpToolTest {

    // concrete subclass to access protected helpers
    static class TestTool extends AbstractMcpTool {
        TestTool(AdtSessionBridge bridge) { super(bridge); }

        @Override public String getName() { return "test"; }
        @Override public String getDescription() { return "test tool"; }
        @Override public JsonObject getInputSchema() { return new JsonObject(); }
        @Override public String execute(JsonObject args) { return ""; }

        // expose protected statics for testing
        static String pub_optString(JsonObject o, String k) { return optString(o, k); }
        static int pub_optInt(JsonObject o, String k, int d) { return optInt(o, k, d); }
        static String pub_toObjectUrl(String s) { return toObjectUrl(s); }
        static String pub_urlEncode(String v) { return urlEncode(v); }
        static String pub_sanitizeFmSource(String s) { return sanitizeFmSource(s); }
        static String pub_escapeXml(String t) { return escapeXml(t); }
    }

    @Test
    void optString_returnsValue() {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", "ZCL_FOO");
        assertEquals("ZCL_FOO", TestTool.pub_optString(obj, "name"));
    }

    @Test
    void optStringMissingOrNull() {
        assertNull(TestTool.pub_optString(new JsonObject(), "nope"));

        JsonObject obj = new JsonObject();
        obj.add("x", JsonNull.INSTANCE);
        assertNull(TestTool.pub_optString(obj, "x"));
        assertNull(TestTool.pub_optString(null, "key"));
    }

    @Test
    void optInt_returnsValue() {
        JsonObject obj = new JsonObject();
        obj.addProperty("max", 50);
        assertEquals(50, TestTool.pub_optInt(obj, "max", 100));
    }

    @Test
    void optInt_returnsDefault() {
        assertEquals(42, TestTool.pub_optInt(new JsonObject(), "missing", 42));
    }

    @Test
    void toObjectUrl_stripsSourceMain() {
        assertEquals("/sap/bc/adt/oo/classes/zcl_foo",
                TestTool.pub_toObjectUrl("/sap/bc/adt/oo/classes/zcl_foo/source/main"));
    }

    @Test
    void toObjectUrl_noSourceSuffix() {
        assertEquals("/sap/bc/adt/oo/classes/zcl_foo",
                TestTool.pub_toObjectUrl("/sap/bc/adt/oo/classes/zcl_foo"));
    }

    @Test
    void toObjectUrl_null() {
        assertNull(TestTool.pub_toObjectUrl(null));
    }

    @Test
    void urlEncode_spaceBecomes_plus() {
        String encoded = TestTool.pub_urlEncode("hello world");
        assertTrue(encoded.contains("+") || encoded.contains("%20"));
    }

    @Test
    void urlEncode_null() {
        assertEquals("", TestTool.pub_urlEncode(null));
    }

    @Test
    void sanitizeFmSource_removesAdtInjectedLines() {
        // ADT injects *" lines into function module source that aren't real comments
        String input = "*\" comment injected by ADT\nDATA lv_foo TYPE string.\n*\" another comment\nWRITE lv_foo.";
        String result = TestTool.pub_sanitizeFmSource(input);
        assertFalse(result.contains("*\""));
        assertTrue(result.contains("DATA lv_foo"));
        assertTrue(result.contains("WRITE lv_foo"));
    }

    @Test
    void sanitizeFmSource_keepsRegularAbapComments() {
        // regular * comments (without the quote) are valid ABAP and should stay
        String src = "* This is a normal comment\nDATA lv_x TYPE i.";
        String result = TestTool.pub_sanitizeFmSource(src);
        assertTrue(result.contains("* This is a normal comment"), "regular ABAP comments should survive");
    }

    @Test
    void sanitizeFmSource_null() {
        assertNull(TestTool.pub_sanitizeFmSource(null));
    }

    @Test
    void sanitizeFmSource_noComments() {
        assertEquals("WRITE 'hi'.", TestTool.pub_sanitizeFmSource("WRITE 'hi'."));
    }

    @Test
    void xmlSpecialCharsAreEscaped() {
        assertEquals("&amp;&lt;&gt;&quot;&apos;", TestTool.pub_escapeXml("&<>\"'"));
    }

    @Test
    void escapeXml_null() {
        assertEquals("", TestTool.pub_escapeXml(null));
    }

    @Test
    void escapeXml_plainText() {
        assertEquals("hello", TestTool.pub_escapeXml("hello"));
    }

    @Test
    void resolveSourceUrlArg_directUrl() {
        AdtSessionBridge bridge = mock(AdtSessionBridge.class);
        TestTool tool = new TestTool(bridge);

        JsonObject args = new JsonObject();
        args.addProperty("objectSourceUrl", "/sap/bc/adt/oo/classes/zcl_x/source/main");
        assertEquals("/sap/bc/adt/oo/classes/zcl_x/source/main", tool.resolveSourceUrlArg(args));
    }

    @Test
    void resolveSourceUrlArg_fromTypeAndName() {
        AdtSessionBridge bridge = mock(AdtSessionBridge.class);
        TestTool tool = new TestTool(bridge);

        JsonObject args = new JsonObject();
        args.addProperty("objectType", "CLAS");
        args.addProperty("objectName", "ZCL_FOO");
        String url = tool.resolveSourceUrlArg(args);
        assertNotNull(url);
        assertTrue(url.contains("zcl_foo"));
        assertTrue(url.endsWith("/source/main"));
    }

    @Test
    void resolveSourceUrlArg_missingBothThrows() {
        AdtSessionBridge bridge = mock(AdtSessionBridge.class);
        TestTool tool = new TestTool(bridge);

        assertThrows(IllegalArgumentException.class,
                () -> tool.resolveSourceUrlArg(new JsonObject()));
    }
}
