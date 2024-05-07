///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.editor.EditorWindow;
import edu.cmu.tetradapp.editor.FinalizingParameterEditor;
import edu.cmu.tetradapp.editor.ParameterEditor;
import edu.cmu.tetradapp.model.SessionNodeWrapper;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.model.UnlistedSessionModel;
import edu.cmu.tetradapp.session.*;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.DisplayNode;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.Point;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serial;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Wraps a SessionNodeWrapper as a DisplayNode for presentation in a SessionWorkbench. Connecting these nodes using
 * SessionEdges results in parents being added to appropriate SessionNodes underlying. Double clicking these nodes
 * launches corresponding model editors.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see SessionEditorWorkbench
 * @see edu.cmu.tetrad.graph.Edge
 * @see SessionNode
 * @see Session
 */
public final class SessionEditorNode extends DisplayNode {

    @Serial
    private static final long serialVersionUID = -6145843764762585351L;
    /**
     * The simulation edu.cmu.tetrad.study (used to edit the repetition values).
     */
    private final SimulationStudy simulationStudy;
    /**
     * The configuration for this editor node.
     */
    private final SessionNodeConfig config;

    /**
     * The popup for this node.
     */
    JPopupMenu popup;
    /**
     * If an editor has been opened, this is a reference to that editor. Used to close the editor if necessary.
     */
    private EditorWindow spawnedEditor;

    /**
     * A reference to the sessionWrapper, the model associated with this node.
     */
    private SessionWrapper sessionWrapper;

    /**
     * Wraps the given SessionNodeWrapper as a SessionEditorNode.
     *
     * @param modelNode       a {@link edu.cmu.tetradapp.model.SessionNodeWrapper} object
     * @param simulationStudy a {@link SimulationStudy} object
     */
    public SessionEditorNode(SessionNodeWrapper modelNode, SimulationStudy simulationStudy) {
        setModelNode(modelNode);
        TetradApplicationConfig appConfig = TetradApplicationConfig.getInstance();
        this.config = appConfig.getSessionNodeConfig(modelNode.getButtonType());
        if (this.config == null) {
            throw new NullPointerException("There is no configuration for node of type: " + modelNode.getButtonType());
        }
        if (simulationStudy == null) {
            throw new NullPointerException(
                    "Simulation edu.cmu.tetrad.study must not be null.");
        }
        SessionDisplayComp displayComp = this.config.getSessionDisplayCompInstance();

        this.simulationStudy = simulationStudy;
        displayComp.setName(modelNode.getSessionName());

        if (displayComp instanceof NoteDisplayComp) {
            createParamObjects(this);
            setDisplayComp(displayComp);
            setLayout(new BorderLayout());
            add((JComponent) getSessionDisplayComp(), BorderLayout.CENTER);
            setSelected(false);
            this.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e) || e.isControlDown()) {
                        ToolTipManager toolTipManager
                                = ToolTipManager.sharedInstance();
                        toolTipManager.setInitialDelay(750);
                        getNotePopup().show(SessionEditorNode.this, e.getX(), e.getY());
                    }

