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

import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.test.RowsSettable;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.model.MarkovCheckIndTestModel;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModels;
import edu.cmu.tetradapp.util.*;
import static edu.cmu.tetradapp.util.ParameterComponents.toArray;
import java.awt.*;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import org.jetbrains.annotations.NotNull;

/**
 * A model for the Markov check. The Markov check for a given graph and dataset
 * checks whether the graph is Markov with respect to the dataset. The Markov
 * check can be used to check whether a graph is Markov with respect to a
 * dataset, or whether a graph is Markov with respect to a dataset and a set of
 * variables. The Markov check can also be used to check whether a graph is
 * Markov with respect to a dataset and a set of variables, given a set of
 * knowledge. For facts of the form X _||_ Y | Z, X and Y should be in the last
 * tier of the knowledge, and Z should be in previous tiers.
 *
 * @author josephramsey
 */
public class MarkovCheckEditor extends JPanel {

    private final MarkovCheckIndTestModel model;
    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    private final JLabel markovTestLabel = new JLabel("(Unspecified Test)");
    private final JComboBox<IndependenceTestModel> indTestJComboBox = new JComboBox<>();
    private final JComboBox<String> conditioningSetTypeJComboBox = new JComboBox<>();
    private final JLabel testLabel = new JLabel("(Unspecified Test)");
    private final JLabel conditioningLabelDep = new JLabel("(Unspecified)");
    private final JLabel conditioningLabelIndep = new JLabel("(Unspecified)");
    private final DoubleTextField percent;
    boolean updatingTestModels = true;
    private AbstractTableModel tableModelIndep;
    private AbstractTableModel tableModelDep;
    private JLabel fractionDepLabelIndep;
    private JLabel fractionDepLabelDep;
    private JLabel ksLabelDep;
    private JLabel ksLabelIndep;
    private JLabel binomialPLabelDep;
    private JLabel binomialPLabelIndep;
    private JLabel andersonDarlingA2LabelDep;
    private JLabel andersonDarlingA2LabelIndep;
    private JLabel andersonDarlingPLabelDep;
    private JLabel andersonDarlingPLabelIndep;
    private int sortDir;
    private int lastSortCol;
    private IndependenceWrapper independenceWrapper;
    private JPanel histogramPanelIndep;
    private JPanel histogramPanelDep;

    /**
     * Constructs a new editor for the given model.
     */
    public MarkovCheckEditor(MarkovCheckIndTestModel model) {
        if (model == null) {
            throw new NullPointerException("Expecting a model");
        }

        conditioningSetTypeJComboBox.addItem("Parents(X) (Local Markov)");
        conditioningSetTypeJComboBox.addItem("Parents(X) for a Valid Order (Ordered Local Markov)");
        conditioningSetTypeJComboBox.addItem("MarkovBlanket(X)");
        conditioningSetTypeJComboBox.addItem("All Subsets (Global Markov)");

        conditioningSetTypeJComboBox.addActionListener(e -> {
            switch ((String) Objects.requireNonNull(conditioningSetTypeJComboBox.getSelectedItem())) {
                case "Parents(X) (Local Markov)":
                    model.getMarkovCheck().setSetType(ConditioningSetType.LOCAL_MARKOV);
                    break;
                case "Parents(X) for a Valid Order (Ordered Local Markov)":
                    model.getMarkovCheck().setSetType(ConditioningSetType.ORDERED_LOCAL_MARKOV);
                    break;
                case "MarkovBlanket(X)":
                    model.getMarkovCheck().setSetType(ConditioningSetType.MARKOV_BLANKET);
                    break;
                case "All Subsets (Global Markov)":
                    model.getMarkovCheck().setSetType(ConditioningSetType.GLOBAL_MARKOV);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown conditioning set type: "
                            + conditioningSetTypeJComboBox.getSelectedItem());
            }

            class MyWatchedProcess extends WatchedProcess {

                public void watch() {
                    if (model.getMarkovCheck().getSetType() == ConditioningSetType.GLOBAL_MARKOV && model.getVars().size() > 12) {
                        int ret = JOptionPane.showOptionDialog(MarkovCheckEditor.this,
                                "The all subsets option is exponential and can become extremely slow beyond 12"
                                + "\nvariables. You may possibly be required to force quit Tetrad. Continue?", "Warning",
                                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, null, null
                        );

                        if (ret == JOptionPane.NO_OPTION) {
                            return;
                        }
                    }

                    setTest();
                    model.getMarkovCheck().generateResults();
                    tableModelIndep.fireTableDataChanged();
                    tableModelDep.fireTableDataChanged();

                    histogramPanelDep.removeAll();
                    histogramPanelIndep.removeAll();
                    histogramPanelDep.add(createHistogramPanel(false), BorderLayout.CENTER);
                    histogramPanelIndep.add(createHistogramPanel(true), BorderLayout.CENTER);
                    histogramPanelDep.validate();
                    histogramPanelIndep.validate();
                    histogramPanelDep.repaint();
                    histogramPanelIndep.repaint();
                    setLabelTexts();
                }
            }

            new MyWatchedProcess();
        });

        this.model = model;
        refreshTestList();

