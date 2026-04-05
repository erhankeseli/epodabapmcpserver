package com.epod.adt.mcp.adt;

import java.util.Map;

/**
 * Stub for tests — same public API as the real bridge but
 * everything throws UnsupportedOperationException.
 * Tests use Mockito to mock this.
 */
public class AdtSessionBridge {

    public void connect(String projectName) {
        throw new UnsupportedOperationException("stub");
    }

    public boolean isLoggedIn() {
        return false;
    }

    public void logout() { }

    public String getProjectName() {
        return null;
    }

    public String get(String path, String accept) {
        throw new UnsupportedOperationException("stub");
    }

    public String get(String path, String accept, Map<String, String> extraHeaders) {
        throw new UnsupportedOperationException("stub");
    }

    public String post(String path, String body, String contentType, String accept) {
        throw new UnsupportedOperationException("stub");
    }

    public String post(String path, String body, String contentType, String accept, Map<String, String> extraHeaders) {
        throw new UnsupportedOperationException("stub");
    }

    public String put(String path, String body, String contentType) {
        throw new UnsupportedOperationException("stub");
    }

    public String put(String path, String body, String contentType, String accept) {
        throw new UnsupportedOperationException("stub");
    }

    public String put(String path, String body, String contentType, String accept, Map<String, String> extraHeaders) {
        throw new UnsupportedOperationException("stub");
    }

    public String putWithHeaders(String path, String body, String contentType, Map<String, String> extraHeaders) {
        throw new UnsupportedOperationException("stub");
    }

    public void delete(String path) {
        throw new UnsupportedOperationException("stub");
    }

    public String postEnqueue(String path, String body, String contentType, String accept, Map<String, String> extraHeaders) {
        throw new UnsupportedOperationException("stub");
    }

    public String putEnqueue(String path, String body, String contentType, String accept, Map<String, String> extraHeaders) {
        throw new UnsupportedOperationException("stub");
    }
}
