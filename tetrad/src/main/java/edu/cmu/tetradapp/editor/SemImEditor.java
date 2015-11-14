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
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetradapp.model.SemEstimatorWrapper;
import edu.cmu.tetradapp.model.SemImWrapper;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphNodeMeasured;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.cmu.tetradapp.workbench.LayoutMenu;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Edits a SEM instantiated model.
 *
 * @author Donald Crimbchin
 * @author Joseph Ramsey
 */
public final class SemImEditor extends JPanel implements LayoutEditable, DoNotScroll {
    public enum TabbedPaneDefault {GRAPHICAL, TABULAR, COVMATRIX, STATS}

    /**
     * The SemIm being edited.
     */
    private ISemIm semIm;

    /**
     * The graphical editor for the SemIm.
     */
    private SemImGraphicalEditor semImGraphicalEditor;

    /**
     * Edits the parameters in a simple list.
     */
    private SemImTabularEditor semImTabularEditor;

    /**
     * Displays one of four possible implied covariance matrices.
     */
    private ImpliedMatricesPanel impliedMatricesPanel;

    /**
     * Displays the model statistics.
     */
    private ModelStatisticsPanel modelStatisticsPanel;

    /**
     * Maximum number of free parameters for which statistics will be
     * calculated. (Calculating standard errors is high complexity.) Set this to
     * zero to turn  off statistics calculations (which can be problematic
     * sometimes).
     */
    private int maxFreeParamsForStatistics = 1000;

    /**
     * True iff covariance parameters are edited as correlations.
     */
    private boolean editCovariancesAsCorrelations = false;

    /**
     * True iff covariance parameters are edited as correlations.
     */
    private boolean editIntercepts = false;
    private JTabbedPane tabbedPane;
    private String graphicalEditorTitle = "Graphical Editor";
    private String tabularEditorTitle = "Tabular Editor";
    private boolean editable = true;
    private int matrixSelection = 0;
    private JCheckBoxMenuItem meansItem;
    private JCheckBoxMenuItem interceptsItem;
    private JMenuItem errorTerms;

    //========================CONSTRUCTORS===========================//

    public SemImEditor(ISemIm semIm) {
        this(semIm, "Graphical Editor", "Tabular Editor");
    }

    public SemImEditor(SemIm semIm) {
        this((ISemIm) semIm);
    }

    public SemImEditor(final ISemIm semIm, String graphicalEditorTitle,
                       String tabularEditorTitle) {
        this(semIm, graphicalEditorTitle, tabularEditorTitle, TabbedPaneDefault.GRAPHICAL);
    }

    /**
     * Constructs an editor for the given SemIm.
     */
    public SemImEditor(final ISemIm semIm, String graphicalEditorTitle,
                       String tabularEditorTitle, TabbedPaneDefault tabbedPaneDefault) {
        if (semIm == null) {
            throw new NullPointerException("The SEM IM has not been specified.");
        }

//        checkForUnmeasuredLatents(semIm);

        this.semIm = semIm;
//        semIm.getEstIm().getGraph().setShowErrorTerms(false);
        this.graphicalEditorTitle = graphicalEditorTitle;
        this.tabularEditorTitle = tabularEditorTitle;
        setLayout(new BorderLayout());

        tabbedPane = new JTabbedPane();
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);


        if (tabbedPaneDefault == TabbedPaneDefault.GRAPHICAL) {
            tabbedPane.add(graphicalEditorTitle, graphicalEditor());
            tabbedPane.add(tabularEditorTitle, tabularEditor());
            tabbedPane.add("Implied Matrices", impliedMatricesPanel());
            tabbedPane.add("Model Statistics", modelStatisticsPanel());
//            tabbedPane.setSelectedComponent(graphicalEditor());
        } else if (tabbedPaneDefault == TabbedPaneDefault.TABULAR) {
            tabbedPane.add(tabularEditorTitle, tabularEditor());
            tabbedPane.add(graphicalEditorTitle, graphicalEditor());
            tabbedPane.add("Implied Matrices", impliedMatricesPanel());
            tabbedPane.add("Model Statistics", modelStatisticsPanel());
//            tabbedPane.setSelectedComponent(tabularEditor());
        } else if (tabbedPaneDefault == TabbedPaneDefault.COVMATRIX) {
            tabbedPane.add("Implied Matrices", impliedMatricesPanel());
            tabbedPane.add("Model Statistics", modelStatisticsPanel());
            tabbedPane.add(graphicalEditorTitle, graphicalEditor());
            tabbedPane.add(tabularEditorTitle, tabularEditor());
//            tabbedPane.setSelectedComponent(impliedMatricesPanel());
        } else if (tabbedPaneDefault == TabbedPaneDefault.STATS) {
            tabbedPane.add("Model Statistics", modelStatisticsPanel());
            tabbedPane.add(graphicalEditorTitle, graphicalEditor());
            tabbedPane.add(tabularEditorTitle, tabularEditor());
            tabbedPane.add("Implied Matrices", impliedMatricesPanel());
//            tabbedPane.setSelectedComponent(modelStatisticsPanel());
        }

        add(tabbedPane, BorderLayout.CENTER);

        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        menuBar.add(file);
//        file.add(new SaveScreenshot(this, true, "Save Screenshot..."));
        file.add(new SaveComponentImage(semImGraphicalEditor.getWorkbench(),
                "Save Graph Image..."));
        file.add(this.getCopyMatrixMenuItem());

        JCheckBoxMenuItem covariances =
                new JCheckBoxMenuItem("Show standard deviations");
        JCheckBoxMenuItem correlations =
                new JCheckBoxMenuItem("Show correlations");

        ButtonGroup correlationGroup = new ButtonGroup();
        correlationGroup.add(covariances);
        correlationGroup.add(correlations);
        covariances.setSelected(true);

        covariances.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setEditCovariancesAsCorrelations(false);
            }
        });

        correlations.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setEditCovariancesAsCorrelations(true);
            }
        });

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

