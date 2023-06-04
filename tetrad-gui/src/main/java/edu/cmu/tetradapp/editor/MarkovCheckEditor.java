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
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndTestMSep;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.MarkovCheckIndTestModel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModels;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.UniformityTest;
import edu.cmu.tetradapp.util.WatchedProcess;
import org.apache.commons.math3.util.FastMath;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.text.NumberFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.apache.commons.math3.util.FastMath.min;


/**
 * Lists independence facts specified by user and allows the list to be sorted by independence fact or by p value.
 *
 * @author josephramsey
 */
public class MarkovCheckEditor extends JPanel {
    private final MarkovCheckIndTestModel model;
    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    private final IndTestMSep msep;
    private final Graph graph;
    private boolean parallelized = false;
    private final JLabel markovTestLabel = new JLabel("(Unspecified Test)");
    private final Parameters parameters;
    private AbstractTableModel tableModelIndep;
    private AbstractTableModel tableModelDep;
    private int sortDir;
    private int lastSortCol;
    private final JTextArea testDescTextArea = new JTextArea();
    private double fractionDependentIndep = Double.NaN;
    private double fractionDependentDep = Double.NaN;
    private JLabel fractionDepLabelIndep;
    private JLabel fractionDepLabelDep;
    private JLabel ksLabelDep;
    private JLabel ksLabelIndep;
    private final JComboBox<IndependenceTestModel> indTestComboBox = new JComboBox<>();
    boolean updatingTestModels = true;
    private IndependenceTest independenceTest;
    private final DataModel dataSet;
    private final JLabel faithfulnessTestLabel = new JLabel("(Unspecified Test)");
    private IndependenceWrapper independenceWrapper;
    private JTextField textField;

    /**
     * Constructs a new editor for the given model.
     */
    public MarkovCheckEditor(MarkovCheckIndTestModel model) {
        if (model == null) {
            throw new NullPointerException("Expecting a model");
        }

        this.model = model;

        this.dataSet = model.getDataModel();
        this.graph = model.getGraph();
        this.parameters = model.getParameters();

        refreshTestList();
        setTest();

        indTestComboBox.addActionListener(e -> {
            setTest();

            if (model.getResults(true).isEmpty() || model.getResults(false).isEmpty()) {
                generateResults(true);
                generateResults(false);
            }
        });

        Graph sourceGraph = model.getGraph();
        List<Node> variables = independenceTest.getVariables();

        List<Node> newVars = new ArrayList<>();

        for (Node node : variables) {
            if (sourceGraph.getNode(node.getName()) != null) {
                newVars.add(node);
            }
        }

        sourceGraph = edu.cmu.tetrad.graph.GraphUtils.replaceNodes(sourceGraph, newVars);

        List<Node> missingVars = new ArrayList<>();

        for (Node w : sourceGraph.getNodes()) {
            if (independenceTest.getVariable(w.getName()) == null) {
                missingVars.add(w);
                if (missingVars.size() >= 5) break;
            }
        }

        if (!missingVars.isEmpty()) {
            throw new IllegalArgumentException("At least these variables in the DAG are missing from the data:" +
                    "\n    " + missingVars);
        }

        msep = new IndTestMSep(this.graph);
        model.setVars(this.graph.getNodeNames());

        JPanel indep = buildGuiIndep();
        JPanel dep = buildGuiDep();

        Box box = Box.createVerticalBox();
        Box box1 = Box.createHorizontalBox();
        box1.add(indTestComboBox);
        box1.add(Box.createHorizontalStrut(10));
        JButton params = new JButton("Params");

        params.addActionListener(e -> {
//            setTest();
            JOptionPane dialog = new JOptionPane(createParamsPanel(independenceWrapper, parameters), JOptionPane.PLAIN_MESSAGE);
            dialog.createDialog("Set Parameters").setVisible(true);
            setTest();
            generateResults(true);
            generateResults(false);
        });

        box1.add(params);
        box.add(box1);

        JTabbedPane pane = new JTabbedPane();
        pane.addTab("Check Local Markov", indep);
        pane.addTab("Check Local Faithfulness", dep);
        box.add(pane);

        add(box);

        generateResults(true);
        generateResults(false);

        invalidate();
        repaint();
    }

