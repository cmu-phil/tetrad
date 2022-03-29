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
    public SemImEditor(final SemImWrapper semImWrapper) {
        this(semImWrapper, "Graphical Editor", "Tabular Editor", TabbedPaneDefault.GRAPHICAL);
    }

    /**
     * Constructs a new SemImEditor from the given OldSemEstimateAdapter.
     */
    public SemImEditor(final SemEstimatorWrapper semEstWrapper) {
        this(new SemImWrapper(semEstWrapper.getSemEstimator().getEstimatedSem()));
    }

    /**
     * Constructs an editor for the given SemIm.
     */
    public SemImEditor(final SemImWrapper wrapper, final String graphicalEditorTitle,
                       final String tabularEditorTitle, final TabbedPaneDefault tabbedPaneDefault) {

        if (wrapper == null) {
            throw new NullPointerException("The SEM IM wrapper has not been specified.");
        }

        setLayout(new BorderLayout());
        this.targetPanel = new JPanel();
        this.targetPanel.setLayout(new BorderLayout());
        add(this.targetPanel, BorderLayout.CENTER);

        this.wrapper = wrapper;

        if (wrapper.getNumModels() > 1) {
            final JComboBox<Integer> comp = new JComboBox<>();

            for (int i = 0; i < wrapper.getNumModels(); i++) {
                comp.addItem(i + 1);
            }

            comp.setSelectedIndex(wrapper.getModelIndex());

            comp.addActionListener((e) -> {
                wrapper.setModelIndex(((Integer) comp.getSelectedItem()) - 1);
                this.oneEditorPanel = new OneEditor(wrapper, graphicalEditorTitle, tabularEditorTitle, tabbedPaneDefault);
                this.targetPanel.add(this.oneEditorPanel, BorderLayout.CENTER);
                validate();
            });

            final Box b = Box.createHorizontalBox();
            b.add(new JLabel("Using model"));
            b.add(comp);
            b.add(new JLabel("from "));
            b.add(new JLabel(wrapper.getModelSourceName()));
            b.add(Box.createHorizontalGlue());

            add(b, BorderLayout.NORTH);
        }

        this.oneEditorPanel = new OneEditor(wrapper, graphicalEditorTitle, tabularEditorTitle, tabbedPaneDefault);
        this.targetPanel.add(this.oneEditorPanel, BorderLayout.CENTER);
    }

    @Override
    public Graph getGraph() {
        return this.oneEditorPanel.getGraph();
    }

    @Override
    public Map<Edge, Object> getModelEdgesToDisplay() {
        return this.oneEditorPanel.getModelEdgesToDisplay();
    }

    @Override
    public Map<Node, Object> getModelNodesToDisplay() {
        return this.oneEditorPanel.getModelNodesToDisplay();
    }

    @Override
    public IKnowledge getKnowledge() {
        return this.oneEditorPanel.getKnowledge();
    }

    @Override
    public Graph getSourceGraph() {
        return this.oneEditorPanel.getSourceGraph();
    }

    @Override
    public void layoutByGraph(final Graph graph) {
        this.oneEditorPanel.layoutByGraph(graph);
    }

    @Override
    public void layoutByKnowledge() {

    }

    public void setEditable(final boolean editable) {
        this.oneEditorPanel.setEditable(editable);
    }

    public int getTabSelectionIndex() {
        return this.oneEditorPanel.getTabSelectionIndex();
    }

    public int getMatrixSelection() {
        return this.matrixSelection;
    }

    public GraphWorkbench getWorkbench() {
        return this.oneEditorPanel.getWorkbench();
    }

    public void displaySemIm(final SemIm updatedSem, final int tabSelectionIndex, final int matrixSelection) {
        this.oneEditorPanel.displaySemIm(updatedSem, tabSelectionIndex, matrixSelection);
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

        public OneEditor(final SemImWrapper wrapper, final String graphicalEditorTitle, final String tabularEditorTitle,
                         final TabbedPaneDefault tabbedPaneDefault) {
            this.semImWrapper = wrapper;
            this.graphicalEditorTitle = graphicalEditorTitle;
            this.tabularEditorTitle = tabularEditorTitle;
            displaySemIm(graphicalEditorTitle, tabularEditorTitle, tabbedPaneDefault);
        }

        private void displaySemIm(final String graphicalEditorTitle, final String tabularEditorTitle, final TabbedPaneDefault tabbedPaneDefault) {
            this.tabbedPane = new JTabbedPane();
            this.tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
            setLayout(new BorderLayout());

            if (tabbedPaneDefault == TabbedPaneDefault.GRAPHICAL) {
                this.tabbedPane.add(graphicalEditorTitle, graphicalEditor());
                this.tabbedPane.add(tabularEditorTitle, tabularEditor());
                this.tabbedPane.add("Implied Matrices", impliedMatricesPanel());
                this.tabbedPane.add("Model Statistics", modelStatisticsPanel());
            } else if (tabbedPaneDefault == TabbedPaneDefault.TABULAR) {
                this.tabbedPane.add(tabularEditorTitle, tabularEditor());
                this.tabbedPane.add(graphicalEditorTitle, graphicalEditor());
                this.tabbedPane.add("Implied Matrices", impliedMatricesPanel());
                this.tabbedPane.add("Model Statistics", modelStatisticsPanel());
            } else if (tabbedPaneDefault == TabbedPaneDefault.COVMATRIX) {
                this.tabbedPane.add("Implied Matrices", impliedMatricesPanel());
                this.tabbedPane.add("Model Statistics", modelStatisticsPanel());
                this.tabbedPane.add(graphicalEditorTitle, graphicalEditor());
                this.tabbedPane.add(tabularEditorTitle, tabularEditor());
            } else if (tabbedPaneDefault == TabbedPaneDefault.STATS) {
                this.tabbedPane.add("Model Statistics", modelStatisticsPanel());
                this.tabbedPane.add(graphicalEditorTitle, graphicalEditor());
                this.tabbedPane.add(tabularEditorTitle, tabularEditor());
                this.tabbedPane.add("Implied Matrices", impliedMatricesPanel());
            }

            SemImEditor.this.targetPanel.add(this.tabbedPane, BorderLayout.CENTER);

            final JMenuBar menuBar = new JMenuBar();
            final JMenu file = new JMenu("File");
            menuBar.add(file);
            file.add(new SaveComponentImage(this.semImGraphicalEditor.getWorkbench(),
                    "Save Graph Image..."));
            file.add(this.getCopyMatrixMenuItem());
            final JMenuItem saveSemAsXml = new JMenuItem("Save SEM as XML");
            file.add(saveSemAsXml);

            saveSemAsXml.addActionListener(e -> {
                try {
                    final File outfile = EditorUtils.getSaveFile("semIm", "xml", getComp(),
                            false, "Save SEM IM as XML...");

                    final SemIm bayesIm = (SemIm) SemImEditor.this.oneEditorPanel.getSemIm();
                    final FileOutputStream out = new FileOutputStream(outfile);

                    final Element element = SemXmlRenderer.getElement(bayesIm);
                    final Document document = new Document(element);
                    final Serializer serializer = new Serializer(out);
                    serializer.setLineSeparator("\n");
                    serializer.setIndent(2);
                    serializer.write(document);
                    out.close();
                } catch (final IOException ioException) {
                    ioException.printStackTrace();
                }
            });

            final JCheckBoxMenuItem covariances
                    = new JCheckBoxMenuItem("Show standard deviations");
            final JCheckBoxMenuItem correlations
                    = new JCheckBoxMenuItem("Show correlations");

            final ButtonGroup correlationGroup = new ButtonGroup();
            correlationGroup.add(covariances);
            correlationGroup.add(correlations);
            covariances.setSelected(true);

            covariances.addActionListener((e) -> {
                setEditCovariancesAsCorrelations(false);
            });

            correlations.addActionListener((e) -> {
                setEditCovariancesAsCorrelations(true);
            });

            this.errorTerms = new JMenuItem();

            // By default, hide the error terms.
            if (getSemGraph().isShowErrorTerms()) {
                this.errorTerms.setText("Hide Error Terms");
            } else {
                this.errorTerms.setText("Show Error Terms");
            }

            this.errorTerms.addActionListener((e) -> {
                final JMenuItem menuItem = (JMenuItem) e.getSource();

                if ("Hide Error Terms".equals(menuItem.getText())) {
                    menuItem.setText("Show Error Terms");
                    getSemGraph().setShowErrorTerms(false);
                    graphicalEditor().resetLabels();
                } else if ("Show Error Terms".equals(menuItem.getText())) {
                    menuItem.setText("Hide Error Terms");
                    getSemGraph().setShowErrorTerms(true);
                    graphicalEditor().resetLabels();
                }
            });

            this.meansItem = new JCheckBoxMenuItem("Show means");
            this.interceptsItem = new JCheckBoxMenuItem("Show intercepts");

            final ButtonGroup meansGroup = new ButtonGroup();
            meansGroup.add(this.meansItem);
            meansGroup.add(this.interceptsItem);
            this.meansItem.setSelected(true);

            this.meansItem.addActionListener((e) -> {
                if (this.meansItem.isSelected()) {
                    setEditIntercepts(false);
                }
            });

            this.interceptsItem.addActionListener((e) -> {
                if (this.interceptsItem.isSelected()) {
                    setEditIntercepts(true);
                }
            });

            final JMenu params = new JMenu("Parameters");
            params.add(this.errorTerms);
            params.addSeparator();
            params.add(covariances);
            params.add(correlations);
            params.addSeparator();

            if (!SemImEditor.this.wrapper.getSemIm().isCyclic()) {
                params.add(this.meansItem);
                params.add(this.interceptsItem);
            }

            menuBar.add(params);
            menuBar.add(new LayoutMenu(this));

            SemImEditor.this.targetPanel.add(menuBar, BorderLayout.NORTH);
            add(this.tabbedPane, BorderLayout.CENTER);
        }

        @Override
        public Graph getGraph() {
            return this.semImGraphicalEditor.getWorkbench().getGraph();
        }

        @Override
        public Map<Edge, Object> getModelEdgesToDisplay() {
            return getWorkbench().getModelEdgesToDisplay();
        }

        @Override
        public Map<Node, Object> getModelNodesToDisplay() {
            return getWorkbench().getModelNodesToDisplay();
        }

        @Override
        public IKnowledge getKnowledge() {
            return this.semImGraphicalEditor.getWorkbench().getKnowledge();
        }

        @Override
        public Graph getSourceGraph() {
            return this.semImGraphicalEditor.getWorkbench().getSourceGraph();
        }

        @Override
        public void layoutByGraph(final Graph graph) {
            final SemGraph _graph = (SemGraph) this.semImGraphicalEditor.getWorkbench().getGraph();
            _graph.setShowErrorTerms(false);
            this.semImGraphicalEditor.getWorkbench().layoutByGraph(graph);
            _graph.resetErrorPositions();
//        semImGraphicalEditor.getWorkbench().setGraph(_graph);
            this.errorTerms.setText("Show Error Terms");
        }

        @Override
        public void layoutByKnowledge() {
            final SemGraph _graph = (SemGraph) this.semImGraphicalEditor.getWorkbench().getGraph();
            _graph.setShowErrorTerms(false);
            this.semImGraphicalEditor.getWorkbench().layoutByKnowledge();
            _graph.resetErrorPositions();
//        semImGraphicalEditor.getWorkbench().setGraph(_graph);
            this.errorTerms.setText("Show Error Terms");
        }

        private void checkForUnmeasuredLatents(final ISemIm semIm) {
            final List<Node> unmeasuredLatents = semIm.listUnmeasuredLatents();

            if (!unmeasuredLatents.isEmpty()) {
                final StringBuilder buf = new StringBuilder();
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
            return getSemIm().getSemPm().getGraph();
        }

        /**
         * @return the index of the currently selected tab. Used to construct a
         * new SemImEditor in the same state as a previous one.
         */
        public int getTabSelectionIndex() {
            return this.tabbedPane.getSelectedIndex();
        }

        /**
         * @return the index of the matrix that was being viewed most recently.
         * Used to construct a new SemImEditor in the same state as the previous
         * one.
         */
        public int getMatrixSelection() {
            return impliedMatricesPanel().getMatrixSelection();
        }

        /**
         * Sets a new SemIm to edit.
         */
        public void displaySemIm(final SemIm semIm, final int tabSelectionIndex,
                                 final int matrixSelection) {
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

            SemImEditor.this.wrapper = new SemImWrapper(semIm);

            final Graph oldGraph = getSemIm().getSemPm().getGraph();

            GraphUtils.arrangeBySourceGraph(getSemIm().getSemPm().getGraph(), oldGraph);
            this.matrixSelection = matrixSelection;
            impliedMatricesPanel().setMatrixSelection(matrixSelection);

            this.semImGraphicalEditor = null;
            this.semImTabularEditor = null;
            this.impliedMatricesPanel = null;
            this.modelStatisticsPanel = null;

            this.tabbedPane.removeAll();
            this.tabbedPane.add(getGraphicalEditorTitle(), graphicalEditor());
            this.tabbedPane.add(getTabularEditorTitle(), tabularEditor());
            this.tabbedPane.add("Implied Matrices", impliedMatricesPanel());
            this.tabbedPane.add("Model Statistics", modelStatisticsPanel());

            this.tabbedPane.setSelectedIndex(tabSelectionIndex);
            this.tabbedPane.validate();
        }

        public GraphWorkbench getWorkbench() {
            return this.semImGraphicalEditor.getWorkbench();
        }

        //========================PRIVATE METHODS===========================//
        private JMenuItem getCopyMatrixMenuItem() {
            final JMenuItem item = new JMenuItem("Copy Implied Covariance Matrix");
            item.addActionListener((e) -> {
                final String s = this.impliedMatricesPanel.getMatrixInTabDelimitedForm();
                final Clipboard board = Toolkit.getDefaultToolkit().getSystemClipboard();
                final StringSelection selection = new StringSelection(s);
                board.setContents(selection, selection);
            });
            return item;
        }

        private ISemIm getSemIm() {
            return this.semImWrapper.getSemIm();
        }

        private SemImGraphicalEditor graphicalEditor() {
            if (this.semImGraphicalEditor == null) {
                this.semImGraphicalEditor = new SemImGraphicalEditor(SemImEditor.this.wrapper,
                        this, this.maxFreeParamsForStatistics);
                this.semImGraphicalEditor.addPropertyChangeListener((evt) -> {
                    SemImEditor.this.firePropertyChange(evt.getPropertyName(), null, null);
                });
            }
            return this.semImGraphicalEditor;
        }

        private SemImTabularEditor tabularEditor() {
            if (this.semImTabularEditor == null) {
                this.semImTabularEditor = new SemImTabularEditor(SemImEditor.this.wrapper, this,
                        this.maxFreeParamsForStatistics);
            }
            this.semImTabularEditor.addPropertyChangeListener((evt) -> {
                SemImEditor.this.firePropertyChange(evt.getPropertyName(), null, null);
            });
            return this.semImTabularEditor;
        }

        private ImpliedMatricesPanel impliedMatricesPanel() {
            if (this.impliedMatricesPanel == null) {
                this.impliedMatricesPanel
                        = new ImpliedMatricesPanel(SemImEditor.this.wrapper, this.matrixSelection);
            }
            return this.impliedMatricesPanel;
        }

        private ModelStatisticsPanel modelStatisticsPanel() {
            if (this.modelStatisticsPanel == null) {
                this.modelStatisticsPanel = new ModelStatisticsPanel(SemImEditor.this.wrapper);
            }
            return this.modelStatisticsPanel;
        }

        public boolean isEditCovariancesAsCorrelations() {
            return this.editCovariancesAsCorrelations;
        }

        private void setEditCovariancesAsCorrelations(
                final boolean editCovariancesAsCorrelations) {
            this.editCovariancesAsCorrelations = editCovariancesAsCorrelations;
            graphicalEditor().resetLabels();
            tabularEditor().getTableModel().fireTableDataChanged();
        }

        public boolean isEditIntercepts() {
            return this.editIntercepts;
        }

        private void setEditIntercepts(final boolean editIntercepts) {
            this.editIntercepts = editIntercepts;
            graphicalEditor().resetLabels();
            tabularEditor().getTableModel().fireTableDataChanged();

            this.meansItem.setSelected(!editIntercepts);
            this.interceptsItem.setSelected(editIntercepts);
        }

        private String getGraphicalEditorTitle() {
            return this.graphicalEditorTitle;
        }

        private String getTabularEditorTitle() {
            return this.tabularEditorTitle;
        }

        public boolean isEditable() {
            return this.editable;
        }

        public void setEditable(final boolean editable) {
            graphicalEditor().setEditable(editable);
            tabularEditor().setEditable(editable);
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
        private Object lastEditedObject = null;

        /**
         * This delay needs to be restored when the component is hidden.
         */
        private int savedTooltipDelay = 0;

        /**
         * The editor that sits inside the SemImEditor that allows the user to
         * edit the SemIm graphically.
         */
        private SemImEditor.OneEditor editor = null;

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
        public SemImGraphicalEditor(final SemImWrapper semImWrapper, final SemImEditor.OneEditor editor,
                                    final int maxFreeParamsForStatistics) {
            this.wrapper = semImWrapper;
            this.editor = editor;
            this.maxFreeParamsForStatistics = maxFreeParamsForStatistics;

            setLayout(new BorderLayout());
            final JScrollPane scroll = new JScrollPane(workbench());

            add(scroll, BorderLayout.CENTER);

            setBorder(new TitledBorder("Click parameter values to edit"));

            final ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
            setSavedTooltipDelay(toolTipManager.getInitialDelay());

            // Laborious code that follows is intended to make sure tooltips come
            // almost immediately within the sem im editor but more slowly outside.
            // Ugh.
            workbench().addComponentListener(new ComponentAdapter() {
                @Override
                public void componentShown(final ComponentEvent e) {
                    resetLabels();
                    final ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                    toolTipManager.setInitialDelay(100);
                }

                @Override
                public void componentHidden(final ComponentEvent e) {
                    final ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                    toolTipManager.setInitialDelay(getSavedTooltipDelay());
                }
            });

            workbench().addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(final MouseEvent e) {
                    if (workbench().contains(e.getPoint())) {

                        // Commenting out the resetLabels, since it seems to make
                        // people confused when they can't move the mouse away
                        // from the text field they are editing without the
                        // textfield disappearing. jdramsey 3/16/2005.
//                    resetLabels();
                        final ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                        toolTipManager.setInitialDelay(100);
                    }
                }

                @Override
                public void mouseExited(final MouseEvent e) {
                    if (!workbench().contains(e.getPoint())) {
                        final ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                        toolTipManager.setInitialDelay(getSavedTooltipDelay());
                    }
                }
            });

            // Make sure the graphical editor reflects changes made to parameters
            // in other editors.
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentShown(final ComponentEvent e) {
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

            final Parameter parameter = getEdgeParameter(edge);
            double d = semIm().getParamValue(parameter);

            if (this.editor.isEditCovariancesAsCorrelations()
                    && parameter.getType() == ParamType.COVAR) {
                final Node nodeA = parameter.getNodeA();
                final Node nodeB = parameter.getNodeB();

                final double varA = semIm().getParamValue(nodeA, nodeA);
                final double varB = semIm().getParamValue(nodeB, nodeB);

                d /= Math.sqrt(varA * varB);
            }

            final DoubleTextField field = new DoubleTextField(d, 10, NumberFormatUtil.getInstance().getNumberFormat());
            field.setFilter((value, oldValue) -> {
                try {
                    setEdgeValue(edge, new Double(value).toString());
                    return value;
                } catch (final IllegalArgumentException e) {
                    return oldValue;
                }
            });

            final Box box = Box.createHorizontalBox();
            box.add(Box.createHorizontalGlue());
            box.add(new JLabel("New value: "));
            box.add(field);
            box.add(Box.createHorizontalGlue());

            field.addAncestorListener(new AncestorListener() {
                @Override
                public void ancestorMoved(final AncestorEvent ancestorEvent) {
                }

                @Override
                public void ancestorRemoved(final AncestorEvent ancestorEvent) {
                }

                @Override
                public void ancestorAdded(final AncestorEvent ancestorEvent) {
                    final Container ancestor = ancestorEvent.getAncestor();

                    if (ancestor instanceof JDialog) {
                        SemImGraphicalEditor.this.dialog = ancestor;
                    }

                    field.selectAll();
                    field.grabFocus();
                }
            });

            field.addActionListener((e) -> {
                if (this.dialog != null) {
                    this.dialog.setVisible(false);
                }
            });

            JOptionPane.showMessageDialog(this.workbench.getComponent(edge), box, "Coefficient for " + edge, JOptionPane.PLAIN_MESSAGE);

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

        private void beginNodeEdit(final Node node) {
            finishEdit();

            if (!isEditable()) {
                return;
            }

//        if (!semIm().getSemPm().getGraph().isParameterizable(node)) {
//            return;
//        }
            final Parameter parameter = getNodeParameter(node);
            if (this.editor.isEditCovariancesAsCorrelations()
                    && parameter.getType() == ParamType.VAR) {
                return;
            }

            final double d;
            final String prefix;
            String postfix = "";

            if (parameter.getType() == ParamType.MEAN) {
                if (this.editor.isEditIntercepts()) {
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
            field.setFilter((value, oldValue) -> {
                try {
                    setNodeValue(node, new Double(value).toString());
                    return value;
                } catch (final IllegalArgumentException e) {
                    return oldValue;
                }
            });

            final Box box = Box.createHorizontalBox();
            box.add(Box.createHorizontalGlue());
            box.add(new JLabel("New value: "));
            box.add(field);
            box.add(Box.createHorizontalGlue());

            field.addAncestorListener(new AncestorListener() {
                @Override
                public void ancestorMoved(final AncestorEvent ancestorEvent) {
                }

                @Override
                public void ancestorRemoved(final AncestorEvent ancestorEvent) {
                }

                @Override
                public void ancestorAdded(final AncestorEvent ancestorEvent) {
                    final Container ancestor = ancestorEvent.getAncestor();

                    if (ancestor instanceof JDialog) {
                        SemImGraphicalEditor.this.dialog = ancestor;
                    }
                }
            });

            field.addActionListener((e) -> {
                if (this.dialog != null) {
                    this.dialog.setVisible(false);
                }
            });

            final String s;

            if (parameter.getType() == ParamType.MEAN) {
                if (this.editor.isEditIntercepts()) {
                    s = "Intercept for " + node;
                } else {
                    s = "Mean for " + node;
                }
            } else {
                s = "Standard Deviation for " + node;
            }

            JOptionPane.showMessageDialog(this.workbench.getComponent(node), box, s, JOptionPane.PLAIN_MESSAGE);

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
                resetLabels();
            }
        }

        private ISemIm semIm() {
            return this.wrapper.getSemIm();
        }

        private Graph graph() {
            return this.wrapper.getSemIm().getSemPm().getGraph();
        }

        private GraphWorkbench workbench() {
            if (this.getWorkbench() == null) {
                this.workbench = new GraphWorkbench(graph());
                this.workbench.enableEditing(false);
                this.getWorkbench().setAllowDoubleClickActions(false);
                this.getWorkbench().addPropertyChangeListener((evt) -> {
                    if ("BackgroundClicked".equals(
                            evt.getPropertyName())) {
                        finishEdit();
                    }
                });
                resetLabels();
                addMouseListenerToGraphNodesMeasured();
            }
            return getWorkbench();
        }

        private void setLastEditedObject(final Object o) {
            this.lastEditedObject = o;
        }

        private Object lastEditedObject() {
            return this.lastEditedObject;
        }

        public void resetLabels() {
            final Matrix implCovar = semIm().getImplCovar(false);

            for (final Object o : graph().getEdges()) {
                resetEdgeLabel((Edge) (o), implCovar);
            }

            final List<Node> nodes = graph().getNodes();

            for (final Object node : nodes) {
                resetNodeLabel((Node) node, implCovar);
            }

            workbench().repaint();
        }

        private void resetEdgeLabel(final Edge edge, final Matrix implCovar) {
            final Parameter parameter = getEdgeParameter(edge);

            if (parameter != null) {
                double val = semIm().getParamValue(parameter);
                double standardError;

                try {
                    standardError = semIm().getStandardError(parameter,
                            this.maxFreeParamsForStatistics);
                } catch (final Exception exception) {
                    standardError = Double.NaN;
                }

                double tValue;
                try {
                    tValue = semIm().getTValue(parameter, this.maxFreeParamsForStatistics);
                } catch (final Exception exception) {
                    tValue = Double.NaN;
                }

                double pValue;

                try {
                    pValue = semIm().getPValue(parameter, this.maxFreeParamsForStatistics);
                } catch (final Exception exception) {
                    pValue = Double.NaN;
                }

                if (this.editor.isEditCovariancesAsCorrelations()
                        && parameter.getType() == ParamType.COVAR) {
                    final Node nodeA = edge.getNode1();
                    final Node nodeB = edge.getNode2();

                    final double varA = semIm().getVariance(nodeA, implCovar);
                    final double varB = semIm().getVariance(nodeB, implCovar);

                    val /= Math.sqrt(varA * varB);
                }

                final JLabel label = new JLabel();

                if (parameter.getType() == ParamType.COVAR) {
                    label.setForeground(Color.GREEN.darker().darker());
                }

                if (parameter.isFixed()) {
                    label.setForeground(Color.RED);
                }

                label.setBackground(Color.white);
                label.setOpaque(true);
                label.setFont(this.SMALL_FONT);

                label.setText(" " + asString(val) + " ");

                label.setToolTipText(parameter.getName() + " = " + asString(val));
                label.addMouseListener(new EdgeMouseListener(edge, this));
                if (!Double.isNaN(standardError) && semIm().isEstimated()) {
                    label.setToolTipText("SE=" + asString(standardError) + ", T="
                            + asString(tValue) + ", P=" + asString(pValue));
                }

                workbench().setEdgeLabel(edge, label);
            } else {
                workbench().setEdgeLabel(edge, null);
            }
        }

        private void resetNodeLabel(final Node node, final Matrix implCovar) {
            if (!semIm().getSemPm().getGraph().isParameterizable(node)) {
                return;
            }

            final Parameter parameter = semIm().getSemPm().getVarianceParameter(node);
            double meanOrIntercept = Double.NaN;

            final JLabel label = new JLabel();
            label.setBackground(Color.WHITE);
            label.addMouseListener(new NodeMouseListener(node, this));
            label.setFont(this.SMALL_FONT);

            String tooltip = "";
            final NodeType nodeType = node.getNodeType();

            if (nodeType == NodeType.MEASURED || nodeType == NodeType.LATENT) {
                if (this.editor.isEditIntercepts()) {
                    meanOrIntercept = semIm().getIntercept(node);
                } else {
                    meanOrIntercept = semIm().getMean(node);
                }
            }

            double stdDev = semIm().getStdDev(node, implCovar);

            if (this.editor.isEditCovariancesAsCorrelations() && !Double.isNaN(stdDev)) {
                stdDev = 1.0;
            }

            if (parameter != null) {
                final double standardError = semIm().getStandardError(parameter,
                        this.maxFreeParamsForStatistics);
                final double tValue
                        = semIm().getTValue(parameter, this.maxFreeParamsForStatistics);
                final double pValue
                        = semIm().getPValue(parameter, this.maxFreeParamsForStatistics);

                tooltip = "SE=" + asString(standardError) + ", T="
                        + asString(tValue) + ", P=" + asString(pValue);
            }

            if (!Double.isNaN(meanOrIntercept)) {
                label.setForeground(Color.GREEN.darker());
                label.setText(asString(meanOrIntercept));

                if (this.editor.isEditIntercepts()) {
                    tooltip = "<html>" + "B0_" + node.getName() + " = "
                            + asString(meanOrIntercept) + "</html>";
                } else {
                    tooltip = "<html>" + "Mean(" + node.getName() + ") = "
                            + asString(meanOrIntercept) + "</html>";
                }
            } else if (!this.editor.isEditCovariancesAsCorrelations()
                    && !Double.isNaN(stdDev)) {
                label.setForeground(Color.BLUE);
                label.setText(asString(stdDev));

                tooltip = "<html>" + node.getName() + " ~ N(0," + asString(stdDev)
                        + ")" + "<br><br>" + tooltip + "</html>";

            } else if (this.editor.isEditCovariancesAsCorrelations()) {
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

        private Parameter getNodeParameter(final Node node) {
            Parameter parameter = semIm().getSemPm().getMeanParameter(node);

            if (parameter == null) {
                parameter = semIm().getSemPm().getVarianceParameter(node);
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
        private Parameter getEdgeParameter(final Edge edge) {
            if (Edges.isDirectedEdge(edge)) {
                return semIm().getSemPm().getCoefficientParameter(edge.getNode1(), edge.getNode2());
            } else if (Edges.isBidirectedEdge(edge)) {
                return semIm().getSemPm().getCovarianceParameter(edge.getNode1(), edge.getNode2());
            }

            throw new IllegalArgumentException(
                    "This is not a directed or bidirected edge: " + edge);
        }

        private void setEdgeValue(final Edge edge, final String text) {
            try {
                final Parameter parameter = getEdgeParameter(edge);
                double d = new Double(text);

                if (this.editor.isEditCovariancesAsCorrelations()
                        && parameter.getType() == ParamType.COVAR) {
                    final Node nodeA = edge.getNode1();
                    final Node nodeB = edge.getNode2();

                    final Matrix implCovar = semIm().getImplCovar(false);

                    final double varA = semIm().getVariance(nodeA, implCovar);
                    final double varB = semIm().getVariance(nodeB, implCovar);

                    d *= Math.sqrt(varA * varB);

                    semIm().setParamValue(parameter, d);
                    this.firePropertyChange("modelChanged", null, null);
                } else if (!this.editor.isEditCovariancesAsCorrelations()
                        && parameter.getType() == ParamType.COVAR) {
                    semIm().setParamValue(parameter, d);
                    this.firePropertyChange("modelChanged", null, null);
                } else if (parameter.getType() == ParamType.COEF) {
//                semIm().setParamValue(parameter, d);
//                firePropertyChange("modelChanged", null, null);

                    final Node x = parameter.getNodeA();
                    final Node y = parameter.getNodeB();

                    semIm().setEdgeCoef(x, y, d);

                    if (this.editor.isEditIntercepts()) {
                        final double intercept = semIm().getIntercept(y);
                        semIm().setIntercept(y, intercept);
                    }

                    this.firePropertyChange("modelChanged", null, null);
                }
            } catch (final NumberFormatException e) {
                // Let the old value be reinstated.
            }

            resetLabels();
            workbench().repaint();
            setLastEditedObject(null);
        }

        private void setNodeValue(final Node node, final String text) {
            try {
                final Parameter parameter = getNodeParameter(node);
                final double d = new Double(text);

                if (parameter.getType() == ParamType.VAR && d >= 0) {
                    semIm().setParamValue(node, node, d * d);
                    this.firePropertyChange("modelChanged", null, null);
                } else if (parameter.getType() == ParamType.MEAN) {
                    if (this.editor.isEditIntercepts()) {
                        semIm().setIntercept(node, d);
                    } else {
                        semIm().setMean(node, d);
                    }

                    this.firePropertyChange("modelChanged", null, null);
                }
            } catch (final Exception exception) {
                exception.printStackTrace(System.err);
                // Let the old value be reinstated.
            }

            resetLabels();
            workbench().repaint();
            setLastEditedObject(null);
        }

        private int getSavedTooltipDelay() {
            return this.savedTooltipDelay;
        }

        private void setSavedTooltipDelay(final int savedTooltipDelay) {
            if (this.savedTooltipDelay == 0) {
                this.savedTooltipDelay = savedTooltipDelay;
            }
        }

        private String asString(final double value) {
            final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

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
            final List nodes = graph().getNodes();

            for (final Object node : nodes) {
                final Object displayNode = workbench().getModelNodesToDisplay().get(node);

                if (displayNode instanceof GraphNodeMeasured) {
                    final DisplayNode _displayNode = (DisplayNode) displayNode;
                    _displayNode.setToolTipText(
                            getEquationOfNode(_displayNode.getModelNode())
                    );
                }
            }
        }

        private String getEquationOfNode(final Node node) {
            String eqn = node.getName() + " = B0_" + node.getName();

            final SemGraph semGraph = semIm().getSemPm().getGraph();
            final List parentNodes = semGraph.getParents(node);

            for (final Object parentNodeObj : parentNodes) {
                final Node parentNode = (Node) parentNodeObj;
//            Parameter edgeParam = semIm().getEstIm().getEdgeParameter(
//                    semGraph.getEdge(parentNode, node));
                final Parameter edgeParam = getEdgeParameter(
                        semGraph.getDirectedEdge(parentNode, node));

                if (edgeParam != null) {
                    eqn = eqn + " + " + edgeParam.getName() + "*" + parentNode;
                }
            }

            eqn = eqn + " + " + semIm().getSemPm().getGraph().getExogenous(node);

            return eqn;
        }

        public GraphWorkbench getWorkbench() {
            return this.workbench;
        }

        private boolean isEditable() {
            return this.editable;
        }

        public void setEditable(final boolean editable) {
            workbench().setAllowEdgeReorientations(editable);
//        workbench().setAllowMultipleSelection(editable);
//        workbench().setAllowNodeDragging(false);
            workbench().setAllowDoubleClickActions(editable);
            workbench().setAllowNodeEdgeSelection(editable);
            this.editable = editable;
        }

        final class EdgeMouseListener extends MouseAdapter {

            private final Edge edge;
            private final SemImGraphicalEditor editor;

            public EdgeMouseListener(final Edge edge, final SemImGraphicalEditor editor) {
                this.edge = edge;
                this.editor = editor;
            }

            private Edge getEdge() {
                return this.edge;
            }

            private SemImGraphicalEditor getEditor() {
                return this.editor;
            }

            public void mouseClicked(final MouseEvent e) {
                getEditor().beginEdgeEdit(getEdge());
            }
        }

        final class NodeMouseListener extends MouseAdapter {

            private final Node node;
            private final SemImGraphicalEditor editor;

            public NodeMouseListener(final Node node, final SemImGraphicalEditor editor) {
                this.node = node;
                this.editor = editor;
            }

            private Node getNode() {
                return this.node;
            }

            private SemImGraphicalEditor getEditor() {
                return this.editor;
            }

            @Override
            public void mouseClicked(final MouseEvent e) {
                getEditor().beginNodeEdit(getNode());
            }
        }

        final class EdgeActionListener implements ActionListener {

            private final SemImGraphicalEditor editor;
            private final Edge edge;

            public EdgeActionListener(final SemImGraphicalEditor editor, final Edge edge) {
                this.editor = editor;
                this.edge = edge;
            }

            @Override
            public void actionPerformed(final ActionEvent ev) {
                final DoubleTextField doubleTextField = (DoubleTextField) ev.getSource();
                final String s = doubleTextField.getText();
                getEditor().setEdgeValue(getEdge(), s);
            }

            private SemImGraphicalEditor getEditor() {
                return this.editor;
            }

            private Edge getEdge() {
                return this.edge;
            }
        }

        final class NodeActionListener implements ActionListener {

            private final SemImGraphicalEditor editor;
            private final Node node;

            public NodeActionListener(final SemImGraphicalEditor editor, final Node node) {
                this.editor = editor;
                this.node = node;
            }

            @Override
            public void actionPerformed(final ActionEvent ev) {
                final DoubleTextField doubleTextField = (DoubleTextField) ev.getSource();
                final String s = doubleTextField.getText();
                getEditor().setNodeValue(getNode(), s);
            }

            private SemImGraphicalEditor getEditor() {
                return this.editor;
            }

            private Node getNode() {
                return this.node;
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

        public SemImTabularEditor(final SemImWrapper wrapper, final SemImEditor.OneEditor editor,
                                  final int maxFreeParamsForStatistics) {
            this.wrapper = wrapper;
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
//        setBorder(new TitledBorder("Click parameter values to edit"));

            if (semIm().isEstimated()) {
                setBorder(new TitledBorder("Null hypothesis for T and P is that the parameter is zero"));
            } else {
                setBorder(new TitledBorder("Click parameter values to edit"));
            }

            final JTable table = new JTable() {
                private static final long serialVersionUID = -530774590911763214L;

                @Override
                public TableCellEditor getCellEditor(final int row, final int col) {
                    return new DataCellEditor();
                }
            };
            this.tableModel = new ParamTableModel(wrapper, editor, maxFreeParamsForStatistics);
            table.setModel(getTableModel());
            this.tableModel.addTableModelListener((e) -> {
                this.firePropertyChange("modelChanged", null, null);
            });

            add(new JScrollPane(table), BorderLayout.CENTER);
        }

        private ISemIm semIm() {
            return this.wrapper.getSemIm();
        }

        public ParamTableModel getTableModel() {
            return this.tableModel;
        }

        public void setEditable(final boolean editable) {
            this.tableModel.setEditable(editable);
            this.editable = editable;
            this.tableModel.fireTableStructureChanged();
        }

        public boolean isEditable() {
            return this.editable;
        }
    }

    final class ParamTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 2210883212769846304L;

        private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        private final SemImWrapper wrapper;
        private SemImEditor.OneEditor editor = null;
        private int maxFreeParamsForStatistics = 50;
        private boolean editable = true;

        public ParamTableModel(final SemImWrapper wrapper, final SemImEditor.OneEditor editor,
                               final int maxFreeParamsForStatistics) {
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
            final int numNodes = semIm().getVariableNodes().size();
            return semIm().getNumFreeParams() + semIm().getFixedParameters().size() + numNodes;
        }

        @Override
        public int getColumnCount() {
            return 7;
        }

        @Override
        public String getColumnName(final int column) {
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
        public Object getValueAt(final int row, final int column) {
            final List nodes = semIm().getVariableNodes();
            final List parameters = new ArrayList<>(semIm().getFreeParameters());
            parameters.addAll(semIm().getFixedParameters());

            final int numParams = semIm().getNumFreeParams() + semIm().getFixedParameters().size();

            if (row < numParams) {
                final Parameter parameter = ((Parameter) parameters.get(row));

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
                                    this.maxFreeParamsForStatistics));
                        }
                    case 5:
                        if (parameter.isFixed()) {
                            return "*";
                        } else {
                            return asString(semIm().getTValue(parameter,
                                    this.maxFreeParamsForStatistics));
                        }
                    case 6:
                        if (parameter.isFixed()) {
                            return "*";
                        } else {
                            return asString(semIm().getPValue(parameter,
                                    this.maxFreeParamsForStatistics));
                        }
                }
            } else if (row < numParams + nodes.size()) {
                final int index = row - numParams;
                final Node node = semIm().getVariableNodes().get(index);
                final int n = semIm().getSampleSize();
                final int df = n - 1;
                final double mean = semIm().getMean(node);
                final double stdDev = semIm().getMeanStdDev(node);
                final double stdErr = stdDev / Math.sqrt(n);
//            double tValue = mean * Math.sqrt(n - 1) / stdDev;

                final double tValue = mean / stdErr;
                final double p = 2.0 * (1.0 - ProbUtils.tCdf(Math.abs(tValue), df));

//            double ar = covars.get(index, index);
//            stdDev = Math.sqrt(ar);
//            n = semIm().getSampleSize();
                switch (column) {
                    case 0:
                        return nodes.get(index);
                    case 1:
                        return nodes.get(index);
                    case 2:
                        if (this.editor.isEditIntercepts()) {
                            return "Intercept";
                        } else {
                            return "Mean";
                        }
                    case 3:
                        if (this.editor.isEditIntercepts()) {
                            final double intercept = semIm().getIntercept(node);
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

        private double paramValue(final Parameter parameter) {
            double paramValue = semIm().getParamValue(parameter);

            if (this.editor.isEditCovariancesAsCorrelations()) {
                if (parameter.getType() == ParamType.VAR) {
                    paramValue = 1.0;
                }
                if (parameter.getType() == ParamType.COVAR) {
                    final Node nodeA = parameter.getNodeA();
                    final Node nodeB = parameter.getNodeB();

                    final double varA = semIm().getParamValue(nodeA, nodeA);
                    final double varB = semIm().getParamValue(nodeB, nodeB);

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
        public boolean isCellEditable(final int rowIndex, final int columnIndex) {
            return isEditable() && columnIndex == 3;
        }

        private boolean isEditable() {
            return this.editable;
        }

        @Override
        public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {

            if (columnIndex == 3) {
                try {
                    double value = Double.parseDouble((String) aValue);

                    if (rowIndex < semIm().getNumFreeParams()) {
                        final Parameter parameter = semIm().getFreeParameters().get(rowIndex);

                        if (parameter.getType() == ParamType.VAR) {
                            value = value * value;
                            semIm().setErrVar(parameter.getNodeA(), value);
                        } else if (parameter.getType() == ParamType.COEF) {
                            final Node x = parameter.getNodeA();
                            final Node y = parameter.getNodeB();

                            semIm().setEdgeCoef(x, y, value);

                            final double intercept = semIm().getIntercept(y);

                            if (this.editor.isEditIntercepts()) {
                                semIm().setIntercept(y, intercept);
                            }
                        }

                        this.editor.firePropertyChange("modelChanged", 0, 0);

//                    if (semIm().getParamValue(parameter) != value) {
//                        semIm().setParamValue(parameter, value);
//                        editor.firePropertyChange("modelChanged", 0, 0);
//                    }
                    } else {
                        final int index = rowIndex - semIm().getNumFreeParams();
                        final Node node = semIm().getVariableNodes().get(index);

                        if (semIm().getMean(semIm().getVariableNodes().get(index)) != value) {
                            if (this.editor.isEditIntercepts()) {
                                semIm().setIntercept(node, value);
                                this.editor.firePropertyChange("modelChanged", 0, 0);
                            } else {
                                semIm().setMean(node, value);
                                this.editor.firePropertyChange("modelChanged", 0, 0);
                            }

                        }
                    }
                } catch (final Exception exception) {
                    // The old value will be reinstated automatically.
                }

                fireTableDataChanged();
            }
        }

        private String asString(final double value) {
            if (Double.isNaN(value)) {
                return " * ";
            } else {
                return this.nf.format(value);
            }
        }

        private String typeString(final Parameter parameter) {
            final ParamType type = parameter.getType();

            if (type == ParamType.COEF) {
                return "Edge Coef.";
            }

            if (this.editor.isEditCovariancesAsCorrelations()) {
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
            return this.wrapper.getSemIm();
        }

        public void setEditable(final boolean editable) {
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
        private int matrixSelection = 0;
        private JComboBox selector;

        public ImpliedMatricesPanel(final SemImWrapper wrapper, final int matrixSelection) {
            this.wrapper = wrapper;

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
            final StringBuilder builder = new StringBuilder();
            final TableModel model = impliedJTable().getModel();
            for (int row = 0; row < model.getRowCount(); row++) {
                for (int col = 0; col < model.getColumnCount(); col++) {
                    final Object o = model.getValueAt(row, col);
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
            if (this.selector == null) {
                this.selector = new JComboBox();
                final List<String> selections = getImpliedSelections();

                for (final Object selection : selections) {
                    this.selector.addItem(selection);
                }

                this.selector.addItemListener((e) -> {
                    final String item = (String) e.getItem();
                    setMatrixSelection(getImpliedSelections().indexOf(item));
                });
            }
            return this.selector;
        }

        public void setMatrixSelection(final int index) {
            selector().setSelectedIndex(index);
            switchView(index);
        }

        private void switchView(final int index) {
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

        private void switchView(final boolean a, final boolean b) {
            impliedJTable().setModel(new ImpliedCovTable(this.wrapper, a, b));
            //     impliedJTable().getTableHeader().setReorderingAllowed(false);
            impliedJTable().setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            impliedJTable().setRowSelectionAllowed(false);
            impliedJTable().setCellSelectionEnabled(false);
            impliedJTable().doLayout();
        }

        private List<String> getImpliedSelections() {
            final List<String> list = new ArrayList<>();
            list.add("Implied covariance matrix (all variables)");
            list.add("Implied covariance matrix (measured variables only)");
            list.add("Implied correlation matrix (all variables)");
            list.add("Implied correlation matrix (measured variables only)");
            return list;
        }

        private ISemIm getSemIm() {
            return this.wrapper.getSemIm();
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
        public ImpliedCovTable(final SemImWrapper wrapper, final boolean measured,
                               final boolean correlations) {
            this.wrapper = wrapper;
            this.measured = measured;
            this.correlations = correlations;

            this.nf = NumberFormatUtil.getInstance().getNumberFormat();

            if (measured() && covariances()) {
                this.matrix = getSemIm().getImplCovarMeas().toArray();
            } else if (measured() && !covariances()) {
                this.matrix = corr(getSemIm().getImplCovarMeas().toArray());
            } else if (!measured() && covariances()) {
                final Matrix implCovarC = getSemIm().getImplCovar(false);
                this.matrix = implCovarC.toArray();
            } else if (!measured() && !covariances()) {
                final Matrix implCovarC = getSemIm().getImplCovar(false);
                this.matrix = corr(implCovarC.toArray());
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
            if (measured()) {
                return this.getSemIm().getMeasuredNodes().size() + 1;
            } else {
                return this.getSemIm().getVariableNodes().size() + 1;
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
            if (measured()) {
                return this.getSemIm().getMeasuredNodes().size() + 1;
            } else {
                return this.getSemIm().getVariableNodes().size() + 1;
            }
        }

        /**
         * @return the name of the column at columnIndex, which is "" for column
         * 0 and the name of the variable for the other columns.
         */
        @Override
        public String getColumnName(final int columnIndex) {
            if (columnIndex == 0) {
                return "";
            } else {
                if (measured()) {
                    final List nodes = getSemIm().getMeasuredNodes();
                    final Node node = ((Node) nodes.get(columnIndex - 1));
                    return node.getName();
                } else {
                    final List nodes = getSemIm().getVariableNodes();
                    final Node node = ((Node) nodes.get(columnIndex - 1));
                    return node.getName();
                }
            }
        }

        /**
         * @return the value being displayed in a cell, either a variable name
         * or a Double.
         */
        @Override
        public Object getValueAt(final int rowIndex, final int columnIndex) {
            if (rowIndex == 0) {
                return getColumnName(columnIndex);
            }
            if (columnIndex == 0) {
                return getColumnName(rowIndex);
            } else if (rowIndex < columnIndex) {
                return null;
            } else {
                return this.nf.format(this.matrix[rowIndex - 1][columnIndex - 1]);
            }
        }

        private boolean covariances() {
            return !correlations();
        }

        private double[][] corr(final double[][] implCovar) {
            final int length = implCovar.length;
            final double[][] corr = new double[length][length];

            for (int i = 1; i < length; i++) {
                for (int j = 0; j < i; j++) {
                    final double d1 = implCovar[i][j];
                    final double d2 = implCovar[i][i];
                    final double d3 = implCovar[j][j];
                    final double d4 = d1 / Math.pow(d2 * d3, 0.5);

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
            return this.measured;
        }

        /**
         * @return true iff correlations (rather than covariances) are
         * displayed.
         */
        private boolean correlations() {
            return this.correlations;
        }

        private ISemIm getSemIm() {
            return this.wrapper.getSemIm();
        }
    }

    final class ModelStatisticsPanel extends JTextArea {

        private static final long serialVersionUID = -9096723049787232471L;

        private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        private final SemImWrapper wrapper;

        public ModelStatisticsPanel(final SemImWrapper wrapper) {
            this.wrapper = wrapper;
            reset();

            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentShown(final ComponentEvent e) {
                    reset();
                }
            });
        }

        private void reset() {
            setText("");
            setLineWrap(true);
            setWrapStyleWord(true);

            final double modelChiSquare;
            final double modelDof;
            final double modelPValue;

            final SemPm semPm = semIm().getSemPm();
            final List<Node> variables = semPm.getVariableNodes();
            boolean containsLatent = false;
            for (final Node node : variables) {
                if (node.getNodeType() == NodeType.LATENT) {
                    containsLatent = true;
                    break;
                }
            }

            try {
                modelChiSquare = semIm().getChiSquare();
                modelDof = semIm().getSemPm().getDof();
                modelPValue = semIm().getPValue();
            } catch (final Exception exception) {
                append("Model statistics not available.");
                return;
            }

            if (containsLatent) {
                append("\nEstimated degrees of Freedom = " + (int) modelDof);
            } else {
                append("\nDegrees of Freedom = " + (int) modelDof);
            }
//        append("\n(If the model is latent, this is the estimated degrees of freedom.)");

            append("\nChi Square = " + this.nf.format(modelChiSquare));

            if (modelDof >= 0) {
                final String pValueString = modelPValue > 0.001 ? this.nf.format(modelPValue)
                        : new DecimalFormat("0.0000E0").format(modelPValue);
                append("\nP Value = " + (Double.isNaN(modelPValue) || modelDof == 0 ? "undefined" : pValueString));
                append("\nBIC Score = " + this.nf.format(semIm().getBicScore()));
                append("\nCFI = " + this.nf.format(semIm().getCfi()));
                append("\nRMSEA = " + this.nf.format(semIm().getRmsea()));

//            append("\n(Experimental!) KIC Score = " + nf.format(semIm().getKicScore()));
//            append("\n\nThe null hypothesis for the above chi square test is that " +
//                    "the population covariance matrix over all variables (sigma) " +
//                    "is equal to the covariance matrix, over the same variables, " +
//                    "written as a function of the free model parameters (Bollen, " +
//                    "Structural Equations with Latent Variables, 110).");
            } else {
                final int numToFix = (int) Math.abs(modelDof);
                append("\n\nA SEM with negative degrees of freedom is underidentified, "
                        + "\nand other model statistics are meaningless.  Please increase "
                        + "\nthe degrees of freedom to 0 or above by fixing at least "
                        + numToFix + " parameter" + (numToFix == 1 ? "." : "s."));
            }

            append("\n\nThe above chi square test assumes that the maximum "
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
            return this.wrapper.getSemIm();
        }
    }

    private Component getComp() {
        final EditorWindow editorWindow =
                (EditorWindow) SwingUtilities.getAncestorOfClass(
                        EditorWindow.class, this);

        if (editorWindow != null) {
            return editorWindow.getRootPane().getContentPane();
        } else {
            return editorWindow;
        }
    }

}
