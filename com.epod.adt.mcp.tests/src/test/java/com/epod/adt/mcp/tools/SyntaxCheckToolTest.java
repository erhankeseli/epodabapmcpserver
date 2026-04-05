package com.epod.adt.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.epod.adt.mcp.adt.AdtSessionBridge;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class SyntaxCheckToolTest {

    private AdtSessionBridge bridge;
    private SyntaxCheckTool tool;

    @BeforeEach
    void setUp() {
        bridge = mock(AdtSessionBridge.class);
        tool = new SyntaxCheckTool(bridge);
    }

    @Test
    void toolName() {
        assertEquals("sap_syntax_check", tool.getName());
        assertNotNull(tool.getDescription());
    }

    @Test
    void schemaRequiresBothTypeAndName() {
        JsonObject schema = tool.getInputSchema();
        var required = schema.getAsJsonArray("required");
        assertEquals(2, required.size());
        // just check they're both there
        String r0 = required.get(0).getAsString();
        String r1 = required.get(1).getAsString();
        assertTrue((r0.equals("objectType") && r1.equals("objectName"))
                || (r0.equals("objectName") && r1.equals("objectType")));
    }

    @Test
    void runsCheckOnResolvedUrl() {
        when(bridge.post(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("<checkMessages/>");

        JsonObject args = new JsonObject();
        args.addProperty("objectType", "CLAS");
        args.addProperty("objectName", "ZCL_TEST");
        String result = tool.execute(args);

        // should POST to /sap/bc/adt/checkruns
        verify(bridge).post(eq("/sap/bc/adt/checkruns"), contains("zcl_test"), anyString(), anyString());

        // result should parse as JSON with errorCount
        JsonObject parsed = JsonParser.parseString(result).getAsJsonObject();
        assertEquals(0, parsed.get("errorCount").getAsInt());
    }
}
