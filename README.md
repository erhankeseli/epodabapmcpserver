# EPOD ADT MCP Server

An Eclipse ADT plugin that exposes SAP ABAP systems to AI coding assistants via the [Model Context Protocol (MCP)](https://modelcontextprotocol.io). It runs an embedded HTTP server inside Eclipse, allowing any MCP-compatible client to search, read, edit, test, and analyze ABAP code -- all through the existing ADT SNC/SSO session.

No credentials are stored or transmitted by the plugin. Authentication is handled transparently by the Eclipse ADT communication framework using the SNC/X.509 session already established in your ABAP project.


## How It Works

```
MCP Client  --(HTTP/JSON-RPC)-->  Eclipse Plugin  --(ADT REST API / SNC)-->  SAP System
```

The plugin embeds an MCP-compliant HTTP server (default: `http://127.0.0.1:3000/mcp`) that:

1. Listens for JSON-RPC requests from any MCP client
2. Translates each request into ADT REST API calls against your connected SAP system
3. Returns the results back over the MCP protocol

The server binds to `127.0.0.1` only and is not accessible from the network.


## MCP Tools

The plugin registers 14 tools that cover the core ABAP development workflow:

| Tool | Description |
|------|-------------|
| `sap_search_object` | Search for ABAP objects by name, type, or pattern |
| `sap_get_source` | Read the source code of an ABAP object |
| `sap_set_source` | Write/update source code of an ABAP object |
| `sap_activate` | Activate an ABAP object |
| `sap_syntax_check` | Run syntax check on an object |
| `sap_object_structure` | Get the structure/metadata of an ABAP object |
| `sap_usage_references` | Find where-used references for an object |
| `sap_lock` | Lock an object for editing |
| `sap_unlock` | Unlock a previously locked object |
| `sap_run_unit_test` | Execute ABAP Unit tests |
| `sap_create_object` | Create a new ABAP object (class, program, etc.) |
| `sap_inactive_objects` | List objects that are inactive |
| `sap_sql_query` | Execute SQL queries via ADT |
| `sap_atc_run` | Run ATC (ABAP Test Cockpit) checks |


## Prerequisites

- **Eclipse 2024-03** or later with **SAP ADT** (ABAP Development Tools) installed
- **Java 17** or later
- An ABAP project in your Eclipse workspace with an active SNC/SSO connection to SAP
- **Maven 3.9+** (for building from source)


## Installation

### From Update Site (Pre-built)

1. In Eclipse, go to **Help > Install New Software...**
2. Add the update site URL (or point to a local directory containing the built site)
3. Select **EPOD ADT MCP Server** and complete the installation
4. Restart Eclipse

### From Source

1. Clone the repository:
   ```
   git clone https://git.epod.dev/erhan/epodabapmcpserver.git
   ```

2. Edit the target platform file to point to your local Eclipse ADT installation:
   ```
   com.epod.adt.mcp.target/epod-adt-mcp.target
   ```
   Update the `path` attribute to match your Eclipse installation directory.

3. Build with Maven:
   ```
   mvn clean verify
   ```

4. The update site is generated at:
   ```
   com.epod.adt.mcp.site/target/repository/
   ```

5. Install from the local update site in Eclipse, or copy the plugin JAR from
   `com.epod.adt.mcp.plugin/target/` into your Eclipse `dropins/` folder.


## Usage

### Starting the MCP Server

1. Open the **ABAP MCP Server** view in Eclipse:
   **Window > Show View > Other... > ABAP MCP Server**

2. Select your ABAP project from the dropdown and click **Connect**.
   This uses the project's existing ADT session (SNC/SSO) -- no credentials dialog.

3. Click **Start Server**. The embedded MCP server starts on the configured port (default: 3000).

### Configuring the Port

Go to **Window > Preferences > ABAP MCP Server** to change the server port or enable auto-start on connect.

### Connecting an MCP Client

Once the server is running, any MCP-compatible client can connect to it. Add the following to your client's MCP configuration:

```json
{
  "mcpServers": {
    "abap-adt": {
      "type": "streamable-http",
      "url": "http://127.0.0.1:3000/mcp"
    }
  }
}
```

### Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/mcp` | POST | JSON-RPC requests (initialize, tools/list, tools/call) |
| `/mcp` | GET | SSE stream for server-sent notifications |
| `/mcp` | DELETE | Close an MCP session |
| `/health` | GET | Health check (returns server status as JSON) |


## Project Structure

```
com.epod.adt.mcp.plugin/       Main plugin source code
  src/com/epod/adt/mcp/
    server/McpServer.java       Embedded HTTP server (MCP protocol 2024-11-05)
    adt/AdtSessionBridge.java   ADT REST API bridge (SNC-transparent)
    adt/AdtUrlResolver.java     Maps object types to ADT REST URLs
    adt/AdtXmlParser.java       Parses ADT XML responses
    tools/                      14 MCP tool implementations
    ui/McpServerView.java       Eclipse view (project selector, start/stop, console)
    preferences/                Preference page (port, auto-start)
com.epod.adt.mcp.feature/      Eclipse feature definition
com.epod.adt.mcp.site/         Eclipse update site (p2 repository)
com.epod.adt.mcp.target/       Target platform definition
pom.xml                         Parent POM (Maven Tycho 4.0.4)
```


## Build

Built with [Maven Tycho](https://projects.eclipse.org/projects/technology.tycho) 4.0.4:

```
mvn clean verify
```

The target platform resolves against your local Eclipse ADT installation. Update the path in `com.epod.adt.mcp.target/epod-adt-mcp.target` before building.

Supported platforms: Windows (x86_64), Linux (x86_64), macOS (x86_64, aarch64).


## Protocol

The server implements [MCP protocol version 2024-11-05](https://spec.modelcontextprotocol.io/2024-11-05/) over Streamable HTTP transport with JSON-RPC 2.0 messaging. Session management uses the `Mcp-Session-Id` header.


## License

Copyright 2025 Erhan Keseli. Licensed under the [Apache License, Version 2.0](LICENSE).
