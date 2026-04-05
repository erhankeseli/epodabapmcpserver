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
package com.epod.adt.mcp.ui;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.part.ViewPart;

import com.epod.adt.mcp.Activator;
import com.epod.adt.mcp.adt.AdtSessionBridge;
import com.epod.adt.mcp.server.McpServer;
import com.epod.adt.mcp.server.McpServer.IServerStatusListener;
import com.epod.adt.mcp.tools.ActivateTool;
import com.epod.adt.mcp.tools.AtcRunTool;
import com.epod.adt.mcp.tools.CreateObjectTool;
import com.epod.adt.mcp.tools.GetSourceTool;
import com.epod.adt.mcp.tools.InactiveObjectsTool;
import com.epod.adt.mcp.tools.LockTool;
import com.epod.adt.mcp.tools.ObjectStructureTool;
import com.epod.adt.mcp.tools.RunUnitTestTool;
import com.epod.adt.mcp.tools.SearchObjectTool;
import com.epod.adt.mcp.tools.SetSourceTool;
import com.epod.adt.mcp.tools.SqlQueryTool;
import com.epod.adt.mcp.tools.SyntaxCheckTool;
import com.epod.adt.mcp.tools.UnlockTool;
import com.epod.adt.mcp.tools.UsageReferencesTool;

/**
 * Main Eclipse view for the ABAP MCP Server plugin.
 * Provides UI to select an ABAP project, connect via SNC/SSO, and control the MCP server.
 */
public class McpServerView extends ViewPart implements IServerStatusListener {

    public static final String ID = "com.epod.adt.mcp.ui.McpServerView";

    private static final String ABAP_NATURE = "com.sap.adt.abapnature";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int CONSOLE_MAX_CHARS = 500_000;

    private AdtSessionBridge bridge;
    private McpServer mcpServer;

    private Label connectionStatusLabel;
    private Label serverStatusLabel;
    private Label connectionIndicator;
    private Label serverIndicator;
    private Combo projectCombo;
    private Button connectButton;
    private Button toggleServerButton;
    private Button clearButton;
    private StyledText console;

    private Color colorGreen;
    private Color colorRed;
    private Color colorGray;

    @Override
    public void createPartControl(Composite parent) {
        bridge = new AdtSessionBridge();

        Display display = parent.getDisplay();
        colorGreen = new Color(display, 0, 160, 0);
        colorRed = new Color(display, 200, 0, 0);
        colorGray = new Color(display, 128, 128, 128);

        Composite container = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 8;
        layout.marginHeight = 8;
        layout.verticalSpacing = 6;
        container.setLayout(layout);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        createStatusBar(container);
        createProjectRow(container);
        createActionRow(container);
        createConsole(container);

        refreshProjectList();
        updateButtonStates();
    }

    @Override
    public void setFocus() {
        if (projectCombo != null && !projectCombo.isDisposed()) {
            projectCombo.setFocus();
        }
    }

    @Override
    public void dispose() {
        if (mcpServer != null && mcpServer.isRunning()) {
            mcpServer.stop();
        }
        if (colorGreen != null && !colorGreen.isDisposed()) {
            colorGreen.dispose();
        }
        if (colorRed != null && !colorRed.isDisposed()) {
            colorRed.dispose();
        }
        if (colorGray != null && !colorGray.isDisposed()) {
            colorGray.dispose();
        }
        super.dispose();
    }

