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
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndTestDSep;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.MarkovCheckIndTestModel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModel;
import edu.cmu.tetradapp.ui.model.IndependenceTestModels;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.WatchedProcess;
import org.apache.commons.math3.util.FastMath;

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
    private final IndTestDSep dsep;
    private final Graph graph;
    private final boolean parallelized = true;
    private AbstractTableModel tableModelIndep;
    private AbstractTableModel tableModelDep;
    private int sortDir;
    private int lastSortCol;
    private final JTextArea testDescTextArea = new JTextArea();
    private double fractionDependentIndep = Double.NaN;
    private double fractionDependentDep = Double.NaN;
    private JLabel fractionDepLabelIndep;
    private JLabel fractionDepLabelDep;
    private final JComboBox<IndependenceTestModel> indTestComboBox = new JComboBox<>();
    boolean updatingTestModels = true;
    private IndependenceTest independenceTest;
    private final DataModel dataSet;

    /**
     * Constructs a new editor for the given model.
     */
    public MarkovCheckEditor(MarkovCheckIndTestModel model) {
        if (model == null) {
            throw new NullPointerException("Expecting a model");
        }

        Parameters parameters = model.getParameters();
        this.dataSet = model.getDataModel();
        this.graph = model.getGraph();

        refreshTestList();

        IndependenceTestModel selectedItem = (IndependenceTestModel) indTestComboBox.getSelectedItem();
        Class<IndependenceWrapper> clazz = (selectedItem == null) ? null : selectedItem.getIndependenceTest().getClazz();

        if (clazz != null) {
            try {
                IndependenceWrapper independenceTest1 = clazz.getDeclaredConstructor(new Class[0]).newInstance();
                independenceTest = independenceTest1.getTest(dataSet, parameters);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        if (independenceTest == null) {
            throw new NullPointerException("Expecting a test");
        }

        this.model = model;
        Graph sourceGraph = model.getGraph();
        List<Node> variables = independenceTest.getVariables();

        List<Node> newVars = new ArrayList<>();

        for (Node node : variables) {
            if (sourceGraph.getNode(node.getName()) != null) {
                newVars.add(node);
            }
        }

        sourceGraph = edu.cmu.tetrad.graph.GraphUtils.replaceNodes(sourceGraph, newVars);

        for (Edge e : sourceGraph.getEdges()) {
            if (!e.isDirected()) {
                throw new IllegalArgumentException("At least this edge in the source graph is not directed: " + e);
            }
        }

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

        if (sourceGraph.paths().existsDirectedCycle()) {
            JOptionPane.showMessageDialog(
                    JOptionUtils.centeringComp().getTopLevelAncestor(),
                    "That graph is not a DAG. For linear models, this is OK, but for nonlinear models," +
                            "\nyou would either have to form the “collapsed graph” or use sigma-separation.");
        }

        dsep = new IndTestDSep(this.graph);
        model.setVars(this.graph.getNodeNames());

        JPanel indep = buildGuiIndep();
        JPanel dep = buildGuiDep();

        Box box = Box.createVerticalBox();
        Box box1 = Box.createHorizontalBox();
        box1.add(indTestComboBox);
        box1.add(Box.createHorizontalStrut(10));
        box1.add(new JButton("Params"));
        box.add(box1);

        JTabbedPane pane = new JTabbedPane();
        pane.addTab("Check Markov", indep);
        pane.addTab("Check Faithfulness", dep);
        box.add(pane);

        add(box);
    }

    //========================PUBLIC METHODS==========================//

    /**
     * Performs the action of opening a session from a file.
     */
    private JPanel buildGuiDep() {
        JButton list = new JButton("CHECK");
        list.setFont(new Font("Dialog", Font.BOLD, 14));

        list.addActionListener(e -> generateResults(false));

        JButton clear = new JButton("Clear");
        clear.setFont(new Font("Dialog", Font.PLAIN, 14));
        clear.addActionListener(e -> {
            model.getResults(false).clear();
            revalidate();
            repaint();
        });

        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Checks whether X ~_||_ Y | parents(X) for dconn(X, Y | parents(X))"));
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);

        Box b2a = Box.createHorizontalBox();
        b2a.add(new JLabel("Test: "));
        b2a.add(new JLabel(getIndependenceTest().toString()));
        b2a.add(Box.createHorizontalGlue());
        b1.add(b2a);

        b1.add(Box.createVerticalStrut(5));

        this.tableModelDep = new AbstractTableModel() {
            public String getColumnName(int column) {
                if (column == 0) {
                    return "Index";
                } else if (column == 1) {
                    return "Fact";
                } else if (column == 2) {
                    return "Result";
                } else if (column == 3) {
                    return "P-Value/Bump";
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

                    return "dconn(" + fact.getX() + ", " + fact.getY() + (Z.isEmpty() ? "" : " | " + z) + ")";
                }

                IndependenceResult result = model.getResults(false).get(rowIndex);

                if (columnIndex == 2) {
                    if (getIndependenceTest() instanceof IndTestDSep) {
                        if (result.isIndependent()) {
                            return "D-SEPARATED";
                        } else {
                            return "d-connected";
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

        JButton showHistogram = new JButton("Show Score Histogram");
        showHistogram.setFont(new Font("Dialog", Font.PLAIN, 14));
        showHistogram.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JPanel component = createHistogramPanel(false);
                EditorWindow editorWindow = new EditorWindow(component, "Histogram", "Close", false, MarkovCheckEditor.this);
                DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
                editorWindow.pack();
                editorWindow.setVisible(true);
            }
        });

        b4.add(Box.createHorizontalGlue());
        b4.add(clear);
        b4.add(list);
        b4.add(showHistogram);

        b1.add(b4);
        b1.add(Box.createVerticalStrut(10));

        Box b5 = Box.createHorizontalBox();
        b5.add(Box.createGlue());

        int dependent = 0;

        for (IndependenceResult result : model.getResults(false)) {
            if (result.isDependent() && !Double.isNaN(result.getPValue())) dependent++;
        }

        fractionDependentDep = dependent / (double) model.getResults(false).size();

        fractionDepLabelDep = new JLabel("% dependent = "
                + ((Double.isNaN(fractionDependentDep)
                ? "-" : NumberFormatUtil.getInstance().getNumberFormat().format(fractionDependentDep))));

        b5.add(fractionDepLabelDep);
        b1.add(b5);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(b1, BorderLayout.CENTER);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        return panel;
    }

    private JPanel buildGuiIndep() {
        JButton list = new JButton("CHECK");
        list.setFont(new Font("Dialog", Font.BOLD, 14));

        list.addActionListener(e -> generateResults(true));

        JButton clear = new JButton("Clear");
        clear.setFont(new Font("Dialog", Font.PLAIN, 14));
        clear.addActionListener(e -> {
            model.getResults(true).clear();
            revalidate();
            repaint();
        });

        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Checks whether X _||_ Y | parents(X) for dsep(X, Y | parents(X))"));
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);

        Box b2a = Box.createHorizontalBox();
        b2a.add(new JLabel("Test: "));
        b2a.add(new JLabel(getIndependenceTest().toString()));
        b2a.add(Box.createHorizontalGlue());
        b1.add(b2a);


        b1.add(Box.createVerticalStrut(5));

        this.tableModelIndep = new AbstractTableModel() {
            public String getColumnName(int column) {
                if (column == 0) {
                    return "Index";
                } else if (column == 1) {
                    return "Fact";
                } else if (column == 2) {
                    return "Result";
                } else if (column == 3) {
                    return "P-value/Bump";
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

                    return "dsep(" + fact.getX() + ", " + fact.getY() + (Z.isEmpty() ? "" : " | " + z) + ")";
                }

                if (columnIndex == 2) {
                    if (getIndependenceTest() instanceof IndTestDSep) {
                        if (result.isIndependent()) {
                            return "D-SEPARATED";
                        } else {
                            return "d-connected";
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

        JButton showHistogram = new JButton("Show P-Value Histogram");
        showHistogram.setFont(new Font("Dialog", Font.PLAIN, 14));
        showHistogram.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JPanel component = createHistogramPanel(true);
                EditorWindow editorWindow = new EditorWindow(component, "Histogram", "Close", false, MarkovCheckEditor.this);
                DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
                editorWindow.pack();
                editorWindow.setVisible(true);
            }
        });

        b4.add(Box.createHorizontalGlue());
        b4.add(clear);
        b4.add(list);
        b4.add(showHistogram);

        b1.add(b4);
        b1.add(Box.createVerticalStrut(10));

        Box b5 = Box.createHorizontalBox();
        b5.add(Box.createGlue());

        int dependent = 0;

        for (IndependenceResult result : model.getResults(true)) {
            if (result.isDependent() && !Double.isNaN(result.getPValue())) dependent++;
        }

        fractionDependentIndep = dependent / (double) model.getResults(true).size();

        fractionDepLabelIndep = new JLabel("% dependent = "
                + ((Double.isNaN(fractionDependentIndep)
                ? "-" : NumberFormatUtil.getInstance().getNumberFormat().format(fractionDependentIndep))));

        b5.add(fractionDepLabelIndep);
        b1.add(b5);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(b1, BorderLayout.CENTER);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        return panel;
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
//                    List<Node> desc = dag.paths().getDescendants(Collections.singletonList(x));
//                    List<Node> nondesc = dag.getNodes();
//                    nondesc.removeAll(desc);
//                    nondesc.removeAll(dag.getParents(x));
//                    nondesc.remove(x);

                    Set<Node> z = new HashSet<>(graph.getParents(x));
                    Set<Node> ds = new HashSet<>();
                    Set<Node> dc = new HashSet<>();

                    List<Node> other = graph.getNodes();
                    other.removeAll(z);

                    for (Node y : other) {
                        if (y == x) continue;
                        if (dsep.isDSeparated(x, y, z)) {
                            ds.add(y);
                        } else {
                            dc.add(y);
                        }
                    }

//                    System.out.println("Node " + x + " parents = " + z
//                            + " non-descendants = " + nondesc);
                    System.out.println("Node " + x + " parents = " + z
                            + " d-separated | z = " + ds + " d-connected | z = " + dc);

                    if (indep) {
                        for (Node y : ds) {
                            facts.add(new IndependenceFact(x, y, z));
                        }
                    } else {
                        for (Node y : dc) {
                            facts.add(new IndependenceFact(x, y, z));
                        }
                    }
                }

                class IndCheckTask implements Callable<List<IndependenceResult>> {

                    private final int from;
                    private final int to;
                    private final List<IndependenceFact> facts;

                    IndCheckTask(int from, int to, List<IndependenceFact> facts) {
                        this.from = from;
                        this.to = to;
                        this.facts = facts;
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
                            IndependenceResult result = independenceTest.checkIndependence(x, y, z);
                            boolean indep = result.isIndependent();
                            double pValue = result.getPValue();
                            independenceTest.setVerbose(verbose);

                            if (!Double.isNaN(pValue)) {
                                results.add(new IndependenceResult(fact, indep, pValue));

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
                            facts);

                    if (!parallelized) {
                        List<IndependenceResult> _results = task.call();
                        model.getResults(indep).addAll(_results);
                    } else {
                        tasks.add(task);
                    }
                }

                if (parallelized) {
                    List<Future<List<IndependenceResult>>> theseResults = ForkJoinPool.commonPool().invokeAll(tasks);

                    for (Future<List<IndependenceResult>> future : theseResults) {
                        try {
                            model.getResults(indep).addAll(future.get());
                        } catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                int dependent = 0;

                for (IndependenceResult result : model.getResults(indep)) {
                    if (result.isDependent() && !Double.isNaN(result.getPValue())) dependent++;
                }

                if (indep) {
                    fractionDependentIndep = dependent / (double) model.getResults(indep).size();
                } else {
                    fractionDependentDep = dependent / (double) model.getResults(indep).size();
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

        if (model.getResults(indep).isEmpty()) {
            JLabel label = new JLabel("No results available; please click the Check button first.");
            JPanel panel = new JPanel();
            panel.add(label);
            return panel;
        }

        DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(model.getResults(indep).size(), 1),
                Collections.singletonList(new ContinuousVariable("P-Values")));

        for (int i = 0; i < model.getResults(indep).size(); i++) {
            dataSet.setDouble(i, 0, model.getResults(indep).get(i).getPValue());
        }

        Histogram histogram = new Histogram(dataSet);
        histogram.setTarget("P-Values");
        HistogramView view = new HistogramView(histogram);

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

        this.updatingTestModels = true;
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

}





