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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.IndTestType;
import edu.cmu.tetrad.search.Ling;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.model.*;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * Edits some algorithms to search for Markov blanket patterns.
 *
 * @author Joseph Ramsey
 */
public class LingSearchEditor extends AbstractSearchEditor
        implements KnowledgeEditable, LayoutEditable, DoNotScroll {

//    private JTabbedPane tabbedPane;
    private LingDisplay lingDisplay;

    //=========================CONSTRUCTORS============================//

    public LingSearchEditor(LingRunner runner) {
        super(runner, "Result Graph");
    }

    //=============================== Public Methods ==================================//

    public Graph getGraph() {
        return getWorkbench().getGraph();
    }

    @Override
    public Map getModelEdgesToDisplay() {
        return getWorkbench().getModelEdgesToDisplay();
    }

    public Map getModelNodesToDisplay() {
        return getWorkbench().getModelNodesToDisplay();
    }

    public void layoutByGraph(Graph graph) {
        getWorkbench().layoutByGraph(graph);
    }

    public void layoutByKnowledge() {
        GraphWorkbench resultWorkbench = getWorkbench();
        Graph graph = resultWorkbench.getGraph();
        IKnowledge knowledge = getAlgorithmRunner().getParams().getKnowledge();
        SearchGraphUtils.arrangeByKnowledgeTiers(graph, knowledge);
//        resultWorkbench.setGraph(graph);
    }

    public Rectangle getVisibleRect() {
        return getWorkbench().getVisibleRect();
    }

    //==========================PROTECTED METHODS============================//


    /**
     * Sets up the editor, does the layout, and so on.
     */
    protected void setup(String resultLabel) {
        setLayout(new BorderLayout());
        add(getToolbar(), BorderLayout.WEST);
        //JTabbedPane tabbedPane = new JTabbedPane();
//        tabbedPane = new JTabbedPane();
        lingDisplay = lingDisplay();
//        tabbedPane.add("Result", lingDisplay);

        /*if (getAlgorithmRunner().getSelectedDataModel() instanceof DataSet) {
            tabbedPane.add("Model Statistics", modelStatsText);
            tabbedPane.add("DAG in pattern", dagWorkbench);
        }*/

        add(lingDisplay, BorderLayout.CENTER);
//        add(tabbedPane, BorderLayout.CENTER);
        add(menuBar(), BorderLayout.NORTH);
    }

    private LingDisplay lingDisplay() {
        Graph resultGraph = resultGraph();
        Ling.StoredGraphs storedGraphs = arrangeGraphs();
        LingDisplay display = new LingDisplay(storedGraphs);

        // Superfluous?
        getGraphHistory().clear();
        getGraphHistory().add(resultGraph);

        setWorkbench(display.getWorkbench());
        getWorkbench().setAllowDoubleClickActions(false);
        getWorkbench().setAllowNodeEdgeSelection(true);

        setWorkbenchScroll(new JScrollPane(display));
        getWorkbenchScroll().setPreferredSize(new Dimension(450, 450));
        getWorkbenchScroll().setBorder(new TitledBorder(""));

        getWorkbench().addMouseListener(new MouseAdapter() {
            public void mouseExited(MouseEvent e) {
                storeLatestWorkbenchGraph();
            }
        });


        return display;
    }

    private Ling.StoredGraphs arrangeGraphs() {
        LingRunner runner = (LingRunner) getAlgorithmRunner();
        Graph resultGraph = runner.getResultGraph();

        Ling.StoredGraphs storedGraphs = runner.getStoredGraphs();
        if (storedGraphs == null) storedGraphs = new Ling.StoredGraphs();

        Graph latestWorkbenchGraph = runner.getParams() .getSourceGraph();
        Graph sourceGraph = runner.getSourceGraph();

        boolean arrangedAll = false;

        for (int i = 0; i < storedGraphs.getNumGraphs(); i++) {
            arrangedAll = GraphUtils.arrangeBySourceGraph(storedGraphs.getGraph(i),
                    latestWorkbenchGraph);
        }

        if (!arrangedAll) {
            for (int i = 0; i < storedGraphs.getNumGraphs(); i++) {
                arrangedAll = GraphUtils.arrangeBySourceGraph(resultGraph, sourceGraph);
            }
        }

        if (!arrangedAll) {
            for (int i = 0; i < storedGraphs.getNumGraphs(); i++) {
                GraphUtils.circleLayout(resultGraph, 200, 200, 150);
            }
        }
        return storedGraphs;
    }

    private Graph resultGraph() {
        Graph resultGraph = getAlgorithmRunner().getResultGraph();

        if (resultGraph == null) {
            resultGraph = new EdgeListGraph();
        }

        return resultGraph;
    }


    /**
     * Executes the algorithm. The execution takes place inside a thread, so one
     * cannot count on a result graph having been found when the method
     */
    public void execute() {
        Window owner = (Window) getTopLevelAncestor();

        final WatchedProcess process = new WatchedProcess(owner) {
            public void watch() {
                getExecuteButton().setEnabled(false);
                setErrorMessage(null);

                if (!knowledgeMessageShown) {
                    IKnowledge knowledge = getAlgorithmRunner().getParams().getKnowledge();
                    if (!knowledge.isEmpty()) {
                        JOptionPane.showMessageDialog(
                                JOptionUtils.centeringComp(),
                                "Using previously set knowledge. (To edit, use " +
                                        "the Knowledge menu.)");
                        knowledgeMessageShown = true;
                    }
                }

                try {
                    storeLatestWorkbenchGraph();
                    getAlgorithmRunner().execute();
                    LingRunner runner = (LingRunner) getAlgorithmRunner();
                    arrangeGraphs();
                    lingDisplay.resetGraphs(runner.getStoredGraphs());
                } catch (Exception e) {
                    CharArrayWriter writer1 = new CharArrayWriter();
                    PrintWriter writer2 = new PrintWriter(writer1);
                    e.printStackTrace(writer2);
                    String message = writer1.toString();
                    writer2.close();

                    e.printStackTrace(System.out);

                    TetradLogger.getInstance().error(message);

                    String messageString = e.getMessage();

                    if (e.getCause() != null) {
                        messageString = e.getCause().getMessage();
                    }

                    if (messageString == null) {
                        messageString = message;
                    }
                    setErrorMessage(messageString);

                    TetradLogger.getInstance().error("************Algorithm stopped!");

                    getExecuteButton().setEnabled(true);
                    throw new RuntimeException(e);
                }

                getWorkbenchScroll().setBorder(
                        new TitledBorder(getResultLabel()));
                Graph resultGraph = resultGraph();

                doDefaultArrangement(resultGraph);
                getWorkbench().setBackground(Color.WHITE);
                getWorkbench().setGraph(resultGraph);
                getGraphHistory().clear();
                getGraphHistory().add(resultGraph);
                getWorkbench().repaint();

                // For Mimbuild, e.g., that need to do a second stage.
                firePropertyChange("algorithmFinished", null, null);
                getExecuteButton().setEnabled(true);
                firePropertyChange("modelChanged", null, null);
            }
        };

        Thread watcher = new Thread() {
            public void run() {
                while (true) {
                    try {
                        sleep(300);

                        if (!process.isAlive()) {
                            getExecuteButton().setEnabled(true);
                            return;
                        }
                    }
                    catch (InterruptedException e) {
                        getExecuteButton().setEnabled(true);
                        return;
                    }
                }
            }
        };

        watcher.start();
    }


    /**
     * Construct the toolbar panel.
     */
    protected JPanel getToolbar() {
        JPanel toolbar = new JPanel();

        getExecuteButton().setText("Execute*");
        getExecuteButton().addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                execute();
            }
        });

        Box b1 = Box.createVerticalBox();
        Box b21 = Box.createVerticalBox();

        Box b211 = Box.createHorizontalBox();
        b211.add(new JLabel("Threshold "));
        LingParams params = (LingParams) getAlgorithmRunner().getParams();
        double pruneFactor = params.getThreshold();
        DoubleTextField field = new DoubleTextField(pruneFactor, 8, NumberFormatUtil.getInstance().getNumberFormat());

        field.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                if (value > 0.0) {
                    setThreshold(value);
                    return value;
                }

                return oldValue;
            }
        });

        b211.add(field);

        b21.add(b211);

        JPanel paramsPanel = new JPanel();
        paramsPanel.add(b21);
        paramsPanel.setBorder(new TitledBorder("Parameters"));
        b1.add(paramsPanel);
        b1.add(Box.createVerticalStrut(10));

        Box b2 = Box.createHorizontalBox();
        b2.add(Box.createGlue());
        b2.add(getExecuteButton());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(10));

        if (getAlgorithmRunner().getDataModel() instanceof DataSet) {
            Box b3 = Box.createHorizontalBox();
            b3.add(Box.createGlue());
            b1.add(b3);
        }

        if (getAlgorithmRunner().getParams() instanceof MeekSearchParams) {
            b1.add(Box.createVerticalStrut(5));
            Box hBox = Box.createHorizontalBox();
            hBox.add(Box.createHorizontalGlue());
            b1.add(hBox);
            b1.add(Box.createVerticalStrut(5));
        }

        Box b4 = Box.createHorizontalBox();
        JLabel label = new JLabel("<html>" + "*Please note that some" +
                "<br>searches may take a" + "<br>long time to complete." +
                "</html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setBorder(new TitledBorder(""));
        b4.add(label);

        b1.add(Box.createVerticalStrut(10));
        b1.add(b4);

        toolbar.add(b1);
        return toolbar;
    }


    private void setThreshold(double value) {
        LingParams params = (LingParams) getAlgorithmRunner().getParams();
        params.setThreshold(value);
    }

    protected void doPostExecutionSteps() {
//        calcStats();
        System.out.println("Post execution.");



//        getWorkbench().addPropertyChangeListener(new PropertyChangeListener() {
//            public void propertyChange(PropertyChangeEvent evt) {
//                System.out.println(evt.getPropertyName());
//            }
//        });
    }

    protected void addSpecialMenus(JMenuBar menuBar) {
        if (!(getAlgorithmRunner() instanceof IGesRunner)) {
            JMenu test = new JMenu("Independence");
            menuBar.add(test);

            IndTestMenuItems.addIndependenceTestChoices(test, this);

            test.addSeparator();
        }

        JMenu graph = new JMenu("Graph");

        graph.add(new GraphPropertiesAction(getWorkbench()));
        graph.add(new PathsAction(getWorkbench()));

        menuBar.add(graph);

    }

    public Graph getSourceGraph() {
        Graph sourceGraph = getWorkbench().getGraph();

        if (sourceGraph == null) {
            sourceGraph = getAlgorithmRunner().getSourceGraph();
        }
        return sourceGraph;
    }

    public List<String> getVarNames() {
        SearchParams params = getAlgorithmRunner().getParams();
        return params.getVarNames();
    }


    public void setTestType(IndTestType testType) {
        super.setTestType(testType);
    }

    public IndTestType getTestType() {
        return super.getTestType();
    }

    public void setKnowledge(IKnowledge knowledge) {
        getAlgorithmRunner().getParams().setKnowledge(knowledge);
    }

    public IKnowledge getKnowledge() {
        return getAlgorithmRunner().getParams().getKnowledge();
    }

    //================================PRIVATE METHODS====================//

    protected void doDefaultArrangement(Graph resultGraph) {
        if (getLatestWorkbenchGraph() != null) {   //(alreadyLaidOut) {
            GraphUtils.arrangeBySourceGraph(resultGraph,
                    getLatestWorkbenchGraph());
        } else if (getKnowledge().isDefaultToKnowledgeLayout()) {
            SearchGraphUtils.arrangeByKnowledgeTiers(resultGraph,
                    getKnowledge());
//            alreadyLaidOut = true;
        } else {
            GraphUtils.circleLayout(resultGraph, 200, 200, 150);
//            alreadyLaidOut = true;
        }
    }

//    private JScrollPane dagWorkbenchScroll(String resultLabel, Graph dag) {
//
//        GraphWorkbench dagWorkbench = new GraphWorkbench(dag);
//        dagWorkbench.setAllowDoubleClickActions(false);
//        dagWorkbench.setAllowNodeEdgeSelection(true);
//        JScrollPane dagWorkbenchScroll = new JScrollPane(dagWorkbench);
//        dagWorkbenchScroll.setPreferredSize(new Dimension(450, 450));
//        dagWorkbenchScroll.setBorder(new TitledBorder(resultLabel));
//
//        dagWorkbench.addMouseListener(new MouseAdapter() {
//            public void mouseExited(MouseEvent e) {
//                storeLatestWorkbenchGraph();
//            }
//        });
//
//        return dagWorkbenchScroll;
//    }

}


