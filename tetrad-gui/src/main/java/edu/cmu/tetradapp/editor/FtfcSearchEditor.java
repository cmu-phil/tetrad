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

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.model.*;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.rmi.MarshalledObject;

/**
 * Abstract base class for a number of search editors.
 *
 * @author Joseph Ramsey
 */
public class FtfcSearchEditor extends JPanel {

    /**
     * The algorithm wrapper being viewed.
     */
    private FtfcRunner mimRunner;

    /**
     * The workbench displaying the result workbench.
     */
    private GraphWorkbench workbench;

    /**
     * The latest thread.
     */
    private Thread thread;


    /**
     * Delay for displaying progress bar.
     */
    private final int delay = 200;

    /**
     * True iff the algorithm stopped with an error.
     */
    private String errorMessage;

    /**
     * The button one clicks to executeButton the algorithm.
     */
    private JButton executeButton = new JButton();

    /**
     * The label for the result graph workbench.
     */
    private String resultLabel;

    /**
     * The scrollpange for the result workbench.
     */
    private JScrollPane workbenchScroll;
    private JPanel displayPanel;
    private GraphWorkbench structureWorkbench;

    //============================CONSTRUCTORS===========================//

    private FtfcSearchEditor(FtfcRunner mimRunner, String resultLabel) {
        if (mimRunner == null) {
            throw new NullPointerException();
        }

        if (resultLabel == null) {
            throw new NullPointerException();
        }

        this.mimRunner = mimRunner;
        this.resultLabel = resultLabel;

        setup(resultLabel);
    }

    /**
     * Allows the user to pop up an editor for a MimBuildRunner.
     */
    public FtfcSearchEditor(FtfcRunner runner) {
        this(runner, "Result MAG");
    }

    //==============================PRIVATE METHODS=======================//