//        menuBar.add(graph);

        meansItem = new JCheckBoxMenuItem("Show means");
        interceptsItem = new JCheckBoxMenuItem("Show intercepts");

        ButtonGroup meansGroup = new ButtonGroup();
        meansGroup.add(meansItem);
        meansGroup.add(interceptsItem);
        meansItem.setSelected(true);

        meansItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setEditIntercepts(false);
            }
        });

        interceptsItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setEditIntercepts(true);
            }
        });

        JMenu params = new JMenu("Parameters");
        params.add(errorTerms);
        params.addSeparator();
        params.add(covariances);
        params.add(correlations);
        params.addSeparator();

        if (!semIm.isCyclic()) {
            params.add(meansItem);
            params.add(interceptsItem);
        }

        menuBar.add(params);
        menuBar.add(new LayoutMenu(this));

        add(menuBar, BorderLayout.NORTH);
    }

    public Graph getGraph() {
        return semImGraphicalEditor.getWorkbench().getGraph();
    }

    @Override
    public Map<Edge, Object> getModelEdgesToDisplay() {
        return getWorkbench().getModelEdgesToDisplay();
    }

    public Map<Node, Object> getModelNodesToDisplay() {
        return getWorkbench().getModelNodesToDisplay();
    }

    public IKnowledge getKnowledge() {
        return semImGraphicalEditor.getWorkbench().getKnowledge();
    }

    public Graph getSourceGraph() {
        return semImGraphicalEditor.getWorkbench().getSourceGraph();
    }

    public void layoutByGraph(Graph graph) {
        SemGraph _graph = (SemGraph) semImGraphicalEditor.getWorkbench().getGraph();
        _graph.setShowErrorTerms(false);
        semImGraphicalEditor.getWorkbench().layoutByGraph(graph);
        _graph.resetErrorPositions();
//        semImGraphicalEditor.getWorkbench().setGraph(_graph);
        errorTerms.setText("Show Error Terms");
    }

    public void layoutByKnowledge() {
        SemGraph _graph = (SemGraph) semImGraphicalEditor.getWorkbench().getGraph();
        _graph.setShowErrorTerms(false);
        semImGraphicalEditor.getWorkbench().layoutByKnowledge();
        _graph.resetErrorPositions();
//        semImGraphicalEditor.getWorkbench().setGraph(_graph);
        errorTerms.setText("Show Error Terms");
    }

    private void checkForUnmeasuredLatents(ISemIm semIm) {
        List<Node> unmeasuredLatents = semIm.listUnmeasuredLatents();

        if (!unmeasuredLatents.isEmpty()) {
            StringBuilder buf = new StringBuilder();
            buf.append("This model has the following latent(s) without measured children: ");

            for (int i = 0; i < unmeasuredLatents.size(); i++) {
                buf.append(unmeasuredLatents.get(i));

                if (i < unmeasuredLatents.size() - 1) {
                    buf.append(", ");
                }
            }

            buf.append(".\nAs a result, standard errors for non-mean parameters cannot be calculated.");

            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    buf.toString(), "FYI", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private SemGraph getSemGraph() {
        return semIm.getSemPm().getGraph();
    }

    /**
     * Constructs a new SemImEditor from the given OldSemEstimateAdapter.
     */
    public SemImEditor(SemImWrapper semImWrapper) {
        this(semImWrapper.getSemIm());
    }

    /**
     * Constructs a new SemImEditor from the given OldSemEstimateAdapter.
     */
    public SemImEditor(SemEstimatorWrapper semEstWrapper) {
        this(semEstWrapper.getSemEstimator().getEstimatedSem());
    }

    /**
     * @return the index of the currently selected tab. Used to construct a new
     * SemImEditor in the same state as a previous one.
     */
    public int getTabSelectionIndex() {
        return tabbedPane.getSelectedIndex();
    }

    /**
     * @return the index of the matrix that was being viewed most recently. Used
     * to construct a new SemImEditor in the same state as the previous one.
     */
    public int getMatrixSelection() {
        return impliedMatricesPanel().getMatrixSelection();
    }

    /**
     * Sets a new SemIm to edit.
     */
    public void setSemIm(SemIm semIm, int tabSelectionIndex,
                         int matrixSelection) {
        if (semIm == null) {
            throw new NullPointerException();
        }

        if (tabSelectionIndex < 0 || tabSelectionIndex >= 4) {
            throw new IllegalArgumentException(
                    "Tab selection must be 0, 1, 2, or 3: " + tabSelectionIndex);
        }

        if (matrixSelection < 0 || matrixSelection >= 4) {
            throw new IllegalArgumentException(
                    "Matrix selection must be 0, 1, 2, or 3: " + matrixSelection);
        }

        Graph oldGraph = this.semIm.getSemPm().getGraph();

        this.semIm = semIm;
        GraphUtils.arrangeBySourceGraph(semIm.getSemPm().getGraph(), oldGraph);
        this.matrixSelection = matrixSelection;
        impliedMatricesPanel().setMatrixSelection(matrixSelection);

        this.semImGraphicalEditor = null;
        this.semImTabularEditor = null;
        this.impliedMatricesPanel = null;
        this.modelStatisticsPanel = null;

        tabbedPane.removeAll();
        tabbedPane.add(getGraphicalEditorTitle(), graphicalEditor());
        tabbedPane.add(getTabularEditorTitle(), tabularEditor());
        tabbedPane.add("Implied Matrices", impliedMatricesPanel());
        tabbedPane.add("Model Statistics", modelStatisticsPanel());

        tabbedPane.setSelectedIndex(tabSelectionIndex);
        tabbedPane.validate();
    }

    public GraphWorkbench getWorkbench() {
        return semImGraphicalEditor.getWorkbench();
    }

    //========================PRIVATE METHODS===========================//


    private JMenuItem getCopyMatrixMenuItem() {
        JMenuItem item = new JMenuItem("Copy Implied Covariance Matrix");
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String s = impliedMatricesPanel.getMatrixInTabDelimitedForm();
                Clipboard board = Toolkit.getDefaultToolkit().getSystemClipboard();
                StringSelection selection = new StringSelection(s);
                board.setContents(selection, selection);
            }
        });
        return item;
    }


    private ISemIm getSemIm() {
        return semIm;
    }

    private SemImGraphicalEditor graphicalEditor() {
        if (this.semImGraphicalEditor == null) {
            this.semImGraphicalEditor = new SemImGraphicalEditor(getSemIm(),
                    this, this.maxFreeParamsForStatistics);
            this.semImGraphicalEditor.addPropertyChangeListener(
                    new PropertyChangeListener() {
                        public void propertyChange(PropertyChangeEvent evt) {
                            SemImEditor.this.firePropertyChange(evt.getPropertyName(), null,
                                    null);
                        }
                    });
        }
        return this.semImGraphicalEditor;
    }

    private SemImTabularEditor tabularEditor() {
        if (this.semImTabularEditor == null) {
            this.semImTabularEditor = new SemImTabularEditor(getSemIm(), this,
                    this.maxFreeParamsForStatistics);
        }
        this.semImTabularEditor.addPropertyChangeListener(
                new PropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent evt) {
                        SemImEditor.this.firePropertyChange(evt.getPropertyName(), null, null);
                    }
                });
        return this.semImTabularEditor;
    }

    private ImpliedMatricesPanel impliedMatricesPanel() {
        if (this.impliedMatricesPanel == null) {
            this.impliedMatricesPanel =
                    new ImpliedMatricesPanel(getSemIm(), this.matrixSelection);
        }
        return this.impliedMatricesPanel;
    }

    private ModelStatisticsPanel modelStatisticsPanel() {
        if (this.modelStatisticsPanel == null) {
            this.modelStatisticsPanel = new ModelStatisticsPanel(getSemIm());
        }
        return this.modelStatisticsPanel;
    }

    public boolean isEditCovariancesAsCorrelations() {
        return editCovariancesAsCorrelations;
    }

    private void setEditCovariancesAsCorrelations(
            boolean editCovariancesAsCorrelations) {
        this.editCovariancesAsCorrelations = editCovariancesAsCorrelations;
        graphicalEditor().resetLabels();
        tabularEditor().getTableModel().fireTableDataChanged();
    }

    public boolean isEditIntercepts() {
        return editIntercepts;
    }

    public void setEditIntercepts(boolean editIntercepts) {
        this.editIntercepts = editIntercepts;
        graphicalEditor().resetLabels();
        tabularEditor().getTableModel().fireTableDataChanged();

        meansItem.setSelected(!editIntercepts);
        interceptsItem.setSelected(editIntercepts);
    }

    private String getGraphicalEditorTitle() {
        return graphicalEditorTitle;
    }

    private String getTabularEditorTitle() {
        return tabularEditorTitle;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setEditable(boolean editable) {
        graphicalEditor().setEditable(editable);
        tabularEditor().setEditable(editable);
        this.editable = editable;
    }
}

