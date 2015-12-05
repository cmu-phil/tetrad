///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.ParamType;
import edu.cmu.tetrad.sem.Parameter;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.session.DelegatesEditing;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.model.SemPmWrapper;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.util.StringTextField;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphNodeMeasured;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.cmu.tetradapp.workbench.LayoutMenu;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Map;

/**
 * Edits a SEM PM model.
 *
 * @author Donald Crimbchin
 * @author Joseph Ramsey
 */
public final class SemPmEditor extends JPanel implements DelegatesEditing,
        LayoutEditable {

    /**
     * The SemPm being edited.
     */
    private SemPm semPm;

    /**
     * The graphical editor for the SemPm.
     */
    private SemPmGraphicalEditor graphicalEditor;

    /**
     * A reference to the error terms menu item so it can be reset.
     */
    private JMenuItem errorTerms;

    //========================CONSTRUCTORS===========================//

    /**
     * Constructs an editor for the given SemIm.
     */
    public SemPmEditor(final SemPm semPm) {
        if (semPm == null) {
            throw new NullPointerException("SemPm must not be null.");
        }

        this.semPm = semPm;
        setLayout(new BorderLayout());
        add(graphicalEditor(), BorderLayout.CENTER);

        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        menuBar.add(file);
//        file.add(new SaveScreenshot(this, true, "Save Screenshot..."));
        file.add(new SaveComponentImage(graphicalEditor.getWorkbench(),
                "Save Graph Image..."));

        errorTerms = new JMenuItem();

        // By default, hide the error terms.
//        getSemGraph().setShowErrorTerms(false);

        if (getSemGraph().isShowErrorTerms()) {
            errorTerms.setText("Hide Error Terms");
        } else {
            errorTerms.setText("Show Error Terms");
        }

        errorTerms.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JMenuItem menuItem = (JMenuItem) e.getSource();

                if ("Hide Error Terms".equals(menuItem.getText())) {
                    menuItem.setText("Show Error Terms");
                    getSemGraph().setShowErrorTerms(false);
                    graphicalEditor().resetLabels();
                } else if ("Show Error Terms".equals(menuItem.getText())) {
                    menuItem.setText("Hide Error Terms");
                    getSemGraph().setShowErrorTerms(true);
                    graphicalEditor().resetLabels();
                }
            }
        });

        JMenuItem fixOneLoadingPerLatent = new JMenuItem("Fix One Loading Per Latent");

        fixOneLoadingPerLatent.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int ret = JOptionPane.showConfirmDialog(JOptionUtils.centeringComp(),
                        "This will fix one measurement for each latent to 1.0 " +
                                "and cannot be undone. Proceed?", "Confirm",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

                if (ret == JOptionPane.YES_OPTION) {
                    Graph graph = semPm.getGraph();

                    semPm.fixOneLoadingPerLatent();

//                    for (Node x : nodes) {
//                        if (x.getNodeType() == NodeType.LATENT) {
//                            Parameter p = semPm.getParameter(x, x);
//                            p.setFixed(true);
//                            p.setInitializedRandomly(false);
//                            p.setStartingValue(1.0);
//                        }
//                    }

                    graphicalEditor.resetLabels();
                }
            }
        });

        JMenuItem fixLatentErrorVariances = new JMenuItem("Fix Latent Error Variances");

        fixLatentErrorVariances.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int ret = JOptionPane.showConfirmDialog(JOptionUtils.centeringComp(),
                        "This will fix each latent error variance to 1.0. Proceed?", "Confirm",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

                if (ret == JOptionPane.YES_OPTION) {
                    semPm.fixLatentErrorVariances();

                    graphicalEditor.resetLabels();
                }
            }
        });

