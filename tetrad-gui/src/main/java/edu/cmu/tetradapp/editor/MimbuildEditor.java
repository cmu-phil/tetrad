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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TaskManager;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.model.MimBuildRunner;
import edu.cmu.tetradapp.model.MimBuildTrekRunner;
import edu.cmu.tetradapp.model.MimRunner;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.rmi.MarshalledObject;
import java.util.prefs.Preferences;

/**
 * Abstract base class for a number of search editors.
 *
 * @author Joseph Ramsey
 */
public class MimbuildEditor extends JPanel {

    /**
     * The algorithm wrapper being viewed.
     */
    private final MimRunner mimRunner;

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
    private final JButton executeButton = new JButton();

    /**
     * The label for the result graph workbench.
     */
    private final String resultLabel;

    /**
     * The scrollpange for the result workbench.
     */
    private JScrollPane workbenchScroll;
    private JPanel displayPanel;

    //============================CONSTRUCTORS===========================//

    /**
     * Allows the user to pop up an editor for a MimBuildRunner.
     */
    public MimbuildEditor(MimBuildRunner runner) {
        this(runner, "Result MAG");
    }

    public MimbuildEditor(MimBuildTrekRunner runner) {
        this(runner, "Result MAG");
    }

    private MimbuildEditor(MimRunner mimRunner, String resultLabel) {
        if (mimRunner == null) {
            throw new NullPointerException();
        }

        if (resultLabel == null) {
            throw new NullPointerException();
        }

        this.mimRunner = mimRunner;
        this.resultLabel = resultLabel;

        this.setup(resultLabel);

        Preferences.userRoot().putBoolean("BPCrDown", false);

        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(new KeyEventDispatcher() {
                    public boolean dispatchKeyEvent(KeyEvent e) {
                        int keyCode = e.getKeyCode();
                        int id = e.getID();

                        if (keyCode == KeyEvent.VK_R) {
                            if (id == KeyEvent.KEY_PRESSED) {
                                Preferences.userRoot().putBoolean("BPCrDown", true);
                            } else if (id == KeyEvent.KEY_RELEASED) {
                                Preferences.userRoot().putBoolean("BPCrDown", false);
                            }
                        }

                        return false;
                    }
                });


    }

    //==============================PRIVATE METHODS=======================//

    /**
     * Construct the toolbar panel.
     */
    private JPanel getToolbar() {
        JPanel toolbar = new JPanel();
        this.getExecuteButton().setText("Execute*");
        this.getExecuteButton().addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                MimbuildEditor.this.execute();
            }
        });

        JCheckBox showMaxP = new JCheckBox("Show Max P Value Result");

        showMaxP.setSelected(this.getMimRunner().getParams().getBoolean("showMaxP", false));

        showMaxP.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JCheckBox box = (JCheckBox) e.getSource();
                boolean selected = box.isSelected();

                Parameters params = MimbuildEditor.this.getMimRunner().getParams();
                params.set("showMaxP", selected);

                if (selected) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "Only the graph with the highest P value will be shown, until deselected.");
                } else if (!selected) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "Max P mode turned off and reset.");
                    final double maxP = -1;
                    params.set("maxP", maxP);
                    params.set("maxStructureGraph", (Graph) null);
                }
            }
        });

        JCheckBox include3Clusters = new JCheckBox("Include 3-clusters");

        include3Clusters.setSelected(this.getMimRunner().getParams().getBoolean("includeThreeClusters", true));

        include3Clusters.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JCheckBox box = (JCheckBox) e.getSource();
                boolean selected = box.isSelected();
                MimbuildEditor.this.getMimRunner().getParams().set("includeThreeClusters", selected);
            }
        });

        Box b1 = Box.createVerticalBox();
        b1.add(this.getParamsPanel());
        b1.add(Box.createVerticalStrut(10));
        Box b2 = Box.createHorizontalBox();
        b2.add(Box.createGlue());
        b2.add(this.getExecuteButton());
        b1.add(b2);
//
//        Box b3 = Box.createHorizontalBox();
//        JLabel label = new JLabel("<html>" + "*Please note that some" +
//                "<br>searches may take a" + "<br>long time to complete." +
//                "</html>");
//        label.setHorizontalAlignment(SwingConstants.CENTER);
//        label.setVerticalAlignment(SwingConstants.CENTER);
//        label.setBorder(new TitledBorder(""));
//        b3.add(label);


