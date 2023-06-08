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
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.ParamDescription;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.MarkovCheckIndTestModel;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModels;
import edu.cmu.tetradapp.util.*;
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
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static edu.cmu.tetradapp.util.ParameterComponents.toArray;


/**
 * Lists independence facts specified by user and allows the list to be sorted by independence fact or by p value.
 *
 * @author josephramsey
 */
public class MarkovCheckEditor extends JPanel {
    private final MarkovCheckIndTestModel model;
    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    private final JLabel markovTestLabel = new JLabel("(Unspecified Test)");
    private AbstractTableModel tableModelIndep;
    private AbstractTableModel tableModelDep;
    private JLabel fractionDepLabelIndep;
    private JLabel fractionDepLabelDep;
    private JLabel ksLabelDep;
    private JLabel ksLabelIndep;
    private JLabel masLabellDep;
    private JLabel masLabellIndep;
    private int sortDir;
    private int lastSortCol;
    private final JTextArea testDescTextArea = new JTextArea();
    private final JComboBox<IndependenceTestModel> indTestComboBox = new JComboBox<>();
    boolean updatingTestModels = true;
    private final JLabel faithfulnessTestLabel = new JLabel("(Unspecified Test)");
    private IndependenceWrapper independenceWrapper;

    /**
     * Constructs a new editor for the given model.
     */
    public MarkovCheckEditor(MarkovCheckIndTestModel model) {
        if (model == null) {
            throw new NullPointerException("Expecting a model");
        }

        this.model = model;
        refreshTestList();

        indTestComboBox.addActionListener(e -> {
            class MyWatchedProcess extends WatchedProcess {
                public void watch() {
                    setTest();
                    model.getMarkovCheck().generateResults();
                    tableModelIndep.fireTableDataChanged();
                    tableModelDep.fireTableDataChanged();
                    setLabelTexts();
                }
            }

            SwingUtilities.invokeLater(MyWatchedProcess::new);
        });

        setTest();

        Graph _graph = model.getGraph();
        Graph graph = GraphUtils.replaceNodes(_graph, model.getMarkovCheck().getVariables());

        JPanel indep = buildGuiIndep();
        JPanel dep = buildGuiDep();

        tableModelIndep.fireTableDataChanged();
        tableModelDep.fireTableDataChanged();

        Graph sourceGraph = model.getGraph();
        List<Node> variables = model.getMarkovCheck().getVariables();

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
                if (missingVars.size() >= 5) break;
            }
        }

        if (!missingVars.isEmpty()) {
            throw new IllegalArgumentException("At least these variables in the DAG are missing from the data:" +
                    "\n    " + missingVars);
        }

        model.setVars(graph.getNodeNames());

        Box box = Box.createVerticalBox();
        Box box1 = Box.createHorizontalBox();
        box1.add(indTestComboBox);
        box1.add(Box.createHorizontalStrut(10));
        JButton params = new JButton("Params");

        params.addActionListener(e -> {
            JOptionPane dialog = new JOptionPane(createParamsPanel(independenceWrapper, model.getParameters()), JOptionPane.PLAIN_MESSAGE);
            dialog.createDialog("Set Parameters").setVisible(true);

            class MyWatchedProcess2 extends WatchedProcess {

                @Override
                public void watch() {
                    setTest();
                    model.getMarkovCheck().generateResults();
                    tableModelIndep.fireTableDataChanged();
                    tableModelDep.fireTableDataChanged();
                    setLabelTexts();
                }
            }

            SwingUtilities.invokeLater(MyWatchedProcess2::new);
        });

        box1.add(params);
        box.add(box1);

        JTabbedPane pane = new JTabbedPane();
        pane.addTab("Check Local Markov", indep);
        pane.addTab("Check Local Faithfulness", dep);
        box.add(pane);

        class MyWatchedProcess extends WatchedProcess {
            public void watch() {
                setTest();
                model.getMarkovCheck().generateResults();
                tableModelIndep.fireTableDataChanged();
                tableModelDep.fireTableDataChanged();
                setLabelTexts();
            }
        }

        SwingUtilities.invokeLater(MyWatchedProcess::new);

        add(box);
    }

    private void setTest() {
        IndependenceTestModel selectedItem = (IndependenceTestModel) indTestComboBox.getSelectedItem();
        Class<IndependenceWrapper> clazz = (selectedItem == null) ? null : selectedItem.getIndependenceTest().getClazz();
        IndependenceTest independenceTest;

        if (clazz != null) {
            try {
                independenceWrapper = clazz.getDeclaredConstructor(new Class[0]).newInstance();
                independenceTest = independenceWrapper.getTest(model.getDataModel(), model.getParameters());
                model.setIndependenceTest(independenceTest);
                markovTestLabel.setText(model.getMarkovCheck().getIndependenceTest().toString());
                faithfulnessTestLabel.setText(model.getMarkovCheck().getIndependenceTest().toString());
                invalidate();
                repaint();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e1) {
                throw new RuntimeException(e1);
            }
        }


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

        markovTestLabel.setText(model.getMarkovCheck().getIndependenceTest().toString());
        faithfulnessTestLabel.setText(model.getMarkovCheck().getIndependenceTest().toString());

        Box b2a = Box.createHorizontalBox();
        b2a.add(Box.createHorizontalGlue());
        b1.add(b2a);
        b1.add(Box.createVerticalStrut(5));

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
                return model.getResults(false).size();
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                if (rowIndex > model.getResults(false).size()) return null;

                if (columnIndex == 0) {
                    return rowIndex + 1;
                }
                if (columnIndex == 1) {
                    IndependenceFact fact = model.getResults(false).get(rowIndex).getFact();

                    List<Node> Z = new ArrayList<>(fact.getZ());
                    Collections.sort(Z);

                    String z = Z.stream().map(Node::getName).collect(Collectors.joining(", "));

                    return "Dep(" + fact.getX() + ", " + fact.getY() + (Z.isEmpty() ? "" : " | " + z) + ")";
                }

                IndependenceResult result = model.getResults(false).get(rowIndex);

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

        setLabelTexts();

        b5.add(fractionDepLabelDep);
        b1.add(b5);

        Box b6 = Box.createHorizontalBox();
        b6.add(Box.createHorizontalGlue());
        b6.add(ksLabelDep);
        b1.add(b6);

        Box b7 = Box.createHorizontalBox();
        b7.add(Box.createHorizontalGlue());
        b7.add(masLabellDep);
        b1.add(b7);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(b1, BorderLayout.CENTER);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        return panel;
    }

    private JPanel buildGuiIndep() {

        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Tests whether X _||_ Y | parents(X) for msep(X, Y | parents(X))"));
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);

        markovTestLabel.setText(model.getMarkovCheck().getIndependenceTest().toString());
        faithfulnessTestLabel.setText(model.getMarkovCheck().getIndependenceTest().toString());

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
                List<IndependenceResult> results = model.getResults(true);
                return results.size();
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

        setLabelTexts();

        b5.add(fractionDepLabelIndep);
        b1.add(b5);

        Box b6 = Box.createHorizontalBox();
        b6.add(Box.createHorizontalGlue());
        b6.add(ksLabelIndep);
        b1.add(b6);

        Box b7 = Box.createHorizontalBox();
        b7.add(Box.createHorizontalGlue());
        b7.add(masLabellIndep);
        b1.add(b7);

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

        if (fractionDepLabelIndep == null) {
            fractionDepLabelIndep = new JLabel();
        }

        if (fractionDepLabelDep == null) {
            fractionDepLabelDep = new JLabel();
        }

        if (masLabellIndep == null) {
            masLabellIndep = new JLabel();
        }

        if (masLabellDep == null) {
            masLabellDep = new JLabel();
        }

        ksLabelIndep.setText("P-value of Kolmogorov-Smirnov Uniformity Test = "
                + ((Double.isNaN(model.getMarkovCheck().getKsPValue(true))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getKsPValue(true)))));
        ksLabelDep.setText("P-value of Kolmogorov-Smirnov Uniformity Test = "
                + ((Double.isNaN(model.getMarkovCheck().getKsPValue(false))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getKsPValue(false)))));
        fractionDepLabelIndep.setText("% dependent = "
                + ((Double.isNaN(model.getMarkovCheck().getFractionDependent(true))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getFractionDependent(true)))));
        fractionDepLabelDep.setText("% dependent = "
                + ((Double.isNaN(model.getMarkovCheck().getFractionDependent(false))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getFractionDependent(false)))));
        masLabellIndep.setText("Markov Adequacy Score = "
                + ((Double.isNaN(model.getMarkovCheck().getMarkovAdequacyScore(0.01))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getMarkovAdequacyScore(0.01)))));
        masLabellDep.setText("Markov Adequacy Score = "
                + ((Double.isNaN(model.getMarkovCheck().getMarkovAdequacyScore(0.01))
                ? "-"
                : NumberFormatUtil.getInstance().getNumberFormat().format(model.getMarkovCheck().getMarkovAdequacyScore(0.01)))));

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
        DataModel dataSet = model.getDataModel();

        if (dataSet.isContinuous() && !(dataSet instanceof ICovarianceMatrix)) {
            // covariance dataset is continuous at the same time - Zhou
            return DataType.Continuous;
        } else if (dataSet.isDiscrete()) {
            return DataType.Discrete;
        } else if (dataSet.isMixed()) {
            return DataType.Mixed;
        } else if (dataSet instanceof ICovarianceMatrix) { // Better to add an isCovariance() - Zhou
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

    // Paramter panel code from Kevin Bui.
    private JPanel createParamsPanel(IndependenceWrapper independenceWrapper, Parameters params) {
        Set<String> testParameters = new HashSet<>(independenceWrapper.getParameters());
        return createParamsPanel("Parameters", testParameters, params);
    }

    private JPanel createParamsPanel(String title, Set<String> params, Parameters parameters) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));

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
        ParamDescriptions paramDescs = ParamDescriptions.getInstance();
        return params.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        e -> createParameterComponent(e, parameters, paramDescs.get(e)),
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
}






