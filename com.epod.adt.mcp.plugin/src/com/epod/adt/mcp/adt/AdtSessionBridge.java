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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;

import com.sap.adt.communication.message.HeadersFactory;
import com.sap.adt.communication.message.IHeaders;
import com.sap.adt.communication.message.IMessageBody;
import com.sap.adt.communication.message.IResponse;
import com.sap.adt.communication.resources.AdtRestResourceFactory;
import com.sap.adt.communication.resources.IRestResource;
import com.sap.adt.communication.resources.IRestResourceFactory;
import com.sap.adt.communication.resources.ResourceException;
import com.sap.adt.communication.session.AdtSystemSessionFactory;
import com.sap.adt.communication.session.IEnqueueSystemSession;
import com.sap.adt.communication.session.ISystemSession;
import com.sap.adt.project.IAdtCoreProject;

/**
 * Bridge between MCP tools and Eclipse ADT REST API.
 * Handles SNC/SSO authentication transparently via ADT communication framework.
 */
public class AdtSessionBridge {

	public static final String SESSION_TYPE_HEADER = "X-sap-adt-sessiontype";

	private String destinationId;
	private IRestResourceFactory resourceFactory;
	private String projectName;

	public void connect(String projectName) {
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		if (project == null || !project.isOpen()) {
			throw new IllegalArgumentException("Project not found or closed: " + projectName);
		}
		IAdtCoreProject adtProject = project.getAdapter(IAdtCoreProject.class);
		if (adtProject == null) {
			throw new IllegalArgumentException("Not an ADT project: " + projectName);
		}
		this.destinationId = adtProject.getDestinationId();
		this.resourceFactory = AdtRestResourceFactory.createRestResourceFactory();
		this.projectName = projectName;
	}

	public boolean isLoggedIn() {
		return destinationId != null;
	}

	public void logout() {
		destinationId = null;
		resourceFactory = null;
		projectName = null;
	}

	public String getProjectName() {
		return projectName;
	}

	// --- HTTP Methods ---

	public String get(String path, String accept) {
		return get(path, accept, null);
	}

	public String get(String path, String accept, Map<String, String> extraHeaders) {
		ensureConnected();
		URI uri = URI.create(path);
		IRestResource resource = resourceFactory.createResourceWithStatelessSession(uri, destinationId);
		IHeaders headers = HeadersFactory.newHeaders();
		if (accept != null) {
			headers.setField(HeadersFactory.newField("Accept", accept));
		}
		applyExtraHeaders(headers, extraHeaders);
		IResponse response = resource.get(null, headers, IResponse.class);
		return readResponseBody(response);
	}

	public String post(String path, String body, String contentType, String accept) {
		return post(path, body, contentType, accept, null);
	}

	public String post(String path, String body, String contentType, String accept, Map<String, String> extraHeaders) {
		ensureConnected();
		URI uri = URI.create(path);
		IRestResource resource = resourceFactory.createResourceWithStatelessSession(uri, destinationId);
		IHeaders headers = HeadersFactory.newHeaders();
		if (contentType != null) {
			headers.setField(HeadersFactory.newField("Content-Type", contentType));
		}
		if (accept != null) {
			headers.setField(HeadersFactory.newField("Accept", accept));
		}
		applyExtraHeaders(headers, extraHeaders);
		IMessageBody messageBody = body != null ? createMessageBody(body, contentType) : null;
		IResponse response = resource.post(null, headers, IResponse.class, messageBody);
		return readResponseBody(response);
	}

	public String put(String path, String body, String contentType) {
		return put(path, body, contentType, null, null);
	}

	public String put(String path, String body, String contentType, String accept) {
		return put(path, body, contentType, accept, null);
	}

	public String put(String path, String body, String contentType, String accept, Map<String, String> extraHeaders) {
		ensureConnected();
		URI uri = URI.create(path);
		IRestResource resource = resourceFactory.createResourceWithStatelessSession(uri, destinationId);
		IHeaders headers = HeadersFactory.newHeaders();
		if (contentType != null) {
			headers.setField(HeadersFactory.newField("Content-Type", contentType));
		}
		if (accept != null) {
			headers.setField(HeadersFactory.newField("Accept", accept));
		}
		applyExtraHeaders(headers, extraHeaders);
		IMessageBody messageBody = body != null ? createMessageBody(body, contentType) : null;
		IResponse response = resource.put(null, headers, IResponse.class, messageBody);
		return readResponseBody(response);
	}

	public String putWithHeaders(String path, String body, String contentType, Map<String, String> extraHeaders) {
		return put(path, body, contentType, null, extraHeaders);
	}