//        b1.add(Box.createVerticalStrut(10));
//        b1.add(b3);

        b1.add(Box.createVerticalStrut(20));

        Box b4 = Box.createHorizontalBox();
        b4.add(showMaxP);
        b4.add(Box.createHorizontalGlue());
        b1.add(b4);

        Box b5 = Box.createHorizontalBox();
        b5.add(Box.createHorizontalStrut(25));
        b5.add(new JLabel("(Click to set/reset)"));
        b5.add(Box.createHorizontalGlue());
        b1.add(b5);


        b1.add(Box.createVerticalStrut(10));
        Box b6 = Box.createHorizontalBox();
        b6.add(include3Clusters);
        b6.add(Box.createHorizontalGlue());
        b1.add(b6);


        this.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("algorithmFinished".equals(evt.getPropertyName())) {
                    MimbuildEditor.this.specialToolbarSetup();
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
                MimbuildEditor.this.getExecuteButton().setEnabled(false);
                MimbuildEditor.this.setErrorMessage(null);

                try {
//                    mimRunner.getParameters().setClusters(clusterEditor.getClusters());
                    MimbuildEditor.this.getMimRunner().execute();
                } catch (Exception e) {
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
                    MimbuildEditor.this.setErrorMessage(messageString);

                    TetradLogger.getInstance().error("************Algorithm stopped!");

                    MimbuildEditor.this.getExecuteButton().setEnabled(true);
                    throw new RuntimeException(e);
                }

                MimbuildEditor.this.updateDisplayPanel();

                MimbuildEditor.this.getWorkbenchScroll().setBorder(
                        new TitledBorder(MimbuildEditor.this.getResultLabel()));
                Graph resultGraph = MimbuildEditor.this.resultGraph();

                MimbuildEditor.this.doDefaultArrangement(resultGraph);

                GraphUtils.arrangeBySourceGraph(resultGraph, MimbuildEditor.this.getWorkbench().getGraph());

                MimbuildEditor.this.getWorkbench().setBackground(Color.WHITE);
                MimbuildEditor.this.getWorkbench().setGraph(resultGraph);
                MimbuildEditor.this.getWorkbench().repaint();

                // For Mimbuild, e.g., that need to do a second stage.
                MimbuildEditor.this.firePropertyChange("algorithmFinished", null, null);

                MimbuildEditor.this.getExecuteButton().setEnabled(true);
                MimbuildEditor.this.firePropertyChange("modelChanged", null, null);
            }
        };

        Thread thread = new Thread(runnable);
        thread.setPriority(Thread.NORM_PRIORITY + 1);
        this.thread = thread;
        thread.start();

        Thread watcher = new Thread() {
            public void run() {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    return;
                }

                if (MimbuildEditor.this.getErrorMessage() != null) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "Stopped with error:\n" + MimbuildEditor.this.getErrorMessage());
                    return;
                }

                JProgressBar progressBar = new JProgressBar(0, 100);
                progressBar.setIndeterminate(true);

                JButton stopButton = new JButton("Stop");

                stopButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        if (MimbuildEditor.this.thread() != null) {
                            MimbuildEditor.this.thread().stop();
                            TaskManager.getInstance().setCanceled(true);

                            JOptionPane.showMessageDialog(
                                    JOptionUtils.centeringComp(),
                                    "Algorithm stopped");
                            MimbuildEditor.this.getExecuteButton().setEnabled(true);

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
                dialog.setLocationRelativeTo(MimbuildEditor.this);
                dialog.setVisible(true);

                while (MimbuildEditor.this.thread().isAlive()) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        return;
                    }
                }

                dialog.setVisible(false);

                dialog.dispose();

                if (MimbuildEditor.this.getErrorMessage() != null) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "Stopped with error:\n" + MimbuildEditor.this.getErrorMessage());
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
        return workbench;
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
        this.setLayout(new BorderLayout());
        this.add(this.getToolbar(), BorderLayout.WEST);
        this.add(this.workbenchScroll(resultLabel), BorderLayout.CENTER);

        displayPanel = new JPanel();
        displayPanel.setLayout(new BorderLayout());
        displayPanel.setPreferredSize(new Dimension(500, 500));

        this.updateDisplayPanel();

        this.add(displayPanel, BorderLayout.CENTER);
        this.add(this.menuBar(), BorderLayout.NORTH);
    }

    private void updateDisplayPanel() {
        displayPanel.removeAll();

        JTabbedPane tabbedPane = new JTabbedPane();

        if (this.getMimRunner().getStructureGraph() != null) {
            if (this.getMimRunner().getStructureGraph() != null) {
//                DataGraphUtils.circleLayout(structureGraph, 200, 200, 150);
                Graph structureGraph = this.getMimRunner().getStructureGraph();
                this.doDefaultArrangement(structureGraph);
                GraphWorkbench structureWorkbench = new GraphWorkbench(structureGraph);
                structureWorkbench.setAllowDoubleClickActions(false);

                tabbedPane.add("Structure Model",
                        new JScrollPane(structureWorkbench));
            }
        } else {
            tabbedPane.add("Structure Model",
                    new JScrollPane(new GraphWorkbench()));
        }

        if (this.getMimRunner().getClusters() != null) {
            ClusterEditor editor = new ClusterEditor(this.getMimRunner().getClusters(),
                    this.getMimRunner().getData().getVariableNames());
            tabbedPane.add("Measurement Model", editor);
        }

        if (this.getMimRunner().getFullGraph() != null) {
            Graph fullGraph = this.getMimRunner().getFullGraph();
            this.doDefaultArrangement(fullGraph);
            GraphUtils.fruchtermanReingoldLayout(fullGraph);

            GraphWorkbench fullGraphBench = new GraphWorkbench(fullGraph);
            tabbedPane.add("Full Graph", new JScrollPane(fullGraphBench));
        } else {
            tabbedPane.add("Full Graph", new JScrollPane(new GraphWorkbench()));
        }

        displayPanel.add(tabbedPane, BorderLayout.CENTER);
        displayPanel.revalidate();
        displayPanel.repaint();
    }

    private Graph resultGraph() {
        Graph resultGraph = this.getMimRunner().getResultGraph();

        if (resultGraph == null) {
            resultGraph = new EdgeListGraph();
        }

        return resultGraph;
    }

    private JScrollPane workbenchScroll(String resultLabel) {
        Graph resultGraph = this.resultGraph();

        Graph sourceGraph = this.getMimRunner().getSourceGraph();
        Graph latestWorkbenchGraph =
                (Graph) this.getMimRunner().getParams().get("sourceGraph", null);

        boolean arrangedAll = GraphUtils.arrangeBySourceGraph(resultGraph,
                latestWorkbenchGraph);

        if (!arrangedAll) {
            GraphUtils.arrangeBySourceGraph(resultGraph, sourceGraph);
        }

        workbench = new GraphWorkbench(resultGraph);
        workbench.setAllowDoubleClickActions(false);
        workbenchScroll = new JScrollPane(this.getWorkbench());
        this.getWorkbenchScroll().setPreferredSize(new Dimension(450, 450));
        this.getWorkbenchScroll().setBorder(new TitledBorder(resultLabel));

        workbench.addMouseListener(new MouseAdapter() {
            public void mouseExited(MouseEvent e) {
                MimbuildEditor.this.storeLatestWorkbenchGraph();
            }
        });

        return this.getWorkbenchScroll();
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
        Graph graph = (Graph) this.getMimRunner().getParams().get("sourceGraph", null);

        if (graph == null) {
            return this.getMimRunner().getSourceGraph();
        }

        return graph;
    }

    private void storeLatestWorkbenchGraph() {
        Graph latestWorkbenchGraph = this.getWorkbench().getGraph();

        if (latestWorkbenchGraph.getNumNodes() == 0) {
            return;
        }

        try {
            Graph graph = new MarshalledObject<>(latestWorkbenchGraph).get();
            this.getMimRunner().getParams().set("sourceGraph", graph);
        } catch (IOException e) {
            this.getMimRunner().getParams().set("sourceGraph", (Graph) null);
        } catch (ClassNotFoundException e) {
            this.getMimRunner().getParams().set("sourceGraph", (Graph) null);
            e.printStackTrace();
        }
    }

    private Box getParamsPanel() {
        Box b2 = Box.createVerticalBox();
        b2.add(this.getIndTestParamBox());
        b2.setBorder(new TitledBorder("Parameters"));
        return b2;
    }

    private void specialToolbarSetup() {
    }

    private JComponent getIndTestParamBox() {
        Parameters params = this.getMimRunner().getParams();
        return this.getIndTestParamBox(params);
    }

    /**
     * Factory to return the correct param editor for independence test params.
     * This will go in a little box in the search editor.
     */
    private JComponent getIndTestParamBox(Parameters params) {
        if (params == null) {
            throw new NullPointerException();
        }

        if (params instanceof Parameters) {
            MimRunner runner = this.getMimRunner();
            params.set("varNames", runner.getParams().get("varNames", null));
            DataModel dataModel = runner.getData();

            if (dataModel instanceof DataSet) {
                DataSet data = (DataSet) runner.getData();
                boolean discrete = data.isDiscrete();
                return new BuildPureClustersIndTestParamsEditor2(params,
                        discrete);
            } else if (dataModel instanceof ICovarianceMatrix) {
                return new BuildPureClustersIndTestParamsEditor2(params, false);
            }
        }

        if (params instanceof Parameters) {
            MimRunner runner = this.getMimRunner();
            params.set("varNames", runner.getParams().get("varNames", null));

            boolean discreteData = false;

            if (runner.getData() instanceof DataSet) {
                discreteData = runner.getData().isDiscrete();
            }

            return new PurifyIndTestParamsEditor(params, discreteData);
        }

        if (params instanceof Parameters) {
            MimRunner runner = this.getMimRunner();
            params.set("varNames", runner.getParams().get("varNames", null));
            return new MimBuildIndTestParamsEditor(params);
        }

        throw new IllegalArgumentException(
                "Unrecognized Parameters: " + params.getClass());
    }
}