/**
 * Edits the parameters of the SemIm using a graph workbench.
 */
final class SemImGraphicalEditor extends JPanel {

    /**
     * Font size for parameter values in the graph.
     */
    private static Font SMALL_FONT = new Font("Dialog", Font.PLAIN, 10);

    /**
     * Background color of the edit panel when you click on the parameters.
     */
    private static Color LIGHT_YELLOW = new Color(255, 255, 215);

    /**
     * The SemIM being edited.
     */
    private ISemIm semIm;

    /**
     * Workbench for the graphical editor.
     */
    private GraphWorkbench workbench;

    /**
     * Stores the last active edge so that it can be reset properly.
     */
    private Object lastEditedObject = null;

    /**
     * This delay needs to be restored when the component is hidden.
     */
    private int savedTooltipDelay = 0;

    /**
     * The editor that sits inside the SemImEditor that allows the user to edit
     * the SemIm graphically.
     */
    private SemImEditor editor = null;

    /**
     * Maximum number of free parameters for which model statistics will be
     * calculated. The algorithm for calculating these is expensive.
     */
    private int maxFreeParamsForStatistics;

    /**
     * True iff this graphical display is editable.
     */
    private boolean editable = true;
    private Container dialog;

    /**
     * Constructs a SemIm graphical editor for the given SemIm.
     */
    public SemImGraphicalEditor(ISemIm semIm, SemImEditor editor,
                                int maxFreeParamsForStatistics) {
        this.semIm = semIm;
        this.editor = editor;
        this.maxFreeParamsForStatistics = maxFreeParamsForStatistics;

        setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(workbench());
        scroll.setPreferredSize(new Dimension(450, 450));

        add(scroll, BorderLayout.CENTER);

        setBorder(new TitledBorder("Click parameter values to edit"));

        ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
        setSavedTooltipDelay(toolTipManager.getInitialDelay());

        // Laborious code that follows is intended to make sure tooltips come
        // almost immediately within the sem im editor but more slowly outside.
        // Ugh.
        workbench().addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent e) {
                resetLabels();
                ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                toolTipManager.setInitialDelay(100);
            }

            public void componentHidden(ComponentEvent e) {
                ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                toolTipManager.setInitialDelay(getSavedTooltipDelay());
            }
        });

        workbench().addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                if (workbench().contains(e.getPoint())) {

                    // Commenting out the resetLabels, since it seems to make
                    // people confused when they can't move the mouse away
                    // from the text field they are editing without the
                    // textfield disappearing. jdramsey 3/16/2005.
//                    resetLabels();
                    ToolTipManager toolTipManager =
                            ToolTipManager.sharedInstance();
                    toolTipManager.setInitialDelay(100);
                }
            }

            public void mouseExited(MouseEvent e) {
                if (!workbench().contains(e.getPoint())) {
                    ToolTipManager toolTipManager =
                            ToolTipManager.sharedInstance();
                    toolTipManager.setInitialDelay(getSavedTooltipDelay());
                }
            }
        });

        // Make sure the graphical editor reflects changes made to parameters
        // in other editors.
        addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent e) {
                resetLabels();
            }
        });
    }

    //========================PRIVATE METHODS===========================//


    private void beginEdgeEdit(final Edge edge) {
        finishEdit();

        if (!isEditable()) {
            return;
        }

        Parameter parameter = getEdgeParameter(edge);
        double d = semIm().getParamValue(parameter);

        if (editor.isEditCovariancesAsCorrelations() &&
                parameter.getType() == ParamType.COVAR) {
            Node nodeA = parameter.getNodeA();
            Node nodeB = parameter.getNodeB();

            double varA = semIm().getParamValue(nodeA, nodeA);
            double varB = semIm().getParamValue(nodeB, nodeB);

            d /= Math.sqrt(varA * varB);
        }

        final DoubleTextField field = new DoubleTextField(d, 10, NumberFormatUtil.getInstance().getNumberFormat());
        field.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    setEdgeValue(edge, new Double(value).toString());
                    return value;
                } catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });


        Box box = Box.createHorizontalBox();
        box.add(Box.createHorizontalGlue());
        box.add(new JLabel("New value: "));
        box.add(field);
        box.add(Box.createHorizontalGlue());

        field.addAncestorListener(new AncestorListener() {
            public void ancestorMoved(AncestorEvent ancestorEvent) {
            }

            public void ancestorRemoved(AncestorEvent ancestorEvent) {
            }

            public void ancestorAdded(AncestorEvent ancestorEvent) {
                Container ancestor = ancestorEvent.getAncestor();

                if (ancestor instanceof JDialog) {
                    SemImGraphicalEditor.this.dialog = ancestor;
                }

                field.selectAll();
                field.grabFocus();
            }
        });

        field.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                if (SemImGraphicalEditor.this.dialog != null) {
                    SemImGraphicalEditor.this.dialog.setVisible(false);
                }
            }
        });

        JOptionPane.showMessageDialog(workbench.getComponent(edge), box, "Coefficient for " + edge, JOptionPane.PLAIN_MESSAGE);


//        final DoubleTextField doubleTextField = new DoubleTextField(d, 7, NumberFormatUtil.getInstance().getNumberFormat());
//        doubleTextField.setPreferredSize(new Dimension(60, 20));
//        doubleTextField.addActionListener(new EdgeActionListener(this, edge));
//
//        doubleTextField.addFocusListener(new FocusAdapter() {
//            public void focusLost(FocusEvent e) {
//                DoubleTextField field = (DoubleTextField) e.getSource();
//                String s = field.getText();
//                setEdgeValue(edge, s);
////                field.grabFocus();
//            }
//        });
//
////        JLabel instruct = new JLabel("Press Enter when done");
////        instruct.setFont(SMALL_FONT);
////
////        instruct.setForeground(Color.GRAY);
//
//        Box b1 = Box.createHorizontalBox();
//        b1.add(new JLabel(parameter.getName() + " = "));
//        b1.add(doubleTextField);
//
//        Box b2 = Box.createHorizontalBox();
////        b2.add(instruct);
//
//        JPanel editPanel = new JPanel();
//        editPanel.setLayout(new BoxLayout(editPanel, BoxLayout.Y_AXIS));
//        editPanel.setBackground(LIGHT_YELLOW);
//        editPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
//        editPanel.add(b1);
//        editPanel.add(Box.createVerticalStrut(5));
//        editPanel.add(b2);
//
//        workbench().setEdgeLabel(edge, editPanel);
//        setLastEditedObject(edge);
//
//        workbench().repaint();
//        doubleTextField.grabFocus();
//        doubleTextField.selectAll();
    }

    private void beginNodeEdit(final Node node) {
        finishEdit();

        if (!isEditable()) {
            return;
        }

//        if (!semIm().getSemPm().getGraph().isParameterizable(node)) {
//            return;
//        }

        Parameter parameter = getNodeParameter(node);
        if (editor.isEditCovariancesAsCorrelations() &&
                parameter.getType() == ParamType.VAR) {
            return;
        }

        double d;
        String prefix;
        String postfix = "";

        if (parameter.getType() == ParamType.MEAN) {
            if (editor.isEditIntercepts()) {
                d = semIm().getIntercept(node);
                prefix = "B0_" + node.getName() + " = ";
            } else {
                d = semIm().getMean(node);
                prefix = "Mean(" + node.getName() + ") = ";
            }
        } else {
            d = Math.sqrt(semIm().getParamValue(parameter));
            prefix = node.getName() + " ~ N(0,";
            postfix = ")";
        }

        final DoubleTextField field = new DoubleTextField(d, 10, NumberFormatUtil.getInstance().getNumberFormat());
        field.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    setNodeValue(node, new Double(value).toString());
                    return value;
                } catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        Box box = Box.createHorizontalBox();
        box.add(Box.createHorizontalGlue());
        box.add(new JLabel("New value: "));
        box.add(field);
        box.add(Box.createHorizontalGlue());

        field.addAncestorListener(new AncestorListener() {
            public void ancestorMoved(AncestorEvent ancestorEvent) {
            }

            public void ancestorRemoved(AncestorEvent ancestorEvent) {
            }

            public void ancestorAdded(AncestorEvent ancestorEvent) {
                Container ancestor = ancestorEvent.getAncestor();

                if (ancestor instanceof JDialog) {
                    SemImGraphicalEditor.this.dialog = ancestor;
                }
            }
        });

        field.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                if (SemImGraphicalEditor.this.dialog != null) {
                    SemImGraphicalEditor.this.dialog.setVisible(false);
                }
            }
        });

        String s;

        if (parameter.getType() == ParamType.MEAN) {
            if (editor.isEditIntercepts()) {
                s = "Intercept for " + node;
            } else {
                s = "Mean for " + node;
            }
        } else {
            s = "Standard Deviation for " + node;
        }

        JOptionPane.showMessageDialog(workbench.getComponent(node), box, s, JOptionPane.PLAIN_MESSAGE);