//        JMenuItem fixErrorVariances = new JMenuItem("Fix Error Variances");
//
//        fixErrorVariances.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                int ret = JOptionPane.showConfirmDialog(JOptionUtils.centeringComp(),
//                        "This will fix each error variance to 1.0. Proceed?", "Confirm",
//                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
//
//                if (ret == JOptionPane.YES_OPTION) {
//                    Graph graph = semPm.getGraph();
//                    List<Node> nodes = graph.getNodes();
//
//                    for (Node x : nodes) {
//                        Parameter p = semPm.getParameter(x, x);
//                        p.setFixed(true);
//                        p.setInitializedRandomly(false);
//                        p.setStartingValue(1.0);
//                    }
//
//                    graphicalEditor.resetLabels();
//                }
//            }
//        });

//        JMenuItem unfixAll = new JMenuItem("Unfix All");
//
//        unfixAll.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                int ret = JOptionPane.showConfirmDialog(JOptionUtils.centeringComp(),
//                        "This will unfix all parameters. Proceed?", "Confirm",
//                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
//
//                if (ret == JOptionPane.YES_OPTION) {
//                    for (Parameter p : semPm.getParameters()) {
//                        p.setFixed(false);
//                        p.setInitializedRandomly(true);
//
//                        graphicalEditor.resetLabels();
//                    }
//                }
//            }
//        }
//
//        );

        JMenuItem startFactorLoadingsAtOne = new JMenuItem("Start All Factor Loadings At 1.0");

        startFactorLoadingsAtOne.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int ret = JOptionPane.showConfirmDialog(JOptionUtils.centeringComp(),
                        "This will start all factor loadings at 1.0 " +
                                "for purposes of estimation. Proceed?", "Confirm",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

                if (ret == JOptionPane.YES_OPTION) {
                    Graph graph = semPm.getGraph();

                    for (Edge edge : graph.getEdges()) {
                        Node x = edge.getNode1();
                        Node y = edge.getNode2();

                        Parameter p;

                        if (edge.pointsTowards(x)) {
                            if (!(x.getNodeType() == NodeType.MEASURED
                                    && y.getNodeType() == NodeType.LATENT)) {
                                continue;
                            }

                            p = semPm.getParameter(y, x);
                        } else {
                            if (!(y.getNodeType() == NodeType.MEASURED
                                    && x.getNodeType() == NodeType.LATENT)) {
                                continue;
                            }

                            p = semPm.getParameter(x, y);
                        }

                        p.setInitializedRandomly(false);
                        p.setStartingValue(1.0);
                    }

                    graphicalEditor.resetLabels();
                }
            }
        });

        JMenu params = new JMenu("Parameters");
        params.add(errorTerms);
        params.add(fixOneLoadingPerLatent);
        params.add(fixLatentErrorVariances);
//        params.add(fixErrorVariances);
//        params.add(unfixAll);
        params.add(startFactorLoadingsAtOne);
        menuBar.add(params);

        menuBar.add(new

                LayoutMenu(this)

        );

        add(menuBar, BorderLayout.NORTH);

    }

    private SemGraph getSemGraph() {
        return semPm.getGraph();
    }

    /**
     * Constructs a new SemImEditor from the given OldSemEstimateAdapter.
     */
    public SemPmEditor(SemPmWrapper semImWrapper) {
        this(semImWrapper.getSemPm());
    }

    public JComponent getEditDelegate() {
        return this;
    }

    public Graph getGraph() {
        return graphicalEditor().getWorkbench().getGraph();
    }

    @Override
    public Map getModelEdgesToDisplay() {
        return graphicalEditor().getWorkbench().getModelEdgesToDisplay();
    }
    public Map getModelNodesToDisplay() {
        return graphicalEditor().getWorkbench().getModelNodesToDisplay();
    }

    public IKnowledge getKnowledge() {
        return graphicalEditor().getWorkbench().getKnowledge();
    }

    public Graph getSourceGraph() {
        return graphicalEditor().getWorkbench().getSourceGraph();
    }

    public void layoutByGraph(Graph graph) {
        SemGraph _graph = (SemGraph) graphicalEditor().getWorkbench().getGraph();
        _graph.setShowErrorTerms(false);
        graphicalEditor().getWorkbench().layoutByGraph(graph);
        _graph.resetErrorPositions();
//        graphicalEditor().getWorkbench().setGraph(_graph);
        errorTerms.setText("Show Error Terms");
    }

    public void layoutByKnowledge() {
        SemGraph _graph = (SemGraph) graphicalEditor().getWorkbench().getGraph();
        _graph.setShowErrorTerms(false);
        graphicalEditor().getWorkbench().layoutByKnowledge();
        _graph.resetErrorPositions();
//        graphicalEditor().getWorkbench().setGraph(_graph);
        errorTerms.setText("Show Error Terms");
    }

    //========================PRIVATE METHODS===========================//

    private SemPm getSemPm() {
        return semPm;
    }

    private SemPmGraphicalEditor graphicalEditor() {
        if (this.graphicalEditor == null) {
            this.graphicalEditor = new SemPmGraphicalEditor(getSemPm());
            this.graphicalEditor.addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    firePropertyChange(evt.getPropertyName(), null, null);
                }
            });
        }
        return this.graphicalEditor;
    }

}