    private void setTest() {
        IndependenceTestModel selectedItem = (IndependenceTestModel) indTestComboBox.getSelectedItem();
        Class<IndependenceWrapper> clazz = (selectedItem == null) ? null : selectedItem.getIndependenceTest().getClazz();

        if (clazz != null) {
            try {
                independenceWrapper = clazz.getDeclaredConstructor(new Class[0]).newInstance();
                independenceTest = independenceWrapper.getTest(dataSet, parameters);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e1) {
                throw new RuntimeException(e1);
            }
        }

        if (independenceTest == null) {
            throw new NullPointerException("Expecting a test");
        }

        markovTestLabel.setText(independenceTest.toString());
        faithfulnessTestLabel.setText(independenceTest.toString());

        model.getResults(true).clear();
        model.getResults(false).clear();

        invalidate();
        repaint();
    }

    //========================PUBLIC METHODS==========================//

    /**
     * Performs the action of opening a session from a file.
     */
    private JPanel buildGuiDep() {
        Box b1 = Box.createVerticalBox();
        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Tests whether X _||_ Y | parents(X) for mconn(X, Y | parents(X))"));
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);

        markovTestLabel.setText(independenceTest.toString());
        faithfulnessTestLabel.setText(independenceTest.toString());

        Box b2a = Box.createHorizontalBox();
        b2a.add(Box.createHorizontalGlue());
        b1.add(b2a);
        b1.add(Box.createVerticalStrut(5));