//        DoubleTextField field = new DoubleTextField(d, 7, NumberFormatUtil.getInstance().getNumberFormat());
//        field.setPreferredSize(new Dimension(60, 20));
//        field.addActionListener(new NodeActionListener(this, node));
//
//        field.addFocusListener(new FocusAdapter() {
//            public void focusLost(FocusEvent e) {
//                DoubleTextField field = (DoubleTextField) e.getSource();
//                field.grabFocus();
//            }
//        });
//
//        JLabel instruct = new JLabel("Press Enter when done");
//        instruct.setFont(SMALL_FONT);
//        instruct.setForeground(Color.GRAY);
//
//        Box b1 = Box.createHorizontalBox();
//        b1.add(new JLabel(prefix));
//        b1.add(field);
//        b1.add(new JLabel(postfix));
//
//        Box b2 = Box.createHorizontalBox();
//        b2.add(instruct);
//
//        JPanel editPanel = new JPanel();
//        editPanel.setLayout(new BoxLayout(editPanel, BoxLayout.Y_AXIS));
//        editPanel.setBackground(LIGHT_YELLOW);
//        editPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
//        editPanel.add(b1);
//        editPanel.add(Box.createVerticalStrut(5));
//        editPanel.add(b2);
//
//        workbench().setNodeLabel(node, editPanel, 15, 2);
//        setLastEditedObject(node);
//
//        workbench().repaint();
//        field.grabFocus();
//        field.selectAll();
    }

    private void finishEdit() {
        if (lastEditedObject() != null) {
            Object o = lastEditedObject();
            String s;

            // No longer necessarily a jlabel... could be a box of jlables...
            // need an interface to avoid classcastexceptions... jdramsey 7/24/2005
            // todo:
//            JPanel panel = (JPanel) workbench().getLabel(o);
//
//            DoubleTextField textField = null;
//
//            for (int i = 0; i < panel.getComponentCount(); i++) {
//                if (panel.getComponent(i) instanceof DoubleTextField) {
//                    textField = (DoubleTextField) panel.getComponent(i);
//                    break;
//                }
//            }
//
//            if (textField == null) {
//                throw new NullPointerException();
//            }
//
//            s = textField.getText();
//
//            if (o instanceof Edge) {
//                Edge edge = (Edge) o;
//                setEdgeValue(edge, s);
//                resetLabels();
//            } else {
//                Node node = (Node) o;
//                setNodeValue(node, s);
//                resetLabels();
//            }

            resetLabels();
        }
    }

    private ISemIm semIm() {
        return this.semIm;
    }

    private Graph graph() {
        return this.semIm().getSemPm().getGraph();
    }

    private GraphWorkbench workbench() {
        if (this.getWorkbench() == null) {
            this.workbench = new GraphWorkbench(graph());
            this.getWorkbench().setAllowDoubleClickActions(false);
            this.getWorkbench().addPropertyChangeListener(
                    new PropertyChangeListener() {
                        public void propertyChange(PropertyChangeEvent evt) {
                            if ("BackgroundClicked".equals(
                                    evt.getPropertyName())) {
                                finishEdit();
                            }
                        }
                    });
            resetLabels();
            addMouseListenerToGraphNodesMeasured();
        }
        return getWorkbench();
    }

    private void setLastEditedObject(Object o) {
        this.lastEditedObject = o;
    }

    private Object lastEditedObject() {
        return lastEditedObject;
    }

    public void resetLabels() {
        TetradMatrix implCovar = semIm().getImplCovar(false);

        for (Object o : graph().getEdges()) {
            resetEdgeLabel((Edge) (o), implCovar);
        }

        List<Node> nodes = graph().getNodes();

        for (Object node : nodes) {
            resetNodeLabel((Node) node, implCovar);
        }

        workbench().repaint();
    }

    private void resetEdgeLabel(Edge edge, TetradMatrix implCovar) {
        Parameter parameter = getEdgeParameter(edge);

        if (parameter != null) {
            double val = semIm().getParamValue(parameter);
            double standardError;

            try {
                standardError = semIm().getStandardError(parameter,
                        maxFreeParamsForStatistics);
            } catch (Exception e) {
                standardError = Double.NaN;
            }

            double tValue;
            try {
                tValue = semIm().getTValue(parameter, maxFreeParamsForStatistics);
            } catch (Exception e) {
                tValue = Double.NaN;
            }

            double pValue;

            try {
                pValue = semIm().getPValue(parameter, maxFreeParamsForStatistics);
            } catch (Exception e) {
                pValue = Double.NaN;
            }

            if (editor.isEditCovariancesAsCorrelations() &&
                    parameter.getType() == ParamType.COVAR) {
                Node nodeA = edge.getNode1();
                Node nodeB = edge.getNode2();

                double varA = semIm().getVariance(nodeA, implCovar);
                double varB = semIm().getVariance(nodeB, implCovar);

                val /= Math.sqrt(varA * varB);
            }

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
            label.setText(" " + asString(val) + " ");
            label.setToolTipText(parameter.getName() + " = " + asString(val));
            label.addMouseListener(new EdgeMouseListener(edge, this));
            if (!Double.isNaN(standardError) && semIm().isEstimated()) {
                label.setToolTipText("SE=" + asString(standardError) + ", T=" +
                        asString(tValue) + ", P=" + asString(pValue));
            }

            workbench().setEdgeLabel(edge, label);
        } else {
            workbench().setEdgeLabel(edge, null);
        }
    }

    private void resetNodeLabel(Node node, TetradMatrix implCovar) {
        if (!semIm().getSemPm().getGraph().isParameterizable(node)) {
            return;
        }

        Parameter parameter = semIm().getSemPm().getVarianceParameter(node);
        double meanOrIntercept = Double.NaN;

        JLabel label = new JLabel();
        label.setBackground(Color.WHITE);
        label.addMouseListener(new NodeMouseListener(node, this));
        label.setFont(SMALL_FONT);

        String tooltip = "";
        NodeType nodeType = node.getNodeType();

        if (nodeType == NodeType.MEASURED || nodeType == NodeType.LATENT) {
            if (editor.isEditIntercepts()) {
                meanOrIntercept = semIm().getIntercept(node);
            } else {
                meanOrIntercept = semIm().getMean(node);
            }
        }

        double stdDev = semIm().getStdDev(node, implCovar);

        if (editor.isEditCovariancesAsCorrelations() && !Double.isNaN(stdDev)) {
            stdDev = 1.0;
        }

        if (parameter != null) {
            double standardError = semIm().getStandardError(parameter,
                    maxFreeParamsForStatistics);
            double tValue =
                    semIm().getTValue(parameter, maxFreeParamsForStatistics);
            double pValue =
                    semIm().getPValue(parameter, maxFreeParamsForStatistics);

            tooltip = "SE=" + asString(standardError) + ", T=" +
                    asString(tValue) + ", P=" + asString(pValue);
        }

        if (!Double.isNaN(meanOrIntercept)) {
            label.setForeground(Color.GREEN.darker());
            label.setText(asString(meanOrIntercept));

            if (editor.isEditIntercepts()) {
                tooltip = "<html>" + "B0_" + node.getName() + " = " +
                        asString(meanOrIntercept) + "</html>";
            } else {
                tooltip = "<html>" + "Mean(" + node.getName() + ") = " +
                        asString(meanOrIntercept) + "</html>";
            }
        } else if (!editor.isEditCovariancesAsCorrelations() &&
                !Double.isNaN(stdDev)) {
            label.setForeground(Color.BLUE);
            label.setText(asString(stdDev));

            tooltip = "<html>" + node.getName() + " ~ N(0," + asString(stdDev)
                    + ")" + "<br><br>" + tooltip + "</html>";

        } else if (editor.isEditCovariancesAsCorrelations()) {
            label.setForeground(Color.GRAY);
            label.setText(asString(stdDev));
        }

        if (parameter != null && parameter.isFixed()) {
            label.setForeground(Color.RED);
        }

        label.setToolTipText(tooltip);

        // Offset the nodes slightly differently depending on whether
        // they're error nodes or not.
        if (nodeType == NodeType.ERROR) {
            label.setOpaque(false);
            workbench().setNodeLabel(node, label, -10, -10);
        } else {
            label.setOpaque(false);
            workbench().setNodeLabel(node, label, 0, 0);
        }
    }

    private Parameter getNodeParameter(Node node) {
        Parameter parameter = semIm().getSemPm().getMeanParameter(node);

        if (parameter == null) {
            parameter = semIm().getSemPm().getVarianceParameter(node);
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
            return semIm().getSemPm().getCoefficientParameter(edge.getNode1(), edge.getNode2());
        } else if (Edges.isBidirectedEdge(edge)) {
            return semIm().getSemPm().getCovarianceParameter(edge.getNode1(), edge.getNode2());
        }

        throw new IllegalArgumentException(
                "This is not a directed or bidirected edge: " + edge);
    }

    private void setEdgeValue(Edge edge, String text) {
        try {
            Parameter parameter = getEdgeParameter(edge);
            double d = new Double(text);

            if (editor.isEditCovariancesAsCorrelations() &&
                    parameter.getType() == ParamType.COVAR) {
                Node nodeA = edge.getNode1();
                Node nodeB = edge.getNode2();

                TetradMatrix implCovar = semIm().getImplCovar(false);

                double varA = semIm().getVariance(nodeA, implCovar);
                double varB = semIm().getVariance(nodeB, implCovar);

                d *= Math.sqrt(varA * varB);

                semIm().setParamValue(parameter, d);
                SemImGraphicalEditor.this.firePropertyChange("modelChanged", null, null);
            } else if (!editor.isEditCovariancesAsCorrelations() &&
                    parameter.getType() == ParamType.COVAR) {
                semIm().setParamValue(parameter, d);
                SemImGraphicalEditor.this.firePropertyChange("modelChanged", null, null);
            } else if (parameter.getType() == ParamType.COEF) {
//                semIm().setParamValue(parameter, d);
//                firePropertyChange("modelChanged", null, null);

                Node x = parameter.getNodeA();
                Node y = parameter.getNodeB();

                semIm.setEdgeCoef(x, y, d);

                if (editor.isEditIntercepts()) {
                    double intercept = semIm.getIntercept(y);
                    semIm.setIntercept(y, intercept);
                }

                SemImGraphicalEditor.this.firePropertyChange("modelChanged", null, null);
            }
        } catch (NumberFormatException e) {
            // Let the old value be reinstated.
        }

        resetLabels();
        workbench().repaint();
        setLastEditedObject(null);
    }

    private void setNodeValue(Node node, String text) {
        try {
            Parameter parameter = getNodeParameter(node);
            double d = new Double(text);

            if (parameter.getType() == ParamType.VAR && d >= 0) {
                semIm().setParamValue(node, node, d * d);
                SemImGraphicalEditor.this.firePropertyChange("modelChanged", null, null);
            } else if (parameter.getType() == ParamType.MEAN) {
                if (editor.isEditIntercepts()) {
                    semIm().setIntercept(node, d);
                } else {
                    semIm().setMean(node, d);
                }

                SemImGraphicalEditor.this.firePropertyChange("modelChanged", null, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Let the old value be reinstated.
        }

        resetLabels();
        workbench().repaint();
        setLastEditedObject(null);
    }

    private int getSavedTooltipDelay() {
        return savedTooltipDelay;
    }

    private void setSavedTooltipDelay(int savedTooltipDelay) {
        if (this.savedTooltipDelay == 0) {
            this.savedTooltipDelay = savedTooltipDelay;
        }
    }

    private String asString(double value) {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        if (Double.isNaN(value)) {
            return " * ";
        } else {
            return nf.format(value);
        }
    }

//    private String asString2(double value) {
//        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
//
//        if (Double.isNaN(value)) {
//            return "*";
//        } else {
//            return nf.format(value);
//        }
//    }


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

        SemGraph semGraph = semIm().getSemPm().getGraph();
        List parentNodes = semGraph.getParents(node);

        for (Object parentNodeObj : parentNodes) {
            Node parentNode = (Node) parentNodeObj;
//            Parameter edgeParam = semIm().getEstIm().getEdgeParameter(
//                    semGraph.getEdge(parentNode, node));
            Parameter edgeParam = getEdgeParameter(
                    semGraph.getDirectedEdge(parentNode, node));

            if (edgeParam != null) {
                eqn = eqn + " + " + edgeParam.getName() + "*" + parentNode;
            }
        }

        eqn = eqn + " + " + semIm().getSemPm().getGraph().getExogenous(node);

        return eqn;
    }

    public GraphWorkbench getWorkbench() {
        return workbench;
    }

    private boolean isEditable() {
        return editable;
    }

    public void setEditable(boolean editable) {
        workbench().setAllowEdgeReorientations(editable);
//        workbench().setAllowMultipleSelection(editable);
//        workbench().setAllowNodeDragging(false);
        workbench().setAllowDoubleClickActions(editable);
        workbench().setAllowNodeEdgeSelection(editable);
        this.editable = editable;
    }

    final static class EdgeMouseListener extends MouseAdapter {
        private Edge edge;
        private SemImGraphicalEditor editor;

        public EdgeMouseListener(Edge edge, SemImGraphicalEditor editor) {
            this.edge = edge;
            this.editor = editor;
        }

        private Edge getEdge() {
            return edge;
        }

        private SemImGraphicalEditor getEditor() {
            return editor;
        }

        public void mouseClicked(MouseEvent e) {
            getEditor().beginEdgeEdit(getEdge());
        }
    }

    final static class NodeMouseListener extends MouseAdapter {
        private Node node;
        private SemImGraphicalEditor editor;

        public NodeMouseListener(Node node, SemImGraphicalEditor editor) {
            this.node = node;
            this.editor = editor;
        }

        private Node getNode() {
            return node;
        }

        private SemImGraphicalEditor getEditor() {
            return editor;
        }

        public void mouseClicked(MouseEvent e) {
            getEditor().beginNodeEdit(getNode());
        }
    }

    final static class EdgeActionListener implements ActionListener {
        private SemImGraphicalEditor editor;
        private Edge edge;

        public EdgeActionListener(SemImGraphicalEditor editor, Edge edge) {
            this.editor = editor;
            this.edge = edge;
        }

        public void actionPerformed(ActionEvent ev) {
            DoubleTextField doubleTextField = (DoubleTextField) ev.getSource();
            String s = doubleTextField.getText();
            getEditor().setEdgeValue(getEdge(), s);
        }

        private SemImGraphicalEditor getEditor() {
            return editor;
        }

        private Edge getEdge() {
            return edge;
        }
    }

    final static class NodeActionListener implements ActionListener {
        private SemImGraphicalEditor editor;
        private Node node;

        public NodeActionListener(SemImGraphicalEditor editor, Node node) {
            this.editor = editor;
            this.node = node;
        }

        public void actionPerformed(ActionEvent ev) {
            DoubleTextField doubleTextField = (DoubleTextField) ev.getSource();
            String s = doubleTextField.getText();
            getEditor().setNodeValue(getNode(), s);
        }

        private SemImGraphicalEditor getEditor() {
            return editor;
        }

        private Node getNode() {
            return node;
        }
    }
}

