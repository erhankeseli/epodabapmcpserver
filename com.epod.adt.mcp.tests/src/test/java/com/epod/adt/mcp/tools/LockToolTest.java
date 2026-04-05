package com.epod.adt.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LockToolTest {

    @Test
    void parsesStandardLockResponse() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <asx:values xmlns:asx="http://www.sap.com/abapxml">
                  <LOCK_HANDLE>ABC123DEF456</LOCK_HANDLE>
                </asx:values>
                """;
        assertEquals("ABC123DEF456", LockTool.extractLockHandle(xml));
    }

    @Test
    void namespacedTagsAlsoWork() {
        // real SAP response might look like this
        String xml = "<asx:values xmlns:asx=\"http://www.sap.com/abapxml\">" +
                "<asx:lockHandle>handle_xyz</asx:lockHandle></asx:values>";
        assertEquals("handle_xyz", LockTool.extractLockHandle(xml));
    }

    @Test
    void whitespaceIsTrimmed() {
        String xml = "<root><lockHandle>  TRIMME  </lockHandle></root>";
        assertEquals("TRIMME", LockTool.extractLockHandle(xml));
    }

    @ParameterizedTest
    @CsvSource({
            "lockHandle,val1",
            "LOCK_HANDLE,val2",
    })
    void variousTagNames(String tag, String expected) {
        String xml = "<root><" + tag + ">" + expected + "</" + tag + "></root>";
        assertEquals(expected, LockTool.extractLockHandle(xml));
    }

    @Test
    void emptyOrMissingInputThrows() {
        assertThrows(IllegalStateException.class, () -> LockTool.extractLockHandle(""));
        assertThrows(IllegalStateException.class, () -> LockTool.extractLockHandle(null));
        // XML with no lock handle element
        assertThrows(IllegalStateException.class,
                () -> LockTool.extractLockHandle("<root><something>not a handle</something></root>"));
    }
}