        List<IndependenceResult> results = model.getResults(false);
        this.tableModelDep = new AbstractTableModel() {
            public String getColumnName(int column) {
                if (column == 0) {
                    return "Index";
                } else if (column == 1) {
                    return "Graphical Prediction";
                } else if (column == 2) {
                    return "Test Result";
                } else if (column == 3) {
                    return "P-Value or Bump";
                }

                return null;
            }

            public int getColumnCount() {
                return 4;//2 + MarkovFactsEditor.this.indTestProducers.size();
            }

            public int getRowCount() {
                return results.size();
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                if (rowIndex > results.size()) return null;

                if (columnIndex == 0) {
                    return rowIndex + 1;
                }
                if (columnIndex == 1) {
                    IndependenceFact fact = results.get(rowIndex).getFact();

                    List<Node> Z = new ArrayList<>(fact.getZ());
                    Collections.sort(Z);

                    String z = Z.stream().map(Node::getName).collect(Collectors.joining(", "));

                    return "Dep(" + fact.getX() + ", " + fact.getY() + (Z.isEmpty() ? "" : " | " + z) + ")";
                }

                IndependenceResult result = results.get(rowIndex);

                if (columnIndex == 2) {
                    if (getIndependenceTest() instanceof IndTestMSep) {
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

            public Class getColumnClass(int columnIndex) {
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
        scroll.setPreferredSize(new Dimension(400, 400));
        b1.add(scroll);

        Box b4 = Box.createHorizontalBox();
        b4.add(Box.createGlue());
        b4.add(Box.createHorizontalStrut(10));

        String title = "P-Value or Bump for Local Faithfulness";

        JButton showHistogram = new JButton("Show Histogram for P-Values or Bumps");
        showHistogram.setFont(new Font("Dialog", Font.PLAIN, 14));
        showHistogram.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JPanel component = createHistogramPanel(false);
                EditorWindow editorWindow = new EditorWindow(component, title, "Close", false, MarkovCheckEditor.this);
                DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
                editorWindow.pack();
                editorWindow.setVisible(true);
            }
        });

        b4.add(Box.createHorizontalGlue());
        b4.add(showHistogram);

        JButton help = new JButton("Help");

        help.addActionListener(e -> {
            String text = getHelpMessage();
            JOptionPane.showMessageDialog(help, text, "Help", JOptionPane.INFORMATION_MESSAGE);
        });

        b4.add(help);

        b1.add(b4);
        b1.add(Box.createVerticalStrut(10));

        Box b5 = Box.createHorizontalBox();
        b5.add(Box.createGlue());

        int dependent = 0;

        for (IndependenceResult result : results) {
            if (result.isDependent() && !Double.isNaN(result.getPValue())) dependent++;
        }

        fractionDependentDep = dependent / (double) results.size();

        fractionDepLabelDep = new JLabel("% dependent = "
                + ((Double.isNaN(fractionDependentDep)
                ? "-" : NumberFormatUtil.getInstance().getNumberFormat().format(fractionDependentDep))));

        List<Double> pValues = getPValues(results);

        double kgPValue;

        if (pValues.size() < 2) {
            kgPValue = Double.NaN;
        } else {
            kgPValue = UniformityTest.getPValue(pValues);
        }

        ksLabelDep = new JLabel("P-value of Kolmogorov-Smirnov Uniformity Test p-value = "
                + ((Double.isNaN(kgPValue)
                ? "-" : NumberFormatUtil.getInstance().getNumberFormat().format(kgPValue))));

        b5.add(fractionDepLabelDep);
        b1.add(b5);

        Box b6 = Box.createHorizontalBox();
        b6.add(Box.createHorizontalGlue());
        b6.add(ksLabelDep);
        b1.add(b6);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(b1, BorderLayout.CENTER);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        return panel;
    }

    private JPanel buildGuiIndep() {
        JButton clear = new JButton("Clear");
        clear.setFont(new Font("Dialog", Font.PLAIN, 14));
        clear.addActionListener(e -> {
            model.getResults(true).clear();
            revalidate();
            repaint();
        });

        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Tests whether X _||_ Y | parents(X) for msep(X, Y | parents(X))"));
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);

        markovTestLabel.setText(getIndependenceTest().toString());
        faithfulnessTestLabel.setText(getIndependenceTest().toString());

        Box b2a = Box.createHorizontalBox();
        b2a.add(Box.createHorizontalGlue());
        b1.add(b2a);

        b1.add(Box.createVerticalStrut(5));

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
                return 4;//2 + MarkovFactsEditor.this.indTestProducers.size();
            }