/**
 * Edits parameter values for a SemIm as a simple list.
 */
final class SemImTabularEditor extends JPanel {
    private ParamTableModel tableModel;
    private boolean editable = true;

    public SemImTabularEditor(ISemIm semIm, SemImEditor editor,
                              int maxFreeParamsForStatistics) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
//        setBorder(new TitledBorder("Click parameter values to edit"));

        if (semIm.isEstimated()) {
            setBorder(new TitledBorder("Null hypothesis for T and P is that the parameter is zero"));
        } else {
            setBorder(new TitledBorder("Click parameter values to edit"));
        }

        JTable table = new JTable() {
            public TableCellEditor getCellEditor(int row, int col) {
                return new DataCellEditor();
            }
        };
        tableModel = new ParamTableModel(semIm, editor, maxFreeParamsForStatistics);
        table.setModel(getTableModel());
        tableModel.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                SemImTabularEditor.this.firePropertyChange("modelChanged", null, null);
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public ParamTableModel getTableModel() {
        return tableModel;
    }

    public void setEditable(boolean editable) {
        tableModel.setEditable(editable);
        this.editable = editable;
        tableModel.fireTableStructureChanged();
    }

    public boolean isEditable() {
        return editable;
    }
}

final class ParamTableModel extends AbstractTableModel {
    private ISemIm semIm;
    private NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    private SemImEditor editor = null;
    private int maxFreeParamsForStatistics = 50;
    private boolean editable = true;