                    e.consume();
                }
            });
        } else {
            setDisplayComp(displayComp);
            setLayout(new BorderLayout());
            add((JComponent) getSessionDisplayComp(), BorderLayout.CENTER);
            setSelected(false);
            createParamObjects(this);
            addListeners(this, modelNode);
        }
    }

    /**
     * Update the model classes of the given node to the latest available model classes, of such exists. Otherwise,
     * leave the model classes of the node unchanged.
     */
    private static void loadModelClassesFromConfig(SessionNode sessionNode) {
        String nodeName = sessionNode.getBoxType();

        if (nodeName != null) {
            String baseName = SessionEditorNode.extractBase(nodeName);
            Class<?>[] newModelClasses = SessionEditorNode.modelClasses(baseName);

            if (newModelClasses != null) {
                sessionNode.setModelClasses(newModelClasses);
            } else {
                throw new RuntimeException("Model classes for this session "
                                           + "node were not set in the configuration.");
            }
        }
    }

    /**
     * @return the substring of <code>name</code> up to but not including a contiguous string of digits at the end. For
     * example, given "Graph123"
     */
    private static String extractBase(String name) {
        if (name == null) {
            throw new NullPointerException("Name must not be null.");
        }

        for (int i = name.length() - 1; i >= 0; i--) {
            if (!Character.isDigit(name.charAt(i))) {
                return name.substring(0, i + 1);
            }
        }

        return "Node";
    }

    /**
     * @return the model classes associated with the given button type.
     * @throws NullPointerException if no classes are stored for the given type.
     */
    private static Class<?>[] modelClasses(String boxType) {
        TetradApplicationConfig config = TetradApplicationConfig.getInstance();
        SessionNodeConfig nodeConfig = config.getSessionNodeConfig(boxType);
        if (nodeConfig == null) {
            throw new NullPointerException("THere is no configuration for " + boxType);
        }

        return nodeConfig.getModels();
    }

    /**
     * <p>adjustToModel.</p>
     */
    public void adjustToModel() {
        String acronym = getAcronym();

        // Set the color.
        getSessionDisplayComp().setHasModel(!"No model".equals(acronym));

        // Set the text for the model acronym.
        getSessionDisplayComp().setAcronym(acronym);

        // Make sure the node is deselected.
        setSelected(false);

        Dimension size = getSize();
        Point location = getLocation();

        int centerX = (int) location.getX() + size.width / 2;
        int centerY = (int) location.getY() + size.height / 2;

        int newX = centerX - getPreferredSize().width / 2;
        int newY = centerY - getPreferredSize().height / 2;

        setLocation(newX, newY);
        setSize(getPreferredSize());
        repaint();
    }

    /**
     * @return the acronym for the contained model class.
     */
    private String getAcronym() {
        SessionNodeWrapper modelNode = (SessionNodeWrapper) getModelNode();
        SessionNode sessionNode = modelNode.getSessionNode();
        Object model = sessionNode.getModel();
        if (model == null) {
            return "No model";
        } else {
            Class<?> modelClass = model.getClass();
            SessionNodeModelConfig modelConfig = this.config.getModelConfig(modelClass);

            if (modelConfig == null) {
                System.out.println("Tried to load model config for " + modelClass);
                return modelClass.getSimpleName();
            }

            return modelConfig.acronym();
        }
    }

    /**
     * Launches the editor associates with this node.
     */
    @Override
    public void doDoubleClickAction() {
        doDoubleClickAction(null);
    }

    /**
     * Performs a double-click action on the given session wrapper graph.
     *
     * @param sessionWrapper a Graph object to perform the action on
     */
    @Override
    public void doDoubleClickAction(Graph sessionWrapper) {
        this.sessionWrapper = (SessionWrapper) sessionWrapper;

        class MyWatchedProcess extends WatchedProcess {
            public void watch() {
                TetradLogger.getInstance().setTetradLoggerConfig(getSessionNode().getLoggerConfig());
                launchEditorVisit();
            }
        }

        new MyWatchedProcess();
    }

    private void launchEditorVisit() {
        try {

            // If there is already an editor open, don't launch another one.
            if (spawnedEditor() != null) {
                return;
            }

            boolean created = createModel(false);

            if (!created) {
                return;
            }

            SessionNode sessionNode = getSessionNode();
            boolean cloned = sessionNode.useClonedModel();

            SessionModel model = sessionNode.getModel();
            Class<?> modelClass = model.getClass();
            SessionNodeModelConfig modelConfig = this.config.getModelConfig(modelClass);

            Object[] arguments = {model};
            JPanel editor = modelConfig.getEditorInstance(arguments);
            addEditorListener(editor);

            ModificationRegistery.registerEditor(sessionNode, editor);

            String descrip = modelConfig.name();
            editor.setName(getName() + " (" + descrip + ")");

            EditorWindow editorWindow = new EditorWindow(editor, editor.getName(), "Done", cloned, this);

            editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
                @Override
                public void internalFrameClosing(InternalFrameEvent e) {
                    if (getChildren().iterator().hasNext()) {
                        finishedEditingDialog();
                    }

                    ModificationRegistery.unregisterSessionNode(
                            sessionNode);
                    SessionEditorNode.this.spawnedEditor = null;

                    EditorWindow window = (EditorWindow) e.getSource();
                    if (window.isCanceled()) {
                        sessionNode.restoreOriginalModel();
                    }

                    sessionNode.forgetSavedModel();
                }
            });

            DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
            editorWindow.pack();
            editorWindow.setVisible(true);
            this.spawnedEditor = editorWindow;

            if (this.sessionWrapper != null) {
                this.sessionWrapper.setSessionChanged(true);
            }

        } catch (CouldNotCreateModelException e) {
            SessionUtils.showPermissibleParentsDialog(e.getModelClass(),
                    this, true, true);
            e.printStackTrace();

        } catch (ClassCastException e) {
            e.printStackTrace();
        } catch (Exception e) {
            Throwable cause = e;

            while (cause.getCause() != null) {
                cause = cause.getCause();
            }

            Component centeringComp = this;
            String s = cause.getMessage();

            if (!"".equals(s)) {
                JOptionPane.showMessageDialog(centeringComp, s,
                        null, JOptionPane.WARNING_MESSAGE);
            }

            e.printStackTrace();
        }

    }

    /**
     * Sets the selection status of the node.
     *
     * @param selected a boolean
     */
    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        getSessionDisplayComp().setSelected(selected);
    }

    private void addListeners(SessionEditorNode sessionEditorNode,
                              SessionNodeWrapper modelNode) {
        // Add a mouse listener for popups.
        sessionEditorNode.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) || e.isControlDown()) {
                    ToolTipManager toolTipManager
                            = ToolTipManager.sharedInstance();
                    toolTipManager.setInitialDelay(750);
                    sessionEditorNode.getPopup().show(sessionEditorNode, e.getX(), e.getY());
                }

                e.consume();
            }
        });

        sessionEditorNode.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                sessionEditorNode.getSimulationStudy().getSession().setSessionChanged(true);
            }
        });

        SessionNode sessionNode = modelNode.getSessionNode();

        sessionNode.addSessionListener(new SessionAdapter() {

            @Override
            public void modelCreated(SessionEvent sessionEvent) {
                sessionEditorNode.adjustToModel();

                // This code is here to allow multiple editor windows to be
                // opened at the same time--if a new model is created,
                // the getModel editor window is simply closed.  jdramsey
                // 5/18/02
                if (sessionEditorNode.spawnedEditor() != null) {
                    EditorWindow editorWindow = sessionEditorNode.spawnedEditor();
                    editorWindow.closeDialog();
                }
            }

            @Override
            public void modelDestroyed(SessionEvent sessionEvent) {
                sessionEditorNode.adjustToModel();

                // This code is here to allow multiple editor windows to be
                // opened at the same time--if the getModel model is destroyed,
                // the getModel editor window is closed. jdramsey 5/18/02
                if (sessionEditorNode.spawnedEditor() != null) {
                    EditorWindow editorWindow = sessionEditorNode.spawnedEditor();
                    editorWindow.closeDialog();
                }
            }

            @Override
            public void modelUnclear(SessionEvent sessionEvent) {
                try {
                    if (SessionEditorNode.this.simulationStudy == null) {
                        boolean created = sessionEditorNode.createModel(false);

                        if (!created) {
                            return;
                        }

                        sessionEditorNode.adjustToModel();
                    }
                } catch (Exception e) {
                    String message = "I could not make a model for this box, sorry. Maybe the \n"
                                     + "parents aren't right or have not been constructed yet.";

                    e.printStackTrace();

                    JOptionPane.showMessageDialog(sessionEditorNode, message);
                }
            }
        });
    }

    /**
     * Adds a property change listener that listends for "changeNodeLabel" events.
     */
    private void addEditorListener(JPanel editor) {
        editor.addPropertyChangeListener(evt -> {
            if ("changeNodeLabel".equals(evt.getPropertyName())) {
                getDisplayComp().setName((String) evt.getNewValue());
                SessionNodeWrapper wrapper
                        = (SessionNodeWrapper) getModelNode();
                wrapper.setSessionName((String) evt.getNewValue());
                adjustToModel();
            }
        });
    }

    /**
     * Creates a popup for a note node.
     *
     * @return - popup
     */
    private JPopupMenu getNotePopup() {
        JPopupMenu popup = new JPopupMenu();

        JMenuItem renameBox = new JMenuItem("Rename note");
        renameBox.setToolTipText("<html>Renames this note.</html>");
        renameBox.addActionListener((e) -> {
            Component centeringComp = this;
            String name
                    = JOptionPane.showInputDialog(centeringComp, "New name:");

            if (!NamingProtocol.isLegalName(name)) {
                JOptionPane.showMessageDialog(centeringComp,
                        NamingProtocol.getProtocolDescription());
                return;
            }

            SessionNodeWrapper wrapper
                    = (SessionNodeWrapper) getModelNode();
            wrapper.setSessionName(name);
            getSessionDisplayComp().setName(name);
            adjustToModel();
        });

        JMenuItem cloneBox = new JMenuItem("Clone Note");
        cloneBox.setToolTipText("<html>"
                                + "Makes a copy of this session note and its contents. To clone<br>"
                                + "a whole subgraph, or to paste into a different sessions, select<br>"
                                + "the subgraph and use the Copy/Paste gadgets in the Edit menu."
                                + "</html>");
        cloneBox.addActionListener((e) -> firePropertyChange("cloneMe", null, this));

        JMenuItem deleteBox = new JMenuItem("Delete Note");
        deleteBox.setToolTipText(
                "<html>Deletes this note from the workbench</html>");

        deleteBox.addActionListener((e) -> {
            if (getSessionNode().getModel() == null) {
                Component centeringComp = this;
                int ret = JOptionPane.showConfirmDialog(centeringComp,
                        "Really delete note?");

                if (ret != JOptionPane.YES_OPTION) {
                    return;
                }
            } else {
                Component centeringComp = this;
                int ret = JOptionPane.showConfirmDialog(centeringComp,
                        "<html>"
                        + "Really delete note? Any information it contains will<br>"
                        + "be destroyed." + "</html>");

                if (ret != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            firePropertyChange("deleteNode", null, null);
        });

        JMenuItem help = new JMenuItem("Help");
        deleteBox.setToolTipText("<html>Shows help for this box.</html>");

        help.addActionListener((e) -> {
            SessionNodeWrapper sessionNodeWrapper
                    = (SessionNodeWrapper) getModelNode();
            SessionNode sessionNode = sessionNodeWrapper.getSessionNode();
            showInfoBoxForModel(sessionNode, sessionNode.getModelClasses());
        });

        popup.add(renameBox);
        popup.add(cloneBox);
        popup.add(deleteBox);
        popup.addSeparator();
        popup.add(help);

        return popup;
    }

    /**
     * Creates the popup for the node.
     */
    private JPopupMenu getPopup() {
        if (this.popup != null && this.popup.isShowing()) {
            return this.popup;
        }

        this.popup = new JPopupMenu();

        JMenuItem createModel = new JMenuItem("Create Model");
        createModel.setToolTipText("<html>Creates a new model for this node"
                                   + "<br>of the type selected.</html>");

        createModel.addActionListener((e) -> {
            try {
                if (getSessionNode().getModel() == null) {
                    createModel(false);
                } else {
                    Component centeringComp = this;
                    JOptionPane.showMessageDialog(centeringComp,
                            "Please destroy the model model first.");
                }
            } catch (Exception e1) {
                Component centeringComp = this;
                JOptionPane.showMessageDialog(centeringComp,
                        "Could not create a model for this box.");
                e1.printStackTrace();
            }
        });

        JMenuItem editModel = new JMenuItem("Edit Model");
        editModel.setToolTipText("<html>Edits the model in this node.</html>");

        editModel.addActionListener((e) -> {
            try {
                if (getSessionNode().getModel() == null) {
                    Component centeringComp = this;
                    JOptionPane.showMessageDialog(centeringComp,
                            "Sorry, no model has been created yet; there's nothing to edit.");
                } else {
                    doDoubleClickAction();
                }
            } catch (Exception e1) {
                Component centeringComp = this;
                JOptionPane.showMessageDialog(centeringComp,
                        "Double click failed. See console for exception.");
                e1.printStackTrace();
            }
        });

        JMenuItem destroyModel = new JMenuItem("Destroy Model");
        destroyModel.setToolTipText("<html>Destroys the model for this node, "
                                    + "<br>if it has one, destroying any "
                                    + "<br>downstream models as well.</html>");

        destroyModel.addActionListener((e) -> {
            Component centeringComp = this;

            if (getSessionNode().getModel() == null) {
                JOptionPane.showMessageDialog(centeringComp,
                        "Sorry, this box does not contain a model to destroy.");
                return;
            }

            Set<SessionNode> children = getSessionNode().getChildren();
            boolean found = false;

            for (SessionNode child : children) {
                if (child.getModel() != null) {
                    found = true;
                    break;
                }
            }

            if (found) {
                int ret = JOptionPane.showConfirmDialog(centeringComp,
                        "Destroying the model in this box will also destroy models in any boxes\n"
                        + "downstream. Is that OK?", null,
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                if (ret != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            // clear all children parameters
            children.forEach(sessionNode -> Arrays.stream(sessionNode.getModelClasses())
                    .forEach(m -> sessionNode.putParam(m, new Parameters())));

            // clear all parameters
            SessionNode sessionNode = getSessionNode();
            Arrays.stream(sessionNode.getModelClasses())
                    .forEach(m -> sessionNode.putParam(m, new Parameters()));

            destroyModel();
        });

        JMenuItem propagateDownstream
                = new JMenuItem("Propagate changes downstream");
        propagateDownstream.setToolTipText("<html>"
                                           + "Fills in this box and downstream boxes with models,"
                                           + "<br>overwriting any models that already exist.</html>");

        propagateDownstream.addActionListener((e) -> {
            Component centeringComp = this;

            if (getSessionNode().getModel() != null && !getSessionNode().getChildren().isEmpty()) {
                int ret = JOptionPane.showConfirmDialog(centeringComp,
                        "You will be rewriting all downstream models. Is that OK?",
                        "Confirm",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                if (ret != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            try {
                createDescendantModels();
            } catch (RuntimeException e1) {
                JOptionPane.showMessageDialog(centeringComp,
                        "Could not complete the creation of descendant models.");
                e1.printStackTrace();
            }
        });

        JMenuItem renameBox = new JMenuItem("Rename Box");
        renameBox.setToolTipText("<html>Renames this session box.</html>");
        renameBox.addActionListener((e) -> {
            Component centeringComp = this;
            String name = JOptionPane.showInputDialog(centeringComp, "New name:");

            if (!NamingProtocol.isLegalName(name)) {
                JOptionPane.showMessageDialog(centeringComp,
                        NamingProtocol.getProtocolDescription());
                return;
            }

            SessionNodeWrapper wrapper
                    = (SessionNodeWrapper) getModelNode();
            wrapper.setSessionName(name);
            getSessionDisplayComp().setName(name);
            adjustToModel();
        });

        JMenuItem cloneBox = new JMenuItem("Clone Box");
        cloneBox.setToolTipText("<html>"
                                + "Makes a copy of this session box and its contents. To clone<br>"
                                + "a whole subgraph, or to paste into a different sessions, select<br>"
                                + "the subgraph and use the Copy/Paste gadgets in the Edit menu."
                                + "</html>");
        cloneBox.addActionListener((e) -> firePropertyChange("cloneMe", null, this));

        JMenuItem deleteBox = new JMenuItem("Delete Box");
        deleteBox.setToolTipText(
                "<html>Deletes this box from the workbench</html>");

        deleteBox.addActionListener((e) -> {
            Component centeringComp = this;
            int ret = JOptionPane.showConfirmDialog(centeringComp,
                    "Are you sure you want to delete this box? It contains some work.",
                    null, JOptionPane.YES_NO_OPTION);

            if (ret != JOptionPane.YES_OPTION) {
                return;
            }

            firePropertyChange("deleteNode", null, null);
        });

        this.popup.add(createModel);

        SessionModel model = getSessionNode().getModel();
        Class<?> modelClass = (model == null)
                ? determineTheModelClass(getSessionNode())
                : model.getClass();
        if (getSessionNode().existsParameterizedConstructor(modelClass)) {
            ParameterEditor paramEditor = getParameterEditor(modelClass);

            if (paramEditor != null) {
                JMenuItem editSimulationParameters = new JMenuItem("Edit Parameters...");
                editSimulationParameters.setToolTipText("<html>");
                editSimulationParameters.addActionListener((e) -> {
                    Parameters param = getSessionNode().getParam(modelClass);
                    Object[] arguments = getSessionNode().getModelConstructorArguments(modelClass);

                    if (param != null) {
                        try {
                            editParameters(modelClass, param, arguments);
                            int ret = JOptionPane.showConfirmDialog(JOptionUtils.centeringComp(),
                                    "Should I overwrite the contents of this box and all delete the contents\n"
                                    + "of all boxes downstream?",
                                    "Double check...", JOptionPane.YES_NO_OPTION);
                            if (ret == JOptionPane.YES_OPTION) {
                                getSessionNode().destroyModel();
                                getSessionNode().createModel(modelClass, true);
                            }
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }
                    }
                });
                this.popup.add(editSimulationParameters);
            }
        }

        this.popup.add(createModel);

        this.popup.add(editModel);
        this.popup.add(destroyModel);

        this.popup.addSeparator();

        this.popup.add(renameBox);
        this.popup.add(cloneBox);
        this.popup.add(deleteBox);

        this.popup.addSeparator();

        addEditLoggerSettings(this.popup);
        this.popup.add(propagateDownstream);

        return this.popup;
    }

    private ParameterEditor getParameterEditor(Class<?> modelClass) {
        SessionNodeModelConfig modelConfig = this.config.getModelConfig(modelClass);
        return modelConfig.getParameterEditorInstance();
    }

    /**
     * Adds the "Edit logger" option if applicable.
     */
    private void addEditLoggerSettings(JPopupMenu menu) {
        SessionNodeWrapper modelNode = (SessionNodeWrapper) getModelNode();
        SessionNode sessionNode = modelNode.getSessionNode();
        TetradLoggerConfig config = sessionNode.getLoggerConfig();
        if (config != null) {
            JMenuItem item = new JMenuItem("Edit Logger Settings ...");
            item.addActionListener((e) -> showLogConfig(config));
            menu.add(item);
        }
    }

    /**
     * Shows a dialog that allows the user to change the settings for the box's model logger.
     */
    private void showLogConfig(TetradLoggerConfig config) {
        List<TetradLoggerConfig.Event> events = config.getSupportedEvents();
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(3, events.size() / 3));
        for (TetradLoggerConfig.Event event : events) {
            String id = event.getId();
            JCheckBox checkBox = new JCheckBox(event.getDescription());
            checkBox.setHorizontalTextPosition(SwingConstants.RIGHT);
            checkBox.setSelected(config.isEventActive(id));
            checkBox.addActionListener((e) -> {
                JCheckBox box = (JCheckBox) e.getSource();
                config.setEventActive(id, box.isSelected());
            });
            panel.add(checkBox);
        }
        panel.setBorder(new TitledBorder("Select Events to Log"));
        // how show the dialog
        JOptionPane.showMessageDialog(this, panel, "Edit Events to Log", JOptionPane.PLAIN_MESSAGE);
    }

    private void executeSessionNode(SessionNode sessionNode) {
        class MyWatchedProcess extends WatchedProcess {
            @Override
            public void watch() {
                final Class c = SessionEditorWorkbench.class;
                Container container = SwingUtilities.getAncestorOfClass(c,
                        SessionEditorNode.this);
                SessionEditorWorkbench workbench
                        = (SessionEditorWorkbench) container;

                System.out.println("Executing " + sessionNode);

                workbench.getSimulationStudy().execute(sessionNode, true);
            }
        }

        new MyWatchedProcess();
    }

    private void createDescendantModels() {
        class MyWatchedProcess extends WatchedProcess {
            @Override
            public void watch() {
                final Class clazz = SessionEditorWorkbench.class;
                Container container = SwingUtilities.getAncestorOfClass(clazz,
                        SessionEditorNode.this);
                SessionEditorWorkbench workbench
                        = (SessionEditorWorkbench) container;

                if (workbench != null) {
                    workbench.getSimulationStudy().createDescendantModels(
                            getSessionNode(), true);
                }
            }
        }

        new MyWatchedProcess();
    }

    /**
     * After editing a session node, either run changes or break edges.
     */
    private void finishedEditingDialog() {
        if (!ModificationRegistery.modelHasChanged(getSessionNode())) {
            return;
        }

        // If there isn't any model downstream, no point to showing the next
        // dialog.
        for (SessionNode child : getChildren()) {
            if (child.getModel() != null) {
                continue;
            }

            return;
        }

        Object[] options = {"Execute", "Break Edges"};
        Component centeringComp = this;
        int selection = JOptionPane.showOptionDialog(centeringComp,
                "Changing this node will affect its children.\n"
                + "Click on \"Execute\" to percolate changes down.\n"
                + "Click on \"Break Edges\" to leave the children the same.",
                "Warning", JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[0]);

        if (selection == 0) {
            for (SessionNode child : getChildren()) {
                executeSessionNode(child);
            }
        } else if (selection == 1) {
            for (Edge edge : this.sessionWrapper.getEdges(getModelNode())) {

                // only break edges to children.
                if (edge.getNode2() == getModelNode()) {
                    SessionNodeWrapper otherWrapper
                            = (SessionNodeWrapper) edge.getNode1();
                    SessionNode other = otherWrapper.getSessionNode();
                    if (getChildren().contains(other)) {
                        this.sessionWrapper.removeEdge(edge);
                    }
                } else {
                    SessionNodeWrapper otherWrapper
                            = (SessionNodeWrapper) edge.getNode2();
                    SessionNode other = otherWrapper.getSessionNode();
                    if (getChildren().contains(other)) {
                        this.sessionWrapper.removeEdge(edge);
                    }
                }
            }
        }
    }

    /**
     * Creates a model in the wrapped SessionNode, given the SessionNode's parent models.
     *
     * @param simulation a boolean
     * @return a boolean
     * @throws java.lang.IllegalStateException if the model cannot be created. The reason why the model cannot be
     *                                         created is in the message of the exception.
     * @throws java.lang.Exception             if any.
     */
    public boolean createModel(boolean simulation) throws Exception {
        if (getSessionNode().getModel() != null) {
            return true;
        }

        SessionNode sessionNode = getSessionNode();
        Class<?> modelClass = determineTheModelClass(sessionNode);

        if (modelClass == null && !simulation) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    this.config.getNodeSpecificMessage());
            return false;
        }

        // Must determine whether that model has a parameterizing object
        // associated with it and if so edit that. (This has to be done
        // before creating the model since it will be an argument to the
        // constructor of the model.)
        if (sessionNode.existsParameterizedConstructor(modelClass)) {
            Parameters params = sessionNode.getParam(modelClass);
            Object[] arguments = sessionNode.getModelConstructorArguments(modelClass);

            if (params != null) {
                boolean edited = editParameters(modelClass, params, arguments);

                if (!edited) {
                    return false;
                }
            }
        }

        // Finally, create the model. Don't worry, if anything goes wrong
        // in the process, an exception will be thrown with an
        // appropriate message.
        sessionNode.createModel(modelClass, simulation);
        return true;
    }

    /**
     * <p>determineTheModelClass.</p>
     *
     * @param sessionNode a {@link SessionNode} object
     * @return the model class, or null if no model class was determined.
     */
    public Class<?> determineTheModelClass(SessionNode sessionNode) {

        // The config file lists which model classes the node can be
        // associated with, based on the node's type.
        SessionEditorNode.loadModelClassesFromConfig(sessionNode);

        Class[] modelClasses = sessionNode.getConsistentModelClasses(true);

        // If you can't even put a model into the object, throw an
        // exception.
        if ((modelClasses == null) || (modelClasses.length == 0)) {
            return null;
        }

        // Choose a single model class either by asking the user or by just
        // returning the single model in the list.
        return modelClasses.length > 1 ? getModelClassFromUser(modelClasses
        ) : modelClasses[0];
    }

    /**
     * @return the selected model class, or null if no model class was selected.
     */
    private Class<?> getModelClassFromUser(Class[] modelClasses) {

        // Count the number of model classes that can be listed for the user;
        // if there's only one, don't ask the user for input.
        List<Class<?>> reducedList = new LinkedList<>();

        for (Class modelClass : modelClasses) {
            if (!(UnlistedSessionModel.class.isAssignableFrom(modelClass))) {
                reducedList.add(modelClass);
            }
        }

        if (reducedList.isEmpty()) {
            throw new RuntimeException("There is no model to choose.");
        }

        SessionNodeWrapper sessionNodeWrapper = (SessionNodeWrapper) getModelNode();
        SessionNode sessionNode = sessionNodeWrapper.getSessionNode();
        ModelChooser chooser = this.config.getModelChooserInstance(sessionNode);
        Component centeringComp = this;

        int selection = JOptionPane.showOptionDialog(centeringComp,
                chooser, chooser.getTitle(), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, null,
                new String[]{"OK", "Cancel"}, "OK");

        if (selection == 0) {
            //this.rememberLastClass = modelTypeChooser.getRemembered();
            return chooser.getSelectedModel();
        } else {
            return null;
        }
    }

    private void showInfoBoxForModel(SessionNode sessionNode, Class<?>[] modelClasses) {

        // Count the number of model classes that can be listed for the user;
        // if there's only one, don't ask the user for input.
        List<Class<?>> reducedList = new LinkedList<>();

        for (Class modelClass : modelClasses) {
            if (!(UnlistedSessionModel.class.isAssignableFrom(modelClass))) {
                reducedList.add(modelClass);
            }
        }

        if (reducedList.isEmpty()) {
            throw new RuntimeException("There is no model to choose.");
        }

        ModelChooser chooser = this.config.getModelChooserInstance(sessionNode);
        Component centeringComp = this;

        JOptionPane.showMessageDialog(centeringComp, chooser,
                "Choose Model for Help...", JOptionPane.QUESTION_MESSAGE);

        chooser.getSelectedModel();
    }

    /**
     * <p>getChildren.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<SessionNode> getChildren() {
        SessionNodeWrapper _sessionNodeWrapper
                = (SessionNodeWrapper) getModelNode();
        SessionNode _sessionNode = _sessionNodeWrapper.getSessionNode();
        return _sessionNode.getChildren();
    }

    /**
     * Destroys the model for the wrapped SessionNode.
     */
    private void destroyModel() {
        getSessionNode().destroyModel();
        getSessionNode().forgetOldModel();
    }

    /**
     * Tries to edit the parameters, returns true if successfully otherwise false is returned
     *
     * @param modelClass   a {@link java.lang.Class} object
     * @param params       a {@link edu.cmu.tetrad.util.Parameters} object
     * @param parentModels an array of {@link java.lang.Object} objects
     * @return a boolean
     */
    public boolean editParameters(Class<?> modelClass, Parameters params,
                                  Object[] parentModels) {
        if (parentModels == null) {
            throw new NullPointerException("Parent models array is null.");
        }

        if (params == null) {
            throw new NullPointerException("Parameters cannot be null.");
        }

        ParameterEditor paramEditor = getParameterEditor(modelClass);

        if (paramEditor == null) {
            // if no editor, then consider the params "edited".
            return true;
        } else {
            paramEditor.setParams(params);
            paramEditor.setParentModels(parentModels);
        }
        // If a finalizing editor and a dialog then let it handle things on itself onw.
        if (paramEditor instanceof FinalizingParameterEditor e && paramEditor instanceof JDialog) {
            e.setup();
            return e.finalizeEdit();
        }
        // wrap editor and deal with response.
        paramEditor.setup();
        JComponent editor = (JComponent) paramEditor;
        SessionNodeWrapper nodeWrapper = (SessionNodeWrapper) getModelNode();
        String buttonType = nodeWrapper.getButtonType();
        editor.setName(buttonType + " Parameter Editor");
        Component centeringComp = this;

        int ret = JOptionPane.showOptionDialog(centeringComp, editor,
                editor.getName(), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, null,
                null, null);

        // if finalizing editor, then deal with specially.
        return ret == JOptionPane.OK_OPTION && (!(paramEditor instanceof FinalizingParameterEditor)
                                                || ((FinalizingParameterEditor) paramEditor).finalizeEdit());

    }

    /**
     * <p>getSessionNode.</p>
     *
     * @return a {@link SessionNode} object
     */
    public SessionNode getSessionNode() {
        SessionNodeWrapper wrapper = (SessionNodeWrapper) this.getModelNode();
        return wrapper.getSessionNode();
    }

    /**
     * @return the spawned editor, if there is one.
     */
    private EditorWindow spawnedEditor() {
        return this.spawnedEditor;
    }

    /**
     * Sets up default parameter objects for the node.
     */
    private void createParamObjects(SessionEditorNode sessionEditorNode) {
        SessionNode sessionNode = sessionEditorNode.getSessionNode();
        Class<?>[] modelClasses = sessionNode.getModelClasses();
        for (Class clazz : modelClasses) {
            // A parameter class might exist if this session was read
            // in from a file.
            if (sessionNode.getParam(clazz) == null) {
                SessionNodeModelConfig modelConfig = this.config.getModelConfig(clazz);
                if (modelConfig == null) {
                    continue;
                }

                sessionNode.putParam(clazz, new Parameters(sessionNode.getParameters()));
            }
        }
    }

    private SimulationStudy getSimulationStudy() {
        return this.simulationStudy;
    }

    private SessionDisplayComp getSessionDisplayComp() {
        return (SessionDisplayComp) getDisplayComp();
    }
}
