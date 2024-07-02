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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.RowSorterEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import java.awt.Point;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import static edu.cmu.tetradapp.util.ParameterComponents.toArray;

/**
 * A model for the Markov check. The Markov check for a given graph and dataset checks whether the graph is Markov with
 * respect to the dataset. The Markov check can be used to check whether a graph is Markov with respect to a dataset, or
 * whether a graph is Markov with respect to a dataset and a set of variables. The Markov check can also be used to
 * check whether a graph is Markov with respect to a dataset and a set of variables, given a set of knowledge. For facts
 * of the form X _||_ Y | Z, X and Y should be in the last tier of the knowledge, and Z should be in previous tiers.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class MarkovCheckEditor extends JPanel {

    private static boolean flipEscapes = true;
    /**
     * The model for the Markov check.
     */
    private final MarkovCheckIndTestModel model;
    /**
     * The number format.
     */
    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    /**
     * The label for the fraction of p-values less than the alpha level.
     */
    private final JLabel markovTestLabel = new JLabel("(Unspecified Test)");
    /**
     * The combo box for the independence test.
     */
    private final JComboBox<IndependenceTestModel> indTestJComboBox = new JComboBox<>();
    /**
     * The combo box for the conditioning set type.
     */
    private final JComboBox<String> conditioningSetTypeJComboBox = new JComboBox<>();
    /**
     * The label for the test.
     */
    private final JLabel testLabel = new JLabel("(Unspecified Test)");
    /**
     * The label for the conditioning set.
     */
    private final JLabel conditioningLabelDep = new JLabel("(Unspecified)");
    /**
     * The label for the conditioning set.
     */
    private final JLabel conditioningLabelIndep = new JLabel("(Unspecified)");
    /**
     * The label for the fraction of p-values less than the alpha level.
     */
    private final DoubleTextField percent;
    /**
     * The label for the fraction of p-values less than the alpha level.
     */
    boolean updatingTestModels = true;
    /**
     * The JTable variable containing the independent table.
     */
    private JTable tableIndep;
    /**
     * Represents a private JTable variable named tableDep. This variable is an instance of the JTable class and is used
     * as a table component in the MarkovCheckEditor class.
     *
     * @see MarkovCheckEditor
     */
    private JTable tableDep;
    /**
     * The table model for the independence test.
     */
    private AbstractTableModel tableModelIndep;
    /**
     * The table model for the independence test.
     */
    private AbstractTableModel tableModelDep;
    /**
     * The label for the fraction of p-values less than the alpha level.
     */
    private JLabel fractionDepLabelIndep;
    /**
     * The label for the fraction of p-values less than the alpha level.
     */
    private JLabel fractionDepLabelDep;
    /**
     * The label for the Kolmogorov-Smirnov test.
     */
    private JLabel ksLabelDep;
    /**
     * The label for the Kolmogorov-Smirnov test.
     */
    private JLabel ksLabelIndep;
    /**
     * The label for the binomial test.
     */
    private JLabel binomialPLabelDep;