    public ParamTableModel(ISemIm semIm, SemImEditor editor,
                           int maxFreeParamsForStatistics) {
        if (semIm == null) {
            throw new NullPointerException("SemIm must not be null.");
        }

        if (maxFreeParamsForStatistics < 0) {
            throw new IllegalArgumentException();
        }

        this.semIm = semIm;
        this.maxFreeParamsForStatistics = maxFreeParamsForStatistics;

        //  List<Parameter> parameters = new ArrayList<Parameter>();
        //  parameters.addAll(semIm.getFreeParameters());
        //  parameters.addAll(semIm.getFixedParameters());
        //  parameters.addAll(semIm.getMeanParameters());

        this.editor = editor;
    }

    public int getRowCount() {
        int numNodes = semIm().getVariableNodes().size();
        return semIm().getNumFreeParams() + semIm().getFixedParameters().size() + numNodes;
    }

    public int getColumnCount() {
        return 7;
    }

    public String getColumnName(int column) {
        switch (column) {
            case 0:
                return "From";
            case 1:
                return "To";
            case 2:
                return "Type";
            case 3:
                return "Value";
            case 4:
                return "SE";
            case 5:
                return "T";
            case 6:
                return "P";
        }

        return null;
    }

    public Object getValueAt(int row, int column) {
        List nodes = semIm().getVariableNodes();
        List parameters = new ArrayList<Parameter>(semIm().getFreeParameters());
        parameters.addAll(semIm().getFixedParameters());

        int numParams = semIm.getNumFreeParams() + semIm().getFixedParameters().size();

        if (row < numParams) {
            Parameter parameter = ((Parameter) parameters.get(row));

            switch (column) {
                case 0:
                    return parameter.getNodeA();
                case 1:
                    return parameter.getNodeB();
                case 2:
                    return typeString(parameter);
                case 3:
                    return asString(paramValue(parameter));
                case 4:
                    if (parameter.isFixed()) {
                        return "*";
                    } else {
                        return asString(semIm().getStandardError(parameter,
                                maxFreeParamsForStatistics));
                    }
                case 5:
                    if (parameter.isFixed()) {
                        return "*";
                    } else {
                        return asString(semIm().getTValue(parameter,
                                maxFreeParamsForStatistics));
                    }
                case 6:
                    if (parameter.isFixed()) {
                        return "*";
                    } else {
                        return asString(semIm().getPValue(parameter,
                                maxFreeParamsForStatistics));
                    }
            }
        } else if (row < numParams + nodes.size()) {
            int index = row - numParams;
            Node node = semIm.getVariableNodes().get(index);
            int n = semIm.getSampleSize();
            int df = n - 1;
            double mean = semIm.getMean(node);
            double stdDev = semIm.getMeanStdDev(node);
            double stdErr = stdDev / Math.sqrt(n);
//            double tValue = mean * Math.sqrt(n - 1) / stdDev;

            double tValue = mean / stdErr;
            double p = 2.0 * (1.0 - ProbUtils.tCdf(Math.abs(tValue), df));

//            double ar = covars.get(index, index);
//            stdDev = Math.sqrt(ar);
//            n = semIm.getSampleSize();

            switch (column) {
                case 0:
                    return nodes.get(index);
                case 1:
                    return nodes.get(index);
                case 2:
                    if (editor.isEditIntercepts()) {
                        return "Intercept";
                    } else {
                        return "Mean";
                    }
                case 3:
                    if (editor.isEditIntercepts()) {
                        double intercept = semIm.getIntercept(node);
                        return asString(intercept);
                    } else {
                        return asString(mean);
                    }
                case 4:
                    return asString(stdErr);
                case 5:
                    return asString(tValue);
                case 6:
                    return asString(p);
            }
        }

        return null;
    }

