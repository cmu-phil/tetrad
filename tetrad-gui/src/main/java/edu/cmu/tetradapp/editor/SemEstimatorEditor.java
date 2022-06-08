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
package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.model.EditorUtils;
import edu.cmu.tetradapp.model.SemEstimatorWrapper;
import edu.cmu.tetradapp.util.*;
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
 * Lets the user interact with a SEM estimator.
 *
 * @author Joseph Ramsey
 */
public final class SemEstimatorEditor extends JPanel {

    private static final long serialVersionUID = 960988184083427499L;

    private final JPanel targetPanel;
    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    private final DataSet dataSet;
    private final SemEstimatorWrapper wrapper;
    private OneEditor oneEditorPanel;

    private final String graphicalEditorTitle = "Graphical Editor";
    private final String tabularEditorTitle = "Tabular Editor";
    private final boolean editable = true;


    public SemEstimatorEditor(SemIm semIm, DataSet dataSet) {
        this(new SemEstimatorWrapper(dataSet, semIm.getSemPm(), new Parameters()));
    }


    public SemEstimatorEditor(SemPm semPm, DataSet dataSet) {
        this(new SemEstimatorWrapper(dataSet, semPm, new Parameters()));
    }

    public SemEstimatorEditor(SemEstimatorWrapper wrapper) {
        setLayout(new BorderLayout());
        this.targetPanel = new JPanel();
        this.targetPanel.setLayout(new BorderLayout());
        add(this.targetPanel, BorderLayout.CENTER);

        this.wrapper = wrapper;
        this.dataSet = wrapper.getSemEstimator().getDataSet();

        this.oneEditorPanel = new OneEditor(wrapper, this.graphicalEditorTitle, this.tabularEditorTitle, TabbedPaneDefault.GRAPHICAL);
        this.targetPanel.add(this.oneEditorPanel, BorderLayout.CENTER);


        JComboBox<Object> optimizerCombo = new JComboBox<>();
        optimizerCombo.addItem("Regression");
        optimizerCombo.addItem("EM");
        optimizerCombo.addItem("Powell");
        optimizerCombo.addItem("Random Search");
        optimizerCombo.addItem("RICF");

        optimizerCombo.addActionListener((e) -> {
            JComboBox box = (JComboBox) e.getSource();
            wrapper.setSemOptimizerType((String) box.getSelectedItem());
        });

        JComboBox<Object> scoreBox = new JComboBox();
        IntTextField restarts = new IntTextField(1, 2);

        scoreBox.addItem("Fgls");
        scoreBox.addItem("Fml");

        scoreBox.addActionListener((e) -> {
            JComboBox box = (JComboBox) e.getSource();
            String type = (String) box.getSelectedItem();
            if ("Fgls".equals(type)) {
                wrapper.setScoreType(ScoreType.Fgls);
            } else if ("Fml".equals(type)) {
                wrapper.setScoreType(ScoreType.Fml);
            }
        });

        restarts.setFilter((value, oldValue) -> {
            try {
                wrapper.setNumRestarts(value);
                return value;
            } catch (Exception e) {
                return oldValue;
            }
        });

        String semOptimizerType = wrapper.getParams().getString("semOptimizerType", "Regression");

        optimizerCombo.setSelectedItem(semOptimizerType);
        ScoreType scoreType = (ScoreType) wrapper.getParams().get("scoreType", ScoreType.Fgls);
        if (scoreType == null) {
            scoreType = ScoreType.Fgls;
        }
        scoreBox.setSelectedItem(scoreType.toString());
        restarts.setValue(wrapper.getParams().getInt("numRestarts", 1));

        JButton estimateButton = new JButton("Estimate Again");

        estimateButton.addActionListener((e) -> {
            Window owner = (Window) getTopLevelAncestor();

            new WatchedProcess(owner) {
                @Override
                public void watch() {
                    reestimate();
                }
            };
        });

        JButton report = new JButton("Report");

        report.addActionListener((e) -> {
            JTextArea textArea = new JTextArea();
            JScrollPane scroll = new JScrollPane(textArea);

            textArea.append(compileReport());

            Box b = Box.createVerticalBox();
            Box b2 = Box.createHorizontalBox();
            b2.add(scroll);
            textArea.setCaretPosition(0);
            b.add(b2);

            JPanel editorPanel = new JPanel(new BorderLayout());
            editorPanel.add(b);

            EditorWindow window = new EditorWindow(editorPanel,
                    "All Paths", "Close", false, this);
            DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
            window.setVisible(true);
        });

        Box lowerBarA = Box.createHorizontalBox();
        lowerBarA.add(new JLabel("Score"));
        lowerBarA.add(scoreBox);
        lowerBarA.add(Box.createHorizontalGlue());
        lowerBarA.add(new JLabel("Random Restarts"));
        lowerBarA.add(restarts);

        Box lowerBarB = Box.createHorizontalBox();
        lowerBarB.add(new JLabel("Choose Optimizer:  "));
        lowerBarB.add(optimizerCombo);
        lowerBarB.add(Box.createHorizontalGlue());
        lowerBarB.add(estimateButton);

        Box lowerBar = Box.createVerticalBox();
        lowerBar.add(lowerBarA);
        lowerBar.add(lowerBarB);

        add(lowerBar, BorderLayout.SOUTH);

        resetSemImEditor();
    }

