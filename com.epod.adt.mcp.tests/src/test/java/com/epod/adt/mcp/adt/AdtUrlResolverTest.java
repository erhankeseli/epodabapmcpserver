package com.epod.adt.mcp.adt;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;

class AdtUrlResolverTest {

    @Test
    void resolveClassUrl() {
        assertEquals("/sap/bc/adt/oo/classes/zcl_foo",
                AdtUrlResolver.resolveObjectUrl("CLAS", "ZCL_FOO"));
    }

    @Test
    void resolveClassUrl_longForm() {
        // both CLAS and CLASS should work the same
        assertEquals(
                AdtUrlResolver.resolveObjectUrl("CLAS", "ZCL_TEST"),
                AdtUrlResolver.resolveObjectUrl("CLASS", "ZCL_TEST"));
    }

    @Test
    void resolveInterfaceUrl() {
        assertEquals("/sap/bc/adt/oo/interfaces/zif_bar",
                AdtUrlResolver.resolveObjectUrl("INTF", "ZIF_BAR"));
    }

    @Test
    void resolveProgramUrl() {
        assertEquals("/sap/bc/adt/programs/programs/zreport",
                AdtUrlResolver.resolveObjectUrl("PROG", "ZREPORT"));
    }

    @Test
    void resolveFunctionGroupUrl() {
        assertEquals("/sap/bc/adt/functions/groups/z_my_fg",
                AdtUrlResolver.resolveObjectUrl("FUGR", "Z_MY_FG"));
    }

    @Test
    void funcUrlKeepsGroupPlaceholder() {
        // FUNC template has {group} that can't be resolved here
        String url = AdtUrlResolver.resolveObjectUrl("FUNC", "Z_MY_FM");
        assertNotNull(url);
        assertTrue(url.contains("{group}"), "should keep {group} placeholder");
        assertTrue(url.contains("z_my_fm"));
    }

    @Test
    void resolveTableUrl() {
        assertEquals("/sap/bc/adt/ddic/tables/ztable",
                AdtUrlResolver.resolveObjectUrl("TABL", "ZTABLE"));
    }

    @Test
    void resolveCdsViewUrl() {
        assertEquals("/sap/bc/adt/ddic/ddl/sources/zi_salesorder",
                AdtUrlResolver.resolveObjectUrl("DDLS", "ZI_SALESORDER"));
    }

    @Test
    void resolveBdefUrl() {
        // camelCase in the path is intentional - that's how ADT serves it
        assertEquals("/sap/bc/adt/acm/behaviorDefinitions/zi_travel",
                AdtUrlResolver.resolveObjectUrl("BDEF", "ZI_TRAVEL"));
    }

    @Test
    void typeIsCaseInsensitive() {
        assertEquals(
                AdtUrlResolver.resolveObjectUrl("CLAS", "ZCL_FOO"),
                AdtUrlResolver.resolveObjectUrl("clas", "ZCL_FOO"));
    }

    @Test
    void nameIsLowercased() {
        String url = AdtUrlResolver.resolveObjectUrl("CLAS", "ZCL_UPPER_CASE");
        assertTrue(url.endsWith("zcl_upper_case"));
    }

    @Test
    void unknownTypeReturnsNull() {
        assertNull(AdtUrlResolver.resolveObjectUrl("XYZZY", "SOMETHING"));
    }

    @Test
    void nullInputsReturnNull() {
        assertNull(AdtUrlResolver.resolveObjectUrl(null, "FOO"));
        assertNull(AdtUrlResolver.resolveObjectUrl("CLAS", null));
        assertNull(AdtUrlResolver.resolveObjectUrl(null, null));
    }

    @Test
    void sourceUrlAppendsSourceMain() {
        String url = AdtUrlResolver.resolveSourceUrl("CLAS", "ZCL_FOO");
        assertEquals("/sap/bc/adt/oo/classes/zcl_foo/source/main", url);
        // unknown types should just return null
        assertNull(AdtUrlResolver.resolveSourceUrl("NOPE", "WHATEVER"));
    }

    @Test
    void typeEnumContainsExpectedEntries() {
        JsonArray arr = AdtUrlResolver.buildTypeEnumArray();
        assertTrue(arr.size() >= 10, "should have at least the primary ABAP types");
        // spot check a few
        boolean hasCLAS = false, hasDDLS = false;
        for (int i = 0; i < arr.size(); i++) {
            String val = arr.get(i).getAsString();
            if ("CLAS".equals(val)) hasCLAS = true;
            if ("DDLS".equals(val)) hasDDLS = true;
        }
        assertTrue(hasCLAS);
        assertTrue(hasDDLS);
    }

    @Test
    void isFunctionModuleUrl_positive() {
        assertTrue(AdtUrlResolver.isFunctionModuleUrl(
                "/sap/bc/adt/functions/groups/zfg/fmodules/z_my_func"));
    }

    @Test
    void isFunctionModuleUrl_negative() {
        assertFalse(AdtUrlResolver.isFunctionModuleUrl(
                "/sap/bc/adt/oo/classes/zcl_foo"));
        assertFalse(AdtUrlResolver.isFunctionModuleUrl(null));
    }
}