	public void delete(String path) {
		ensureConnected();
		URI uri = URI.create(path);
		IRestResource resource = resourceFactory.createResourceWithStatelessSession(uri, destinationId);
		resource.delete(null);
	}

	public IResponse getRaw(String path, String accept, Map<String, String> extraHeaders) {
		ensureConnected();
		URI uri = URI.create(path);
		IRestResource resource = resourceFactory.createResourceWithStatelessSession(uri, destinationId);
		IHeaders headers = HeadersFactory.newHeaders();
		if (accept != null) {
			headers.setField(HeadersFactory.newField("Accept", accept));
		}
		applyExtraHeaders(headers, extraHeaders);
		return resource.get(null, headers, IResponse.class);
	}

	public IResponse postRaw(String path, String body, String contentType, String accept, Map<String, String> extraHeaders) {
		ensureConnected();
		URI uri = URI.create(path);
		IRestResource resource = resourceFactory.createResourceWithStatelessSession(uri, destinationId);
		IHeaders headers = HeadersFactory.newHeaders();
		if (contentType != null) {
			headers.setField(HeadersFactory.newField("Content-Type", contentType));
		}
		if (accept != null) {
			headers.setField(HeadersFactory.newField("Accept", accept));
		}
		applyExtraHeaders(headers, extraHeaders);
		IMessageBody messageBody = body != null ? createMessageBody(body, contentType) : null;
		return resource.post(null, headers, IResponse.class, messageBody);
	}

	// --- Enqueue session methods (for LOCK → WRITE → UNLOCK) ---

	private IEnqueueSystemSession getEnqueueSession() {
		ensureConnected();
		return AdtSystemSessionFactory.createSystemSessionFactory().getEnqueueSession(destinationId);
	}

	private IRestResource createEnqueueResource(String path) {
		URI uri = URI.create(path);
		return resourceFactory.createRestResource(uri, getEnqueueSession());
	}

	public String postEnqueue(String path, String body, String contentType, String accept, Map<String, String> extraHeaders) {
		ensureConnected();
		IRestResource resource = createEnqueueResource(path);
		IHeaders headers = HeadersFactory.newHeaders();
		if (contentType != null) {
			headers.setField(HeadersFactory.newField("Content-Type", contentType));
		}
		if (accept != null) {
			headers.setField(HeadersFactory.newField("Accept", accept));
		}
		applyExtraHeaders(headers, extraHeaders);
		IMessageBody messageBody = body != null ? createMessageBody(body, contentType) : null;
		IResponse response = resource.post(null, headers, IResponse.class, messageBody);
		return readResponseBody(response);
	}

	public String putEnqueue(String path, String body, String contentType, String accept, Map<String, String> extraHeaders) {
		ensureConnected();
		IRestResource resource = createEnqueueResource(path);
		IHeaders headers = HeadersFactory.newHeaders();
		if (contentType != null) {
			headers.setField(HeadersFactory.newField("Content-Type", contentType));
		}
		if (accept != null) {
			headers.setField(HeadersFactory.newField("Accept", accept));
		}
		applyExtraHeaders(headers, extraHeaders);
		IMessageBody messageBody = body != null ? createMessageBody(body, contentType) : null;
		IResponse response = resource.put(null, headers, IResponse.class, messageBody);
		return readResponseBody(response);
	}

	// --- Helpers ---

	private void ensureConnected() {
		if (destinationId == null) {
			throw new IllegalStateException("Not connected to SAP. Call connect() first.");
		}
	}

	private void applyExtraHeaders(IHeaders headers, Map<String, String> extraHeaders) {
		if (extraHeaders != null) {
			for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
				headers.setField(HeadersFactory.newField(entry.getKey(), entry.getValue()));
			}
		}
	}

	public static String readResponseBody(IResponse response) {
		if (response == null) {
			return "";
		}
		IMessageBody body = response.getBody();
		if (body == null) {
			return "";
		}
		try (InputStream is = body.getContent()) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int len;
			while ((len = is.read(buffer)) != -1) {
				baos.write(buffer, 0, len);
			}
			return baos.toString(StandardCharsets.UTF_8.name());
		} catch (IOException e) {
			throw new RuntimeException("Failed to read response body", e);
		}
	}

	private IMessageBody createMessageBody(String content, String contentType) {
		byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
		return new IMessageBody() {
			@Override
			public InputStream getContent() {
				return new ByteArrayInputStream(bytes);
			}

			@Override
			public void writeTo(OutputStream os) throws IOException {
				os.write(bytes);
			}

			@Override
			public long getContentLength() {
				return bytes.length;
			}

			@Override
			public String getContentType() {
				return contentType;
			}
		};
	}
}