        indTestJComboBox.addActionListener(e -> {
            class MyWatchedProcess extends WatchedProcess {

                public void watch() {
                    setTest();
                    model.getMarkovCheck().generateResults();
                    tableModelIndep.fireTableDataChanged();
                    tableModelDep.fireTableDataChanged();

                    histogramPanelDep.removeAll();
                    histogramPanelIndep.removeAll();
                    histogramPanelDep.add(createHistogramPanel(false), BorderLayout.CENTER);
                    histogramPanelIndep.add(createHistogramPanel(true), BorderLayout.CENTER);
                    histogramPanelDep.validate();
                    histogramPanelIndep.validate();
                    histogramPanelDep.repaint();
                    histogramPanelIndep.repaint();
                    setLabelTexts();
                }
            }

            new MyWatchedProcess();
        });

        setTest();

        Graph _graph = model.getGraph();
        Graph graph = GraphUtils.replaceNodes(_graph, model.getMarkovCheck().getVariables(model.getGraph().getNodes(), model.getMarkovCheck().getIndependenceNodes(), model.getMarkovCheck().getConditioningNodes()));

        JPanel indep = buildGuiIndep();
        JPanel dep = buildGuiDep();

        tableModelIndep.fireTableDataChanged();
        tableModelDep.fireTableDataChanged();

        Graph sourceGraph = model.getGraph();
        List<Node> variables = model.getMarkovCheck().getVariables(model.getGraph().getNodes(), model.getMarkovCheck().getIndependenceNodes(), model.getMarkovCheck().getConditioningNodes());

        List<Node> newVars = new ArrayList<>();

        for (Node node : variables) {
            if (sourceGraph.getNode(node.getName()) != null) {
                newVars.add(node);
            }
        }

        sourceGraph = edu.cmu.tetrad.graph.GraphUtils.replaceNodes(sourceGraph, newVars);

        List<Node> missingVars = new ArrayList<>();

        for (Node w : sourceGraph.getNodes()) {
            if (model.getMarkovCheck().getVariable(w.getName()) == null) {
                missingVars.add(w);
                if (missingVars.size() >= 5) {
                    break;
                }
            }
        }

        if (!missingVars.isEmpty()) {
            throw new IllegalArgumentException("At least these variables in the DAG are missing from the data:"
                    + "\n    " + missingVars);
        }

        model.setVars(graph.getNodeNames());

        JButton params = new JButton("Params");
        JButton recalculate = new JButton("Recalculate");

        this.percent = new DoubleTextField(0.5, 4, new DecimalFormat("0.0###"));

        JLabel percentSampleLabel;
        if (model.getMarkovCheck().getIndependenceTest().getData() != null) {
            percentSampleLabel = new JLabel("% Sample:");
        } else if (!(model.getMarkovCheck().getIndependenceTest() instanceof RowsSettable)) {
            percentSampleLabel = new JLabel("(Test cannot be subsampled)");
        } else {
            percentSampleLabel = new JLabel("(Not tabular data)");
        }

        recalculate.addActionListener(e -> refreshResult(model, percent));

        percent.setFilter((value, oldValue) -> {
            if (value < 0.0 || value > 1.0) {
                return oldValue;
            } else {
                return value;
            }
        });

        setLabelTexts();

        params.addActionListener(e -> {
            JOptionPane dialog = new JOptionPane(createParamsPanel(independenceWrapper, model.getParameters()), JOptionPane.PLAIN_MESSAGE);
            dialog.createDialog("Set Parameters").setVisible(true);

            class MyWatchedProcess2 extends WatchedProcess {

                @Override
                public void watch() {
                    refreshResult(model, percent);
                }
            }

            new MyWatchedProcess2();
        });

        JLabel conditioningSetsLabel = new JLabel("Conditioning Sets:");

        JTextArea testDescTextArea = new JTextArea(getHelpMessage());
        testDescTextArea.setEditable(true);
        testDescTextArea.setLineWrap(true);
        testDescTextArea.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(testDescTextArea);
        scroll.setPreferredSize(new Dimension(600, 400));

        JTabbedPane pane = new JTabbedPane();
        pane.addTab("Check Markov", indep);
        pane.addTab("Check Dependent Distribution", dep);
        pane.addTab("Help", new PaddingPanel(scroll));

        class MyWatchedProcess extends WatchedProcess {

            public void watch() {
                setTest();
                model.getMarkovCheck().generateResults();
                tableModelIndep.fireTableDataChanged();
                tableModelDep.fireTableDataChanged();
                histogramPanelDep.removeAll();
                histogramPanelIndep.removeAll();
                histogramPanelDep.add(createHistogramPanel(false), BorderLayout.CENTER);
                histogramPanelIndep.add(createHistogramPanel(true), BorderLayout.CENTER);
                histogramPanelDep.validate();
                histogramPanelIndep.validate();
                histogramPanelDep.repaint();
                histogramPanelIndep.repaint();
                setLabelTexts();
            }
        }

