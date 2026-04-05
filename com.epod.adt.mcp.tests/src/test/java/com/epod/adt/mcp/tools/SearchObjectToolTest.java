package com.epod.adt.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.epod.adt.mcp.adt.AdtSessionBridge;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class SearchObjectToolTest {

    private AdtSessionBridge bridge;
    private SearchObjectTool tool;

    @BeforeEach
    void setUp() {
        bridge = mock(AdtSessionBridge.class);
        tool = new SearchObjectTool(bridge);
    }

    @Test
    void toolMetadata() {
        assertEquals("sap_search_object", tool.getName());
        assertNotNull(tool.getDescription());
        assertFalse(tool.getDescription().isEmpty());
    }

    @Test
    void schemaHasQueryProperty() {
        JsonObject schema = tool.getInputSchema();
        JsonObject props = schema.getAsJsonObject("properties");
        assertTrue(props.has("query"));
        assertTrue(props.has("maxResults"));
    }

    @Test
    void searchCallsAdtQuickSearch() {
        // minimal ADT response, real ones have xmlns declarations
        when(bridge.get(anyString(), eq("application/xml")))
                .thenReturn("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <objectReferences>
                          <objectReference uri="/sap/bc/adt/oo/classes/zcl_result"
                              type="CLAS/OC" name="ZCL_RESULT" description="found it"/>
                        </objectReferences>
                        """);

        JsonObject args = new JsonObject();
        args.addProperty("query", "ZCL_RES*");
        String result = tool.execute(args);

        verify(bridge).get(contains("quickSearch"), eq("application/xml"));

        var arr = JsonParser.parseString(result).getAsJsonArray();
        assertEquals(1, arr.size());
        assertEquals("ZCL_RESULT", arr.get(0).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void executeWithTypeFilter() {
        when(bridge.get(anyString(), anyString())).thenReturn("<objectReferences/>");

        JsonObject args = new JsonObject();
        args.addProperty("query", "Z*");
        args.addProperty("objType", "CLAS");
        args.addProperty("maxResults", 10);
        tool.execute(args);

        verify(bridge).get(argThat(url ->
                url.contains("objectType=CLAS") && url.contains("maxResults=10")),
                anyString());
    }

    @Test
    void missingQueryThrows() {
        assertThrows(IllegalArgumentException.class, () -> tool.execute(new JsonObject()));
    }
}