    private String compileReport() {
        StringBuilder builder = new StringBuilder();

        builder.append("Datset\tFrom\tTo\tType\tValue\tSE\tT\tP");

//       Maximum number of free parameters for which statistics will
//       be calculated. (Calculating standard errors is high
//       complexity.) Set this to zero to turn off statistics
//       calculations (which can be problematic sometimes).
        SemIm estSem = this.wrapper.getEstimatedSemIm();
        String dataName = this.dataSet.getName();

        estSem.getFreeParameters().forEach(parameter -> {
            builder.append("\n");
            builder.append(dataName).append("\t");
            builder.append(parameter.getNodeA()).append("\t");
            builder.append(parameter.getNodeB()).append("\t");
            builder.append(typeString(parameter)).append("\t");
            builder.append(asString(paramValue(estSem, parameter))).append("\t");

//            Maximum number of free parameters for which statistics will
//            be calculated.(Calculating standard errors is high
//            complexity.)Set this to zero to turn off statistics
//            calculations (which can be problematic sometimes).
            final int maxFreeParamsForStatistics = 200;
            builder.append(asString(estSem.getStandardError(parameter,
                    maxFreeParamsForStatistics))).append("\t");
            builder.append(asString(estSem.getTValue(parameter,
                    maxFreeParamsForStatistics))).append("\t");
            builder.append(asString(estSem.getPValue(parameter,
                    maxFreeParamsForStatistics))).append("\t");
        });

        List<Node> nodes = estSem.getVariableNodes();

        nodes.forEach(node -> {
            int n = estSem.getSampleSize();
            int df = n - 1;
            double mean = estSem.getMean(node);
            double stdDev = estSem.getMeanStdDev(node);
            double stdErr = stdDev / Math.sqrt(n);
            double tValue = mean / stdErr;
            double p = 2.0 * (1.0 - ProbUtils.tCdf(Math.abs(tValue), df));
            builder.append("\n");
            builder.append(dataName).append("\t");
            builder.append(node).append("\t");
            builder.append(node).append("\t");
            builder.append("Mean").append("\t");
            builder.append(asString(mean)).append("\t");
            builder.append(asString(stdErr)).append("\t");
            builder.append(asString(tValue)).append("\t");
            builder.append(asString(p)).append("\t");
        });

        return builder.toString();
    }

    private String asString(double value) {
        if (Double.isNaN(value)) {
            return " * ";
        } else {
            return this.nf.format(value);
        }
    }

    private String typeString(Parameter parameter) {
        ParamType type = parameter.getType();

        if (type == ParamType.COEF) {
            return "Coef";
        }

        if (type == ParamType.VAR) {
            //return "Variance";
            return "StdDev";
        }

        if (type == ParamType.COVAR) {
            return "Covar";
        }

        throw new IllegalStateException("Unknown param type.");
    }

    private double paramValue(SemIm im, Parameter parameter) {
        double paramValue = im.getParamValue(parameter);

        if (parameter.getType() == ParamType.VAR) {
            paramValue = Math.sqrt(paramValue);
        }

        return paramValue;
    }

