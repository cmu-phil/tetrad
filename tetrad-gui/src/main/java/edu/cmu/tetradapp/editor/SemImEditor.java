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
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetradapp.model.EditorUtils;
import edu.cmu.tetradapp.model.SemEstimatorWrapper;
import edu.cmu.tetradapp.model.SemImWrapper;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphNodeMeasured;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.cmu.tetradapp.workbench.LayoutMenu;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Serializer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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

    private static final long serialVersionUID = -1856607070184945405L;

    private OneEditor oneEditorPanel;
    private final JPanel targetPanel;
    private int matrixSelection;

    /**
     * Constructs a new SemImEditor from the given OldSemEstimateAdapter.
     */
    public SemImEditor(SemImWrapper semImWrapper) {
        this(semImWrapper, "Graphical Editor", "Tabular Editor", TabbedPaneDefault.GRAPHICAL);
    }

    /**
     * Constructs a new SemImEditor from the given OldSemEstimateAdapter.
     */
    public SemImEditor(SemEstimatorWrapper semEstWrapper) {
        this(new SemImWrapper(semEstWrapper.getSemEstimator().getEstimatedSem()));
    }

    /**
     * Constructs an editor for the given SemIm.
     */
    public SemImEditor(SemImWrapper wrapper, String graphicalEditorTitle,
                       String tabularEditorTitle, TabbedPaneDefault tabbedPaneDefault) {

        if (wrapper == null) {
            throw new NullPointerException("The SEM IM wrapper has not been specified.");
        }

        this.setLayout(new BorderLayout());
        targetPanel = new JPanel();
        targetPanel.setLayout(new BorderLayout());
        this.add(targetPanel, BorderLayout.CENTER);

        this.wrapper = wrapper;

        if (wrapper.getNumModels() > 1) {
            JComboBox<Integer> comp = new JComboBox<>();

            for (int i = 0; i < wrapper.getNumModels(); i++) {
                comp.addItem(i + 1);
            }

            comp.setSelectedIndex(wrapper.getModelIndex());

            comp.addActionListener((e) -> {
                wrapper.setModelIndex(((Integer) comp.getSelectedItem()) - 1);
                oneEditorPanel = new OneEditor(wrapper, graphicalEditorTitle, tabularEditorTitle, tabbedPaneDefault);
                targetPanel.add(oneEditorPanel, BorderLayout.CENTER);
                this.validate();
            });

            Box b = Box.createHorizontalBox();
            b.add(new JLabel("Using model"));
            b.add(comp);
            b.add(new JLabel("from "));
            b.add(new JLabel(wrapper.getModelSourceName()));
            b.add(Box.createHorizontalGlue());

            this.add(b, BorderLayout.NORTH);
        }

        oneEditorPanel = new OneEditor(wrapper, graphicalEditorTitle, tabularEditorTitle, tabbedPaneDefault);
        targetPanel.add(oneEditorPanel, BorderLayout.CENTER);
    }

    @Override
    public Graph getGraph() {
        return oneEditorPanel.getGraph();
    }

    @Override
    public Map<Edge, Object> getModelEdgesToDisplay() {
        return oneEditorPanel.getModelEdgesToDisplay();
    }

    @Override
    public Map<Node, Object> getModelNodesToDisplay() {
        return oneEditorPanel.getModelNodesToDisplay();
    }

    @Override
    public IKnowledge getKnowledge() {
        return oneEditorPanel.getKnowledge();
    }

    @Override
    public Graph getSourceGraph() {
        return oneEditorPanel.getSourceGraph();
    }

    @Override
    public void layoutByGraph(Graph graph) {
        oneEditorPanel.layoutByGraph(graph);
    }

    @Override
    public void layoutByKnowledge() {

    }

    public void setEditable(boolean editable) {
        oneEditorPanel.setEditable(editable);
    }

    public int getTabSelectionIndex() {
        return oneEditorPanel.getTabSelectionIndex();
    }

    public int getMatrixSelection() {
        return matrixSelection;
    }

    public GraphWorkbench getWorkbench() {
        return oneEditorPanel.getWorkbench();
    }

    public void displaySemIm(SemIm updatedSem, int tabSelectionIndex, int matrixSelection) {
        oneEditorPanel.displaySemIm(updatedSem, tabSelectionIndex, matrixSelection);
    }

    public enum TabbedPaneDefault {
        GRAPHICAL, TABULAR, COVMATRIX, tabbedPanedDefault, STATS
    }

    private SemImWrapper wrapper;

    private class OneEditor extends JPanel implements LayoutEditable {

        private static final long serialVersionUID = 6622060253747442717L;

        private final SemImWrapper semImWrapper;

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
         * calculated. (Calculating standard errors is high complexity.) Set
         * this to zero to turn off statistics calculations (which can be
         * problematic sometimes).
         */
        private final int maxFreeParamsForStatistics = 1000;

        /**
         * True iff covariance parameters are edited as correlations.
         */
        private boolean editCovariancesAsCorrelations;

        /**
         * True iff covariance parameters are edited as correlations.
         */
        private boolean editIntercepts;
        private JTabbedPane tabbedPane;
        private String graphicalEditorTitle = "Graphical Editor";
        private String tabularEditorTitle = "Tabular Editor";
        private boolean editable = true;
        private int matrixSelection;
        private JCheckBoxMenuItem meansItem;
        private JCheckBoxMenuItem interceptsItem;
        private JMenuItem errorTerms;

        public OneEditor(SemImWrapper wrapper, String graphicalEditorTitle, String tabularEditorTitle,
                         TabbedPaneDefault tabbedPaneDefault) {
            semImWrapper = wrapper;
            this.graphicalEditorTitle = graphicalEditorTitle;
            this.tabularEditorTitle = tabularEditorTitle;
            this.displaySemIm(graphicalEditorTitle, tabularEditorTitle, tabbedPaneDefault);
        }

        private void displaySemIm(String graphicalEditorTitle, String tabularEditorTitle, TabbedPaneDefault tabbedPaneDefault) {
            tabbedPane = new JTabbedPane();
            tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
            this.setLayout(new BorderLayout());

            if (tabbedPaneDefault == TabbedPaneDefault.GRAPHICAL) {
                tabbedPane.add(graphicalEditorTitle, this.graphicalEditor());
                tabbedPane.add(tabularEditorTitle, this.tabularEditor());
                tabbedPane.add("Implied Matrices", this.impliedMatricesPanel());
                tabbedPane.add("Model Statistics", this.modelStatisticsPanel());
            } else if (tabbedPaneDefault == TabbedPaneDefault.TABULAR) {
                tabbedPane.add(tabularEditorTitle, this.tabularEditor());
                tabbedPane.add(graphicalEditorTitle, this.graphicalEditor());
                tabbedPane.add("Implied Matrices", this.impliedMatricesPanel());
                tabbedPane.add("Model Statistics", this.modelStatisticsPanel());
            } else if (tabbedPaneDefault == TabbedPaneDefault.COVMATRIX) {
                tabbedPane.add("Implied Matrices", this.impliedMatricesPanel());
                tabbedPane.add("Model Statistics", this.modelStatisticsPanel());
                tabbedPane.add(graphicalEditorTitle, this.graphicalEditor());
                tabbedPane.add(tabularEditorTitle, this.tabularEditor());
            } else if (tabbedPaneDefault == TabbedPaneDefault.STATS) {
                tabbedPane.add("Model Statistics", this.modelStatisticsPanel());
                tabbedPane.add(graphicalEditorTitle, this.graphicalEditor());
                tabbedPane.add(tabularEditorTitle, this.tabularEditor());
                tabbedPane.add("Implied Matrices", this.impliedMatricesPanel());
            }

            targetPanel.add(tabbedPane, BorderLayout.CENTER);

            JMenuBar menuBar = new JMenuBar();
            JMenu file = new JMenu("File");
            menuBar.add(file);
            file.add(new SaveComponentImage(semImGraphicalEditor.getWorkbench(),
                    "Save Graph Image..."));
            file.add(getCopyMatrixMenuItem());
            JMenuItem saveSemAsXml = new JMenuItem("Save SEM as XML");
            file.add(saveSemAsXml);

            saveSemAsXml.addActionListener(e -> {
                try {
                    File outfile = EditorUtils.getSaveFile("semIm", "xml", SemImEditor.this.getComp(),
                            false, "Save SEM IM as XML...");

                    SemIm bayesIm = (SemIm) oneEditorPanel.getSemIm();
                    FileOutputStream out = new FileOutputStream(outfile);

                    Element element = SemXmlRenderer.getElement(bayesIm);
                    Document document = new Document(element);
                    Serializer serializer = new Serializer(out);
                    serializer.setLineSeparator("\n");
                    serializer.setIndent(2);
                    serializer.write(document);
                    out.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            });

            JCheckBoxMenuItem covariances
                    = new JCheckBoxMenuItem("Show standard deviations");
            JCheckBoxMenuItem correlations
                    = new JCheckBoxMenuItem("Show correlations");

            ButtonGroup correlationGroup = new ButtonGroup();
            correlationGroup.add(covariances);
            correlationGroup.add(correlations);
            covariances.setSelected(true);

            covariances.addActionListener((e) -> {
                this.setEditCovariancesAsCorrelations(false);
            });

            correlations.addActionListener((e) -> {
                this.setEditCovariancesAsCorrelations(true);
            });

            errorTerms = new JMenuItem();

            // By default, hide the error terms.
            if (this.getSemGraph().isShowErrorTerms()) {
                errorTerms.setText("Hide Error Terms");
            } else {
                errorTerms.setText("Show Error Terms");
            }

            errorTerms.addActionListener((e) -> {
                JMenuItem menuItem = (JMenuItem) e.getSource();

                if ("Hide Error Terms".equals(menuItem.getText())) {
                    menuItem.setText("Show Error Terms");
                    this.getSemGraph().setShowErrorTerms(false);
                    this.graphicalEditor().resetLabels();
                } else if ("Show Error Terms".equals(menuItem.getText())) {
                    menuItem.setText("Hide Error Terms");
                    this.getSemGraph().setShowErrorTerms(true);
                    this.graphicalEditor().resetLabels();
                }
            });

            meansItem = new JCheckBoxMenuItem("Show means");
            interceptsItem = new JCheckBoxMenuItem("Show intercepts");

            ButtonGroup meansGroup = new ButtonGroup();
            meansGroup.add(meansItem);
            meansGroup.add(interceptsItem);
            meansItem.setSelected(true);

            meansItem.addActionListener((e) -> {
                if (meansItem.isSelected()) {
                    this.setEditIntercepts(false);
                }
            });

            interceptsItem.addActionListener((e) -> {
                if (interceptsItem.isSelected()) {
                    this.setEditIntercepts(true);
                }
            });

            JMenu params = new JMenu("Parameters");
            params.add(errorTerms);
            params.addSeparator();
            params.add(covariances);
            params.add(correlations);
            params.addSeparator();

            if (!wrapper.getSemIm().isCyclic()) {
                params.add(meansItem);
                params.add(interceptsItem);
            }

            menuBar.add(params);
            menuBar.add(new LayoutMenu(this));

            targetPanel.add(menuBar, BorderLayout.NORTH);
            this.add(tabbedPane, BorderLayout.CENTER);
        }

        @Override
        public Graph getGraph() {
            return semImGraphicalEditor.getWorkbench().getGraph();
        }

        @Override
        public Map<Edge, Object> getModelEdgesToDisplay() {
            return this.getWorkbench().getModelEdgesToDisplay();
        }

        @Override
        public Map<Node, Object> getModelNodesToDisplay() {
            return this.getWorkbench().getModelNodesToDisplay();
        }

        @Override
        public IKnowledge getKnowledge() {
            return semImGraphicalEditor.getWorkbench().getKnowledge();
        }

        @Override
        public Graph getSourceGraph() {
            return semImGraphicalEditor.getWorkbench().getSourceGraph();
        }

        @Override
        public void layoutByGraph(Graph graph) {
            SemGraph _graph = (SemGraph) semImGraphicalEditor.getWorkbench().getGraph();
            _graph.setShowErrorTerms(false);
            semImGraphicalEditor.getWorkbench().layoutByGraph(graph);
            _graph.resetErrorPositions();
//        semImGraphicalEditor.getWorkbench().setGraph(_graph);
            errorTerms.setText("Show Error Terms");
        }

        @Override
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
            return this.getSemIm().getSemPm().getGraph();
        }

        /**
         * @return the index of the currently selected tab. Used to construct a
         * new SemImEditor in the same state as a previous one.
         */
        public int getTabSelectionIndex() {
            return tabbedPane.getSelectedIndex();
        }

        /**
         * @return the index of the matrix that was being viewed most recently.
         * Used to construct a new SemImEditor in the same state as the previous
         * one.
         */
        public int getMatrixSelection() {
            return this.impliedMatricesPanel().getMatrixSelection();
        }

        /**
         * Sets a new SemIm to edit.
         */
        public void displaySemIm(SemIm semIm, int tabSelectionIndex,
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

            wrapper = new SemImWrapper(semIm);

            Graph oldGraph = this.getSemIm().getSemPm().getGraph();

            GraphUtils.arrangeBySourceGraph(this.getSemIm().getSemPm().getGraph(), oldGraph);
            this.matrixSelection = matrixSelection;
            this.impliedMatricesPanel().setMatrixSelection(matrixSelection);

            semImGraphicalEditor = null;
            semImTabularEditor = null;
            impliedMatricesPanel = null;
            modelStatisticsPanel = null;

            tabbedPane.removeAll();
            tabbedPane.add(this.getGraphicalEditorTitle(), this.graphicalEditor());
            tabbedPane.add(this.getTabularEditorTitle(), this.tabularEditor());
            tabbedPane.add("Implied Matrices", this.impliedMatricesPanel());
            tabbedPane.add("Model Statistics", this.modelStatisticsPanel());

            tabbedPane.setSelectedIndex(tabSelectionIndex);
            tabbedPane.validate();
        }

        public GraphWorkbench getWorkbench() {
            return semImGraphicalEditor.getWorkbench();
        }

        //========================PRIVATE METHODS===========================//
        private JMenuItem getCopyMatrixMenuItem() {
            JMenuItem item = new JMenuItem("Copy Implied Covariance Matrix");
            item.addActionListener((e) -> {
                String s = impliedMatricesPanel.getMatrixInTabDelimitedForm();
                Clipboard board = Toolkit.getDefaultToolkit().getSystemClipboard();
                StringSelection selection = new StringSelection(s);
                board.setContents(selection, selection);
            });
            return item;
        }

        private ISemIm getSemIm() {
            return semImWrapper.getSemIm();
        }

        private SemImGraphicalEditor graphicalEditor() {
            if (semImGraphicalEditor == null) {
                semImGraphicalEditor = new SemImGraphicalEditor(wrapper,
                        this, maxFreeParamsForStatistics);
                semImGraphicalEditor.addPropertyChangeListener((evt) -> {
                    SemImEditor.this.firePropertyChange(evt.getPropertyName(), null, null);
                });
            }
            return semImGraphicalEditor;
        }

        private SemImTabularEditor tabularEditor() {
            if (semImTabularEditor == null) {
                semImTabularEditor = new SemImTabularEditor(wrapper, this,
                        maxFreeParamsForStatistics);
            }
            semImTabularEditor.addPropertyChangeListener((evt) -> {
                SemImEditor.this.firePropertyChange(evt.getPropertyName(), null, null);
            });
            return semImTabularEditor;
        }

        private ImpliedMatricesPanel impliedMatricesPanel() {
            if (impliedMatricesPanel == null) {
                impliedMatricesPanel
                        = new ImpliedMatricesPanel(wrapper, matrixSelection);
            }
            return impliedMatricesPanel;
        }

        private ModelStatisticsPanel modelStatisticsPanel() {
            if (modelStatisticsPanel == null) {
                modelStatisticsPanel = new ModelStatisticsPanel(wrapper);
            }
            return modelStatisticsPanel;
        }

        public boolean isEditCovariancesAsCorrelations() {
            return editCovariancesAsCorrelations;
        }

        private void setEditCovariancesAsCorrelations(
                boolean editCovariancesAsCorrelations) {
            this.editCovariancesAsCorrelations = editCovariancesAsCorrelations;
            this.graphicalEditor().resetLabels();
            this.tabularEditor().getTableModel().fireTableDataChanged();
        }

        public boolean isEditIntercepts() {
            return editIntercepts;
        }

        private void setEditIntercepts(boolean editIntercepts) {
            this.editIntercepts = editIntercepts;
            this.graphicalEditor().resetLabels();
            this.tabularEditor().getTableModel().fireTableDataChanged();

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
            this.graphicalEditor().setEditable(editable);
            this.tabularEditor().setEditable(editable);
            this.editable = editable;
        }
    }

    /**
     * Edits the parameters of the SemIm using a graph workbench.
     */
    final class SemImGraphicalEditor extends JPanel {

        private static final long serialVersionUID = 6469399368858967087L;

        /**
         * Font size for parameter values in the graph.
         */
        private final Font SMALL_FONT = new Font("Dialog", Font.PLAIN, 10);
        private final SemImWrapper wrapper;

        /**
         * Background color of the edit panel when you click on the parameters.
         */
        private final Color LIGHT_YELLOW = new Color(255, 255, 215);

        /**
         * w
         * Workbench for the graphical editor.
         */
        private GraphWorkbench workbench;

        /**
         * Stores the last active edge so that it can be reset properly.
         */
        private Object lastEditedObject;

        /**
         * This delay needs to be restored when the component is hidden.
         */
        private int savedTooltipDelay;

        /**
         * The editor that sits inside the SemImEditor that allows the user to
         * edit the SemIm graphically.
         */
        private OneEditor editor;

        /**
         * Maximum number of free parameters for which model statistics will be
         * calculated. The algorithm for calculating these is expensive.
         */
        private final int maxFreeParamsForStatistics;

        /**
         * True iff this graphical display is editable.
         */
        private boolean editable = true;
        private Container dialog;

        /**
         * Constructs a SemIm graphical editor for the given SemIm.
         */
        public SemImGraphicalEditor(SemImWrapper semImWrapper, OneEditor editor,
                                    int maxFreeParamsForStatistics) {
            wrapper = semImWrapper;
            this.editor = editor;
            this.maxFreeParamsForStatistics = maxFreeParamsForStatistics;

            this.setLayout(new BorderLayout());
            JScrollPane scroll = new JScrollPane(this.workbench());

            this.add(scroll, BorderLayout.CENTER);

            this.setBorder(new TitledBorder("Click parameter values to edit"));

            ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
            this.setSavedTooltipDelay(toolTipManager.getInitialDelay());

            // Laborious code that follows is intended to make sure tooltips come
            // almost immediately within the sem im editor but more slowly outside.
            // Ugh.
            this.workbench().addComponentListener(new ComponentAdapter() {
                @Override
                public void componentShown(ComponentEvent e) {
                    SemImGraphicalEditor.this.resetLabels();
                    ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                    toolTipManager.setInitialDelay(100);
                }

                @Override
                public void componentHidden(ComponentEvent e) {
                    ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                    toolTipManager.setInitialDelay(SemImGraphicalEditor.this.getSavedTooltipDelay());
                }
            });

            this.workbench().addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (SemImGraphicalEditor.this.workbench().contains(e.getPoint())) {

                        // Commenting out the resetLabels, since it seems to make
                        // people confused when they can't move the mouse away
                        // from the text field they are editing without the
                        // textfield disappearing. jdramsey 3/16/2005.
//                    resetLabels();
                        ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                        toolTipManager.setInitialDelay(100);
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (!SemImGraphicalEditor.this.workbench().contains(e.getPoint())) {
                        ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                        toolTipManager.setInitialDelay(SemImGraphicalEditor.this.getSavedTooltipDelay());
                    }
                }
            });

            // Make sure the graphical editor reflects changes made to parameters
            // in other editors.
            this.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentShown(ComponentEvent e) {
                    SemImGraphicalEditor.this.resetLabels();
                }
            });
        }

        //========================PRIVATE METHODS===========================//
        private void beginEdgeEdit(Edge edge) {
            this.finishEdit();

            if (!this.isEditable()) {
                return;
            }

            Parameter parameter = this.getEdgeParameter(edge);
            double d = this.semIm().getParamValue(parameter);

            if (editor.isEditCovariancesAsCorrelations()
                    && parameter.getType() == ParamType.COVAR) {
                Node nodeA = parameter.getNodeA();
                Node nodeB = parameter.getNodeB();

                double varA = this.semIm().getParamValue(nodeA, nodeA);
                double varB = this.semIm().getParamValue(nodeB, nodeB);

                d /= Math.sqrt(varA * varB);
            }

            DoubleTextField field = new DoubleTextField(d, 10, NumberFormatUtil.getInstance().getNumberFormat());
            field.setFilter((value, oldValue) -> {
                try {
                    this.setEdgeValue(edge, new Double(value).toString());
                    return value;
                } catch (IllegalArgumentException e) {
                    return oldValue;
                }
            });

            Box box = Box.createHorizontalBox();
            box.add(Box.createHorizontalGlue());
            box.add(new JLabel("New value: "));
            box.add(field);
            box.add(Box.createHorizontalGlue());

            field.addAncestorListener(new AncestorListener() {
                @Override
                public void ancestorMoved(AncestorEvent ancestorEvent) {
                }

                @Override
                public void ancestorRemoved(AncestorEvent ancestorEvent) {
                }

                @Override
                public void ancestorAdded(AncestorEvent ancestorEvent) {
                    Container ancestor = ancestorEvent.getAncestor();

                    if (ancestor instanceof JDialog) {
                        dialog = ancestor;
                    }

                    field.selectAll();
                    field.grabFocus();
                }
            });

            field.addActionListener((e) -> {
                if (dialog != null) {
                    dialog.setVisible(false);
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
//        b1.add(new JLabel(parameter.getNode() + " = "));
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

        private void beginNodeEdit(Node node) {
            this.finishEdit();

            if (!this.isEditable()) {
                return;
            }

//        if (!semIm().getSemPm().getGraph().isParameterizable(node)) {
//            return;
//        }
            Parameter parameter = this.getNodeParameter(node);
            if (editor.isEditCovariancesAsCorrelations()
                    && parameter.getType() == ParamType.VAR) {
                return;
            }

            double d;
            String prefix;
            String postfix = "";

            if (parameter.getType() == ParamType.MEAN) {
                if (editor.isEditIntercepts()) {
                    d = this.semIm().getIntercept(node);
                    prefix = "B0_" + node.getName() + " = ";
                } else {
                    d = this.semIm().getMean(node);
                    prefix = "Mean(" + node.getName() + ") = ";
                }
            } else {
                d = Math.sqrt(this.semIm().getParamValue(parameter));
                prefix = node.getName() + " ~ N(0,";
                postfix = ")";
            }

            DoubleTextField field = new DoubleTextField(d, 10, NumberFormatUtil.getInstance().getNumberFormat());
            field.setFilter((value, oldValue) -> {
                try {
                    this.setNodeValue(node, new Double(value).toString());
                    return value;
                } catch (IllegalArgumentException e) {
                    return oldValue;
                }
            });

            Box box = Box.createHorizontalBox();
            box.add(Box.createHorizontalGlue());
            box.add(new JLabel("New value: "));
            box.add(field);
            box.add(Box.createHorizontalGlue());

            field.addAncestorListener(new AncestorListener() {
                @Override
                public void ancestorMoved(AncestorEvent ancestorEvent) {
                }

                @Override
                public void ancestorRemoved(AncestorEvent ancestorEvent) {
                }

                @Override
                public void ancestorAdded(AncestorEvent ancestorEvent) {
                    Container ancestor = ancestorEvent.getAncestor();

                    if (ancestor instanceof JDialog) {
                        dialog = ancestor;
                    }
                }
            });

            field.addActionListener((e) -> {
                if (dialog != null) {
                    dialog.setVisible(false);
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
            if (this.lastEditedObject() != null) {
                this.resetLabels();
            }
        }

        private ISemIm semIm() {
            return wrapper.getSemIm();
        }

        private Graph graph() {
            return wrapper.getSemIm().getSemPm().getGraph();
        }

        private GraphWorkbench workbench() {
            if (getWorkbench() == null) {
                workbench = new GraphWorkbench(this.graph());
                workbench.enableEditing(false);
                getWorkbench().setAllowDoubleClickActions(false);
                getWorkbench().addPropertyChangeListener((evt) -> {
                    if ("BackgroundClicked".equals(
                            evt.getPropertyName())) {
                        this.finishEdit();
                    }
                });
                this.resetLabels();
                this.addMouseListenerToGraphNodesMeasured();
            }
            return this.getWorkbench();
        }

        private void setLastEditedObject(Object o) {
            lastEditedObject = o;
        }

        private Object lastEditedObject() {
            return lastEditedObject;
        }

        public void resetLabels() {
            Matrix implCovar = this.semIm().getImplCovar(false);

            for (Object o : this.graph().getEdges()) {
                this.resetEdgeLabel((Edge) (o), implCovar);
            }

            List<Node> nodes = this.graph().getNodes();

            for (Object node : nodes) {
                this.resetNodeLabel((Node) node, implCovar);
            }

            this.workbench().repaint();
        }

        private void resetEdgeLabel(Edge edge, Matrix implCovar) {
            Parameter parameter = this.getEdgeParameter(edge);

            if (parameter != null) {
                double val = this.semIm().getParamValue(parameter);
                double standardError;

                try {
                    standardError = this.semIm().getStandardError(parameter,
                            maxFreeParamsForStatistics);
                } catch (Exception exception) {
                    standardError = Double.NaN;
                }

                double tValue;
                try {
                    tValue = this.semIm().getTValue(parameter, maxFreeParamsForStatistics);
                } catch (Exception exception) {
                    tValue = Double.NaN;
                }

                double pValue;

                try {
                    pValue = this.semIm().getPValue(parameter, maxFreeParamsForStatistics);
                } catch (Exception exception) {
                    pValue = Double.NaN;
                }

                if (editor.isEditCovariancesAsCorrelations()
                        && parameter.getType() == ParamType.COVAR) {
                    Node nodeA = edge.getNode1();
                    Node nodeB = edge.getNode2();

                    double varA = this.semIm().getVariance(nodeA, implCovar);
                    double varB = this.semIm().getVariance(nodeB, implCovar);

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

                label.setText(" " + this.asString(val) + " ");

                label.setToolTipText(parameter.getName() + " = " + this.asString(val));
                label.addMouseListener(new EdgeMouseListener(edge, this));
                if (!Double.isNaN(standardError) && this.semIm().isEstimated()) {
                    label.setToolTipText("SE=" + this.asString(standardError) + ", T="
                            + this.asString(tValue) + ", P=" + this.asString(pValue));
                }

                this.workbench().setEdgeLabel(edge, label);
            } else {
                this.workbench().setEdgeLabel(edge, null);
            }
        }

        private void resetNodeLabel(Node node, Matrix implCovar) {
            if (!this.semIm().getSemPm().getGraph().isParameterizable(node)) {
                return;
            }

            Parameter parameter = this.semIm().getSemPm().getVarianceParameter(node);
            double meanOrIntercept = Double.NaN;

            JLabel label = new JLabel();
            label.setBackground(Color.WHITE);
            label.addMouseListener(new NodeMouseListener(node, this));
            label.setFont(SMALL_FONT);

            String tooltip = "";
            NodeType nodeType = node.getNodeType();

            if (nodeType == NodeType.MEASURED || nodeType == NodeType.LATENT) {
                if (editor.isEditIntercepts()) {
                    meanOrIntercept = this.semIm().getIntercept(node);
                } else {
                    meanOrIntercept = this.semIm().getMean(node);
                }
            }

            double stdDev = this.semIm().getStdDev(node, implCovar);

            if (editor.isEditCovariancesAsCorrelations() && !Double.isNaN(stdDev)) {
                stdDev = 1.0;
            }

            if (parameter != null) {
                double standardError = this.semIm().getStandardError(parameter,
                        maxFreeParamsForStatistics);
                double tValue
                        = this.semIm().getTValue(parameter, maxFreeParamsForStatistics);
                double pValue
                        = this.semIm().getPValue(parameter, maxFreeParamsForStatistics);

                tooltip = "SE=" + this.asString(standardError) + ", T="
                        + this.asString(tValue) + ", P=" + this.asString(pValue);
            }

            if (!Double.isNaN(meanOrIntercept)) {
                label.setForeground(Color.GREEN.darker());
                label.setText(this.asString(meanOrIntercept));

                if (editor.isEditIntercepts()) {
                    tooltip = "<html>" + "B0_" + node.getName() + " = "
                            + this.asString(meanOrIntercept) + "</html>";
                } else {
                    tooltip = "<html>" + "Mean(" + node.getName() + ") = "
                            + this.asString(meanOrIntercept) + "</html>";
                }
            } else if (!editor.isEditCovariancesAsCorrelations()
                    && !Double.isNaN(stdDev)) {
                label.setForeground(Color.BLUE);
                label.setText(this.asString(stdDev));

                tooltip = "<html>" + node.getName() + " ~ N(0," + this.asString(stdDev)
                        + ")" + "<br><br>" + tooltip + "</html>";

            } else if (editor.isEditCovariancesAsCorrelations()) {
                label.setForeground(Color.GRAY);
                label.setText(this.asString(stdDev));
            }

            if (parameter != null && parameter.isFixed()) {
                label.setForeground(Color.RED);
            }

            label.setToolTipText(tooltip);

            // Offset the nodes slightly differently depending on whether
            // they're error nodes or not.
            if (nodeType == NodeType.ERROR) {
                label.setOpaque(false);
                this.workbench().setNodeLabel(node, label, -10, -10);
            } else {
                label.setOpaque(false);
                this.workbench().setNodeLabel(node, label, 0, 0);
            }
        }

        private Parameter getNodeParameter(Node node) {
            Parameter parameter = this.semIm().getSemPm().getMeanParameter(node);

            if (parameter == null) {
                parameter = this.semIm().getSemPm().getVarianceParameter(node);
            }
            return parameter;
        }

        /**
         * @return the parameter for the given edge, or null if the edge does
         * not have a parameter associated with it in the model. The edge must
         * be either directed or bidirected, since it has to come from a
         * SemGraph. For directed edges, this method automatically adjusts if
         * the user has changed the endpoints of an edge X1 --> X2 to X1 <-- X2
         * and returns the correct parameter. @throws IllegalArgumentException
         * if the edge is neither directed nor bidirected.
         */
        private Parameter getEdgeParameter(Edge edge) {
            if (Edges.isDirectedEdge(edge)) {
                return this.semIm().getSemPm().getCoefficientParameter(edge.getNode1(), edge.getNode2());
            } else if (Edges.isBidirectedEdge(edge)) {
                return this.semIm().getSemPm().getCovarianceParameter(edge.getNode1(), edge.getNode2());
            }

            throw new IllegalArgumentException(
                    "This is not a directed or bidirected edge: " + edge);
        }

        private void setEdgeValue(Edge edge, String text) {
            try {
                Parameter parameter = this.getEdgeParameter(edge);
                double d = new Double(text);

                if (editor.isEditCovariancesAsCorrelations()
                        && parameter.getType() == ParamType.COVAR) {
                    Node nodeA = edge.getNode1();
                    Node nodeB = edge.getNode2();

                    Matrix implCovar = this.semIm().getImplCovar(false);

                    double varA = this.semIm().getVariance(nodeA, implCovar);
                    double varB = this.semIm().getVariance(nodeB, implCovar);

                    d *= Math.sqrt(varA * varB);

                    this.semIm().setParamValue(parameter, d);
                    firePropertyChange("modelChanged", null, null);
                } else if (!editor.isEditCovariancesAsCorrelations()
                        && parameter.getType() == ParamType.COVAR) {
                    this.semIm().setParamValue(parameter, d);
                    firePropertyChange("modelChanged", null, null);
                } else if (parameter.getType() == ParamType.COEF) {
//                semIm().setParamValue(parameter, d);
//                firePropertyChange("modelChanged", null, null);

                    Node x = parameter.getNodeA();
                    Node y = parameter.getNodeB();

                    this.semIm().setEdgeCoef(x, y, d);

                    if (editor.isEditIntercepts()) {
                        double intercept = this.semIm().getIntercept(y);
                        this.semIm().setIntercept(y, intercept);
                    }

                    firePropertyChange("modelChanged", null, null);
                }
            } catch (NumberFormatException e) {
                // Let the old value be reinstated.
            }

            this.resetLabels();
            this.workbench().repaint();
            this.setLastEditedObject(null);
        }

        private void setNodeValue(Node node, String text) {
            try {
                Parameter parameter = this.getNodeParameter(node);
                double d = new Double(text);

                if (parameter.getType() == ParamType.VAR && d >= 0) {
                    this.semIm().setParamValue(node, node, d * d);
                    firePropertyChange("modelChanged", null, null);
                } else if (parameter.getType() == ParamType.MEAN) {
                    if (editor.isEditIntercepts()) {
                        this.semIm().setIntercept(node, d);
                    } else {
                        this.semIm().setMean(node, d);
                    }

                    firePropertyChange("modelChanged", null, null);
                }
            } catch (Exception exception) {
                exception.printStackTrace(System.err);
                // Let the old value be reinstated.
            }

            this.resetLabels();
            this.workbench().repaint();
            this.setLastEditedObject(null);
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
            List nodes = this.graph().getNodes();

            for (Object node : nodes) {
                Object displayNode = this.workbench().getModelNodesToDisplay().get(node);

                if (displayNode instanceof GraphNodeMeasured) {
                    DisplayNode _displayNode = (DisplayNode) displayNode;
                    _displayNode.setToolTipText(
                            this.getEquationOfNode(_displayNode.getModelNode())
                    );
                }
            }
        }

        private String getEquationOfNode(Node node) {
            String eqn = node.getName() + " = B0_" + node.getName();

            SemGraph semGraph = this.semIm().getSemPm().getGraph();
            List parentNodes = semGraph.getParents(node);

            for (Object parentNodeObj : parentNodes) {
                Node parentNode = (Node) parentNodeObj;
//            Parameter edgeParam = semIm().getEstIm().getEdgeParameter(
//                    semGraph.getEdge(parentNode, node));
                Parameter edgeParam = this.getEdgeParameter(
                        semGraph.getDirectedEdge(parentNode, node));

                if (edgeParam != null) {
                    eqn = eqn + " + " + edgeParam.getName() + "*" + parentNode;
                }
            }

            eqn = eqn + " + " + this.semIm().getSemPm().getGraph().getExogenous(node);

            return eqn;
        }

        public GraphWorkbench getWorkbench() {
            return workbench;
        }

        private boolean isEditable() {
            return editable;
        }

        public void setEditable(boolean editable) {
            this.workbench().setAllowEdgeReorientations(editable);
//        workbench().setAllowMultipleSelection(editable);
//        workbench().setAllowNodeDragging(false);
            this.workbench().setAllowDoubleClickActions(editable);
            this.workbench().setAllowNodeEdgeSelection(editable);
            this.editable = editable;
        }

        final class EdgeMouseListener extends MouseAdapter {

            private final Edge edge;
            private final SemImGraphicalEditor editor;

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
                this.getEditor().beginEdgeEdit(this.getEdge());
            }
        }

        final class NodeMouseListener extends MouseAdapter {

            private final Node node;
            private final SemImGraphicalEditor editor;

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

            @Override
            public void mouseClicked(MouseEvent e) {
                this.getEditor().beginNodeEdit(this.getNode());
            }
        }

        final class EdgeActionListener implements ActionListener {

            private final SemImGraphicalEditor editor;
            private final Edge edge;

            public EdgeActionListener(SemImGraphicalEditor editor, Edge edge) {
                this.editor = editor;
                this.edge = edge;
            }

            @Override
            public void actionPerformed(ActionEvent ev) {
                DoubleTextField doubleTextField = (DoubleTextField) ev.getSource();
                String s = doubleTextField.getText();
                this.getEditor().setEdgeValue(this.getEdge(), s);
            }

            private SemImGraphicalEditor getEditor() {
                return editor;
            }

            private Edge getEdge() {
                return edge;
            }
        }

        final class NodeActionListener implements ActionListener {

            private final SemImGraphicalEditor editor;
            private final Node node;

            public NodeActionListener(SemImGraphicalEditor editor, Node node) {
                this.editor = editor;
                this.node = node;
            }

            @Override
            public void actionPerformed(ActionEvent ev) {
                DoubleTextField doubleTextField = (DoubleTextField) ev.getSource();
                String s = doubleTextField.getText();
                this.getEditor().setNodeValue(this.getNode(), s);
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

        private static final long serialVersionUID = -3652030288654100645L;

        private final ParamTableModel tableModel;
        private final SemImWrapper wrapper;
        private boolean editable = true;

        public SemImTabularEditor(SemImWrapper wrapper, OneEditor editor,
                                  int maxFreeParamsForStatistics) {
            this.wrapper = wrapper;
            this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
//        setBorder(new TitledBorder("Click parameter values to edit"));

            if (this.semIm().isEstimated()) {
                this.setBorder(new TitledBorder("Null hypothesis for T and P is that the parameter is zero"));
            } else {
                this.setBorder(new TitledBorder("Click parameter values to edit"));
            }

            JTable table = new JTable() {
                private static final long serialVersionUID = -530774590911763214L;

                @Override
                public TableCellEditor getCellEditor(int row, int col) {
                    return new DataCellEditor();
                }
            };
            tableModel = new ParamTableModel(wrapper, editor, maxFreeParamsForStatistics);
            table.setModel(this.getTableModel());
            tableModel.addTableModelListener((e) -> {
                firePropertyChange("modelChanged", null, null);
            });

            this.add(new JScrollPane(table), BorderLayout.CENTER);
        }

        private ISemIm semIm() {
            return wrapper.getSemIm();
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

        private static final long serialVersionUID = 2210883212769846304L;

        private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        private final SemImWrapper wrapper;
        private OneEditor editor;
        private int maxFreeParamsForStatistics = 50;
        private boolean editable = true;

        public ParamTableModel(SemImWrapper wrapper, OneEditor editor,
                               int maxFreeParamsForStatistics) {
            this.wrapper = wrapper;

            if (maxFreeParamsForStatistics < 0) {
                throw new IllegalArgumentException();
            }

            this.maxFreeParamsForStatistics = maxFreeParamsForStatistics;

            //  List<Parameter> parameters = new ArrayList<Parameter>();
            //  parameters.addAll(semIm().getFreeParameters());
            //  parameters.addAll(semIm().getFixedParameters());
            //  parameters.addAll(semIm().getMeanParameters());
            this.editor = editor;
        }

        @Override
        public int getRowCount() {
            int numNodes = this.semIm().getVariableNodes().size();
            return this.semIm().getNumFreeParams() + this.semIm().getFixedParameters().size() + numNodes;
        }

        @Override
        public int getColumnCount() {
            return 7;
        }

        @Override
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

        @Override
        public Object getValueAt(int row, int column) {
            List nodes = this.semIm().getVariableNodes();
            List parameters = new ArrayList<>(this.semIm().getFreeParameters());
            parameters.addAll(this.semIm().getFixedParameters());

            int numParams = this.semIm().getNumFreeParams() + this.semIm().getFixedParameters().size();

            if (row < numParams) {
                Parameter parameter = ((Parameter) parameters.get(row));

                switch (column) {
                    case 0:
                        return parameter.getNodeA();
                    case 1:
                        return parameter.getNodeB();
                    case 2:
                        return this.typeString(parameter);
                    case 3:
                        return this.asString(this.paramValue(parameter));
                    case 4:
                        if (parameter.isFixed()) {
                            return "*";
                        } else {
                            return this.asString(this.semIm().getStandardError(parameter,
                                    maxFreeParamsForStatistics));
                        }
                    case 5:
                        if (parameter.isFixed()) {
                            return "*";
                        } else {
                            return this.asString(this.semIm().getTValue(parameter,
                                    maxFreeParamsForStatistics));
                        }
                    case 6:
                        if (parameter.isFixed()) {
                            return "*";
                        } else {
                            return this.asString(this.semIm().getPValue(parameter,
                                    maxFreeParamsForStatistics));
                        }
                }
            } else if (row < numParams + nodes.size()) {
                int index = row - numParams;
                Node node = this.semIm().getVariableNodes().get(index);
                int n = this.semIm().getSampleSize();
                int df = n - 1;
                double mean = this.semIm().getMean(node);
                double stdDev = this.semIm().getMeanStdDev(node);
                double stdErr = stdDev / Math.sqrt(n);
//            double tValue = mean * Math.sqrt(n - 1) / stdDev;

                double tValue = mean / stdErr;
                double p = 2.0 * (1.0 - ProbUtils.tCdf(Math.abs(tValue), df));

//            double ar = covars.get(index, index);
//            stdDev = Math.sqrt(ar);
//            n = semIm().getSampleSize();
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
                            double intercept = this.semIm().getIntercept(node);
                            return this.asString(intercept);
                        } else {
                            return this.asString(mean);
                        }
                    case 4:
                        return this.asString(stdErr);
                    case 5:
                        return this.asString(tValue);
                    case 6:
                        return this.asString(p);
                }
            }

            return null;
        }

        private double paramValue(Parameter parameter) {
            double paramValue = this.semIm().getParamValue(parameter);

            if (editor.isEditCovariancesAsCorrelations()) {
                if (parameter.getType() == ParamType.VAR) {
                    paramValue = 1.0;
                }
                if (parameter.getType() == ParamType.COVAR) {
                    Node nodeA = parameter.getNodeA();
                    Node nodeB = parameter.getNodeB();

                    double varA = this.semIm().getParamValue(nodeA, nodeA);
                    double varB = this.semIm().getParamValue(nodeB, nodeB);

                    paramValue *= Math.sqrt(varA * varB);
                }
            } else {
                if (parameter.getType() == ParamType.VAR) {
                    paramValue = Math.sqrt(paramValue);
                }
            }

            return paramValue;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return this.isEditable() && columnIndex == 3;
        }

        private boolean isEditable() {
            return editable;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {

            if (columnIndex == 3) {
                try {
                    double value = Double.parseDouble((String) aValue);

                    if (rowIndex < this.semIm().getNumFreeParams()) {
                        Parameter parameter = this.semIm().getFreeParameters().get(rowIndex);

                        if (parameter.getType() == ParamType.VAR) {
                            value = value * value;
                            this.semIm().setErrVar(parameter.getNodeA(), value);
                        } else if (parameter.getType() == ParamType.COEF) {
                            Node x = parameter.getNodeA();
                            Node y = parameter.getNodeB();

                            this.semIm().setEdgeCoef(x, y, value);

                            double intercept = this.semIm().getIntercept(y);

                            if (editor.isEditIntercepts()) {
                                this.semIm().setIntercept(y, intercept);
                            }
                        }

                        editor.firePropertyChange("modelChanged", 0, 0);

//                    if (semIm().getParamValue(parameter) != value) {
//                        semIm().setParamValue(parameter, value);
//                        editor.firePropertyChange("modelChanged", 0, 0);
//                    }
                    } else {
                        int index = rowIndex - this.semIm().getNumFreeParams();
                        Node node = this.semIm().getVariableNodes().get(index);

                        if (this.semIm().getMean(this.semIm().getVariableNodes().get(index)) != value) {
                            if (editor.isEditIntercepts()) {
                                this.semIm().setIntercept(node, value);
                                editor.firePropertyChange("modelChanged", 0, 0);
                            } else {
                                this.semIm().setMean(node, value);
                                editor.firePropertyChange("modelChanged", 0, 0);
                            }

                        }
                    }
                } catch (Exception exception) {
                    // The old value will be reinstated automatically.
                }

                this.fireTableDataChanged();
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
            return wrapper.getSemIm();
        }

        public void setEditable(boolean editable) {
            this.editable = editable;
        }
    }

    /**
     * Dispays the implied covariance and correlation matrices for the given
     * SemIm.
     */
    class ImpliedMatricesPanel extends JPanel {

        private static final long serialVersionUID = 2462316724126834072L;

        private final SemImWrapper wrapper;
        private JTable impliedJTable;
        private int matrixSelection;
        private JComboBox selector;

        public ImpliedMatricesPanel(SemImWrapper wrapper, int matrixSelection) {
            this.wrapper = wrapper;

            this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            this.add(this.selector());
            this.add(Box.createVerticalStrut(10));
            this.add(new JScrollPane(this.impliedJTable()));
            this.add(Box.createVerticalGlue());
            this.setBorder(new TitledBorder("Select Implied Matrix to View"));

            this.setMatrixSelection(matrixSelection);
        }

        /**
         * @return the matrix in tab delimited form.
         */
        public String getMatrixInTabDelimitedForm() {
            StringBuilder builder = new StringBuilder();
            TableModel model = this.impliedJTable().getModel();
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
            if (impliedJTable == null) {
                impliedJTable = new JTable();
                impliedJTable.setTableHeader(null);
            }
            return impliedJTable;
        }

        private JComboBox selector() {
            if (selector == null) {
                selector = new JComboBox();
                List<String> selections = this.getImpliedSelections();

                for (Object selection : selections) {
                    selector.addItem(selection);
                }

                selector.addItemListener((e) -> {
                    String item = (String) e.getItem();
                    this.setMatrixSelection(this.getImpliedSelections().indexOf(item));
                });
            }
            return selector;
        }

        public void setMatrixSelection(int index) {
            this.selector().setSelectedIndex(index);
            this.switchView(index);
        }

        private void switchView(int index) {
            if (index < 0 || index > 3) {
                throw new IllegalArgumentException(
                        "Matrix selection must be 0, 1, 2, or 3.");
            }

            matrixSelection = index;

            switch (index) {
                case 0:
                    this.switchView(false, false);
                    break;
                case 1:
                    this.switchView(true, false);
                    break;
                case 2:
                    this.switchView(false, true);
                    break;
                case 3:
                    this.switchView(true, true);
                    break;
            }
        }

        private void switchView(boolean a, boolean b) {
            this.impliedJTable().setModel(new ImpliedCovTable(wrapper, a, b));
            //     impliedJTable().getTableHeader().setReorderingAllowed(false);
            this.impliedJTable().setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            this.impliedJTable().setRowSelectionAllowed(false);
            this.impliedJTable().setCellSelectionEnabled(false);
            this.impliedJTable().doLayout();
        }

        private List<String> getImpliedSelections() {
            List<String> list = new ArrayList<>();
            list.add("Implied covariance matrix (all variables)");
            list.add("Implied covariance matrix (measured variables only)");
            list.add("Implied correlation matrix (all variables)");
            list.add("Implied correlation matrix (measured variables only)");
            return list;
        }

        private ISemIm getSemIm() {
            return wrapper.getSemIm();
        }

        public int getMatrixSelection() {
            return matrixSelection;
        }
    }

    /**
     * Presents a covariance matrix as a table model for the SemImEditor.
     *
     * @author Donald Crimbchin
     */
    final class ImpliedCovTable extends AbstractTableModel {

        private static final long serialVersionUID = -8269181589527893805L;

        /**
         * True iff the matrices for the observed variables ony should be
         * displayed.
         */
        private final boolean measured;

        /**
         * True iff correlations (rather than covariances) should be displayed.
         */
        private final boolean correlations;

        /**
         * Formats numbers so that they have 4 digits after the decimal place.
         */
        private final NumberFormat nf;
        private final SemImWrapper wrapper;

        /**
         * The matrix being displayed. (This varies.)
         */
        private double[][] matrix;

        /**
         * Constructs a new table for the given covariance matrix, the nodes for
         * which are as specified (in the order they appear in the matrix).
         */
        public ImpliedCovTable(SemImWrapper wrapper, boolean measured,
                               boolean correlations) {
            this.wrapper = wrapper;
            this.measured = measured;
            this.correlations = correlations;

            nf = NumberFormatUtil.getInstance().getNumberFormat();

            if (this.measured() && this.covariances()) {
                matrix = this.getSemIm().getImplCovarMeas().toArray();
            } else if (this.measured() && !this.covariances()) {
                matrix = this.corr(this.getSemIm().getImplCovarMeas().toArray());
            } else if (!this.measured() && this.covariances()) {
                Matrix implCovarC = this.getSemIm().getImplCovar(false);
                matrix = implCovarC.toArray();
            } else if (!this.measured() && !this.covariances()) {
                Matrix implCovarC = this.getSemIm().getImplCovar(false);
                matrix = this.corr(implCovarC.toArray());
            }
        }

        /**
         * @return the number of rows being displayed--one more than the size of
         * the matrix, which may be different depending on whether only the
         * observed variables are being displayed or all the variables are being
         * displayed.
         */
        @Override
        public int getRowCount() {
            if (this.measured()) {
                return getSemIm().getMeasuredNodes().size() + 1;
            } else {
                return getSemIm().getVariableNodes().size() + 1;
            }
        }

        /**
         * @return the number of columns displayed--one more than the size of
         * the matrix, which may be different depending on whether only the
         * observed variables are being displayed or all the variables are being
         * displayed.
         */
        @Override
        public int getColumnCount() {
            if (this.measured()) {
                return getSemIm().getMeasuredNodes().size() + 1;
            } else {
                return getSemIm().getVariableNodes().size() + 1;
            }
        }

        /**
         * @return the name of the column at columnIndex, which is "" for column
         * 0 and the name of the variable for the other columns.
         */
        @Override
        public String getColumnName(int columnIndex) {
            if (columnIndex == 0) {
                return "";
            } else {
                if (this.measured()) {
                    List nodes = this.getSemIm().getMeasuredNodes();
                    Node node = ((Node) nodes.get(columnIndex - 1));
                    return node.getName();
                } else {
                    List nodes = this.getSemIm().getVariableNodes();
                    Node node = ((Node) nodes.get(columnIndex - 1));
                    return node.getName();
                }
            }
        }

        /**
         * @return the value being displayed in a cell, either a variable name
         * or a Double.
         */
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex == 0) {
                return this.getColumnName(columnIndex);
            }
            if (columnIndex == 0) {
                return this.getColumnName(rowIndex);
            } else if (rowIndex < columnIndex) {
                return null;
            } else {
                return nf.format(matrix[rowIndex - 1][columnIndex - 1]);
            }
        }

        private boolean covariances() {
            return !this.correlations();
        }

        private double[][] corr(double[][] implCovar) {
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
                                "Off-diagonal element at (" + i + ", " + j
                                        + ") cannot be converted to correlation: "
                                        + d1 + " <= Math.pow(" + d2 + " * " + d3
                                        + ", 0.5)");
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
         * @return true iff correlations (rather than covariances) are
         * displayed.
         */
        private boolean correlations() {
            return correlations;
        }

        private ISemIm getSemIm() {
            return wrapper.getSemIm();
        }
    }

    final class ModelStatisticsPanel extends JTextArea {

        private static final long serialVersionUID = -9096723049787232471L;

        private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        private final SemImWrapper wrapper;

        public ModelStatisticsPanel(SemImWrapper wrapper) {
            this.wrapper = wrapper;
            this.reset();

            this.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentShown(ComponentEvent e) {
                    ModelStatisticsPanel.this.reset();
                }
            });
        }

        private void reset() {
            this.setText("");
            this.setLineWrap(true);
            this.setWrapStyleWord(true);

            double modelChiSquare;
            double modelDof;
            double modelPValue;

            SemPm semPm = this.semIm().getSemPm();
            List<Node> variables = semPm.getVariableNodes();
            boolean containsLatent = false;
            for (Node node : variables) {
                if (node.getNodeType() == NodeType.LATENT) {
                    containsLatent = true;
                    break;
                }
            }

            try {
                modelChiSquare = this.semIm().getChiSquare();
                modelDof = this.semIm().getSemPm().getDof();
                modelPValue = this.semIm().getPValue();
            } catch (Exception exception) {
                this.append("Model statistics not available.");
                return;
            }

            if (containsLatent) {
                this.append("\nEstimated degrees of Freedom = " + (int) modelDof);
            } else {
                this.append("\nDegrees of Freedom = " + (int) modelDof);
            }
//        append("\n(If the model is latent, this is the estimated degrees of freedom.)");

            this.append("\nChi Square = " + nf.format(modelChiSquare));

            if (modelDof >= 0) {
                String pValueString = modelPValue > 0.001 ? nf.format(modelPValue)
                        : new DecimalFormat("0.0000E0").format(modelPValue);
                this.append("\nP Value = " + (Double.isNaN(modelPValue) || modelDof == 0 ? "undefined" : pValueString));
                this.append("\nBIC Score = " + nf.format(this.semIm().getBicScore()));
                this.append("\nCFI = " + nf.format(this.semIm().getCfi()));
                this.append("\nRMSEA = " + nf.format(this.semIm().getRmsea()));

//            append("\n(Experimental!) KIC Score = " + nf.format(semIm().getKicScore()));
//            append("\n\nThe null hypothesis for the above chi square test is that " +
//                    "the population covariance matrix over all variables (sigma) " +
//                    "is equal to the covariance matrix, over the same variables, " +
//                    "written as a function of the free model parameters (Bollen, " +
//                    "Structural Equations with Latent Variables, 110).");
            } else {
                int numToFix = (int) Math.abs(modelDof);
                this.append("\n\nA SEM with negative degrees of freedom is underidentified, "
                        + "\nand other model statistics are meaningless.  Please increase "
                        + "\nthe degrees of freedom to 0 or above by fixing at least "
                        + numToFix + " parameter" + (numToFix == 1 ? "." : "s."));
            }

            this.append("\n\nThe above chi square test assumes that the maximum "
                    + "likelihood function over the measured variables has been "
                    + "minimized. Under that assumption, the null hypothesis for "
                    + "the test is that the population covariance matrix over all "
                    + "of the measured variables is equal to the estimated covariance "
                    + "matrix over all of the measured variables written as a function "
                    + "of the free model parameters--that is, the unfixed parameters "
                    + "for each directed edge (the linear coefficient for that edge), "
                    + "each exogenous variable (the variance for the error term for "
                    + "that variable), and each bidirected edge (the covariance for "
                    + "the exogenous variables it connects).  The model is explained "
                    + "in Bollen, Structural Equations with Latent Variable, 110. "
                    + "Degrees of freedom are calculated as m (m + 1) / 2 - d, where d "
                    + "is the number of linear coefficients, variance terms, and error "
                    + "covariance terms that are not fixed in the model. For latent models, "
                    + "the degrees of freedom are termed 'estimated' since extra contraints "
                    + "(e.g. pentad constraints) are not taken into account.");

        }

        private ISemIm semIm() {
            return wrapper.getSemIm();
        }
    }

    private Component getComp() {
        EditorWindow editorWindow =
                (EditorWindow) SwingUtilities.getAncestorOfClass(
                        EditorWindow.class, this);

        if (editorWindow != null) {
            return editorWindow.getRootPane().getContentPane();
        } else {
            return editorWindow;
        }
    }

}