    private double paramValue(Parameter parameter) {
        double paramValue = semIm().getParamValue(parameter);

        if (editor.isEditCovariancesAsCorrelations()) {
            if (parameter.getType() == ParamType.VAR) {
                paramValue = 1.0;
            }
            if (parameter.getType() == ParamType.COVAR) {
                Node nodeA = parameter.getNodeA();
                Node nodeB = parameter.getNodeB();

                double varA = semIm().getParamValue(nodeA, nodeA);
                double varB = semIm().getParamValue(nodeB, nodeB);

                paramValue *= Math.sqrt(varA * varB);
            }
        } else {
            if (parameter.getType() == ParamType.VAR) {
                paramValue = Math.sqrt(paramValue);
            }
        }

        return paramValue;
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return isEditable() && columnIndex == 3;
    }

    private boolean isEditable() {
        return this.editable;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {

        if (columnIndex == 3) {
            try {
                double value = Double.parseDouble((String) aValue);

                if (rowIndex < semIm().getNumFreeParams()) {
                    Parameter parameter = semIm.getFreeParameters().get(rowIndex);

                    if (parameter.getType() == ParamType.VAR) {
                        value = value * value;
                        semIm.setErrVar(parameter.getNodeA(), value);
                    } else if (parameter.getType() == ParamType.COEF) {
                        Node x = parameter.getNodeA();
                        Node y = parameter.getNodeB();

                        double intercept = semIm.getIntercept(y);

                        semIm.setEdgeCoef(x, y, value);

                        if (editor.isEditIntercepts()) {
                            semIm.setIntercept(y, intercept);
                        }
                    }

                    editor.firePropertyChange("modelChanged", 0, 0);

//                    if (semIm.getParamValue(parameter) != value) {
//                        semIm.setParamValue(parameter, value);
//                        editor.firePropertyChange("modelChanged", 0, 0);
//                    }
                } else {
                    int index = rowIndex - semIm().getNumFreeParams();
                    Node node = semIm().getVariableNodes().get(index);

                    if (semIm.getMean(semIm.getVariableNodes().get(index)) != value) {
                        if (editor.isEditIntercepts()) {
                            semIm.setIntercept(node, value);
                            editor.firePropertyChange("modelChanged", 0, 0);
                        } else {
                            semIm.setMean(node, value);
                            editor.firePropertyChange("modelChanged", 0, 0);
                        }

                    }
                }
            } catch (Exception e1) {
                // The old value will be reinstated automatically.
            }

            fireTableDataChanged();
        }
    }

    private String asString(double value) {
        if (Double.isNaN(value)) {
            return " * ";
        } else {
            return nf.format(value);
        }
    }

    private String typeString(Parameter parameter) {
        ParamType type = parameter.getType();

        if (type == ParamType.COEF) {
            return "Edge Coef.";
        }

        if (editor.isEditCovariancesAsCorrelations()) {
            if (type == ParamType.VAR) {
                return "Correlation";
            }

            if (type == ParamType.COVAR) {
                return "Correlation";
            }
        }

        if (type == ParamType.VAR) {
            //return "Variance";
            return "Std. Dev.";
        }

        if (type == ParamType.COVAR) {
            return "Covariance";
        }

        throw new IllegalStateException("Unknown param type.");
    }

    private ISemIm semIm() {
        return this.semIm;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }
}

/**
 * Dispays the implied covariance and correlation matrices for the given SemIm.
 */
class ImpliedMatricesPanel extends JPanel {
    private ISemIm semIm;
    private JTable impliedJTable;
    private int matrixSelection = 0;
    private JComboBox selector;

    public ImpliedMatricesPanel(ISemIm semIm, int matrixSelection) {
        this.semIm = semIm;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(selector());
        add(Box.createVerticalStrut(10));
        add(new JScrollPane(impliedJTable()));
        add(Box.createVerticalGlue());
        setBorder(new TitledBorder("Select Implied Matrix to View"));

        setMatrixSelection(matrixSelection);
    }


    /**
     * @return the matrix in tab delimited form.
     */
    public String getMatrixInTabDelimitedForm() {
        StringBuilder builder = new StringBuilder();
        TableModel model = impliedJTable().getModel();
        for (int row = 0; row < model.getRowCount(); row++) {
            for (int col = 0; col < model.getColumnCount(); col++) {
                Object o = model.getValueAt(row, col);
                if (o != null) {
                    builder.append(o);
                }
                builder.append('\t');
            }
            builder.append('\n');
        }
        return builder.toString();
    }


    private JTable impliedJTable() {
        if (this.impliedJTable == null) {
            this.impliedJTable = new JTable();
            this.impliedJTable.setTableHeader(null);
        }
        return this.impliedJTable;
    }

    private JComboBox selector() {
        if (selector == null) {
            selector = new JComboBox();
            List<String> selections = getImpliedSelections();

            for (Object selection : selections) {
                selector.addItem(selection);
            }

            selector.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    String item = (String) e.getItem();
                    setMatrixSelection(getImpliedSelections().indexOf(item));
                }
            });
        }
        return selector;
    }

    public void setMatrixSelection(int index) {
        selector().setSelectedIndex(index);
        switchView(index);
    }

    private void switchView(int index) {
        if (index < 0 || index > 3) {
            throw new IllegalArgumentException(
                    "Matrix selection must be 0, 1, 2, or 3.");
        }

        this.matrixSelection = index;

        switch (index) {
            case 0:
                switchView(false, false);
                break;
            case 1:
                switchView(true, false);
                break;
            case 2:
                switchView(false, true);
                break;
            case 3:
                switchView(true, true);
                break;
        }
    }

    private void switchView(boolean a, boolean b) {
        impliedJTable().setModel(new ImpliedCovTable(getSemIm(), a, b));
        //     impliedJTable().getTableHeader().setReorderingAllowed(false);
        impliedJTable().setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        impliedJTable().setRowSelectionAllowed(false);
        impliedJTable().setCellSelectionEnabled(false);
        impliedJTable().doLayout();
    }

    private static List<String> getImpliedSelections() {
        List<String> list = new ArrayList<String>();
        list.add("Implied covariance matrix (all variables)");
        list.add("Implied covariance matrix (measured variables only)");
        list.add("Implied correlation matrix (all variables)");
        list.add("Implied correlation matrix (measured variables only)");
        return list;
    }

    private ISemIm getSemIm() {
        return this.semIm;
    }

    public int getMatrixSelection() {
        return this.matrixSelection;
    }
}

/**
 * Presents a covariance matrix as a table model for the SemImEditor.
 *
 * @author Donald Crimbchin
 */
final class ImpliedCovTable extends AbstractTableModel {

    /**
     * The SemIm whose implied covariance matrices this model is displaying.
     */
    private ISemIm semIm;

    /**
     * True iff the matrices for the observed variables ony should be
     * displayed.
     */
    private boolean measured;

    /**
     * True iff correlations (rather than covariances) should be displayed.
     */
    private boolean correlations;

    /**
     * Formats numbers so that they have 4 digits after the decimal place.
     */
    private NumberFormat nf;

    /**
     * The matrix being displayed. (This varies.)
     */
    private double[][] matrix;