/**
 * Edits the parameters of the SemIm using a graph workbench.
 */
class SemPmGraphicalEditor extends JPanel {

    /**
     * Font size for parameter values in the graph.
     */
    private static Font SMALL_FONT = new Font("Dialog", Font.PLAIN, 10);

    /**
     * The SemPm being edited.
     */
    private SemPm semPm;

    /**
     * Workbench for the graphical editor.
     */
    private GraphWorkbench workbench;

    /**
     * This delay needs to be restored when the component is hidden.
     */
    private int savedTooltipDelay;

    /**
     * Constructs a SemPm graphical editor for the given SemIm.
     */
    public SemPmGraphicalEditor(SemPm semPm) {
        this.semPm = semPm;
        setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(workbench());
        scroll.setPreferredSize(new Dimension(450, 450));

        add(scroll, BorderLayout.CENTER);
        setBorder(new TitledBorder(
                "Double click parameter names to edit parameters"));

        addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent e) {
                resetLabels();
                ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                setSavedTooltipDelay(toolTipManager.getInitialDelay());
                toolTipManager.setInitialDelay(100);
            }

            public void componentHidden(ComponentEvent e) {
                ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                toolTipManager.setInitialDelay(getSavedTooltipDelay());
            }
        });
    }

    //========================PRIVATE PROTECTED METHODS======================//

    private void beginEdgeEdit(Edge edge) {
        Parameter parameter = getEdgeParameter(edge);
        ParameterEditor paramEditor = new ParameterEditor(parameter, semPm());

        paramEditor.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                firePropertyChange("modelChanged", null, null);
            }
        });

        int ret = JOptionPane.showOptionDialog(workbench(), paramEditor,
                "Parameter Properties", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, null, null);

        if (ret == JOptionPane.OK_OPTION) {
            parameter.setName(paramEditor.getParamName());
            parameter.setFixed(paramEditor.isFixed());
            parameter.setInitializedRandomly(
                    paramEditor.isInitializedRandomly());
            parameter.setStartingValue(paramEditor.getStartingValue());
            resetLabels();
        }
    }

    private void beginNodeEdit(Node node) {
        Parameter parameter = getNodeParameter(node);

        if (parameter == null) {
            throw new IllegalStateException(
                    "There is no variance parameter in " + "model for node " +
                            node + ".");
        }

        ParameterEditor paramEditor = new ParameterEditor(parameter, semPm());

        paramEditor.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                System.out.println("** " + evt.getPropertyName());
                firePropertyChange(evt.getPropertyName(), null, null);
            }
        });

        int ret = JOptionPane.showOptionDialog(workbench(), paramEditor,
                "Parameter Properties", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, null, null);

        if (ret == JOptionPane.OK_OPTION) {
            parameter.setName(paramEditor.getParamName());
            parameter.setFixed(paramEditor.isFixed());
            parameter.setInitializedRandomly(
                    paramEditor.isInitializedRandomly());
            parameter.setStartingValue(paramEditor.getStartingValue());
            resetLabels();
        }
    }

    private SemPm semPm() {
        return this.semPm;
    }

    private Graph graph() {
        return semPm().getGraph();
    }

    private GraphWorkbench workbench() {
        if (this.getWorkbench() == null) {
            this.workbench = new GraphWorkbench(graph());
            this.getWorkbench().setAllowDoubleClickActions(false);
            resetLabels();
            addMouseListenerToGraphNodesMeasured();
        }
        return getWorkbench();
    }

    public void resetLabels() {
        for (Object o : graph().getEdges()) {
            resetEdgeLabel((Edge) (o));
        }

        List nodes = graph().getNodes();

        for (Object node : nodes) {
            resetNodeLabel((Node) node);
        }

        workbench().repaint();
    }

    private void resetEdgeLabel(Edge edge) {
        Parameter parameter = getEdgeParameter(edge);

        if (parameter != null) {
            JLabel label = new JLabel();

            if (parameter.getType() == ParamType.COVAR) {
                label.setForeground(Color.GREEN.darker().darker());
            }

            if (parameter.isFixed()) {
                label.setForeground(Color.RED);
            }

            label.setBackground(Color.white);
            label.setOpaque(true);
            label.setFont(SMALL_FONT);
            label.setText(parameter.getName());
            label.addMouseListener(new EdgeMouseListener(edge, this));
            workbench().setEdgeLabel(edge, label);
        } else {
            workbench().setEdgeLabel(edge, null);
        }
    }

    private void resetNodeLabel(Node node) {
        Parameter parameter = getNodeParameter(node);

        if (parameter == null) {
            workbench().setNodeLabel(node, null, 0, 0);
        } else {
            JLabel label = new JLabel();
            label.setForeground(Color.blue);
            label.setBackground(Color.white);
            label.setFont(SMALL_FONT);
            label.setText(parameter.getName());
            label.addMouseListener(new NodeMouseListener(node, this));

            if (parameter.isFixed()) {
                label.setForeground(Color.RED);
            }

            // Offset the nodes slightly differently depending on whether
            // they're error nodes or not.
            if (node.getNodeType() == NodeType.ERROR) {
                label.setOpaque(false);
                workbench().setNodeLabel(node, label, -10, -10);
            } else {
                label.setOpaque(false);
                workbench().setNodeLabel(node, label, 0, 0);
            }
        }
    }

    private Parameter getNodeParameter(Node node) {
        Parameter parameter = semPm().getMeanParameter(node);

        if (parameter == null) {
            parameter = semPm().getVarianceParameter(node);
        }
        return parameter;
    }

    /**
     * @return the parameter for the given edge, or null if the edge does not
     * have a parameter associated with it in the model. The edge must be either
     * directed or bidirected, since it has to come from a SemGraph. For
     * directed edges, this method automatically adjusts if the user has changed
     * the endpoints of an edge X1 --> X2 to X1 <-- X2 and returns the correct
     * parameter.
     *
     * @throws IllegalArgumentException if the edge is neither directed nor
     *                                  bidirected.
     */
    public Parameter getEdgeParameter(Edge edge) {
        if (Edges.isDirectedEdge(edge)) {
            return semPm().getCoefficientParameter(edge.getNode1(), edge.getNode2());
        } else if (Edges.isBidirectedEdge(edge)) {
            return semPm().getCovarianceParameter(edge.getNode1(), edge.getNode2());
        }

        throw new IllegalArgumentException(
                "This is not a directed or bidirected edge: " + edge);
    }

    private void addMouseListenerToGraphNodesMeasured() {
        List nodes = graph().getNodes();

        for (Object node : nodes) {
            Object displayNode = workbench().getModelNodesToDisplay().get(node);

            if (displayNode instanceof GraphNodeMeasured) {
                DisplayNode _displayNode = (DisplayNode) displayNode;
                _displayNode.setToolTipText(
                        getEquationOfNode(_displayNode.getModelNode())
                );
            }
        }
    }

    private String getEquationOfNode(Node node) {
        String eqn = node.getName() + " = B0_" + node.getName();

        SemGraph semGraph = semPm().getGraph();
        List parentNodes = semGraph.getParents(node);

        for (Object parentNodeObj : parentNodes) {
            Node parentNode = (Node) parentNodeObj;

//            Parameter edgeParam = semPm().getEdgeParameter(
//                    semGraph.getEdge(parentNode, node));

            Parameter edgeParam = getEdgeParameter(
                    semGraph.getDirectedEdge(parentNode, node));

            if (edgeParam != null) {
                eqn = eqn + " + " + edgeParam.getName() + "*" + parentNode;
            }
        }

        eqn = eqn + " + " + semPm().getGraph().getExogenous(node);

        return eqn;
    }

    private int getSavedTooltipDelay() {
        return savedTooltipDelay;
    }

    private void setSavedTooltipDelay(int savedTooltipDelay) {
        this.savedTooltipDelay = savedTooltipDelay;
    }

    public GraphWorkbench getWorkbench() {
        return workbench;
    }

    //=======================PRIVATE INNER CLASSES==========================//

    private final static class EdgeMouseListener extends MouseAdapter {
        private Edge edge;
        private SemPmGraphicalEditor editor;

        public EdgeMouseListener(Edge edge, SemPmGraphicalEditor editor) {
            this.edge = edge;
            this.editor = editor;
        }

        private Edge getEdge() {
            return edge;
        }

        private SemPmGraphicalEditor getEditor() {
            return editor;
        }

        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
                getEditor().beginEdgeEdit(getEdge());
            }
        }
    }

    private final static class NodeMouseListener extends MouseAdapter {
        private Node node;
        private SemPmGraphicalEditor editor;

        public NodeMouseListener(Node node, SemPmGraphicalEditor editor) {
            this.node = node;
            this.editor = editor;
        }

        private Node getNode() {
            return node;
        }

        private SemPmGraphicalEditor getEditor() {
            return editor;
        }

        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
                getEditor().beginNodeEdit(getNode());
            }
        }
    }

    /**
     * Edits the properties of a parameter.
     */
    private static final class ParameterEditor extends JPanel {

        // Needed to avoid paramName conflicts.
        private SemPm semPm;
        private Parameter parameter;
        private String paramName;
        private boolean fixed;
        private boolean initializedRandomly;
        private double startingValue;

        public ParameterEditor(Parameter parameter, SemPm semPm) {
            if (parameter == null) {
                throw new NullPointerException();
            }

            if (semPm == null) {
                throw new NullPointerException();
            }

            this.parameter = parameter;
            setParamName(parameter.getName());
            setFixed(parameter.isFixed());
            setInitializedRandomly(parameter.isInitializedRandomly());
            setStartingValue(parameter.getStartingValue());

            this.semPm = semPm;
            setupEditor();
        }

        private void setupEditor() {
            int length = 8;

            StringTextField nameField =
                    new StringTextField(getParamName(), length);
            nameField.setFilter(new StringTextField.Filter() {
                public String filter(String value, String oldValue) {
                    try {
                        Parameter paramForName =
                                semPm().getParameter(value);

                        // Ignore if paramName already exists.
                        if (paramForName == null &&
                                !value.equals(getParamName())) {
                            setParamName(value);
                        }

                        return getParamName();
                    } catch (IllegalArgumentException e) {
                        return getParamName();
                    } catch (Exception e) {
                        return getParamName();
                    }
                }
            });

            nameField.setHorizontalAlignment(JTextField.RIGHT);
            nameField.grabFocus();
            nameField.selectAll();

            JCheckBox fixedCheckBox = new JCheckBox();
            fixedCheckBox.setSelected(isFixed());
            fixedCheckBox.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    JCheckBox checkBox = (JCheckBox) e.getSource();
                    setFixed(checkBox.isSelected());
                }
            });

            final DoubleTextField startingValueField =
                    new DoubleTextField(getStartingValue(), length,
                            NumberFormatUtil.getInstance().getNumberFormat());

            startingValueField.setFilter(new DoubleTextField.Filter() {
                public double filter(double value, double oldValue) {
                    try {
                        setStartingValue(value);
                        return value;
                    } catch (Exception e) {
                        return oldValue;
                    }
                }
            });

            startingValueField.setEditable(!isInitializedRandomly());

            JRadioButton randomRadioButton = new JRadioButton(
                    "Drawn randomly");
            randomRadioButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    setInitializedRandomly(true);
                    startingValueField.setEditable(false);
                }
            });

            JRadioButton startRadioButton = new JRadioButton();
            startRadioButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    setInitializedRandomly(false);
                    startingValueField.setEditable(true);
                }
            });

            ButtonGroup buttonGroup = new ButtonGroup();
            buttonGroup.add(randomRadioButton);
            buttonGroup.add(startRadioButton);

            if (isInitializedRandomly()) {
                buttonGroup.setSelected(randomRadioButton.getModel(), true);
            } else {
                buttonGroup.setSelected(startRadioButton.getModel(), true);
            }

            final DoubleTextField meanField =
                    new DoubleTextField(0, length, NumberFormatUtil.getInstance().getNumberFormat());

            meanField.setEditable(!isInitializedRandomly());

            final DoubleTextField rangeFromField =
                    new DoubleTextField(0, length, NumberFormatUtil.getInstance().getNumberFormat());

            rangeFromField.setEditable(!isInitializedRandomly());

            final DoubleTextField rangeToField =
                    new DoubleTextField(0, length, NumberFormatUtil.getInstance().getNumberFormat());

            rangeToField.setEditable(!isInitializedRandomly());

            Box b0 = Box.createHorizontalBox();
            b0.add(new JLabel("Parameter Type: "));
            b0.add(Box.createHorizontalGlue());
            b0.add(new JLabel(parameter.getType().toString()));

            Box b1 = Box.createHorizontalBox();
            b1.add(new JLabel("Parameter Name: "));
            b1.add(Box.createHorizontalGlue());
            b1.add(nameField);

            Box b2 = Box.createHorizontalBox();
            b2.add(new JLabel("Fixed for Estimation? "));
            b2.add(Box.createHorizontalGlue());
            b2.add(fixedCheckBox);

            Box b3 = Box.createHorizontalBox();
            b3.add(new JLabel("Starting Value for Estimation:"));
            b3.add(Box.createHorizontalGlue());

            Box b4 = Box.createHorizontalBox();
            b4.add(Box.createHorizontalStrut(10));
            b4.add(randomRadioButton);
            b4.add(Box.createHorizontalGlue());

            Box b5 = Box.createHorizontalBox();
            b5.add(Box.createHorizontalStrut(10));
            b5.add(startRadioButton);
            b5.add(new JLabel("Set to: "));
            b5.add(Box.createHorizontalGlue());
            b5.add(startingValueField);

            Box p = Box.createVerticalBox();

            p.add(b0);
            p.add(b1);
            p.add(b2);
            p.add(b3);
            p.add(b4);
            p.add(b5);

            add(p);
        }

        private SemPm semPm() {
            return this.semPm;
        }

        public String getParamName() {
            return paramName;
        }

        public void setParamName(String name) {
            firePropertyChange("modelChanged", null, null);
            this.paramName = name;
        }

        public double getStartingValue() {
            return startingValue;
        }

        public void setStartingValue(double startingValue) {
            firePropertyChange("modelChanged", null, null);
            this.startingValue = startingValue;
        }

        public boolean isFixed() {
            return fixed;
        }

        public void setFixed(boolean fixed) {
            firePropertyChange("modelChanged", null, null);
            this.fixed = fixed;
        }

        public boolean isInitializedRandomly() {
            return initializedRandomly;
        }

        public void setInitializedRandomly(boolean initializedRandomly) {
            firePropertyChange("modelChanged", null, null);
            this.initializedRandomly = initializedRandomly;
        }

        public Parameter getParameter() {
            return parameter;
        }
    }
}