    private void reestimate() {
        SemOptimizer optimizer;

        String type = this.wrapper.getSemOptimizerType();

        switch (type) {
            case "Regression":
                optimizer = new SemOptimizerRegression();
                break;
            case "EM":
                optimizer = new SemOptimizerEm();
                break;
            case "Powell":
                optimizer = new SemOptimizerPowell();
                break;
            case "Random Search":
                optimizer = new SemOptimizerScattershot();
                break;
            case "RICF":
                optimizer = new SemOptimizerRicf();
                break;
            default:
                throw new IllegalArgumentException("Unexpected optimizer type: "
                        + type);
        }

        int numRestarts = this.wrapper.getNumRestarts();
        optimizer.setNumRestarts(numRestarts);

        SemEstimator estimator = this.wrapper.getSemEstimator();
        estimator.setSemOptimizer(optimizer);

        estimator.setNumRestarts(numRestarts);
        estimator.setScoreType(this.wrapper.getScoreType());

        estimator.estimate();
        resetSemImEditor();
    }

    private void resetSemImEditor() {
        this.oneEditorPanel = new OneEditor(this.wrapper, this.graphicalEditorTitle, this.tabularEditorTitle, TabbedPaneDefault.GRAPHICAL);
        this.targetPanel.removeAll();
        this.targetPanel.add(this.oneEditorPanel, BorderLayout.CENTER);
        validate();
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

    public enum TabbedPaneDefault {
        GRAPHICAL, TABULAR, COVMATRIX, tabbedPanedDefault, STATS
    }

    private class OneEditor extends JPanel implements LayoutEditable {

        private static final long serialVersionUID = 6622060253747442717L;

        private final SemEstimatorWrapper semImWrapper;
        /**
         * Maximum number of free parameters for which statistics will be
         * calculated. (Calculating standard errors is high complexity.) Set
         * this to zero to turn off statistics calculations (which can be
         * problematic sometimes).
         */
        private final int maxFreeParamsForStatistics = 1000;
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
        private JCheckBoxMenuItem meansItem;
        private JCheckBoxMenuItem interceptsItem;
        private JMenuItem errorTerms;

        public OneEditor(SemEstimatorWrapper wrapper, String graphicalEditorTitle, String tabularEditorTitle,
                         TabbedPaneDefault tabbedPaneDefault) {
            this.semImWrapper = wrapper;
            this.graphicalEditorTitle = graphicalEditorTitle;
            this.tabularEditorTitle = tabularEditorTitle;
            displaySemIm(graphicalEditorTitle, tabularEditorTitle, tabbedPaneDefault);
        }

        private void displaySemIm(String graphicalEditorTitle, String tabularEditorTitle, TabbedPaneDefault tabbedPaneDefault) {
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

            SemEstimatorEditor.this.targetPanel.add(this.tabbedPane, BorderLayout.CENTER);

            JMenuBar menuBar = new JMenuBar();
            JMenu file = new JMenu("File");
            menuBar.add(file);
            file.add(new SaveComponentImage(this.semImGraphicalEditor.getWorkbench(),
                    "Save Graph Image..."));
            file.add(this.getCopyMatrixMenuItem());
            JMenuItem saveSemAsXml = new JMenuItem("Save SEM as XML");
            file.add(saveSemAsXml);

            saveSemAsXml.addActionListener(e -> {
                try {
                    File outfile = EditorUtils.getSaveFile("semIm", "xml", getComp(),
                            false, "Save SEM IM as XML...");

                    SemIm im = (SemIm) SemEstimatorEditor.this.oneEditorPanel.getSemIm();
                    FileOutputStream out = new FileOutputStream(outfile);

                    Element element = SemXmlRenderer.getElement(im);
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
            });

            this.meansItem = new JCheckBoxMenuItem("Show means");
            this.interceptsItem = new JCheckBoxMenuItem("Show intercepts");

            ButtonGroup meansGroup = new ButtonGroup();
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

            JMenu params = new JMenu("Parameters");
            params.add(this.errorTerms);
            params.addSeparator();
            params.add(covariances);
            params.add(correlations);
            params.addSeparator();

            if (!SemEstimatorEditor.this.wrapper.getEstimatedSemIm().isCyclic()) {
                params.add(this.meansItem);
                params.add(this.interceptsItem);
            }

            menuBar.add(params);
            menuBar.add(new LayoutMenu(this));

            SemEstimatorEditor.this.targetPanel.add(menuBar, BorderLayout.NORTH);
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
        public void layoutByGraph(Graph graph) {
            SemGraph _graph = (SemGraph) this.semImGraphicalEditor.getWorkbench().getGraph();
            _graph.setShowErrorTerms(false);
            this.semImGraphicalEditor.getWorkbench().layoutByGraph(graph);
            _graph.resetErrorPositions();
//        semImGraphicalEditor.getWorkbench().setGraph(_graph);
            this.errorTerms.setText("Show Error Terms");
        }

        @Override
        public void layoutByKnowledge() {
            SemGraph _graph = (SemGraph) this.semImGraphicalEditor.getWorkbench().getGraph();
            _graph.setShowErrorTerms(false);
            this.semImGraphicalEditor.getWorkbench().layoutByKnowledge();
            _graph.resetErrorPositions();
//        semImGraphicalEditor.getWorkbench().setGraph(_graph);
            this.errorTerms.setText("Show Error Terms");
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

        public GraphWorkbench getWorkbench() {
            return this.semImGraphicalEditor.getWorkbench();
        }

        //========================PRIVATE METHODS===========================//
        private JMenuItem getCopyMatrixMenuItem() {
            JMenuItem item = new JMenuItem("Copy Implied Covariance Matrix");
            item.addActionListener((e) -> {
                String s = this.impliedMatricesPanel.getMatrixInTabDelimitedForm();
                Clipboard board = Toolkit.getDefaultToolkit().getSystemClipboard();
                StringSelection selection = new StringSelection(s);
                board.setContents(selection, selection);
            });
            return item;
        }

        private ISemIm getSemIm() {
            return this.semImWrapper.getEstimatedSemIm();
        }

        private SemImGraphicalEditor graphicalEditor() {
            if (this.semImGraphicalEditor == null) {
                this.semImGraphicalEditor = new SemImGraphicalEditor(SemEstimatorEditor.this.wrapper,
                        this, this.maxFreeParamsForStatistics);
                this.semImGraphicalEditor.addPropertyChangeListener((evt) -> {
                    SemEstimatorEditor.this.firePropertyChange(evt.getPropertyName(), null, null);
                });
            }
            return this.semImGraphicalEditor;
        }

        private SemImTabularEditor tabularEditor() {
            if (this.semImTabularEditor == null) {
                this.semImTabularEditor = new SemImTabularEditor(SemEstimatorEditor.this.wrapper, this,
                        this.maxFreeParamsForStatistics);
            }
            this.semImTabularEditor.addPropertyChangeListener((evt) -> {
                SemEstimatorEditor.this.firePropertyChange(evt.getPropertyName(), null, null);
            });
            return this.semImTabularEditor;
        }

        private ImpliedMatricesPanel impliedMatricesPanel() {
            if (this.impliedMatricesPanel == null) {
                int matrixSelection = 0;
                this.impliedMatricesPanel
                        = new ImpliedMatricesPanel(SemEstimatorEditor.this.wrapper, matrixSelection);
            }
            return this.impliedMatricesPanel;
        }

        private ModelStatisticsPanel modelStatisticsPanel() {
            if (this.modelStatisticsPanel == null) {
                this.modelStatisticsPanel = new ModelStatisticsPanel(SemEstimatorEditor.this.wrapper);
            }
            return this.modelStatisticsPanel;
        }

        public boolean isEditCovariancesAsCorrelations() {
            return this.editCovariancesAsCorrelations;
        }

        private void setEditCovariancesAsCorrelations(
                boolean editCovariancesAsCorrelations) {
            this.editCovariancesAsCorrelations = editCovariancesAsCorrelations;
            graphicalEditor().resetLabels();
            tabularEditor().getTableModel().fireTableDataChanged();
        }

        public boolean isEditIntercepts() {
            return this.editIntercepts;
        }

        private void setEditIntercepts(boolean editIntercepts) {
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

        public void setEditable(boolean editable) {
            graphicalEditor().setEditable(editable);
            tabularEditor().setEditable(editable);
            this.editable = editable;
        }
    }

    /**
     * Dispays the implied covariance and correlation matrices for the given
     * SemIm.
     */
    static class ImpliedMatricesPanel extends JPanel {

        private static final long serialVersionUID = 2462316724126834072L;

        private final SemEstimatorWrapper wrapper;
        private JTable impliedJTable;
        private int matrixSelection;
        private JComboBox selector;

        public ImpliedMatricesPanel(SemEstimatorWrapper wrapper, int matrixSelection) {
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
            if (this.selector == null) {
                this.selector = new JComboBox();
                List<String> selections = getImpliedSelections();

                for (Object selection : selections) {
                    this.selector.addItem(selection);
                }

                this.selector.addItemListener((e) -> {
                    String item = (String) e.getItem();
                    setMatrixSelection(getImpliedSelections().indexOf(item));
                });
            }
            return this.selector;
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
            impliedJTable().setModel(new ImpliedCovTable(this.wrapper, a, b));
            //     impliedJTable().getTableHeader().setReorderingAllowed(false);
            impliedJTable().setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            impliedJTable().setRowSelectionAllowed(false);
            impliedJTable().setCellSelectionEnabled(false);
            impliedJTable().doLayout();
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
            return this.wrapper.getEstimatedSemIm();
        }

        public int getMatrixSelection() {
            return this.matrixSelection;
        }

        public void setMatrixSelection(int index) {
            selector().setSelectedIndex(index);
            switchView(index);
        }
    }

    /**
     * Edits parameter values for a SemIm as a simple list.
     */
    final class SemImTabularEditor extends JPanel {

        private static final long serialVersionUID = -3652030288654100645L;

        private final ParamTableModel tableModel;
        private final SemEstimatorWrapper wrapper;
        private boolean editable = true;

        public SemImTabularEditor(SemEstimatorWrapper wrapper, OneEditor editor,
                                  int maxFreeParamsForStatistics) {
            this.wrapper = wrapper;
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
//        setBorder(new TitledBorder("Click parameter values to edit"));

            if (semIm().isEstimated()) {
                setBorder(new TitledBorder("Null hypothesis for T and P is that the parameter is zero"));
            } else {
                setBorder(new TitledBorder("Click parameter values to edit"));
            }

            JTable table = new JTable() {
                private static final long serialVersionUID = -530774590911763214L;

                @Override
                public TableCellEditor getCellEditor(int row, int col) {
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
            return this.wrapper.getEstimatedSemIm();
        }

        public ParamTableModel getTableModel() {
            return this.tableModel;
        }

        public boolean isEditable() {
            return this.editable;
        }

        public void setEditable(boolean editable) {
            this.tableModel.setEditable(editable);
            this.editable = editable;
            this.tableModel.fireTableStructureChanged();
        }
    }

    final class ParamTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 2210883212769846304L;

        private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        private final SemEstimatorWrapper wrapper;
        private final OneEditor editor;
        private int maxFreeParamsForStatistics = 50;
        private boolean editable = true;

        public ParamTableModel(SemEstimatorWrapper wrapper, OneEditor editor,
                               int maxFreeParamsForStatistics) {
            this.wrapper = wrapper;

            if (maxFreeParamsForStatistics < 0) {
                throw new IllegalArgumentException();
            }

            this.maxFreeParamsForStatistics = maxFreeParamsForStatistics;

            this.editor = editor;
        }

        @Override
        public int getRowCount() {
            int numNodes = semIm().getVariableNodes().size();
            return semIm().getNumFreeParams() + semIm().getFixedParameters().size() + numNodes;
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
            List nodes = semIm().getVariableNodes();
            List parameters = new ArrayList<>(semIm().getFreeParameters());
            parameters.addAll(semIm().getFixedParameters());

            int numParams = semIm().getNumFreeParams() + semIm().getFixedParameters().size();

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
                int index = row - numParams;
                Node node = semIm().getVariableNodes().get(index);
                int n = semIm().getSampleSize();
                int df = n - 1;
                double mean = semIm().getMean(node);
                double stdDev = semIm().getMeanStdDev(node);
                double stdErr = stdDev / Math.sqrt(n);
//            double tValue = mean * Math.sqrt(n - 1) / stdDev;

                double tValue = mean / stdErr;
                double p = 2.0 * (1.0 - ProbUtils.tCdf(Math.abs(tValue), df));

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
                            double intercept = semIm().getIntercept(node);
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

            if (this.editor.isEditCovariancesAsCorrelations()) {
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

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return isEditable() && columnIndex == 3;
        }

        private boolean isEditable() {
            return this.editable;
        }

        public void setEditable(boolean editable) {
            this.editable = editable;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {

            if (columnIndex == 3) {
                try {
                    double value = Double.parseDouble((String) aValue);

                    if (rowIndex < semIm().getNumFreeParams()) {
                        Parameter parameter = semIm().getFreeParameters().get(rowIndex);

                        if (parameter.getType() == ParamType.VAR) {
                            value = value * value;
                            semIm().setErrVar(parameter.getNodeA(), value);
                        } else if (parameter.getType() == ParamType.COEF) {
                            Node x = parameter.getNodeA();
                            Node y = parameter.getNodeB();

                            semIm().setEdgeCoef(x, y, value);

                            double intercept = semIm().getIntercept(y);

                            if (this.editor.isEditIntercepts()) {
                                semIm().setIntercept(y, intercept);
                            }
                        }

                        this.editor.firePropertyChange("modelChanged", 0, 0);

                    } else {
                        int index = rowIndex - semIm().getNumFreeParams();
                        Node node = semIm().getVariableNodes().get(index);

                        if (semIm().getMean(semIm().getVariableNodes().get(index)) != value) {
                            if (this.editor.isEditIntercepts()) {
                                semIm().setIntercept(node, value);
                            } else {
                                semIm().setMean(node, value);
                            }
                            this.editor.firePropertyChange("modelChanged", 0, 0);

                        }
                    }
                } catch (Exception exception) {
                    // The old value will be reinstated automatically.
                }

                fireTableDataChanged();
            }
        }

        private String asString(double value) {
            if (Double.isNaN(value)) {
                return " * ";
            } else {
                return this.nf.format(value);
            }
        }

        private String typeString(Parameter parameter) {
            ParamType type = parameter.getType();

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
            return this.wrapper.getEstimatedSemIm();
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
        private final SemEstimatorWrapper wrapper;
        /**
         * Maximum number of free parameters for which model statistics will be
         * calculated. The algorithm for calculating these is expensive.
         */
        private final int maxFreeParamsForStatistics;
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
        private final OneEditor editor;
        /**
         * True iff this graphical display is editable.
         */
        private boolean editable = true;
        private Container dialog;

        /**
         * Constructs a SemIm graphical editor for the given SemIm.
         */
        public SemImGraphicalEditor(SemEstimatorWrapper semImWrapper, OneEditor editor,
                                    int maxFreeParamsForStatistics) {
            this.wrapper = semImWrapper;
            this.editor = editor;
            this.maxFreeParamsForStatistics = maxFreeParamsForStatistics;

            setLayout(new BorderLayout());
            JScrollPane scroll = new JScrollPane(workbench());

            add(scroll, BorderLayout.CENTER);

            setBorder(new TitledBorder("Click parameter values to edit"));

            ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
            setSavedTooltipDelay(toolTipManager.getInitialDelay());

            // Laborious code that follows is intended to make sure tooltips come
            // almost immediately within the sem im editor but more slowly outside.
            // Ugh.
            workbench().addComponentListener(new ComponentAdapter() {
                @Override
                public void componentShown(ComponentEvent e) {
                    resetLabels();
                    ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                    toolTipManager.setInitialDelay(100);
                }

                @Override
                public void componentHidden(ComponentEvent e) {
                    ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                    toolTipManager.setInitialDelay(getSavedTooltipDelay());
                }
            });

            workbench().addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (workbench().contains(e.getPoint())) {

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
                    if (!workbench().contains(e.getPoint())) {
                        ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                        toolTipManager.setInitialDelay(getSavedTooltipDelay());
                    }
                }
            });

            // Make sure the graphical editor reflects changes made to parameters
            // in other editors.
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentShown(ComponentEvent e) {
                    resetLabels();
                }
            });
        }

        //========================PRIVATE METHODS===========================//
        private void beginEdgeEdit(Edge edge) {
            finishEdit();

            if (!isEditable()) {
                return;
            }

            Parameter parameter = getEdgeParameter(edge);
            double d = semIm().getParamValue(parameter);

            if (this.editor.isEditCovariancesAsCorrelations()
                    && parameter.getType() == ParamType.COVAR) {
                Node nodeA = parameter.getNodeA();
                Node nodeB = parameter.getNodeB();

                double varA = semIm().getParamValue(nodeA, nodeA);
                double varB = semIm().getParamValue(nodeB, nodeB);

                d /= Math.sqrt(varA * varB);
            }

            DoubleTextField field = new DoubleTextField(d, 10, NumberFormatUtil.getInstance().getNumberFormat());
            field.setFilter((value, oldValue) -> {
                try {
                    setEdgeValue(edge, new Double(value).toString());
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

        }

        private void beginNodeEdit(Node node) {
            finishEdit();

            if (!isEditable()) {
                return;
            }

            Parameter parameter = getNodeParameter(node);
            if (this.editor.isEditCovariancesAsCorrelations()
                    && parameter.getType() == ParamType.VAR) {
                return;
            }

            double d;
            String prefix;
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

            DoubleTextField field = new DoubleTextField(d, 10, NumberFormatUtil.getInstance().getNumberFormat());
            field.setFilter((value, oldValue) -> {
                try {
                    setNodeValue(node, new Double(value).toString());
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
                        SemImGraphicalEditor.this.dialog = ancestor;
                    }
                }
            });

            field.addActionListener((e) -> {
                if (this.dialog != null) {
                    this.dialog.setVisible(false);
                }
            });

            String s;

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

        }

        private void finishEdit() {
            if (lastEditedObject() != null) {
                resetLabels();
            }
        }

        private ISemIm semIm() {
            return this.wrapper.getEstimatedSemIm();
        }

        private Graph graph() {
            return this.wrapper.getEstimatedSemIm().getSemPm().getGraph();
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

        private void setLastEditedObject(Object o) {
            this.lastEditedObject = o;
        }

        private Object lastEditedObject() {
            return this.lastEditedObject;
        }

        public void resetLabels() {
            Matrix implCovar = semIm().getImplCovar(false);

            for (Object o : graph().getEdges()) {
                resetEdgeLabel((Edge) (o), implCovar);
            }

            List<Node> nodes = graph().getNodes();

            for (Object node : nodes) {
                resetNodeLabel((Node) node, implCovar);
            }

            workbench().repaint();
        }

        private void resetEdgeLabel(Edge edge, Matrix implCovar) {
            Parameter parameter = getEdgeParameter(edge);

            if (parameter != null) {
                double val = semIm().getParamValue(parameter);
                double standardError;

                try {
                    standardError = semIm().getStandardError(parameter,
                            this.maxFreeParamsForStatistics);
                } catch (Exception exception) {
                    standardError = Double.NaN;
                }

                double tValue;
                try {
                    tValue = semIm().getTValue(parameter, this.maxFreeParamsForStatistics);
                } catch (Exception exception) {
                    tValue = Double.NaN;
                }

                double pValue;

                try {
                    pValue = semIm().getPValue(parameter, this.maxFreeParamsForStatistics);
                } catch (Exception exception) {
                    pValue = Double.NaN;
                }

                if (this.editor.isEditCovariancesAsCorrelations()
                        && parameter.getType() == ParamType.COVAR) {
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

        private void resetNodeLabel(Node node, Matrix implCovar) {
            if (!semIm().getSemPm().getGraph().isParameterizable(node)) {
                return;
            }

            Parameter parameter = semIm().getSemPm().getVarianceParameter(node);
            double meanOrIntercept = Double.NaN;

            JLabel label = new JLabel();
            label.setBackground(Color.WHITE);
            label.addMouseListener(new NodeMouseListener(node, this));
            label.setFont(this.SMALL_FONT);

            String tooltip = "";
            NodeType nodeType = node.getNodeType();

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
                double standardError = semIm().getStandardError(parameter,
                        this.maxFreeParamsForStatistics);
                double tValue
                        = semIm().getTValue(parameter, this.maxFreeParamsForStatistics);
                double pValue
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

        private Parameter getNodeParameter(Node node) {
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
         * the user has changed the endpoints of an edge X1 --> X2 to X1 &lt;-- X2
         * and returns the correct parameter. @throws IllegalArgumentException
         * if the edge is neither directed nor bidirected.
         */
        private Parameter getEdgeParameter(Edge edge) {
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

                if (this.editor.isEditCovariancesAsCorrelations()
                        && parameter.getType() == ParamType.COVAR) {
                    Node nodeA = edge.getNode1();
                    Node nodeB = edge.getNode2();

                    Matrix implCovar = semIm().getImplCovar(false);

                    double varA = semIm().getVariance(nodeA, implCovar);
                    double varB = semIm().getVariance(nodeB, implCovar);

                    d *= Math.sqrt(varA * varB);

                    semIm().setParamValue(parameter, d);
                    this.firePropertyChange("modelChanged", null, null);
                } else if (!this.editor.isEditCovariancesAsCorrelations()
                        && parameter.getType() == ParamType.COVAR) {
                    semIm().setParamValue(parameter, d);
                    this.firePropertyChange("modelChanged", null, null);
                } else if (parameter.getType() == ParamType.COEF) {

                    Node x = parameter.getNodeA();
                    Node y = parameter.getNodeB();

                    semIm().setEdgeCoef(x, y, d);

                    if (this.editor.isEditIntercepts()) {
                        double intercept = semIm().getIntercept(y);
                        semIm().setIntercept(y, intercept);
                    }

                    this.firePropertyChange("modelChanged", null, null);
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
                    this.firePropertyChange("modelChanged", null, null);
                } else if (parameter.getType() == ParamType.MEAN) {
                    if (this.editor.isEditIntercepts()) {
                        semIm().setIntercept(node, d);
                    } else {
                        semIm().setMean(node, d);
                    }

                    this.firePropertyChange("modelChanged", null, null);
                }
            } catch (Exception exception) {
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
            return this.workbench;
        }

        private boolean isEditable() {
            return this.editable;
        }

        public void setEditable(boolean editable) {
            workbench().setAllowEdgeReorientations(editable);
            workbench().setAllowDoubleClickActions(editable);
            workbench().setAllowNodeEdgeSelection(editable);
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
                return this.edge;
            }

            private SemImGraphicalEditor getEditor() {
                return this.editor;
            }

            public void mouseClicked(MouseEvent e) {
                getEditor().beginEdgeEdit(getEdge());
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
                return this.node;
            }

            private SemImGraphicalEditor getEditor() {
                return this.editor;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                getEditor().beginNodeEdit(getNode());
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

            public NodeActionListener(SemImGraphicalEditor editor, Node node) {
                this.editor = editor;
                this.node = node;
            }

            @Override
            public void actionPerformed(ActionEvent ev) {
                DoubleTextField doubleTextField = (DoubleTextField) ev.getSource();
                String s = doubleTextField.getText();
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

    static final class ModelStatisticsPanel extends JTextArea {

        private static final long serialVersionUID = -9096723049787232471L;

        private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        private final SemEstimatorWrapper wrapper;

        public ModelStatisticsPanel(SemEstimatorWrapper wrapper) {
            this.wrapper = wrapper;
            reset();

            addComponentListener(new ComponentAdapter() {
                @Override
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

            SemPm semPm = semIm().getSemPm();
            List<Node> variables = semPm.getVariableNodes();
            boolean containsLatent = false;
            for (Node node : variables) {
                if (node.getNodeType() == NodeType.LATENT) {
                    containsLatent = true;
                    break;
                }
            }

            try {
                modelChiSquare = semIm().getChiSquare();
                modelDof = semIm().getSemPm().getDof();
                modelPValue = semIm().getPValue();
            } catch (Exception exception) {
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
                String pValueString = modelPValue > 0.001 ? this.nf.format(modelPValue)
                        : new DecimalFormat("0.0000E0").format(modelPValue);
                append("\nP Value = " + (Double.isNaN(modelPValue) || modelDof == 0 ? "undefined" : pValueString));
                append("\nBIC Score = " + this.nf.format(semIm().getBicScore()));
                append("\nCFI = " + this.nf.format(semIm().getCfi()));
                append("\nRMSEA = " + this.nf.format(semIm().getRmsea()));

            } else {
                int numToFix = (int) Math.abs(modelDof);
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
            return this.wrapper.getEstimatedSemIm();
        }
    }

    /**
     * Presents a covariance matrix as a table model for the SemImEditor.
     *
     * @author Donald Crimbchin
     */
    static final class ImpliedCovTable extends AbstractTableModel {

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
        private final SemEstimatorWrapper wrapper;

        /**
         * The matrix being displayed. (This varies.)
         */
        private double[][] matrix;

        /**
         * Constructs a new table for the given covariance matrix, the nodes for
         * which are as specified (in the order they appear in the matrix).
         */
        public ImpliedCovTable(SemEstimatorWrapper wrapper, boolean measured,
                               boolean correlations) {
            this.wrapper = wrapper;
            this.measured = measured;
            this.correlations = correlations;

            this.nf = NumberFormatUtil.getInstance().getNumberFormat();

            if (measured() && covariances()) {
                this.matrix = getSemIm().getImplCovarMeas().toArray();
            } else if (measured() && !covariances()) {
                this.matrix = corr(getSemIm().getImplCovarMeas().toArray());
            } else if (!measured() && covariances()) {
                Matrix implCovarC = getSemIm().getImplCovar(false);
                this.matrix = implCovarC.toArray();
            } else if (!measured() && !covariances()) {
                Matrix implCovarC = getSemIm().getImplCovar(false);
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
         * @return the value being displayed in a cell, either a variable name
         * or a Double.
         */
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
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
            return this.wrapper.getEstimatedSemIm();
        }
    }
}