        new MyWatchedProcess();
        //        add(box);
        initComponents(params, recalculate, pane, conditioningSetsLabel, percentSampleLabel);
    }

    private void initComponents(JButton params, JButton recalculate, JTabbedPane pane, JLabel conditioningSetsLabel, JLabel percentSampleLabel) {
        GroupLayout layout = new GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addComponent(pane)
                                .addGroup(layout.createSequentialGroup()
                                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                .addGroup(layout.createSequentialGroup()
                                                        .addComponent(conditioningSetsLabel)
                                                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                                        .addComponent(conditioningSetTypeJComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                                                .addGroup(layout.createSequentialGroup()
                                                        .addComponent(testLabel)
                                                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                                        .addComponent(indTestJComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                                        .addComponent(params)
                                                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                                        .addComponent(recalculate)
                                                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                                        .addComponent(percentSampleLabel)
                                                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                                        .addComponent(percent, GroupLayout.PREFERRED_SIZE, 46, GroupLayout.PREFERRED_SIZE)))
                                        .addGap(0, 0, Short.MAX_VALUE)))
                        .addContainerGap())
        );
        layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(testLabel)
                                .addComponent(indTestJComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addComponent(params)
                                .addComponent(recalculate)
                                .addComponent(percentSampleLabel)
                                .addComponent(percent, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                .addComponent(conditioningSetTypeJComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addComponent(conditioningSetsLabel))
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pane, GroupLayout.DEFAULT_SIZE, 442, Short.MAX_VALUE)
                        .addContainerGap())
        );
    }

    @NotNull
    public static String getHelpMessage() {
        return """
                This tool lets you plot statistics for independence tests of a pair of variables given some conditioning calculated for one of those variables, for a given graph and dataset. Two tables are made, one in which the independence facts predicted by the graph using these conditioning sets are tested in the data and the other in which the graph's predicted dependence facts are tested. The first of these sets is a check for "Markov" (a check for implied independence facts) for the chosen conditioning sets; the is a check of the "Dependent Distribution." (a check of implied dependence facts)”

                Each table gives columns for the independence fact being checked, its test result, and its statistic. This statistic is either a p-value, ranging from 0 to 1, where p-values above the alpha level of the test are judged as independent, or a score bump, where this bump is negative for independent judgments and positive for dependent judgments.

                If the independence test yields a p-value, as for instance, for the Fisher Z test (for the linear, Gaussian case) or else the Chi-Square test (for the multinomial case), then under the null hypothesis of independence and for a consistent test, these p-values should be distributed as Uniform(0, 1). That is, it should be just as likely to see p-values in any range of equal width. If the test is inconsistent or the graph is incorrect (i.e., the parents of some or all of the nodes in the graph are incorrect), then this distribution of p-values will not be Uniform. To visualize this, we display the histogram of the p-values with equally sized bins; the bars in this histogram, for this case, should ideally all be of equal height.

                If the first bar in this histogram is especially high (for the p-value case), that means that many tests are being judged as dependent. For checking the dependent distribution, one hopes that this list is non-empty, in which case this first bar will be especially high since high p-values are examples where the graph is unfaithful to the distribution. These are possibly for cases where paths in the graph cancel unfaithfully. But for checking Markov, one hopes that this first bar will be the same height as all of the other bars.

                To make it especially clear, we give two statistics in the interface. The first is the percentage of p-values judged dependent on the test. If an alpha level is used in the test, this number should be very close to the alpha level for the Local Markov check since the distribution of p-values under this condition is Uniform. For the second, we test the Uniformity of the p-values using a Kolmogorov-Smirnov test. The p-value returned by this test should be greater than the user’s preferred alpha level if the distribution of p-values is Uniform and less than this alpha level if the distribution of p-values is non-uniform.

                If the independence test yields a bump in the score, this score should be negative for independence judgments and positive for dependence judgments. The histogram will reflect this.

                Feel free to select all of the data in the tables, copy it, and paste it into a text file or into Excel. This will let you analyze the data yourself.

                A note about Markov Blankets: The "Markov Blanket" conditioning set choice implements the Markov blanket calculation in a way that is correct for DAGs, CPDAGs, MAGs, and PAGs. For all of these graph types, the list of m-connecting facts in the Faithfulness tab should be empty, since the Markov blanket should screen off the target from any other variables in the dataset. It's possible that for some other graph types, this list may not be empty (i.e., the Markov blanket calculation may not be correct).

                Knowledge may be supplied to the Markov Checker. This will be interpreted as follows. For X _||_ Y | Z checked, X and Y will be drawn from the last tier of the knowledge, and the variables in Z will be drawn from all variables in tiers. Additional forbidden or required edges are not allowed.
                """;
    }

    @NotNull
    private static HistogramPanel getHistogramPanel(List<IndependenceResult> results) {
        DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(results.size(), 1),
                Collections.singletonList(new ContinuousVariable("P-Value or Bump")));

        for (int i = 0; i < results.size(); i++) {
            dataSet.setDouble(i, 0, results.get(i).getPValue());
        }

        Histogram histogram = new Histogram(dataSet, "P-Value or Bump", false);
        HistogramPanel view = new HistogramPanel(histogram, true);

        Color fillColor = new Color(113, 165, 210);
        view.setBarColor(fillColor);

        view.setMaximumSize(new Dimension(300, 200));
        return view;
    }

    //========================PUBLIC METHODS==========================//
    private void refreshResult(MarkovCheckIndTestModel model, DoubleTextField percent) {
        setTest();

        model.getMarkovCheck().setPercentResample(percent.getValue());
        model.getMarkovCheck().generateResults();
        tableModelIndep.fireTableDataChanged();
        tableModelDep.fireTableDataChanged();
        histogramPanelIndep.removeAll();
        histogramPanelDep.add(createHistogramPanel(false), BorderLayout.CENTER);
        histogramPanelIndep.add(createHistogramPanel(true), BorderLayout.CENTER);
        histogramPanelDep.validate();
        histogramPanelIndep.validate();
        histogramPanelDep.repaint();
        histogramPanelIndep.repaint();
        setLabelTexts();
    }

    private void setTest() {
        IndependenceTestModel selectedItem = (IndependenceTestModel) indTestJComboBox.getSelectedItem();
        Class<IndependenceWrapper> clazz = (selectedItem == null) ? null
                : selectedItem.getIndependenceTest().clazz();
        IndependenceTest independenceTest;

        if (clazz != null) {
            try {
                independenceWrapper = clazz.getDeclaredConstructor(new Class[0]).newInstance();
                independenceTest = independenceWrapper.getTest(model.getDataModel(), model.getParameters());
                model.setIndependenceTest(independenceTest);
                markovTestLabel.setText(model.getMarkovCheck().getIndependenceTest().toString());
                testLabel.setText(model.getMarkovCheck().getIndependenceTest().toString());
                invalidate();
                repaint();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                    | NoSuchMethodException e1) {
                TetradLogger.getInstance().forceLogMessage("Error: " + e1.getMessage());
                throw new RuntimeException(e1);
            }
        }

    }

    private JPanel buildGuiIndep() {
        JPanel tablelPanel = new JPanel(new BorderLayout());

        String setType = (String) conditioningSetTypeJComboBox.getSelectedItem();

        conditioningLabelIndep.setText("Tests graphical predictions of Indep(X, Y | " + setType + ")");
        tablelPanel.add(conditioningLabelIndep, BorderLayout.NORTH);

        markovTestLabel.setText(model.getMarkovCheck().getIndependenceTest().toString());
        testLabel.setText(model.getMarkovCheck().getIndependenceTest().toString());

        this.tableModelIndep = new AbstractTableModel() {
            public String getColumnName(int column) {
                if (column == 0) {
                    return "Index";
                } else if (column == 1) {
                    return "Graphical Prediction";
                } else if (column == 2) {
                    return "Test Result";
                } else if (column == 3) {
                    return "P-value or Bump";
                }

                return null;
            }

            public int getColumnCount() {
                return 4;
            }

            public int getRowCount() {
                List<IndependenceResult> results = model.getResults(true);
                return results.size();
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                if (rowIndex > model.getResults(true).size()) {
                    return null;
                }

                if (columnIndex == 0) {
                    return rowIndex + 1;
                }

                IndependenceResult result = model.getResults(true).get(rowIndex);

                if (columnIndex == 1) {
                    IndependenceFact fact = model.getResults(true).get(rowIndex).getFact();
                    List<Node> Z = new ArrayList<>(fact.getZ());
                    String z = Z.stream().map(Node::getName).collect(Collectors.joining(", "));
                    return "Ind(" + fact.getX() + ", " + fact.getY() + (Z.isEmpty() ? "" : " | " + z) + ")";
                }

                if (columnIndex == 2) {
                    if (model.getMarkovCheck().getIndependenceTest() instanceof MsepTest) {
                        if (result.isIndependent()) {
                            return "M-SEPARATED";
                        } else {
                            return "m-connected";
                        }
                    } else {
                        if (result.isIndependent()) {
                            return "INDEPENDENT";
                        } else {
                            return "dependent";
                        }
                    }
                }

                if (columnIndex == 3) {
                    return nf.format(result.getPValue());
                }

                return null;
            }

            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) {
                    return Number.class;
                }
                if (columnIndex == 1) {
                    return String.class;
                } else {
                    return Number.class;
                }
            }
        };

        JTable table = new JTable(tableModelIndep);

        tableModelIndep.addTableModelListener(e -> {
            if (e.getColumn() == 2) {
                table.revalidate();
                table.repaint();
            }
        });

        table.getColumnModel().getColumn(0).setMinWidth(40);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(1).setMinWidth(200);
        table.getColumnModel().getColumn(1).setCellRenderer(new Renderer());
        table.getColumnModel().getColumn(2).setMinWidth(100);
        table.getColumnModel().getColumn(2).setMaxWidth(100);
        table.getColumnModel().getColumn(3).setMinWidth(100);
        table.getColumnModel().getColumn(3).setMaxWidth(100);

        table.getColumnModel().getColumn(2).setCellRenderer(new Renderer());
        table.getColumnModel().getColumn(3).setCellRenderer(new Renderer());

        JTableHeader header = table.getTableHeader();

        header.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                JTableHeader header = (JTableHeader) e.getSource();
                Point point = e.getPoint();
                int col = header.columnAtPoint(point);
                int sortCol = header.getTable().convertColumnIndexToModel(col);

                MarkovCheckEditor.this.sortByColumn(sortCol, true);
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        tablelPanel.add(scroll, BorderLayout.CENTER);

        JLabel label = new JLabel("Table contents can be selected and copied in to, e.g., Excel.");
        tablelPanel.add(label, BorderLayout.SOUTH);

        setLabelTexts();

        Box a4 = Box.createVerticalBox();
        histogramPanelIndep = new JPanel();
        histogramPanelIndep.setLayout(new BorderLayout());
        histogramPanelIndep.setBorder(new EmptyBorder(10, 10, 10, 10));
        histogramPanelIndep.add(createHistogramPanel(true), BorderLayout.CENTER);
        a4.add(histogramPanelIndep);

        Box a5 = Box.createHorizontalBox();
        a5.add(Box.createHorizontalGlue());
        a5.add(fractionDepLabelIndep);
        a4.add(a5);

        Box a6 = Box.createHorizontalBox();
        a6.add(Box.createHorizontalGlue());
        a6.add(ksLabelIndep);
        a4.add(a6);

        Box a7 = Box.createHorizontalBox();
        a7.add(Box.createHorizontalGlue());
        a7.add(binomialPLabelIndep);
        a4.add(a7);

        Box a8 = Box.createHorizontalBox();
        a8.add(Box.createHorizontalGlue());
        a8.add(andersonDarlingA2LabelIndep);
        a4.add(a8);

        Box a9 = Box.createHorizontalBox();
        a9.add(Box.createHorizontalGlue());
        a9.add(andersonDarlingPLabelIndep);
        a4.add(a9);

        JPanel checkMarkovPanel = new JPanel(new BorderLayout());
        checkMarkovPanel.add(new PaddingPanel(tablelPanel), BorderLayout.CENTER);
        checkMarkovPanel.add(new PaddingPanel(a4), BorderLayout.EAST);

        return checkMarkovPanel;
    }

    /**
     * Performs the action of opening a session from a file.
     */
    private JPanel buildGuiDep() {
        JPanel tablelPanel = new JPanel(new BorderLayout());

        String setType = (String) conditioningSetTypeJComboBox.getSelectedItem();

        conditioningLabelDep.setText("Tests graphical predictions of Dep(X, Y | " + setType + ")");
        tablelPanel.add(conditioningLabelDep, BorderLayout.NORTH);

        markovTestLabel.setText(model.getMarkovCheck().getIndependenceTest().toString());
        testLabel.setText(model.getMarkovCheck().getIndependenceTest().toString());

//        a1.add(Box.createVerticalStrut(5));
        this.tableModelDep = new AbstractTableModel() {
            public String getColumnName(int column) {
                if (column == 0) {
                    return "Index";
                } else if (column == 1) {
                    return "Graphical Prediction";
                } else if (column == 2) {
                    return "Test Result";
                } else if (column == 3) {
                    return "P-value or Bump";
                }

                return null;
            }

            public int getColumnCount() {
                return 4;
            }

            public int getRowCount() {
                List<IndependenceResult> results = model.getResults(true);
                return results.size();
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                if (rowIndex > model.getResults(true).size()) {
                    return null;
                }

                if (columnIndex == 0) {
                    return rowIndex + 1;
                }

                IndependenceResult result = model.getResults(true).get(rowIndex);

                if (columnIndex == 1) {
                    IndependenceFact fact = model.getResults(true).get(rowIndex).getFact();
                    List<Node> Z = new ArrayList<>(fact.getZ());
                    String z = Z.stream().map(Node::getName).collect(Collectors.joining(", "));
                    return "Ind(" + fact.getX() + ", " + fact.getY() + (Z.isEmpty() ? "" : " | " + z) + ")";
                }

                if (columnIndex == 2) {
                    if (model.getMarkovCheck().getIndependenceTest() instanceof MsepTest) {
                        if (result.isIndependent()) {
                            return "M-SEPARATED";
                        } else {
                            return "m-connected";
                        }
                    } else {
                        if (result.isIndependent()) {
                            return "INDEPENDENT";
                        } else {
                            return "dependent";
                        }
                    }
                }

                if (columnIndex == 3) {
                    return nf.format(result.getPValue());
                }

                return null;
            }

            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) {
                    return Number.class;
                }
                if (columnIndex == 1) {
                    return String.class;
                } else {
                    return Number.class;
                }
            }
        };

        JTable table = new JTable(tableModelDep);

        tableModelDep.addTableModelListener(e -> {
            if (e.getColumn() == 2) {
                table.revalidate();
                table.repaint();
            }
        });

        table.getColumnModel().getColumn(0).setMinWidth(40);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(1).setMinWidth(200);
        table.getColumnModel().getColumn(1).setCellRenderer(new Renderer());
        table.getColumnModel().getColumn(2).setMinWidth(100);
        table.getColumnModel().getColumn(2).setMaxWidth(100);
        table.getColumnModel().getColumn(3).setMinWidth(100);
        table.getColumnModel().getColumn(3).setMaxWidth(100);

        table.getColumnModel().getColumn(2).setCellRenderer(new Renderer());
        table.getColumnModel().getColumn(3).setCellRenderer(new Renderer());

        JTableHeader header = table.getTableHeader();

        header.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                JTableHeader header = (JTableHeader) e.getSource();
                Point point = e.getPoint();
                int col = header.columnAtPoint(point);
                int sortCol = header.getTable().convertColumnIndexToModel(col);

                MarkovCheckEditor.this.sortByColumn(sortCol, false);
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        tablelPanel.add(scroll, BorderLayout.CENTER);

        Box a3 = Box.createHorizontalBox();
        JLabel label = new JLabel("Table contents can be selected and copied in to, e.g., Excel.");
        a3.add(label);
        a3.add(Box.createHorizontalGlue());
        tablelPanel.add(label, BorderLayout.SOUTH);

        setLabelTexts();

        Box a4 = Box.createVerticalBox();
        histogramPanelDep = new JPanel();
        histogramPanelDep.setLayout(new BorderLayout());
        histogramPanelDep.setBorder(new EmptyBorder(10, 10, 10, 10));
        histogramPanelDep.add(createHistogramPanel(true), BorderLayout.CENTER);
        a4.add(histogramPanelDep);

        Box a5 = Box.createHorizontalBox();
        a5.add(Box.createHorizontalGlue());
        a5.add(fractionDepLabelDep);
        a4.add(a5);

        Box a6 = Box.createHorizontalBox();
        a6.add(Box.createHorizontalGlue());
        a6.add(ksLabelDep);
        a4.add(a6);

        Box a7 = Box.createHorizontalBox();
        a7.add(Box.createHorizontalGlue());
        a7.add(binomialPLabelDep);
        a4.add(a7);

        Box a8 = Box.createHorizontalBox();
        a8.add(Box.createHorizontalGlue());
        a8.add(andersonDarlingA2LabelDep);
        a4.add(a8);

        Box a9 = Box.createHorizontalBox();
        a9.add(Box.createHorizontalGlue());
        a9.add(andersonDarlingPLabelDep);
        a4.add(a9);

        Box a11 = Box.createHorizontalBox();
        a11.add(a4);

        JPanel checkDependDistributionPanel = new JPanel(new BorderLayout());
        checkDependDistributionPanel.add(new PaddingPanel(tablelPanel), BorderLayout.CENTER);
        checkDependDistributionPanel.add(new PaddingPanel(a4), BorderLayout.EAST);

        return checkDependDistributionPanel;
    }

    private void sortByColumn(int sortCol, boolean indep) {
        if (sortCol == this.getLastSortCol()) {
            this.setSortDir(-1 * this.getSortDir());
        } else {
            this.setSortDir(1);
        }

        this.setLastSortCol(sortCol);
        model.getResults(indep).sort(Comparator.comparing(
                IndependenceResult::getFact));

        tableModelIndep.fireTableDataChanged();
        tableModelDep.fireTableDataChanged();

        invalidate();
        repaint();
    }

    private void setLabelTexts() {
        if (ksLabelIndep == null) {
            ksLabelIndep = new JLabel();
        }

        if (ksLabelDep == null) {
            ksLabelDep = new JLabel();
        }

        if (binomialPLabelIndep == null) {
            binomialPLabelIndep = new JLabel();
        }

        if (binomialPLabelDep == null) {
            binomialPLabelDep = new JLabel();
        }

        if (andersonDarlingA2LabelIndep == null) {
            andersonDarlingA2LabelIndep = new JLabel();
        }

        if (andersonDarlingA2LabelDep == null) {
            andersonDarlingA2LabelDep = new JLabel();
        }

        if (andersonDarlingPLabelIndep == null) {
            andersonDarlingPLabelIndep = new JLabel();
        }

        if (andersonDarlingPLabelDep == null) {
            andersonDarlingPLabelDep = new JLabel();
        }

        if (fractionDepLabelIndep == null) {
            fractionDepLabelIndep = new JLabel();
        }

        if (fractionDepLabelDep == null) {
            fractionDepLabelDep = new JLabel();
        }

        ksLabelIndep.setText("P-value of KS Uniformity Test = "
                + ((Double.isNaN(model.getMarkovCheck().getKsPValue(true))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getKsPValue(true)))));
        ksLabelDep.setText("P-value of KS Uniformity Test = "
                + ((Double.isNaN(model.getMarkovCheck().getKsPValue(false))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getKsPValue(false)))));

        andersonDarlingA2LabelIndep.setText("A^2 = "
                + ((Double.isNaN(model.getMarkovCheck().getAndersonDarlingA2Star(true))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getAndersonDarlingA2Star(true)))));
        andersonDarlingA2LabelDep.setText("A^2* = "
                + ((Double.isNaN(model.getMarkovCheck().getAndersonDarlingA2Star(false))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getAndersonDarlingA2Star(false)))));

        andersonDarlingPLabelIndep.setText("P-value of the Anderson-Darling test = "
                + ((Double.isNaN(model.getMarkovCheck().getAndersonDarlingP(true))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getAndersonDarlingP(true)))));
        andersonDarlingPLabelDep.setText("P-value of the Anderson-Darling test = "
                + ((Double.isNaN(model.getMarkovCheck().getAndersonDarlingP(false))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getAndersonDarlingP(false)))));
        binomialPLabelIndep.setText("P-value of Binomial Test = "
                + ((Double.isNaN(model.getMarkovCheck().getBinomialP(true))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getBinomialP(true)))));
        binomialPLabelDep.setText("P-value of Binomial Test = "
                + ((Double.isNaN(model.getMarkovCheck().getBinomialP(false))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getBinomialP(false)))));
        fractionDepLabelIndep.setText("% dependent = "
                + ((Double.isNaN(model.getMarkovCheck().getFractionDependent(true))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getFractionDependent(true)))));
        fractionDepLabelDep.setText("% dependent = "
                + ((Double.isNaN(model.getMarkovCheck().getFractionDependent(false))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getFractionDependent(false)))));

        conditioningLabelIndep.setText("Tests graphical predictions of Indep(X, Y | " + conditioningSetTypeJComboBox.getSelectedItem() + ")");
        conditioningLabelDep.setText("Tests graphical predictions of Dep(X, Y | " + conditioningSetTypeJComboBox.getSelectedItem() + ")");
    }

    private int getLastSortCol() {
        return this.lastSortCol;
    }

    private void setLastSortCol(int lastSortCol) {
        if (lastSortCol < 0 || lastSortCol > 4) {
            throw new IllegalArgumentException();
        }

        this.lastSortCol = lastSortCol;
    }

    private int getSortDir() {
        return this.sortDir;
    }

    private void setSortDir(int sortDir) {
        if (!(sortDir == 1 || sortDir == -1)) {
            throw new IllegalArgumentException();
        }

        this.sortDir = sortDir;
    }

    private Box createHistogramPanel(boolean indep) {
        List<IndependenceResult> results = model.getResults(indep);

        if (results.isEmpty()) {
            return Box.createVerticalBox();
        }

        HistogramPanel view = getHistogramPanel(results);
        Box box = Box.createVerticalBox();
        box.add(view);
        return box;
    }

    private DataType getDataType() {
        DataModel dataSet = model.getDataModel();

        if (dataSet.isContinuous() && !(dataSet instanceof ICovarianceMatrix)) {
            return DataType.Continuous;
        } else if (dataSet.isDiscrete()) {
            return DataType.Discrete;
        } else if (dataSet.isMixed()) {
            return DataType.Mixed;
        } else if (dataSet instanceof ICovarianceMatrix) {
            return DataType.Covariance;
        } else {
            return null;
        }
    }

    private void refreshTestList() {
        DataType dataType = getDataType();

        this.indTestJComboBox.removeAllItems();

        List<IndependenceTestModel> models = IndependenceTestModels.getInstance().getModels(dataType);

        for (IndependenceTestModel model : models) {
            this.indTestJComboBox.addItem(model);
        }

        this.updatingTestModels = false;
        this.indTestJComboBox.setEnabled(this.indTestJComboBox.getItemCount() > 0);

        indTestJComboBox.setSelectedItem(IndependenceTestModels.getInstance().getDefaultModel(dataType));
    }

    // Parameter panel code from Kevin Bui.
    private JPanel createParamsPanel(IndependenceWrapper independenceWrapper, Parameters params) {
        Set<String> testParameters = new HashSet<>(independenceWrapper.getParameters());
        return createParamsPanel(testParameters, params);
    }

    private JPanel createParamsPanel(Set<String> params, Parameters parameters) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Parameters"));

        Box paramsBox = Box.createVerticalBox();

        Box[] boxes = toArray(createParameterComponents(params, parameters));
        int lastIndex = boxes.length - 1;
        for (int i = 0; i < lastIndex; i++) {
            paramsBox.add(boxes[i]);
            paramsBox.add(Box.createVerticalStrut(10));
        }
        paramsBox.add(boxes[lastIndex]);

        panel.add(new PaddingPanel(paramsBox), BorderLayout.CENTER);

        return panel;
    }

    private Map<String, Box> createParameterComponents(Set<String> params, Parameters parameters) {
        ParamDescriptions paramDescriptions = ParamDescriptions.getInstance();
        return params.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        e -> createParameterComponent(e, parameters, paramDescriptions.get(e)),
                        (u, v) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s.", u));
                        },
                        TreeMap::new));
    }

    private Box createParameterComponent(String parameter, Parameters parameters, ParamDescription paramDesc) {
        JComponent component;
        Object defaultValue = paramDesc.getDefaultValue();
        if (defaultValue instanceof Double) {
            double lowerBoundDouble = paramDesc.getLowerBoundDouble();
            double upperBoundDouble = paramDesc.getUpperBoundDouble();
            component = getDoubleField(parameter, parameters, (Double) defaultValue, lowerBoundDouble, upperBoundDouble);
        } else if (defaultValue instanceof Integer) {
            int lowerBoundInt = paramDesc.getLowerBoundInt();
            int upperBoundInt = paramDesc.getUpperBoundInt();
            component = getIntTextField(parameter, parameters, (Integer) defaultValue, lowerBoundInt, upperBoundInt);
        } else if (defaultValue instanceof Long) {
            long lowerBoundLong = paramDesc.getLowerBoundLong();
            long upperBoundLong = paramDesc.getUpperBoundLong();
            component = getLongTextField(parameter, parameters, (Long) defaultValue, lowerBoundLong, upperBoundLong);
        } else if (defaultValue instanceof Boolean) {
            component = getBooleanSelectionBox(parameter, parameters, (Boolean) defaultValue);
        } else if (defaultValue instanceof String) {
            component = getStringField(parameter, parameters, (String) defaultValue);
        } else {
            throw new IllegalArgumentException("Unexpected type: " + defaultValue.getClass());
        }

        Box paramRow = Box.createHorizontalBox();

        JLabel paramLabel = new JLabel(paramDesc.getShortDescription());
        String longDescription = paramDesc.getLongDescription();
        if (longDescription != null) {
            paramLabel.setToolTipText(longDescription);
        }
        paramRow.add(paramLabel);
        paramRow.add(Box.createHorizontalGlue());
        paramRow.add(component);

        return paramRow;
    }

    private DoubleTextField getDoubleField(String parameter, Parameters parameters,
            double defaultValue, double lowerBound, double upperBound) {
        DoubleTextField field = new DoubleTextField(parameters.getDouble(parameter, defaultValue),
                8, new DecimalFormat("0.####"), new DecimalFormat("0.0#E0"), 0.001);

        field.setFilter((value, oldValue) -> {
            if (value == field.getValue()) {
                return oldValue;
            }

            if (value < lowerBound) {
                return oldValue;
            }

            if (value > upperBound) {
                return oldValue;
            }

            try {
                parameters.set(parameter, value);
            } catch (Exception e) {
                // Ignore.
            }

            return value;
        });

        return field;
    }

    private IntTextField getIntTextField(String parameter, Parameters parameters,
            int defaultValue, double lowerBound, double upperBound) {
        IntTextField field = new IntTextField(parameters.getInt(parameter, defaultValue), 8);

        field.setFilter((value, oldValue) -> {
            if (value == field.getValue()) {
                return oldValue;
            }

            if (value < lowerBound) {
                return oldValue;
            }

            if (value > upperBound) {
                return oldValue;
            }

            try {
                parameters.set(parameter, value);
            } catch (Exception e) {
                // Ignore.
            }

            return value;
        });

        return field;
    }

    private LongTextField getLongTextField(String parameter, Parameters parameters,
            long defaultValue, long lowerBound, long upperBound) {
        LongTextField field = new LongTextField(parameters.getLong(parameter, defaultValue), 8);

        field.setFilter((value, oldValue) -> {
            if (value == field.getValue()) {
                return oldValue;
            }

            if (value < lowerBound) {
                return oldValue;
            }

            if (value > upperBound) {
                return oldValue;
            }

            try {
                parameters.set(parameter, value);
            } catch (Exception e) {
                // Ignore.
            }

            return value;
        });

        return field;
    }

    // Zhou's new implementation with yes/no radio buttons
    private Box getBooleanSelectionBox(String parameter, Parameters parameters, boolean defaultValue) {
        Box selectionBox = Box.createHorizontalBox();

        JRadioButton yesButton = new JRadioButton("Yes");
        JRadioButton noButton = new JRadioButton("No");

        // Button group to ensure only one option can be selected
        ButtonGroup selectionBtnGrp = new ButtonGroup();
        selectionBtnGrp.add(yesButton);
        selectionBtnGrp.add(noButton);

        boolean aBoolean = parameters.getBoolean(parameter, defaultValue);

        // Set default selection
        if (aBoolean) {
            yesButton.setSelected(true);
        } else {
            noButton.setSelected(true);
        }

        // Add to containing box
        selectionBox.add(yesButton);
        selectionBox.add(noButton);

        // Event listener
        yesButton.addActionListener((e) -> {
            JRadioButton button = (JRadioButton) e.getSource();
            if (button.isSelected()) {
                parameters.set(parameter, true);
            }
        });

        // Event listener
        noButton.addActionListener((e) -> {
            JRadioButton button = (JRadioButton) e.getSource();
            if (button.isSelected()) {
                parameters.set(parameter, false);
            }
        });

        return selectionBox;
    }

    private StringTextField getStringField(String parameter, Parameters parameters, String defaultValue) {
        StringTextField field = new StringTextField(parameters.getString(parameter, defaultValue), 20);

        field.setFilter((value, oldValue) -> {
            if (value.equals(field.getValue().trim())) {
                return oldValue;
            }

            try {
                parameters.set(parameter, value);
            } catch (Exception e) {
                // Ignore.
            }

            return value;
        });

        return field;
    }

    static class Renderer extends DefaultTableCellRenderer {

        private JTable table;
        private boolean selected;

        public Renderer() {
        }

        public void setValue(Object value) {
            if (selected) {
                setForeground(table.getSelectionForeground());
                setBackground(table.getSelectionBackground());
            } else {
                this.setForeground(table.getForeground());
                this.setBackground(table.getBackground());
            }

            this.setText(value.toString());
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            this.table = table;
            selected = isSelected;
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
    }
}