    /**
     * Constructs a new table for the given covariance matrix, the nodes for
     * which are as specified (in the order they appear in the matrix).
     */
    public ImpliedCovTable(ISemIm semIm, boolean measured,
                           boolean correlations) {
        this.semIm = semIm;
        this.measured = measured;
        this.correlations = correlations;

        this.nf = NumberFormatUtil.getInstance().getNumberFormat();

        if (measured() && covariances()) {
            matrix = getSemIm().getImplCovarMeas().toArray();
        } else if (measured() && !covariances()) {
            matrix = corr(getSemIm().getImplCovarMeas().toArray());
        } else if (!measured() && covariances()) {
            TetradMatrix implCovarC = getSemIm().getImplCovar(false);
            matrix = implCovarC.toArray();
        } else if (!measured() && !covariances()) {
            TetradMatrix implCovarC = getSemIm().getImplCovar(false);
            matrix = corr(implCovarC.toArray());
        }
    }

    /**
     * @return the number of rows being displayed--one more than the size of the
     * matrix, which may be different depending on whether only the observed
     * variables are being displayed or all the variables are being displayed.
     */
    public int getRowCount() {
        if (measured()) {
            return this.getSemIm().getMeasuredNodes().size() + 1;
        } else {
            return this.getSemIm().getVariableNodes().size() + 1;
        }
    }

    /**
     * @return the number of columns displayed--one more than the size of the
     * matrix, which may be different depending on whether only the observed
     * variables are being displayed or all the variables are being displayed.
     */
    public int getColumnCount() {
        if (measured()) {
            return this.getSemIm().getMeasuredNodes().size() + 1;
        } else {
            return this.getSemIm().getVariableNodes().size() + 1;
        }
    }

    /**
     * @return the name of the column at columnIndex, which is "" for column 0
     * and the name of the variable for the other columns.
     */
    public String getColumnName(int columnIndex) {
        if (columnIndex == 0) {
            return "";
        } else {
            if (measured()) {
                List nodes = getSemIm().getMeasuredNodes();
                Node node = ((Node) nodes.get(columnIndex - 1));
                return node.getName();
            } else {
                List nodes = getSemIm().getVariableNodes();
                Node node = ((Node) nodes.get(columnIndex - 1));
                return node.getName();
            }
        }
    }

    /**
     * @return the value being displayed in a cell, either a variable name or a
     * Double.
     */
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex == 0) {
            return getColumnName(columnIndex);
        }
        if (columnIndex == 0) {
            return getColumnName(rowIndex);
        } else if (rowIndex < columnIndex) {
            return null;
        } else {
            return nf.format(matrix[rowIndex - 1][columnIndex - 1]);
        }
    }

    private boolean covariances() {
        return !correlations();
    }

    private static double[][] corr(double[][] implCovar) {
        int length = implCovar.length;
        double[][] corr = new double[length][length];

        for (int i = 1; i < length; i++) {
            for (int j = 0; j < i; j++) {
                double d1 = implCovar[i][j];
                double d2 = implCovar[i][i];
                double d3 = implCovar[j][j];
                double d4 = d1 / Math.pow(d2 * d3, 0.5);

                if (d4 <= 1.0 || Double.isNaN(d4)) {
                    corr[i][j] = d4;
                } else {
                    throw new IllegalArgumentException(
                            "Off-diagonal element at (" + i + ", " + j +
                                    ") cannot be converted to correlation: " +
                                    d1 + " <= Math.pow(" + d2 + " * " + d3 +
                                    ", 0.5)");
                }
            }
        }

        for (int i = 0; i < length; i++) {
            corr[i][i] = 1.0;
        }

        return corr;
    }

    /**
     * @return true iff only observed variables are displayed.
     */
    private boolean measured() {
        return measured;
    }

    /**
     * @return true iff correlations (rather than covariances) are displayed.
     */
    private boolean correlations() {
        return correlations;
    }

    private ISemIm getSemIm() {
        return semIm;
    }
}

final class ModelStatisticsPanel extends JTextArea {
    private ISemIm semIm;
    private NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    public ModelStatisticsPanel(ISemIm semIm) {
        this.semIm = semIm;
        reset();

        addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent e) {
                reset();
            }
        });
    }

    private void reset() {
        setText("");
        setLineWrap(true);
        setWrapStyleWord(true);

        double modelChiSquare;
        double modelDof;
        double modelPValue;

        ISemIm semIm = getSemIm();
        SemPm semPm = semIm.getSemPm();
        List<Node> variables = semPm.getVariableNodes();
        boolean containsLatent = false;
        for (Node node : variables) {
            if (node.getNodeType() == NodeType.LATENT) {
                containsLatent = true;
                break;
            }
        }

        try {
            modelChiSquare = getSemIm().getChiSquare();
            modelDof = getSemIm().getSemPm().getDof();
            modelPValue = getSemIm().getPValue();
        } catch (Exception e) {
            append("Model statistics not available.");
            return;
        }

        if (containsLatent) {
            append("\nEstimated degrees of Freedom = " + (int) modelDof);
        } else {
            append("\nDegrees of Freedom = " + (int) modelDof);
        }
//        append("\n(If the model is latent, this is the estimated degrees of freedom.)");

        append("\nChi Square = " + nf.format(modelChiSquare));

        if (modelDof >= 0) {
            String pValueString = modelPValue > 0.001 ? nf.format(modelPValue)
                    : new DecimalFormat("0.0000E0").format(modelPValue);
            append("\nP Value = " + (Double.isNaN(modelPValue) || modelDof == 0 ? "undefined" : pValueString));
            append("\nBIC Score = " + nf.format(semIm.getBicScore()));

//            append("\n(Experimental!) KIC Score = " + nf.format(semIm.getKicScore()));

//            append("\n\nThe null hypothesis for the above chi square test is that " +
//                    "the population covariance matrix over all variables (sigma) " +
//                    "is equal to the covariance matrix, over the same variables, " +
//                    "written as a function of the free model parameters (Bollen, " +
//                    "Structural Equations with Latent Variables, 110).");

        } else {
            int numToFix = (int) Math.abs(modelDof);
            append("\n\nA SEM with negative degrees of freedom is underidentified, " +
                    "\nand other model statistics are meaningless.  Please increase " +
                    "\nthe degrees of freedom to 0 or above by fixing at least " +
                    numToFix + " parameter" + (numToFix == 1 ? "." : "s."));
        }

        append("\n\nThe above chi square test assumes that the maximum " +
                "likelihood function over the measured variables has been " +
                "minimized. Under that assumption, the null hypothesis for " +
                "the test is that the population covariance matrix over all " +
                "of the measured variables is equal to the estimated covariance " +
                "matrix over all of the measured variables written as a function " +
                "of the free model parameters--that is, the unfixed parameters " +
                "for each directed edge (the linear coefficient for that edge), " +
                "each exogenous variable (the variance for the error term for " +
                "that variable), and each bidirected edge (the covariance for " +
                "the exogenous variables it connects).  The model is explained " +
                "in Bollen, Structural Equations with Latent Variable, 110. " +
                "Degrees of freedom are calculated as m (m + 1) / 2 - d, where d " +
                "is the number of linear coefficients, variance terms, and error " +
                "covariance terms that are not fixed in the model. For latent models, " +
                "the degrees of freedom are termed 'estimated' since extra contraints " +
                "(e.g. pentad constraints) are not taken into account.");

    }

    private ISemIm getSemIm() {
        return this.semIm;
    }
}