    /**
     * Construct the toolbar panel.
     */
    private JPanel getToolbar() {
        JPanel toolbar = new JPanel();
        getExecuteButton().setText("Execute*");
        getExecuteButton().addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                execute();
            }
        });

        Box b1 = Box.createVerticalBox();
        b1.add(getParamsPanel());
        b1.add(Box.createVerticalStrut(10));
        Box b2 = Box.createHorizontalBox();
        b2.add(Box.createGlue());
        b2.add(getExecuteButton());
        b1.add(b2);

        Box b3 = Box.createHorizontalBox();
        JLabel label = new JLabel("<html>" + "*Please note that some" +
                "<br>searches may take a" + "<br>long time to complete." +
                "</html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setBorder(new TitledBorder(""));
        b3.add(label);

        b1.add(Box.createVerticalStrut(10));
        b1.add(b3);

        addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("algorithmFinished".equals(evt.getPropertyName())) {
                    specialToolbarSetup();
                }
            }
        });

        toolbar.add(b1);
        return toolbar;
    }

    /**
     * Executes the algorithm.
     */
    private void execute() {
        Runnable runnable = new Runnable() {
            public void run() {
                getExecuteButton().setEnabled(false);
                setErrorMessage(null);

                try {
//                    mimRunner.getParams().setClusters(clusterEditor.getClusters());
                    getMimRunner().execute();
                }
                catch (Exception e) {
                    CharArrayWriter writer1 = new CharArrayWriter();
                    PrintWriter writer2 = new PrintWriter(writer1);
                    e.printStackTrace(writer2);
                    String message = writer1.toString();
                    writer2.close();

                    e.printStackTrace(System.out);

                    TetradLogger.getInstance().error(message);

                    String messageString = e.getMessage();
                    if (messageString == null) {
                        messageString = message;
                    }
                    setErrorMessage(messageString);

                    TetradLogger.getInstance().error("************Algorithm stopped!");

                    getExecuteButton().setEnabled(true);
                    throw new RuntimeException(e);
                }

                updateDisplayPanel();

                getWorkbenchScroll().setBorder(
                        new TitledBorder(getResultLabel()));
                Graph resultGraph = resultGraph();

                doDefaultArrangement(resultGraph);

                GraphUtils.arrangeBySourceGraph(resultGraph, getWorkbench().getGraph());

                getWorkbench().setBackground(Color.WHITE);
                getWorkbench().setGraph(resultGraph);
                getWorkbench().repaint();

                // For Mimbuild, e.g., that need to do a second stage.
                firePropertyChange("algorithmFinished", null, null);

                getExecuteButton().setEnabled(true);
                firePropertyChange("modelChanged", null, null);
            }
        };

        final Thread thread = new Thread(runnable);
        thread.setPriority(Thread.NORM_PRIORITY + 1);
        this.thread = thread;
        thread.start();

        Thread watcher = new Thread() {
            public void run() {
                try {
                    sleep(delay);
                }
                catch (InterruptedException e) {
                    return;
                }

                if (getErrorMessage() != null) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "Stopped with error:\n" + getErrorMessage());
                    return;
                }

                JProgressBar progressBar = new JProgressBar(0, 100);
                progressBar.setIndeterminate(true);

                JButton stopButton = new JButton("Stop");

                stopButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        if (thread() != null) {
                            thread().stop();

                            JOptionPane.showMessageDialog(
                                    JOptionUtils.centeringComp(),
                                    "Algorithm stopped");
                            getExecuteButton().setEnabled(true);

                            TetradLogger.getInstance().error("************Algorithm stopped!");
                        }
                    }
                });

                Box b = Box.createHorizontalBox();
                b.add(progressBar);
                b.add(stopButton);

                Frame ancestor =
                        (Frame) JOptionUtils.centeringComp().getTopLevelAncestor();
                JDialog dialog = new JDialog(ancestor, "Searching...", false);

                dialog.getContentPane().add(b);
                dialog.pack();
                dialog.setLocationRelativeTo(FtfcSearchEditor.this);
                dialog.setVisible(true);

                while (thread().isAlive()) {
                    try {
                        sleep(200);
                    }
                    catch (InterruptedException e) {
                        return;
                    }
                }

                dialog.setVisible(false);

                dialog.dispose();

                if (getErrorMessage() != null) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "Stopped with error:\n" + getErrorMessage());
                }
            }
        };

        watcher.setPriority(Thread.NORM_PRIORITY + 1);
        watcher.start();
    }

    private void doDefaultArrangement(Graph graph) {
        GraphUtils.circleLayout(graph, 200, 200, 150);
        GraphUtils.fruchtermanReingoldLayout(graph);
    }

    /**
     * Makes the result workbench available to inner classes.
     */
    private GraphWorkbench getWorkbench() {
        return this.workbench;
    }

    private JButton getExecuteButton() {
        return executeButton;
    }

    private MimRunner getMimRunner() {
        return mimRunner;
    }

    /**
     * Sets up the editor, does the layout, and so on.
     */
    private void setup(String resultLabel) {
        setLayout(new BorderLayout());
        add(getToolbar(), BorderLayout.WEST);
        add(workbenchScroll(resultLabel), BorderLayout.CENTER);

        displayPanel = new JPanel();
        displayPanel.setLayout(new BorderLayout());
        displayPanel.setPreferredSize(new Dimension(500, 500));

        updateDisplayPanel();

        add(displayPanel, BorderLayout.CENTER);
        add(menuBar(), BorderLayout.NORTH);
    }

    private void updateDisplayPanel() {
        displayPanel.removeAll();

        JTabbedPane tabbedPane = new JTabbedPane();

        if (getMimRunner().getStructureGraph() != null) {
            if (getMimRunner().getStructureGraph() != null) {
//                DataGraphUtils.circleLayout(structureGraph, 200, 200, 150);
                Graph structureGraph = getMimRunner().getStructureGraph();
                doDefaultArrangement(structureGraph);
                structureWorkbench = new GraphWorkbench(structureGraph);
                structureWorkbench.setAllowDoubleClickActions(false);

                tabbedPane.add("Structure Model",
                        new JScrollPane(structureWorkbench));
            }
        }

        if (getMimRunner().getClusters() != null) {
            ClusterEditor editor =  new ClusterEditor(getMimRunner().getClusters(),
                    getMimRunner().getData().getVariableNames());
            tabbedPane.add("Measurement Model", editor);
        }

        if (getMimRunner().getFullGraph() != null) {
            Graph fullGraph = getMimRunner().getFullGraph();
            doDefaultArrangement(fullGraph);
            GraphUtils.fruchtermanReingoldLayout(fullGraph);

            GraphWorkbench fullGraphBench = new GraphWorkbench(fullGraph);
            tabbedPane.add("Full Graph", new JScrollPane(fullGraphBench));
        }

        displayPanel.add(tabbedPane, BorderLayout.CENTER);
        displayPanel.revalidate();
        displayPanel.repaint();
    }

    private Graph resultGraph() {
        Graph resultGraph = getMimRunner().getResultGraph();

        if (resultGraph == null) {
            resultGraph = new EdgeListGraph();
        }

        return resultGraph;
    }

    private JScrollPane workbenchScroll(String resultLabel) {
        Graph resultGraph = resultGraph();

        Graph sourceGraph = getMimRunner().getSourceGraph();
        Graph latestWorkbenchGraph =
                getMimRunner().getParams().getSourceGraph();

        boolean arrangedAll = GraphUtils.arrangeBySourceGraph(resultGraph,
                latestWorkbenchGraph);

        if (!arrangedAll) {
            GraphUtils.arrangeBySourceGraph(resultGraph, sourceGraph);
        }

        this.workbench = new GraphWorkbench(resultGraph);
        this.workbench.setAllowDoubleClickActions(false);
        this.workbenchScroll = new JScrollPane(getWorkbench());
        getWorkbenchScroll().setPreferredSize(new Dimension(450, 450));
        getWorkbenchScroll().setBorder(new TitledBorder(resultLabel));

        this.workbench.addMouseListener(new MouseAdapter() {
            public void mouseExited(MouseEvent e) {
                storeLatestWorkbenchGraph();
            }
        });

        return getWorkbenchScroll();
    }

    private JMenuBar menuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        menuBar.add(file);
        return menuBar;
    }

    private Thread thread() {
        return thread;
    }

    private String getErrorMessage() {
        return errorMessage;
    }

    private void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    private String getResultLabel() {
        return resultLabel;
    }

    private JScrollPane getWorkbenchScroll() {
        return workbenchScroll;
    }

    public Graph getLatestWorkbenchGraph() {
        Graph graph = getMimRunner().getParams().getSourceGraph();

        if (graph == null) {
            return getMimRunner().getSourceGraph();
        }

        return graph;
    }

    private void storeLatestWorkbenchGraph() {
        Graph latestWorkbenchGraph = getWorkbench().getGraph();

        if (latestWorkbenchGraph.getNumNodes() == 0) {
            return;
        }

        try {
            Graph graph = new MarshalledObject<Graph>(latestWorkbenchGraph).get();
            getMimRunner().getParams().setSourceGraph(graph);
        }
        catch (IOException e) {
            getMimRunner().getParams().setSourceGraph(null);
        }
        catch (ClassNotFoundException e) {
            getMimRunner().getParams().setSourceGraph(null);
            e.printStackTrace();
        }
    }

    private Box getParamsPanel() {
        Box b2 = Box.createVerticalBox();
        b2.add(getIndTestParamBox());
        b2.setBorder(new TitledBorder("Parameters"));
        return b2;
    }

    private void specialToolbarSetup() {
    }

    private JComponent getIndTestParamBox() {
        MimParams params = getMimRunner().getParams();
        MimIndTestParams indTestParams = params.getMimIndTestParams();
        return getIndTestParamBox(indTestParams);
    }

    /**
     * Factory to return the correct param editor for independence test params.
     * This will go in a little box in the search editor.
     */
    private JComponent getIndTestParamBox(MimIndTestParams indTestParams) {
        if (indTestParams == null) {
            throw new NullPointerException();
        }

        if (indTestParams instanceof FtfcIndTestParams) {
            MimRunner runner = getMimRunner();
            FtfcIndTestParams params =  (FtfcIndTestParams) indTestParams;
            params.setVarNames(runner.getParams().getVarNames());
            return new FtfcIndTestParamsEditor(params);
        }

        throw new IllegalArgumentException(
                "Unrecognized IndTestParams: " + indTestParams.getClass());
    }
}