            public int getRowCount() {
                return model.getResults(true).size();
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                if (rowIndex > model.getResults(true).size()) return null;

                if (columnIndex == 0) {
                    return rowIndex + 1;
                }

                IndependenceResult result = model.getResults(true).get(rowIndex);

                if (columnIndex == 1) {
                    IndependenceFact fact = model.getResults(true).get(rowIndex).getFact();

                    List<Node> Z = new ArrayList<>(fact.getZ());
                    Collections.sort(Z);

                    String z = Z.stream().map(Node::getName).collect(Collectors.joining(", "));

                    return "Ind(" + fact.getX() + ", " + fact.getY() + (Z.isEmpty() ? "" : " | " + z) + ")";
                }

                if (columnIndex == 2) {
                    if (getIndependenceTest() instanceof IndTestMSep) {
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

            public Class getColumnClass(int columnIndex) {
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
        scroll.setPreferredSize(new Dimension(400, 400));
        b1.add(scroll);

        Box b4 = Box.createHorizontalBox();
        b4.add(Box.createGlue());
        b4.add(Box.createHorizontalStrut(10));

        String title = "P-Value or Bump for Local Markov";

        JButton showHistogram = new JButton("Show Histogram for P-Values or Bumps");
        showHistogram.setFont(new Font("Dialog", Font.PLAIN, 14));
        showHistogram.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JPanel component = createHistogramPanel(true);
                EditorWindow editorWindow = new EditorWindow(component, title, "Close", false, MarkovCheckEditor.this);
                DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
                editorWindow.pack();
                editorWindow.setVisible(true);
            }
        });

        b4.add(Box.createHorizontalGlue());
        b4.add(showHistogram);

        JButton help = new JButton("Help");

        help.addActionListener(e -> {
            String text = getHelpMessage();
            JOptionPane.showMessageDialog(help, text, "Help", JOptionPane.INFORMATION_MESSAGE);
        });

        b4.add(help);

        b1.add(b4);
        b1.add(Box.createVerticalStrut(10));

        Box b5 = Box.createHorizontalBox();
        b5.add(Box.createGlue());

        int dependent = 0;

        for (IndependenceResult result : model.getResults(true)) {
            if (result.isDependent() && !Double.isNaN(result.getPValue())) dependent++;
        }

        List<IndependenceResult> results = model.getResults(true);
        fractionDependentIndep = dependent / (double) results.size();

        fractionDepLabelIndep = new JLabel("% dependent = "
                + ((Double.isNaN(fractionDependentIndep)
                ? "-" : NumberFormatUtil.getInstance().getNumberFormat().format(fractionDependentIndep))));

        List<Double> pValues = getPValues(results);

        double ksPValue;

        if (pValues.size() < 2) {
            ksPValue = Double.NaN;
        } else {
            ksPValue = UniformityTest.getPValue(pValues);
        }

        ksLabelIndep = new JLabel("P-value of Kolmogorov-Smirnov Uniformity Test p-value = "
                + ((Double.isNaN(ksPValue)
                ? "-" : NumberFormatUtil.getInstance().getNumberFormat().format(ksPValue))));

        b5.add(fractionDepLabelIndep);
        b1.add(b5);

        Box b6 = Box.createHorizontalBox();
        b6.add(Box.createHorizontalGlue());
        b6.add(ksLabelIndep);
        b1.add(b6);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(b1, BorderLayout.CENTER);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        return panel;
    }

    @NotNull
    private static String getHelpMessage() {
        return "\n" +
                "This tool lets you plot statistics for independence tests of a pair of variables given parents of the one for a given\n" +
                "graph and dataset. Two tables are made, one in which the independence facts predicted by the graph are tested in the\n" +
                "data and the other in which the graph's predicted dependence facts are tested. We call the first set of facts \"local\n" +
                "Markov\"; the second we call \"local Faithfulness.” By \"local,\" we mean that we are only testing independence facts of\n" +
                "variables given their parents in the graph.\n" +
                "\n" +
                "Each table gives columns for the independence fact being checked, its test result, and its statistic. This statistic is\n" +
                "either a p-value, ranging from 0 to 1, where p-values above the alpha level of the test are judged as independent, or a\n" +
                "score bump, where this bump is negative for independent judgments and positive for dependent judgments.\n" +
                "\n" +
                "If the independence test yields a p-value, as for instance, for the Fisher Z test (for the linear, Gaussian case) or\n" +
                "else the Chi-Square test (for the multinomial case), then under the null hypothesis of independence and for a consistent\n" +
                "test, these p-values should be distributed as Uniform(0, 1). That is, it should be just as likely to see p-values in any\n" +
                "range of equal width. If the test is inconsistent or the graph is incorrect (i.e., the parents of some or all of the\n" +
                "nodes in the graph are incorrect), then this distribution of p-values will not be Uniform. To visualize this, we have a\n" +
                "button that lets you display the histogram of the p-values with equally sized bins; the bars in this histogram, for this\n" +
                "case, should ideally all be of equal height.\n" +
                "\n" +
                "If the first bar in this histogram is especially high (for the p-value case), that means that many tests are being\n" +
                "judged as dependent. For checking Faithfulness, one hopes that this first bar will be especially high, since high\n" +
                "p-values are for examples where the graph is unfaithful to the distribution. These are likely for for cases where paths\n" +
                "in the graph cancel unfaithfully. But for checking Markov, one hopes that this first bar will be the same height as all\n" +
                "of the other bars.\n" +
                "\n" +
                "To make it especially clear, we give two statistics in the interface. The first is the percentage of p-values judged\n" +
                "dependent on the test. If an alpha level is used in the test, this number should be very close to the alpha level for\n" +
                "the Local Markov check since the distribution of p-values under this condition is Uniform. For the second, we test the\n" +
                "Uniformity of the p-values using a Kolmogorov-Smirnov test. The p-value returned by this test should be greater than the\n" +
                "user’s preferred alpha level if the distribution of p-values is Uniform and less then this alpha level if the\n" +
                "distribution of p-values is non-Uniform.\n" +
                "\n" +
                "If the independence test yields a bump in the score, this score should be negative for independence judgments and\n" +
                "positive for dependence judgments. The histogram will reflect this.\n" +
                "\n" +
                "Feel free to select all of the data in the tables, copy it, and paste it into a text file or into Excel. This will let\n" +
                "you analyze the data yourself.\n";
    }