//    /**
//     * The label for the Anderson-Darling test.
//     */
//    private JLabel andersonDarlingA2LabelDep;
//
//    /**
//     * The label for the Anderson-Darling test.
//     */
//    private JLabel andersonDarlingA2LabelIndep;
    /**
     * The label for the binomial test.
     */
    private JLabel binomialPLabelIndep;
    /**
     * The label for the Anderson-Darling test.
     */
    private JLabel andersonDarlingPLabelDep;
    /**
     * The label for the Anderson-Darling test.
     */
    private JLabel andersonDarlingPLabelIndep;
    /**
     * Sort direction.
     */
    private int sortDir;
    /**
     * Last sort column.
     */
    private int lastSortCol;
    /**
     * Independence test model.
     */
    private IndependenceWrapper independenceWrapper;
    /**
     * The histogram panel.
     */
    private JPanel histogramPanelIndep;
    /**
     * The histogram panel.
     */
    private JPanel histogramPanelDep;
    /**
     * A checkbox for the independence tab to flip escapes for some regexes.
     */
    private JCheckBox flipEscapesIndep;
    /**
     * A checkbox for the dependence tab to flip escapes for some regexes.
     */
    private JCheckBox flipEscapesDep;

    /**
     * Constructs a new editor for the given model.
     *
     * @param model a {@link edu.cmu.tetradapp.model.MarkovCheckIndTestModel} object
     */
    public MarkovCheckEditor(MarkovCheckIndTestModel model) {
        if (model == null) {
            throw new NullPointerException("Expecting a model");
        }

        setPreferredSize(new Dimension(1100, 600));

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
                    model.getMarkovCheck().generateResults(true);
                    tableModelIndep.fireTableDataChanged();
                    tableModelDep.fireTableDataChanged();

                    histogramPanelDep.removeAll();
                    histogramPanelIndep.removeAll();
                    histogramPanelDep.add(createHistogramPanel(model.getResults(false)), BorderLayout.CENTER);
                    histogramPanelIndep.add(createHistogramPanel(model.getResults(true)), BorderLayout.CENTER);
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
                    model.getMarkovCheck().generateResults(true);
                    tableModelIndep.fireTableDataChanged();
                    tableModelDep.fireTableDataChanged();

                    histogramPanelDep.removeAll();
                    histogramPanelIndep.removeAll();
                    histogramPanelDep.add(createHistogramPanel(model.getResults(false)), BorderLayout.CENTER);
                    histogramPanelIndep.add(createHistogramPanel(model.getResults(true)), BorderLayout.CENTER);
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
        JButton resample = new JButton("Resample");
        JButton addSample = new JButton("Add Sample");

        this.percent = new DoubleTextField(0.5, 4, new DecimalFormat("0.0###"));

        JLabel percentSampleLabel;
        if (model.getMarkovCheck().getIndependenceTest().getData() != null) {
            percentSampleLabel = new JLabel("% Sample:");
        } else if (!(model.getMarkovCheck().getIndependenceTest() instanceof RowsSettable)) {
            percentSampleLabel = new JLabel("(Test cannot be subsampled)");
        } else {
            percentSampleLabel = new JLabel("(Not tabular data)");
        }

        resample.addActionListener(e -> refreshResult(model, tableIndep, tableDep,
                tableModelIndep, tableModelDep, percent, true));
        addSample.addActionListener(e -> refreshResult(model, tableIndep, tableDep,
                tableModelIndep, tableModelDep, percent, false));

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
                    refreshResult(model, tableIndep, tableDep, tableModelIndep, tableModelDep, percent, true);
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
                model.getMarkovCheck().generateResults(true);
                tableModelIndep.fireTableDataChanged();
                tableModelDep.fireTableDataChanged();
                histogramPanelDep.removeAll();
                histogramPanelIndep.removeAll();
                histogramPanelDep.add(createHistogramPanel(model.getResults(false)), BorderLayout.CENTER);
                histogramPanelIndep.add(createHistogramPanel(model.getResults(true)), BorderLayout.CENTER);
                histogramPanelDep.validate();
                histogramPanelIndep.validate();
                histogramPanelDep.repaint();
                histogramPanelIndep.repaint();
                setLabelTexts();
            }
        }

        new MyWatchedProcess();
        initComponents(params, resample, addSample, pane, conditioningSetsLabel, percentSampleLabel);
    }

    /**
     * <p>getHelpMessage.</p>
     *
     * @return a {@link java.lang.String} object
     */
    @NotNull
    public static String getHelpMessage() {
        return """
                This tool lets one plot statistics for independence tests of a pair of variables given some conditioning calculated for one of those variables for a given graph and dataset. Two tables are made, one in which the independence facts predicted by the graph using these conditioning sets are tested in the data and the other in which the graph's predicted dependence facts are tested. The first of these sets is a check for "Markov" (a check for implied independence facts) for the chosen conditioning sets; the is a check of the "Dependent Distribution." (a check of implied dependence facts)”

                Each table gives columns for the independence fact being checked, its test result, and its statistic. This statistic is either a p-value, ranging from 0 to 1, where p-values above the alpha level of the test are judged as independent, or a score bump, where this bump is negative for independent judgments and positive for dependent judgments.

                If the independence test yields a p-value, for instance, for the Fisher Z test (for the linear, Gaussian case) or else the Chi-Square test (for the multinomial case), then under the null hypothesis of independence and for a consistent test, these p-values should be distributed as Uniform(0, 1). That is, it should be just as likely to see p-values in any range of equal width. If the test is inconsistent or the graph is incorrect (i.e., the parents of some or all of the nodes in the graph are incorrect), then this distribution of p-values will not be Uniform. To visualize this, we display the histogram of the p-values with equally sized bins; the bars in this histogram, for this case, should ideally all be of equal height.

                If the first bar in this histogram is especially high (for the p-value case), that means that many tests are being judged as dependent. For checking the dependent distribution, one hopes that this list is non-empty, in which case this first bar will be especially high since high p-values are examples where the graph is unfaithful to the distribution. These are possibly for cases where paths in the graph cancel unfaithfully. But for checking Markov, one hopes that this first bar will be the same height as all of the other bars.

                To make it especially clear, we give two statistics in the interface. The first is the percentage of p-values judged dependent on the test. If an alpha level is used in the test, this number should be very close to the alpha level for the Local Markov check since the distribution of p-values under this condition is Uniform. For the second, we test the Uniformity of the p-values using a Kolmogorov-Smirnov test. The p-value returned by this test should be greater than the user’s preferred alpha level if the distribution of p-values is Uniform and less than this alpha level if the distribution of p-values is non-uniform.
                `
                If the independence test yields a bump in the score, this score should be negative for independence judgments and positive for dependence judgments. The histogram will reflect this.

                Feel free to select all of the data in the tables, copy it, and paste it into Excel. This will let you analyze the data yourself.

                A note about Markov Blankets: The "Markov Blanket" conditioning set choice implements the Markov blanket calculation in a way that is correct for DAGs, CPDAGs, MAGs, and PAGs. For all of these graph types, the list of m-connecting facts in the Faithfulness tab should be empty since the Markov blanket should screen off the target from any other variables in the dataset. It's possible that for some other graph types, this list may not be empty (i.e., the Markov blanket calculation may not be correct).

                Knowledge may be supplied to the Markov Checker. This will be interpreted as follows. For X _||_ Y | Z checked, X and Y will be drawn from the last tier of the knowledge, and the variables in Z will be drawn from all variables in tiers. Additional forbidden or required edges are not allowed.

                A field is provided, allowing the users to specify using regexes (regular expressions) to display only a subset of the rows in a table. The expressions used are slight deviations of usual regular expressions in that the characters '(', ')', and '|' do not need to be escaped '\\(', '\\)', '\\|") to match an expression with those characters. Rather, to use those characters to control the regexes, the escape sequences should be used. This is because independence facts like "Ind(X, Y | Z)" are common for Tetrad. Note that when a table is subsetted using regexes, the statistics at the bottom of the table will be updated to reflect the subsetted table. The regexes are separated by semicolons.               \s
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
        histogram.setNumBins(10);
        HistogramPanel view = new HistogramPanel(histogram, true);

        Color fillColor = new Color(113, 165, 210);
        view.setBarColor(fillColor);

        view.setMinimumSize(new Dimension(300, 200));
        view.setMaximumSize(new Dimension(300, 200));
        return view;
    }

    @NotNull
    private static DocumentListener getFilterListener(JTextField filterText, TableRowSorter<AbstractTableModel> sorter) {
        return new DocumentListener() {

            /**
             * Filters the table based on the text in the text field.
             */
            private void filter() {
                String text = filterText.getText();
                if (text.trim().isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    String[] textParts = text.split(";+");
                    List<RowFilter<Object, Object>> filters = new ArrayList<>(textParts.length);
                    for (String part : textParts) {
                        try {

                            String trim = part.trim();

                            if (isFlipEscapes()) {
                                // Swap escapes for parentheses and pipes
                                trim = trim.replace("\\(", "<+++<");
                                trim = trim.replace("\\)", ">+++>");
                                trim = trim.replace("\\|", "|+++|");
                                trim = trim.replace("(", "\\(");
                                trim = trim.replace(")", "\\)");
                                trim = trim.replace("|", "\\|");
                                trim = trim.replace("<+++<", "(");
                                trim = trim.replace(">+++>", ")");
                                trim = trim.replace("|+++|", "|");
                            }

                            filters.add(RowFilter.regexFilter(trim));
                        } catch (PatternSyntaxException e) {
                            // ignore
                        }
                    }
                    sorter.setRowFilter(RowFilter.orFilter(filters));
                }
            }

            /**
             * Inserts text into the text field.
             *
             * @param e the document event.
             */
            @Override
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            /**
             * Removes text from the text field.
             *
             * @param e the document event.
             */
            @Override
            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            /**
             * Changes text in the text field.
             *
             * @param e the document event.
             */
            @Override
            public void changedUpdate(DocumentEvent e) {
                // this method won't be called for plain text fields
            }
        };
    }

    private static boolean isFlipEscapes() {
        return flipEscapes;
    }

    private void initComponents(JButton params, JButton resample, JButton addSample, JTabbedPane pane, JLabel conditioningSetsLabel, JLabel percentSampleLabel) {
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
                                                        .addComponent(resample)
                                                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                                        .addComponent(addSample)
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
                                .addComponent(resample)
                                .addComponent(addSample)
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

    //========================PUBLIC METHODS==========================//
    private void refreshResult(MarkovCheckIndTestModel model, JTable tableIndep, JTable tableDep,
                               AbstractTableModel tableModelIndep, AbstractTableModel tableModelDep,
                               DoubleTextField percent, boolean clear) {
        SwingUtilities.invokeLater(() -> {
            setTest();

            tableModelIndep.fireTableDataChanged();
            tableModelDep.fireTableDataChanged();

            model.getMarkovCheck().setPercentResample(percent.getValue());
            model.getMarkovCheck().generateResults(clear);
            tableModelIndep.fireTableDataChanged();
            tableModelDep.fireTableDataChanged();
            updateTables(model, tableIndep, tableDep);
        });
    }

    private void setTest() {
        IndependenceTestModel selectedItem = (IndependenceTestModel) indTestJComboBox.getSelectedItem();

        Class<IndependenceWrapper> clazz = (selectedItem == null) ? null
                : (Class<IndependenceWrapper>) selectedItem.getIndependenceTest().clazz();
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
                TetradLogger.getInstance().log("Error: " + e1.getMessage());
                throw new RuntimeException(e1);
            }
        }

    }

    private JPanel buildGuiIndep() {
        Box tableBox = Box.createVerticalBox();

        String setType = (String) conditioningSetTypeJComboBox.getSelectedItem();

        conditioningLabelIndep.setText("Tests graphical predictions of Indep(X, Y | " + setType + ")");
        tableBox.add(conditioningLabelIndep, BorderLayout.NORTH);

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
//                else if (model.getMarkovCheck().isCpdag() && column == 4) {
//                    return "Min Beta";
//                }

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
                if (columnIndex == 0) {
                    return rowIndex + 1;
                }

                if (rowIndex >= model.getResults(true).size()) {
                    return null;
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

        tableIndep = new JTable(tableModelIndep);

        tableModelIndep.addTableModelListener(e -> {
            if (e.getColumn() == 2) {
                tableIndep.revalidate();
                tableIndep.repaint();
            }
        });

        tableIndep.getColumnModel().getColumn(0).setMaxWidth(40);
        tableIndep.getColumnModel().getColumn(1).setPreferredWidth(200);
        tableIndep.getColumnModel().getColumn(1).setCellRenderer(new Renderer());
        tableIndep.getColumnModel().getColumn(2).setPreferredWidth(100);
        tableIndep.getColumnModel().getColumn(3).setPreferredWidth(100);
        tableIndep.getColumnModel().getColumn(2).setCellRenderer(new Renderer());
        tableIndep.getColumnModel().getColumn(3).setCellRenderer(new Renderer());

        JTableHeader header = tableIndep.getTableHeader();

        header.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                JTableHeader header = (JTableHeader) e.getSource();
                Point point = e.getPoint();
                int col = header.columnAtPoint(point);
                int sortCol = header.getTable().convertColumnIndexToModel(col);

                MarkovCheckEditor.this.sortByColumn(sortCol, true);
            }
        });

        flipEscapesIndep = new JCheckBox("Flip escapes ()|");
        flipEscapesIndep.setSelected(flipEscapes);
        flipEscapesIndep.addActionListener(e -> {
            flipEscapes = flipEscapesIndep.isSelected();
            flipEscapesDep.setSelected(isFlipEscapes());
        });

        addFilterPanel(model, tableModelIndep, tableIndep, tableBox, flipEscapesIndep);

        Box b10 = Box.createHorizontalBox();
        b10.add(Box.createHorizontalGlue());
        JLabel label = new JLabel("Table contents can be selected and copied in to, e.g., Excel.");
        b10.add(label);
        b10.add(Box.createHorizontalStrut(20));

        JLabel label1 = new JLabel("# independencies = " + model.getResults(true).size());
        b10.add(label1);
        b10.add(Box.createHorizontalGlue());

        // Setup a Timer to call update every 5 seconds
        javax.swing.Timer timer = new javax.swing.Timer(1000,
                e -> label1.setText("# independencies = " + model.getResults(true).size()));
        timer.start();

        tableBox.add(b10, BorderLayout.SOUTH);

        setLabelTexts();

        Box a4 = Box.createVerticalBox();
        histogramPanelIndep = new JPanel();
        histogramPanelIndep.setLayout(new BorderLayout());
        histogramPanelIndep.setBorder(new EmptyBorder(10, 10, 10, 10));
        histogramPanelIndep.add(createHistogramPanel(model.getResults(true)), BorderLayout.CENTER);
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

//        Box a8 = Box.createHorizontalBox();
//        a8.add(Box.createHorizontalGlue());
//        a8.add(andersonDarlingA2LabelIndep);
//        a4.add(a8);

        Box a9 = Box.createHorizontalBox();
        a9.add(Box.createHorizontalGlue());
        a9.add(andersonDarlingPLabelIndep);
        a4.add(a9);

        JPanel checkMarkovPanel = new JPanel(new BorderLayout());
        checkMarkovPanel.add(new PaddingPanel(tableBox), BorderLayout.CENTER);
        checkMarkovPanel.add(new PaddingPanel(a4), BorderLayout.EAST);

        return checkMarkovPanel;
    }

    private void addFilterPanel(MarkovCheckIndTestModel model, AbstractTableModel tableModel, JTable table,
                                Box panel, JCheckBox flipEscapes) {
        TableRowSorter<AbstractTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        Box nodeSelectionBox = Box.createHorizontalBox();
        nodeSelectionBox.add(new JLabel("Node Selection:"));
        JComboBox<String> nodeSelection = new JComboBox<>();
        nodeSelection.addItem("All");

        List<String> names = new ArrayList<>();

        for (Node node : model.getGraph().getNodes()) {
            names.add(node.getName());
        }

        names.sort((o1, o2) -> {
            // If o1 ends with an integer, find that integer.
            // If o2 ends with an integer, find that integer.
            // If both end with an integer, compare the integers.

            String[] split1 = o1.split("(?<=\\D)(?=\\d)");
            String[] split2 = o2.split("(?<=\\D)(?=\\d)");

            if (split1.length == 2 && split2.length == 2) {
                String prefix1 = split1[0];
                String prefix2 = split2[0];

                if (prefix1.equals(prefix2)) {
                    return Integer.compare(Integer.parseInt(split1[1]), Integer.parseInt(split2[1]));
                } else {
                    return prefix1.compareTo(prefix2);
                }
            } else if (split1.length == 2) {
                return -1;
            } else if (split2.length == 2) {
                return 1;
            } else {
                return o1.compareTo(o2);
            }
        });

        for (String name : names) {
            nodeSelection.addItem(name);
        }

        nodeSelection.addActionListener(e -> {
            String selectedNode = (String) nodeSelection.getSelectedItem();
            if (selectedNode.equals("All")) {
                sorter.setRowFilter(null);
            } else {
                String a = selectedNode;
                String regex = String.format("(\\(%s,)|(, %s \\|)|(, %s\\)^)", a, a, a);
                sorter.setRowFilter(RowFilter.regexFilter(regex));
            }
        });

        nodeSelectionBox.add(nodeSelection);
        nodeSelectionBox.add(Box.createHorizontalGlue());


        // Create the text field
        JLabel regexLabel = new JLabel("Regexes (semicolon separated):");
        JTextField filterText = new JTextField(15);
        filterText.setMaximumSize(new Dimension(600, 20));
        regexLabel.setLabelFor(filterText);

        // Create a listener for the text field that will update the table's row sort
        filterText.getDocument().addDocumentListener(getFilterListener(filterText, sorter));

        sorter.addRowSorterListener(e -> {
            if (e.getType() == RowSorterEvent.Type.SORTED) {
                updateTables(model, tableIndep, tableDep);
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(550, 400));

        Box filterBox = Box.createHorizontalBox();
//        filterBox.add(regexLabel);
//        filterBox.add(filterText);
        filterBox.add(nodeSelectionBox);
//        filterBox.add(flipEscapes);
        panel.add(filterBox);
        panel.add(scroll);
    }

    private void updateTables(MarkovCheckIndTestModel model, JTable tableIndep, JTable tableDep) {

        {

            SwingUtilities.invokeLater(() -> {

                List<IndependenceResult> results = model.getResults(true);

                List<IndependenceResult> visiblePairs = new ArrayList<>();
                int rowCount = tableIndep.getRowCount();

                for (int i = 0; i < rowCount; i++) {
                    int modelIndex = tableIndep.convertRowIndexToModel(i);

                    if (modelIndex > -1 && modelIndex < results.size()) {
                        visiblePairs.add(results.get(modelIndex));
                    }
                }

                double fractionDependent = model.getMarkovCheck().getFractionDependent(visiblePairs);

                fractionDepLabelIndep.setText(
                        "Fraction dependent = " + ((Double.isNaN(fractionDependent)) ?
                                "NaN" : nf.format(fractionDependent))
                );

                ksLabelIndep.setText(
                        "KS p-value = " + nf.format(model.getMarkovCheck().getKsPValue(visiblePairs))
                );

                binomialPLabelIndep.setText(
                        "Binomial p-value = " + nf.format(model.getMarkovCheck().getBinomialPValue(visiblePairs))
                );

//            andersonDarlingA2LabelIndep.setText(
//                    "Anderson-Darling A^2 = " + nf.format(model.getMarkovCheck().getAndersonDarlingA2(visiblePairs))
//            );

                andersonDarlingPLabelIndep.setText(
                        "Anderson-Darling p-value = " + nf.format(model.getMarkovCheck().getAndersonDarlingPValue(visiblePairs))
                );

                histogramPanelIndep.removeAll();
                histogramPanelIndep.add(createHistogramPanel(visiblePairs), BorderLayout.CENTER);
                histogramPanelIndep.validate();
                histogramPanelIndep.repaint();
            });

//            histogramPanelIndep.removeAll();
//            histogramPanelIndep.add(createHistogramPanel(visiblePairs), BorderLayout.CENTER);
//            histogramPanelIndep.validate();
//            histogramPanelIndep.repaint();
        }

        {
            SwingUtilities.invokeLater(() -> {

                List<IndependenceResult> results = model.getResults(false);

                List<IndependenceResult> visiblePairs = new ArrayList<>();
                int rowCount = tableDep.getRowCount();

                for (int i = 0; i < rowCount; i++) {
                    int modelIndex = tableDep.convertRowIndexToModel(i);

                    if (modelIndex > -1 && modelIndex < results.size()) {
                        visiblePairs.add(results.get(modelIndex));
                    }
                }

                double fractionDependent = model.getMarkovCheck().getFractionDependent(visiblePairs);

                fractionDepLabelDep.setText(
                        "Fraction dependent = " + ((Double.isNaN(fractionDependent)) ?
                                "NaN" : nf.format(fractionDependent))
                );

                ksLabelDep.setText(
                        "KS p-value = " + nf.format(model.getMarkovCheck().getKsPValue(visiblePairs))
                );

                binomialPLabelDep.setText(
                        "Binomial p-value = " + nf.format(model.getMarkovCheck().getBinomialPValue(visiblePairs))
                );

//            andersonDarlingA2LabelDep.setText(
//                    "Anderson-Darling A^2 = " + nf.format(model.getMarkovCheck().getAndersonDarlingA2(visiblePairs))
//            );

                andersonDarlingPLabelDep.setText(
                        "Anderson-Darling p-value = " + nf.format(model.getMarkovCheck().getAndersonDarlingPValue(visiblePairs))
                );

                histogramPanelDep.removeAll();
                histogramPanelDep.add(createHistogramPanel(visiblePairs), BorderLayout.CENTER);
                histogramPanelDep.validate();
                histogramPanelDep.repaint();
            });

//            histogramPanelDep.removeAll();
//            histogramPanelDep.add(createHistogramPanel(visiblePairs), BorderLayout.CENTER);
//            histogramPanelDep.validate();
//            histogramPanelDep.repaint();
        }
    }

    /**
     * Performs the action of opening a session from a file.
     */
    private JPanel buildGuiDep() {
        Box tableBox = Box.createVerticalBox();

        String setType = (String) conditioningSetTypeJComboBox.getSelectedItem();

        conditioningLabelDep.setText("Tests graphical predictions of Dep(X, Y | " + setType + ")");
        tableBox.add(conditioningLabelDep, BorderLayout.NORTH);

        markovTestLabel.setText(model.getMarkovCheck().getIndependenceTest().toString());
        testLabel.setText(model.getMarkovCheck().getIndependenceTest().toString());

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
//                else if (model.getMarkovCheck().isCpdag() && column == 4) {
//                    return "Min Beta";
//                }

                return null;
            }

            public int getColumnCount() {
                return 4;
            }

            public int getRowCount() {
                List<IndependenceResult> results = model.getResults(false);
                return results.size();
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                if (columnIndex == 0) {
                    return rowIndex + 1;
                }

                if (rowIndex >= model.getResults(false).size()) {
                    return null;
                }

                IndependenceResult result = model.getResults(false).get(rowIndex);

                if (columnIndex == 1) {
                    IndependenceFact fact = model.getResults(false).get(rowIndex).getFact();
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

        tableDep = new JTable(tableModelDep);

        tableModelDep.addTableModelListener(e -> {
            if (e.getColumn() == 2) {
                tableDep.revalidate();
                tableDep.repaint();
            }
        });

        tableDep.getColumnModel().getColumn(0).setMaxWidth(40);
        tableDep.getColumnModel().getColumn(1).setPreferredWidth(200);
        tableDep.getColumnModel().getColumn(1).setCellRenderer(new Renderer());
        tableDep.getColumnModel().getColumn(2).setPreferredWidth(100);
        tableDep.getColumnModel().getColumn(3).setPreferredWidth(100);
        tableDep.getColumnModel().getColumn(2).setCellRenderer(new Renderer());
        tableDep.getColumnModel().getColumn(3).setCellRenderer(new Renderer());

        JTableHeader header = tableDep.getTableHeader();

        header.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                JTableHeader header = (JTableHeader) e.getSource();
                Point point = e.getPoint();
                int col = header.columnAtPoint(point);
                int sortCol = header.getTable().convertColumnIndexToModel(col);

                MarkovCheckEditor.this.sortByColumn(sortCol, true);
            }
        });

        flipEscapesDep = new JCheckBox("Flip escapes ()|");
        flipEscapesDep.setSelected(flipEscapes);
        flipEscapesDep.addActionListener(e -> {
            flipEscapes = flipEscapesDep.isSelected();
            flipEscapesDep.setSelected(isFlipEscapes());
        });

        addFilterPanel(model, tableModelDep, tableDep, tableBox, flipEscapesDep);

        Box b10 = Box.createHorizontalBox();
        b10.add(Box.createHorizontalGlue());
        JLabel label = new JLabel("Table contents can be selected and copied in to, e.g., Excel.");
        b10.add(label);
        b10.add(Box.createHorizontalStrut(20));

        JLabel label1 = new JLabel("# Dependencies = " + model.getResults(false).size());
        b10.add(label1);
        b10.add(Box.createHorizontalGlue());

        // Setup a Timer to call update every 5 seconds
        javax.swing.Timer timer = new javax.swing.Timer(1000,
                e -> label1.setText("# Dependencies = " + model.getResults(false).size()));
        timer.start();

        tableBox.add(b10, BorderLayout.SOUTH);

        setLabelTexts();

        Box a4 = Box.createVerticalBox();
        histogramPanelDep = new JPanel();
        histogramPanelDep.setLayout(new BorderLayout());
        histogramPanelDep.setBorder(new EmptyBorder(10, 10, 10, 10));
        histogramPanelDep.add(createHistogramPanel(model.getResults(false)), BorderLayout.CENTER);
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

//        Box a8 = Box.createHorizontalBox();
//        a8.add(Box.createHorizontalGlue());
//        a8.add(andersonDarlingA2LabelDep);
//        a4.add(a8);

        Box a9 = Box.createHorizontalBox();
        a9.add(Box.createHorizontalGlue());
        a9.add(andersonDarlingPLabelDep);
        a4.add(a9);

        JPanel checkMarkovPanel = new JPanel(new BorderLayout());
        checkMarkovPanel.add(new PaddingPanel(tableBox), BorderLayout.CENTER);
        checkMarkovPanel.add(new PaddingPanel(a4), BorderLayout.EAST);

        return checkMarkovPanel;
    }

    /**
     * Sorts the table by the specified column in either ascending or descending order.
     *
     * @param sortCol The index of the column to sort by.
     * @param indep   {@code true} if sorting the independent table, {@code false} if sorting the dependent table.
     */
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

    /**
     * Sets the text of various JLabels in the class. This method initializes the JLabels if they are null and sets the
     * text of each JLabel based on the corresponding values in the MarkovCheck object.
     * <p>
     * The method sets the text for the following JLabels: - ksLabelIndep - ksLabelDep - binomialPLabelIndep -
     * binomialPLabelDep - andersonDarlingA2LabelIndep - andersonDarlingA2LabelDep - andersonDarlingPLabelIndep -
     * andersonDarlingPLabelDep - fractionDepLabelIndep - fractionDepLabelDep - conditioningLabelIndep -
     * conditioningLabelDep
     */
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

//        if (andersonDarlingA2LabelIndep == null) {
//            andersonDarlingA2LabelIndep = new JLabel();
//        }
//
//        if (andersonDarlingA2LabelDep == null) {
//            andersonDarlingA2LabelDep = new JLabel();
//        }

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

//        andersonDarlingA2LabelIndep.setText("A^2 = "
//                                            + ((Double.isNaN(model.getMarkovCheck().getAndersonDarlingA2Star(true))
//                ? "-"
//                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getAndersonDarlingA2Star(true)))));
//        andersonDarlingA2LabelDep.setText("A^2* = "
//                                          + ((Double.isNaN(model.getMarkovCheck().getAndersonDarlingA2Star(false))
//                ? "-"
//                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getAndersonDarlingA2Star(false)))));

        andersonDarlingPLabelIndep.setText("P-value of the Anderson-Darling test = "
                                           + ((Double.isNaN(model.getMarkovCheck().getAndersonDarlingP(true))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getAndersonDarlingP(true)))));
        andersonDarlingPLabelDep.setText("P-value of the Anderson-Darling test = "
                                         + ((Double.isNaN(model.getMarkovCheck().getAndersonDarlingP(false))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getAndersonDarlingP(false)))));
        binomialPLabelIndep.setText("P-value of Binomial Test = "
                                    + ((Double.isNaN(model.getMarkovCheck().getBinomialPValue(true))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getBinomialPValue(true)))));
        binomialPLabelDep.setText("P-value of Binomial Test = "
                                  + ((Double.isNaN(model.getMarkovCheck().getBinomialPValue(false))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getBinomialPValue(false)))));
        fractionDepLabelIndep.setText("Fraction dependent = "
                                      + ((Double.isNaN(model.getMarkovCheck().getFractionDependent(true))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getFractionDependent(true)))));
        fractionDepLabelDep.setText("Fraction dependent = "
                                    + ((Double.isNaN(model.getMarkovCheck().getFractionDependent(false))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getFractionDependent(false)))));

        conditioningLabelIndep.setText("Tests graphical predictions of Indep(X, Y | " + conditioningSetTypeJComboBox.getSelectedItem() + ")");
        conditioningLabelDep.setText("Tests graphical predictions of Dep(X, Y | " + conditioningSetTypeJComboBox.getSelectedItem() + ")");
    }

    private int getLastSortCol() {
        return this.lastSortCol;
    }

    /**
     * Sets the last sort column for the table.
     *
     * @param lastSortCol The last sort column. Must be between 0 and 4 (inclusive).
     * @throws IllegalArgumentException If the last sort column is out of range.
     */
    private void setLastSortCol(int lastSortCol) {
        if (lastSortCol < 0 || lastSortCol > 4) {
            throw new IllegalArgumentException();
        }

        this.lastSortCol = lastSortCol;
    }

    /**
     * Returns the sort direction for the table column.
     *
     * @return The sort direction for the table column. Must be either 1 (ascending) or -1 (descending).
     */
    private int getSortDir() {
        return this.sortDir;
    }

    /**
     * Sets the sort direction for the specified column in the table.
     *
     * @param sortDir The sort direction to set. Must be either 1 (ascending) or -1 (descending).
     * @throws IllegalArgumentException If the sort direction is not valid.
     */
    private void setSortDir(int sortDir) {
        if (!(sortDir == 1 || sortDir == -1)) {
            throw new IllegalArgumentException();
        }

        this.sortDir = sortDir;
    }

    /**
     * Creates a histogram panel to display the results.
     *
     * @param results The list of IndependenceResult objects to display in the histogram.
     * @return The Box containing the histogram panel.
     */
    private Box createHistogramPanel(List<IndependenceResult> results) {
        if (results.isEmpty()) {
            return Box.createVerticalBox();
        }

        HistogramPanel view = getHistogramPanel(results);
        Box box = Box.createVerticalBox();
        box.add(view);
        return box;
    }

    /**
     * Returns the data type of the data set.
     *
     * @return the data type of the data set as a DataType enum object: Continuous, Discrete, Mixed, Covariance, or null
     * if the data set does not match any of the types.
     */
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

    // Parameter panel code from Kevin Bui.

    /**
     * Refreshes the test list in the GUI. Retrieves the data type of the data set. Removes all items from the test
     * combo box. Retrieves the independence test models for the given data type. Adds the independence test models to
     * the test combo box. Disables the test combo box if there are no items. Selects the default model for the data
     * type.
     */
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

    /**
     * Creates a parameters panel for the given independence wrapper and parameters.
     *
     * @param independenceWrapper The independence wrapper implementation.
     * @param params              The parameters for the independence test.
     * @return The JPanel containing the parameters panel.
     */
    private JPanel createParamsPanel(IndependenceWrapper independenceWrapper, Parameters params) {
        Set<String> testParameters = new HashSet<>(independenceWrapper.getParameters());
        return createParamsPanel(testParameters, params);
    }

    /**
     * Creates a parameters panel for the given set of parameters and Parameters object.
     *
     * @param params     The set of parameter names.
     * @param parameters The Parameters object containing the parameter values.
     * @return The JPanel containing the parameters panel.
     */
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

    /**
     * Creates a map of parameter components for the given set of parameters and Parameters object.
     *
     * @param params     The set of parameter names.
     * @param parameters The Parameters object containing the parameter values.
     * @return A map of parameter names to Box components.
     */
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

    /**
     * Creates a parameter component based on the given parameter, Parameters, and ParamDescription.
     *
     * @param parameter  The name of the parameter.
     * @param parameters The Parameters object containing the parameter values.
     * @param paramDesc  The ParamDescription object with information about the parameter.
     * @return A Box component representing the parameter component.
     * @throws IllegalArgumentException If the default value type is unexpected.
     */
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

    /**
     * Returns a DoubleTextField with specified parameters.
     *
     * @param parameter    The name of the parameter.
     * @param parameters   The Parameters object containing the parameter values.
     * @param defaultValue The default value for the DoubleTextField.
     * @param lowerBound   The lower bound for valid values.
     * @param upperBound   The upper bound for valid values.
     * @return A DoubleTextField with the specified parameters.
     */
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

    /**
     * Returns an IntTextField with the specified parameters.
     *
     * @param parameter    The name of the parameter.
     * @param parameters   The Parameters object containing the parameter values.
     * @param defaultValue The default value for the IntTextField.
     * @param lowerBound   The lower bound for valid values.
     * @param upperBound   The upper bound for valid values.
     * @return An IntTextField with the specified parameters.
     */
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

    /**
     * Returns a LongTextField object with the specified parameters.
     *
     * @param parameter    The name of the parameter.
     * @param parameters   The Parameters object containing the parameter values.
     * @param defaultValue The default value for the LongTextField.
     * @param lowerBound   The lower bound for valid values.
     * @param upperBound   The upper bound for valid values.
     * @return A LongTextField object with the specified parameters.
     */
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

    /**
     * Creates a boolean selection box with Yes and No radio buttons.
     *
     * @param parameter    The name of the parameter.
     * @param parameters   The Parameters object containing the parameter values.
     * @param defaultValue The default value for the boolean parameter
     */
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

    /**
     * Returns a StringTextField object with the specified parameters.
     *
     * @param parameter    The name of the parameter.
     * @param parameters   The Parameters object containing the parameter values.
     * @param defaultValue The default value for the StringTextField.
     * @return A StringTextField object with the specified parameters.
     */
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

    /**
     * Custom renderer for the cells of a JTable.
     */
    static class Renderer extends DefaultTableCellRenderer {

        /**
         * The private variable `table` is an instance of the JTable class.
         * <p>
         * This variable represents a table component that displays data in a tabular form. It is typically used to
         * display data from a data source, such as a database or an array.
         * <p>
         * The JTable class provides methods for manipulating the table data, formatting the table appearance, and
         * handling user interactions.
         * <p>
         * The JTable component is usually added to a container, such as a JFrame or a JPanel, to be displayed in a
         * graphical user interface.
         */
        private JTable table;

        /**
         * This private variable represents whether a cell is selected in a JTable.
         * <p>
         * The JTable is a component that displays data in a tabular form. It is used to display data from a data
         * source, such as a database or an array. The Renderer class is a custom renderer for the cells of the JTable,
         * and this variable is used to determine if a cell is selected.
         * <p>
         * When a cell is selected, the selected variable is set to true. Otherwise, it is set to false.
         * <p>
         * The selected variable is used in the setValue method of the Renderer class to set the foreground and
         * background colors of the cell based on its selection state.
         * <p>
         * This variable is primarily used internally within the Renderer class and should not be accessed or modified
         * directly.
         *
         * @see Renderer
         */
        private boolean selected;

        /**
         * Custom renderer for the cells of a JTable.
         * <p>
         * The Renderer class is a custom renderer for the cells of a JTable component. It is used to control the
         * appearance of the cells in the table.
         */
        public Renderer() {
        }

        /**
         * Sets the value for the cell.
         * <p>
         * This method is used to set the value of a cell in a JTable component. The value can be any object, but it
         * will be converted to a string representation before being displayed in the cell.
         * <p>
         * If the value is null, the text value of the cell will be set to an empty string.
         * <p>
         * The appearance of the cell's foreground and background colors will also be updated based on its selection
         * state.
         *
         * @param value the string value for this cell; if value is null, it sets the text value to an empty string
         */
        public void setValue(Object value) {
            if (value == null) return;

            if (selected) {
                setForeground(table.getSelectionForeground());
                setBackground(table.getSelectionBackground());
            } else {
                this.setForeground(table.getForeground());
                this.setBackground(table.getBackground());
            }

            this.setText(value.toString());
        }

        /**
         * Returns the component used for rendering the cell at the specified row and column in the table.
         *
         * @param table      the <code>JTable</code> that is asking the renderer to draw; can be <code>null</code>
         * @param value      the value of the cell to be rendered
         * @param isSelected true if the cell is selected; otherwise false
         * @param hasFocus   true if the cell has focus; otherwise false
         * @param row        the row index of the cell being rendered
         * @param column     the column index of the cell being rendered
         * @return the component used for rendering the cell
         */
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            this.table = table;
            selected = isSelected;
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
    }
}