    private void createStatusBar(Composite parent) {
        Composite statusBar = new Composite(parent, SWT.NONE);
        GridLayout statusLayout = new GridLayout(6, false);
        statusLayout.marginHeight = 4;
        statusLayout.marginWidth = 4;
        statusLayout.horizontalSpacing = 6;
        statusBar.setLayout(statusLayout);
        statusBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Connection status
        connectionIndicator = new Label(statusBar, SWT.NONE);
        connectionIndicator.setText("\u25CF"); // filled circle
        connectionIndicator.setForeground(colorGray);

        connectionStatusLabel = new Label(statusBar, SWT.NONE);
        connectionStatusLabel.setText("Disconnected");
        connectionStatusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Separator
        Label separator = new Label(statusBar, SWT.SEPARATOR | SWT.VERTICAL);
        GridData sepData = new GridData(SWT.CENTER, SWT.FILL, false, false);
        sepData.heightHint = 16;
        separator.setLayoutData(sepData);

        // Server status
        serverIndicator = new Label(statusBar, SWT.NONE);
        serverIndicator.setText("\u25CF");
        serverIndicator.setForeground(colorGray);

        serverStatusLabel = new Label(statusBar, SWT.NONE);
        serverStatusLabel.setText("Server stopped");
        serverStatusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    private void createProjectRow(Composite parent) {
        Composite row = new Composite(parent, SWT.NONE);
        GridLayout rowLayout = new GridLayout(3, false);
        rowLayout.marginHeight = 0;
        rowLayout.marginWidth = 0;
        row.setLayout(rowLayout);
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label label = new Label(row, SWT.NONE);
        label.setText("ABAP Project:");

        projectCombo = new Combo(row, SWT.DROP_DOWN | SWT.READ_ONLY);
        projectCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        projectCombo.setToolTipText("Select an ABAP project from your workspace");

        connectButton = new Button(row, SWT.PUSH);
        connectButton.setText("Connect");
        connectButton.setToolTipText("Connect to the selected ABAP project using its SNC/SSO session");
        GridData connectData = new GridData(SWT.RIGHT, SWT.CENTER, false, false);
        connectData.widthHint = 100;
        connectButton.setLayoutData(connectData);
        connectButton.addListener(SWT.Selection, e -> handleConnect());
    }

    private void createActionRow(Composite parent) {
        Composite row = new Composite(parent, SWT.NONE);
        GridLayout rowLayout = new GridLayout(3, false);
        rowLayout.marginHeight = 0;
        rowLayout.marginWidth = 0;
        row.setLayout(rowLayout);
        row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        toggleServerButton = new Button(row, SWT.PUSH);
        toggleServerButton.setText("Start Server");
        toggleServerButton.setToolTipText("Start or stop the embedded MCP server");
        GridData toggleData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        toggleData.widthHint = 120;
        toggleServerButton.setLayoutData(toggleData);
        toggleServerButton.addListener(SWT.Selection, e -> handleToggleServer());

        clearButton = new Button(row, SWT.PUSH);
        clearButton.setText("Clear");
        clearButton.setToolTipText("Clear the console output");
        GridData clearData = new GridData(SWT.RIGHT, SWT.CENTER, true, false);
        clearData.widthHint = 80;
        clearButton.setLayoutData(clearData);
        clearButton.addListener(SWT.Selection, e -> {
            if (console != null && !console.isDisposed()) {
                console.setText("");
            }
        });
    }

    private void createConsole(Composite parent) {
        console = new StyledText(parent, SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY | SWT.BORDER);
        console.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        console.setAlwaysShowScrollBars(false);
        console.setWordWrap(true);
    }

    private void refreshProjectList() {
        List<String> abapProjects = new ArrayList<>();
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        for (IProject project : projects) {
            try {
                if (project.isOpen() && project.hasNature(ABAP_NATURE)) {
                    abapProjects.add(project.getName());
                }
            } catch (CoreException e) {
                // Skip projects whose nature cannot be determined.
            }
        }

        projectCombo.removeAll();
        for (String name : abapProjects) {
            projectCombo.add(name);
        }

        if (!abapProjects.isEmpty()) {
            projectCombo.select(0);
        }

        if (abapProjects.isEmpty()) {
            appendToConsole("No ABAP projects found in the workspace.");
        } else {
            appendToConsole("Found " + abapProjects.size() + " ABAP project(s) in the workspace.");
        }
    }

    private void handleConnect() {
        String selectedProject = getSelectedProject();
        if (selectedProject == null) {
            appendToConsole("ERROR: Please select an ABAP project first.");
            return;
        }

        appendToConsole("Connecting to project: " + selectedProject + " ...");
        setConnecting(true);

        new Thread(() -> {
            try {
                bridge.connect(selectedProject);
                Display.getDefault().asyncExec(() -> {
                    if (isDisposed()) return;
                    connectionStatusLabel.setText("Connected to " + selectedProject);
                    connectionIndicator.setForeground(colorGreen);
                    appendToConsole("Connected to " + selectedProject + " (via ADT SNC session).");
                    updateButtonStates();
                    setConnecting(false);
                });
            } catch (Exception e) {
                Display.getDefault().asyncExec(() -> {
                    if (isDisposed()) return;
                    connectionStatusLabel.setText("Connection failed");
                    connectionIndicator.setForeground(colorRed);
                    appendToConsole("ERROR: Connection failed: " + e.getMessage());
                    updateButtonStates();
                    setConnecting(false);
                });
            }
        }, "mcp-connect").start();
    }

    private void handleToggleServer() {
        if (mcpServer != null && mcpServer.isRunning()) {
            stopServer();
        } else {
            startServer();
        }
    }

    private void startServer() {
        if (!bridge.isLoggedIn()) {
            appendToConsole("ERROR: Connect to an ABAP project before starting the server.");
            return;
        }

        int port = getConfiguredPort();
        appendToConsole("Starting MCP server on port " + port + " ...");

        try {
            mcpServer = new McpServer(port);
            mcpServer.setStatusListener(this);
            registerSapTools();
            mcpServer.start();

            Display.getDefault().asyncExec(() -> {
                if (isDisposed()) return;
                updateButtonStates();
            });
        } catch (IOException e) {
            appendToConsole("ERROR: Failed to start server: " + e.getMessage());
            logError("Failed to start MCP server", e);
        }
    }

    private void stopServer() {
        if (mcpServer == null) {
            return;
        }

        appendToConsole("Stopping MCP server ...");
        mcpServer.stop();
        mcpServer = null;

        Display.getDefault().asyncExec(() -> {
            if (isDisposed()) return;
            updateButtonStates();
        });
    }

    private void registerSapTools() {
        mcpServer.registerTools(List.of(
            new SearchObjectTool(bridge),
            new GetSourceTool(bridge),
            new SetSourceTool(bridge),
            new ActivateTool(bridge),
            new SyntaxCheckTool(bridge),
            new ObjectStructureTool(bridge),
            new UsageReferencesTool(bridge),
            new LockTool(bridge),
            new UnlockTool(bridge),
            new RunUnitTestTool(bridge),
            new CreateObjectTool(bridge),
            new InactiveObjectsTool(bridge),
            new SqlQueryTool(bridge),
            new AtcRunTool(bridge)
        ));
        appendToConsole("Registered 14 SAP tools on the MCP server.");
    }

    @Override
    public void onStatusChanged(boolean running, String message) {
        Display.getDefault().asyncExec(() -> {
            if (isDisposed()) return;

            if (running) {
                serverStatusLabel.setText("Server running (port " + getConfiguredPort() + ")");
                serverIndicator.setForeground(colorGreen);
            } else {
                serverStatusLabel.setText("Server stopped");
                serverIndicator.setForeground(colorGray);
            }

            appendToConsole(message);
            updateButtonStates();
        });
    }

    /** Appends a timestamped message to the console. Thread-safe. */
    private void appendToConsole(String message) {
        String timestamped = "[" + LocalTime.now().format(TIME_FMT) + "] " + message + "\n";

        if (Display.getCurrent() != null) {
            doAppendToConsole(timestamped);
        } else {
            Display.getDefault().asyncExec(() -> doAppendToConsole(timestamped));
        }
    }

    private void doAppendToConsole(String text) {
        if (console == null || console.isDisposed()) {
            return;
        }
        console.append(text);

        // Trim console if it exceeds the maximum character limit
        if (console.getCharCount() > CONSOLE_MAX_CHARS) {
            int excess = console.getCharCount() - CONSOLE_MAX_CHARS;
            console.replaceTextRange(0, excess, "");
        }

        // Auto-scroll to bottom
        console.setTopIndex(console.getLineCount() - 1);
    }

    private void updateButtonStates() {
        if (isDisposed()) return;

        boolean connected = bridge != null && bridge.isLoggedIn();
        boolean serverRunning = mcpServer != null && mcpServer.isRunning();

        connectButton.setEnabled(!serverRunning);
        projectCombo.setEnabled(!connected || !serverRunning);
        toggleServerButton.setEnabled(connected);
        toggleServerButton.setText(serverRunning ? "Stop Server" : "Start Server");
    }

    private void setConnecting(boolean connecting) {
        if (isDisposed()) return;
        connectButton.setEnabled(!connecting);
        projectCombo.setEnabled(!connecting);
        if (connecting) {
            connectButton.setText("Connecting...");
        } else {
            connectButton.setText("Connect");
        }
    }

    private String getSelectedProject() {
        int idx = projectCombo.getSelectionIndex();
        if (idx < 0) {
            return null;
        }
        return projectCombo.getItem(idx);
    }

    private int getConfiguredPort() {
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        int port = store.getInt("mcp.server.port");
        return (port > 0) ? port : 3000;
    }

    private boolean isDisposed() {
        return console == null || console.isDisposed();
    }

    private static void logError(String message, Throwable t) {
        Activator.getDefault().getLog().log(
            new Status(IStatus.ERROR, Activator.PLUGIN_ID, message, t)
        );
    }
}