    //=============================PRIVATE METHODS=======================//

    private void sortByColumn(int sortCol, boolean indep) {
        if (sortCol == this.getLastSortCol()) {
            this.setSortDir(-1 * this.getSortDir());
        } else {
            this.setSortDir(1);
        }

        this.setLastSortCol(sortCol);
        model.getResults(indep).sort(Comparator.comparing(
                IndependenceResult::getFact));

        if (indep) {
            tableModelIndep.fireTableDataChanged();
        } else {
            tableModelDep.fireTableDataChanged();
        }
    }

    private void generateResults(boolean indep) {
        class MyWatchedProcess extends WatchedProcess {
            public void watch() {
                List<IndependenceResult> results = model.getResults(indep);
                results.clear();

                invalidate();
                repaint();

                if (model.getVars().size() < 2) {
                    if (indep) {
                        tableModelIndep.fireTableDataChanged();
                    } else {
                        tableModelDep.fireTableDataChanged();
                    }
                    return;
                }

                List<IndependenceFact> facts = new ArrayList<>();

                // Listing all facts before checking any (in preparation for parallelization).
                for (Node x : graph.getNodes()) {
                    Set<Node> z = new HashSet<>(graph.getParents(x));
                    Set<Node> ms = new HashSet<>();
                    Set<Node> mc = new HashSet<>();

                    List<Node> other = graph.getNodes();
                    other.removeAll(z);

                    for (Node y : other) {
                        if (y == x) continue;
                        if (msep.isMSeparated(x, y, z)) {
                            ms.add(y);
                        } else {
                            mc.add(y);
                        }
                    }

                    System.out.println("Node " + x + " parents = " + z
                            + " m-separated | z = " + ms + " m-connected | z = " + mc);

                    if (indep) {
                        for (Node y : ms) {
                            facts.add(new IndependenceFact(x, y, z));
                        }
                    } else {
                        for (Node y : mc) {
                            facts.add(new IndependenceFact(x, y, z));
                        }
                    }
                }

                class IndCheckTask implements Callable<List<IndependenceResult>> {

                    private final int from;
                    private final int to;
                    private final List<IndependenceFact> facts;
                    private IndependenceTest independenceTest;

                    IndCheckTask(int from, int to, List<IndependenceFact> facts, IndependenceTest test) {
                        this.from = from;
                        this.to = to;
                        this.facts = facts;
                        this.independenceTest = test;
                    }

                    @Override
                    public List<IndependenceResult> call() {
                        List<IndependenceResult> results = new ArrayList<>();

                        for (int i = from; i < to; i++) {
                            if (Thread.interrupted()) break;
                            IndependenceFact fact = facts.get(i);

                            Node x = fact.getX();
                            Node y = fact.getY();
                            Set<Node> z = fact.getZ();
                            boolean verbose = independenceTest.isVerbose();
                            independenceTest.setVerbose(false);
                            IndependenceResult result;
                            try {
                                result = independenceTest.checkIndependence(x, y, z);
                            } catch (Exception e) {
                                e.printStackTrace();
                                JOptionPane.showMessageDialog(MarkovCheckEditor.this,
                                        "Error while checking independence: " + e.getMessage(),
                                        "Error", JOptionPane.ERROR_MESSAGE);
                                throw new RuntimeException(e);
                            }
                            boolean indep = result.isIndependent();
                            double pValue = result.getPValue();
                            independenceTest.setVerbose(verbose);

                            if (!Double.isNaN(pValue)) {
                                results.add(new IndependenceResult(fact, indep, pValue, Double.NaN));

                                if (indep) {
                                    tableModelIndep.fireTableDataChanged();
                                } else {
                                    tableModelDep.fireTableDataChanged();
                                }
                            }
                        }

                        return results;
                    }
                }

                List<Callable<List<IndependenceResult>>> tasks = new ArrayList<>();

                int chunkSize = getChunkSize(facts.size());

                for (int i = 0; i < facts.size() && !Thread.currentThread().isInterrupted(); i += chunkSize) {
                    IndCheckTask task = new IndCheckTask(i, min(facts.size(), i + chunkSize),
                            facts, independenceTest);

                    if (!parallelized) {
                        List<IndependenceResult> _results = task.call();
                        results.addAll(_results);
                    } else {
                        tasks.add(task);
                    }
                }

                if (parallelized) {
                    List<Future<List<IndependenceResult>>> theseResults = ForkJoinPool.commonPool().invokeAll(tasks);

                    for (Future<List<IndependenceResult>> future : theseResults) {
                        try {
                            results.addAll(future.get());
                        } catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                int dependent = 0;

                for (IndependenceResult result : results) {
                    if (result.isDependent() && !Double.isNaN(result.getPValue())) dependent++;
                }

                if (indep) {
                    fractionDependentIndep = dependent / (double) results.size();
                } else {
                    fractionDependentDep = dependent / (double) results.size();
                }

                List<Double> pValues = getPValues(results);

                double ksPValue;

                if (pValues.size() < 2) {
                    ksPValue = Double.NaN;
                } else {
                    ksPValue = UniformityTest.getPValue(pValues);
                }

                if (indep) {
                    ksLabelIndep.setText("P-value of Kolmogorov-Smirnov Uniformity Test = "
                            + ((Double.isNaN(ksPValue)
                            ? "-" : NumberFormatUtil.getInstance().getNumberFormat().format(ksPValue))));
                } else {
                    ksLabelDep.setText("P-value of Kolmogorov-Smirnov Uniformity Test = "
                            + ((Double.isNaN(ksPValue)
                            ? "-" : NumberFormatUtil.getInstance().getNumberFormat().format(ksPValue))));
                }

                if (indep) {
                    fractionDepLabelIndep.setText("% dependent = "
                            + ((Double.isNaN(fractionDependentIndep)
                            ? "-" : NumberFormatUtil.getInstance().getNumberFormat().format(fractionDependentIndep))));
                } else {
                    fractionDepLabelDep.setText("% dependent = "
                            + ((Double.isNaN(fractionDependentDep)
                            ? "-" : NumberFormatUtil.getInstance().getNumberFormat().format(fractionDependentDep))));
                }

                if (indep) {
                    tableModelIndep.fireTableDataChanged();
                } else {
                    tableModelDep.fireTableDataChanged();
                }
            }
        }

        SwingUtilities.invokeLater(MyWatchedProcess::new);
    }

    private int getChunkSize(int n) {
        int chunk = (int) FastMath.ceil((n / ((double) (5 * Runtime.getRuntime().availableProcessors()))));
        if (chunk < 1) chunk = 1;
        return chunk;
    }

    private IndependenceTest getIndependenceTest() {
        return this.independenceTest;
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

    private JPanel createHistogramPanel(boolean indep) {
        JPanel jPanel = new JPanel();

        List<IndependenceResult> results = model.getResults(indep);

        if (results.isEmpty()) {
            JLabel label = new JLabel("No results available; please click the Check button first.");
            JPanel panel = new JPanel();
            panel.add(label);
            return panel;
        }

        DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(results.size(), 1),
                Collections.singletonList(new ContinuousVariable("P-Value or Bump")));

        for (int i = 0; i < results.size(); i++) {
            dataSet.setDouble(i, 0, results.get(i).getPValue());
        }

        Histogram histogram = new Histogram(dataSet);
        histogram.setNumBins(10);
        histogram.setTarget("P-Value or Bump");
        HistogramView view = new HistogramView(histogram, false);

        Box box = Box.createHorizontalBox();
        box.add(view);
        box.add(Box.createHorizontalStrut(5));
        box.add(Box.createHorizontalGlue());

        Box vBox = Box.createVerticalBox();
        vBox.add(Box.createVerticalStrut(15));
        vBox.add(box);
        vBox.add(Box.createVerticalStrut(5));

        jPanel.setLayout(new BorderLayout());
        jPanel.add(vBox, BorderLayout.CENTER);
        return jPanel;
    }

    public List<Double> getPValues(List<IndependenceResult> results) {
        List<Double> pValues = new ArrayList<>();

        for (IndependenceResult result : results) {
            pValues.add(result.getPValue());
        }

        return pValues;
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

    private DataType getDataType() {
        if (dataSet.isContinuous() && !(dataSet instanceof ICovarianceMatrix)) {
            // covariance dataset is continuous at the same time - Zhou
            parallelized = false;
            return DataType.Continuous;
        } else if (dataSet.isDiscrete()) {
            parallelized = false;
            return DataType.Discrete;
        } else if (dataSet.isMixed()) {
            parallelized = false;
            return DataType.Mixed;
        } else if (dataSet instanceof ICovarianceMatrix) { // Better to add an isCovariance() - Zhou
            parallelized = false;
            return DataType.Covariance;
        } else {
            return null;
        }
    }

    private void refreshTestList() {
        DataType dataType = getDataType();

        this.indTestComboBox.removeAllItems();

        List<IndependenceTestModel> models = IndependenceTestModels.getInstance().getModels(dataType);

        for (IndependenceTestModel model : models) {
            this.indTestComboBox.addItem(model);
        }

        this.updatingTestModels = false;
        this.indTestComboBox.setEnabled(this.indTestComboBox.getItemCount() > 0);

        if (this.indTestComboBox.getSelectedIndex() == -1) {
            this.testDescTextArea.setText("");
        }

        indTestComboBox.setSelectedItem(IndependenceTestModels.getInstance().getDefaultModel(dataType));
    }

    private JPanel createParamsPanel(IndependenceWrapper independenceWrapper, Parameters params) {
        Set<String> testParameters = new HashSet<>(independenceWrapper.getParameters());

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        for (String parameter : testParameters) {
            JPanel subPanel = new JPanel();
            subPanel.setLayout(new BoxLayout(subPanel, BoxLayout.X_AXIS));
            subPanel.add(new JLabel(parameter));
            subPanel.add(Box.createHorizontalGlue());
            textField = new JTextField(params.get(parameter).toString());
            textField.setMaximumSize(new Dimension(Short.MAX_VALUE, textField.getPreferredSize().height));

            textField.addActionListener(e -> {
                try {
                    params.set(parameter, Double.parseDouble(textField.getText()));
                } catch (Exception ex) {
                    // Ignore
                }

                textField.setText(params.get(parameter).toString());
            });

            subPanel.add(textField);
            panel.add(subPanel);
        }

        return panel;
    }
}






