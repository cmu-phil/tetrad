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
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.IndTestType;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.model.AlgorithmRunner;
import edu.cmu.tetradapp.util.GraphHistory;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.rmi.MarshalledObject;
import java.util.ArrayList;

/**
 * Abstract base class for a number of search editors. The advantage of
 * extending this class, in case you were wondering, is that it will handle
 * threading for you, provide a stop button for algorithm, and do logging. The
 * execute button used must be getExecuteButton(), or else logging won't work.
 *
 * @author Joseph Ramsey
 */
public abstract class AbstractSearchEditor extends JPanel implements GraphEditable, IndTestTypeSetter {

    /**
     * The algorithm wrapper being viewed.
     */
    private final AlgorithmRunner algorithmRunner;

    /**
     * The workbench displaying the result workbench.
     */
    private GraphWorkbench workbench;


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

    /**
     * True if the warning message that previously defined knowledge is being
     * used has already been shown and doesn't need to be shown again.
     */
    boolean knowledgeMessageShown;

    /**
     * History of graph edits.
     */
    private final GraphHistory graphHistory = new GraphHistory();

    //============================CONSTRUCTOR===========================//

    AbstractSearchEditor(final AlgorithmRunner algorithmRunner, final String resultLabel) {
        if (algorithmRunner == null) {
            throw new NullPointerException();
        }

        if (resultLabel == null) {
            throw new NullPointerException();
        }

        this.algorithmRunner = algorithmRunner;
        this.resultLabel = resultLabel;

        setup(resultLabel);
    }

    /**
     * Sets the name of this editor.
     */
    public final void setName(final String name) {
        final String oldName = getName();
        super.setName(name);
        firePropertyChange("name", oldName, getName());
    }

    //========================== Public Methods required by GraphEditable ======//


    /**
     * @return the work bench of null if there isn't one.
     */
    public GraphWorkbench getWorkbench() {
        return this.workbench;
    }


    /**
     * Not supported.
     */
    public void setGraph(final Graph g) {
        this.workbench.setGraph(g);
        throw new UnsupportedOperationException("Cannot set the graph on a search editor.");
    }


    /**
     * @return the graph.
     */
    public Graph getGraph() {
        if (this.workbench != null) {
            return this.workbench.getGraph();
        }
        return new EdgeListGraph();
    }


    /**
     * Returns a list of all the SessionNodeWrappers (TetradNodes) and
     * SessionNodeEdges that are model components for the respective
     * SessionNodes and SessionEdges selected in the workbench. Note that the
     * workbench, not the SessionEditorNodes themselves, keeps track of the
     * selection.
     *
     * @return the set of selected model nodes.
     */
    public java.util.List getSelectedModelComponents() {
        final java.util.List<Component> selectedComponents =
                this.workbench.getSelectedComponents();
        final java.util.List<TetradSerializable> selectedModelComponents =
                new ArrayList<>();

        for (final Component comp : selectedComponents) {
            if (comp instanceof DisplayNode) {
                selectedModelComponents.add(
                        ((DisplayNode) comp).getModelNode());
            } else if (comp instanceof DisplayEdge) {
                selectedModelComponents.add(
                        ((DisplayEdge) comp).getModelEdge());
            }
        }

        return selectedModelComponents;
    }


    /**
     * Not supported.
     */
    public void pasteSubsession(final java.util.List sessionElements, final Point upperLeft) {
        throw new UnsupportedOperationException("Cannot paste into Search editor.");
    }

    //===========================PROTECTED METHODS==========================//

    /**
     * Constructs the toolbar panel. For the execute button, must use
     * getExecuteButton() in order for logging to work.
     */
    protected abstract JPanel getToolbar();

    /**
     * Adds any special menus needed for a particular search editor. These will
     * be added to the right of the normal ones.
     */
    protected abstract void addSpecialMenus(JMenuBar menuBar);


    /**
     * Executes the algorithm. The execution takes place inside a thread, so one
     * cannot count on a result graph having been found when the method
     */
    void execute() {
        final Window owner = (Window) getTopLevelAncestor();

        final WatchedProcess process = new WatchedProcess(owner) {
            public void watch() {
                try {
                    getExecuteButton().setEnabled(false);
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
                setErrorMessage(null);

                if (!AbstractSearchEditor.this.knowledgeMessageShown) {
                    final Parameters searchParams = getAlgorithmRunner().getParams();

                    if (searchParams != null) {
                        final IKnowledge knowledge = (IKnowledge) searchParams.get("knowledge", new Knowledge2());
                        if (!knowledge.isEmpty()) {
                            JOptionPane.showMessageDialog(
                                    JOptionUtils.centeringComp(),
                                    "Using previously set knowledge. (To edit, use " +
                                            "the Knowledge menu.)");
                            AbstractSearchEditor.this.knowledgeMessageShown = true;
                        }
                    }
                }


                try {
                    storeLatestWorkbenchGraph();
                    getAlgorithmRunner().execute();
                } catch (final Exception e) {
                    final CharArrayWriter writer1 = new CharArrayWriter();
                    final PrintWriter writer2 = new PrintWriter(writer1);
                    e.printStackTrace(writer2);
                    final String message = writer1.toString();
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

//                getWorkbenchScroll().setBorder(
//                        new TitledBorder(getResultLabel()));
                final Graph resultGraph = resultGraph();

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

        final Thread watcher = new Thread() {
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(300);

                        if (!process.isAlive()) {
                            getExecuteButton().setEnabled(true);
                            return;
                        }
                    } catch (final InterruptedException e) {
                        getExecuteButton().setEnabled(true);
                        return;
                    }
                }
            }
        };

        watcher.start();
    }

    void doPostExecutionSteps() {
        // Do nothing for abstract search editor.
    }

    protected abstract void doDefaultArrangement(Graph resultGraph);


    JButton getExecuteButton() {
        return this.executeButton;
    }

    AlgorithmRunner getAlgorithmRunner() {
        return this.algorithmRunner;
    }

    //===========================PRIVATE METHODS==========================//

    private Graph resultGraph() {
        Graph resultGraph = this.algorithmRunner.getGraph();

        if (resultGraph == null) {
            resultGraph = new EdgeListGraph();
        }

        return resultGraph;
    }

    void setWorkbench(final GraphWorkbench graphWorkbench) {
        this.workbench = graphWorkbench;
    }

    void setWorkbenchScroll(final JScrollPane workbenchScroll) {
        this.workbenchScroll = workbenchScroll;
    }

    /**
     * Sets up the editor, does the layout, and so on.
     */
    void setup(final String resultLabel) {
        setLayout(new BorderLayout());
        add(getToolbar(), BorderLayout.WEST);
        add(workbenchScroll(resultLabel), BorderLayout.CENTER);
        add(menuBar(), BorderLayout.NORTH);
    }


    JScrollPane workbenchScroll(final String resultLabel) {
        final Graph resultGraph = resultGraph();

        final Graph sourceGraph = this.algorithmRunner.getSourceGraph();
        final Parameters searchParams = this.algorithmRunner.getParams();
        Graph latestWorkbenchGraph = null;

        if (searchParams != null) {
            latestWorkbenchGraph = (Graph) searchParams.get("sourceGraph", null);
        }

        if (latestWorkbenchGraph == null) {
            GraphUtils.arrangeBySourceGraph(resultGraph, sourceGraph);
        } else {
            GraphUtils.arrangeBySourceGraph(resultGraph, latestWorkbenchGraph);
        }

//        boolean arrangedAll = DataGraphUtils.arrangeBySourceGraph(resultGraph,
//                latestWorkbenchGraph);
//
//        if (!arrangedAll) {
//            arrangedAll =
//                    DataGraphUtils.arrangeBySourceGraph(resultGraph, sourceGraph);
//        }

//        if (!arrangedAll) {
//            DataGraphUtils.circleLayout(resultGraph, 200, 200, 150);
//        }

        this.workbench = new GraphWorkbench(resultGraph);

        this.graphHistory.clear();
        this.graphHistory.add(resultGraph);

        this.workbench.setAllowDoubleClickActions(false);
        this.workbench.setAllowNodeEdgeSelection(true);
        this.workbenchScroll = new JScrollPane(this.workbench);
//        workbenchScroll.setPreferredSize(new Dimension(450, 450));
//        workbenchScroll.setBorder(new TitledBorder(resultLabel));

        this.workbench.addMouseListener(new MouseAdapter() {
            public void mouseExited(final MouseEvent e) {
                storeLatestWorkbenchGraph();
            }
        });

        return this.workbenchScroll;
    }

//     JMenuItem copy = new JMenuItem(new CopySubgraphAction(this));
//        JMenuItem paste = new JMenuItem(new PasteSubgraphAction(this));
//
//        copy.setAccelerator(
//                KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
//        paste.setAccelerator(
//                KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK));
//
//        edit.add(copy);
//        edit.add(paste);

    /**
     * Creates the menubar for the search editor.
     */
    JMenuBar menuBar() {
        final JMenuBar menuBar = new JMenuBar();
        final JMenu file = new JMenu("File");
        menuBar.add(file);
        final JMenu fileMenu = new GraphFileMenu(this, getWorkbench());
        file.add(fileMenu);
//        file.add(new SaveGraph(this, "Save Graph..."));
//        file.add(new SaveScreenshot(this, true, "Save Screenshot..."));
        file.add(new SaveComponentImage(this.workbench, "Save Graph Image..."));

        final JMenu edit = new JMenu("Edit");
        final JMenuItem copy = new JMenuItem(new CopySubgraphAction(this));
        copy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
        edit.add(copy);

        menuBar.add(edit);

        addSpecialMenus(menuBar);

        return menuBar;
    }


    String getResultLabel() {
        return this.resultLabel;
    }

    JScrollPane getWorkbenchScroll() {
        return this.workbenchScroll;
    }

    Graph getLatestWorkbenchGraph() {
        if (this.algorithmRunner.getParams() == null) {
            return null;
        }

        final Graph graph = (Graph) this.algorithmRunner.getParams().get("sourceGraph", null);

        if (graph == null) {
            return this.algorithmRunner.getSourceGraph();
        }

        return graph;
    }

    void storeLatestWorkbenchGraph() {
        final Graph latestWorkbenchGraph = this.workbench.getGraph();

        if (latestWorkbenchGraph.getNumNodes() == 0) {
            return;
        }

        final Parameters searchParams = this.algorithmRunner.getParams();

        try {
            final Graph graph = new MarshalledObject<>(latestWorkbenchGraph).get();

            if (graph == null) {
                throw new NullPointerException("Null graph");
            }

            if (searchParams != null) {
                searchParams.set("sourceGraph", graph);
            }
        } catch (final IOException e) {
            e.printStackTrace();

            if (searchParams != null) {
                searchParams.set("sourceGraph", (Graph) null);
            }
        } catch (final ClassNotFoundException e) {
            if (searchParams != null) {
                searchParams.set("sourceGraph", (Graph) null);
            }

            e.printStackTrace();
        }
    }

    GraphHistory getGraphHistory() {
        return this.graphHistory;
    }

    public void setTestType(final IndTestType testType) {
        getAlgorithmRunner().getParams().set("indTestType", testType);
    }

    public IndTestType getTestType() {
        return (IndTestType) getAlgorithmRunner().getParams().get("indTestType", IndTestType.FISHER_Z);
    }

    public DataModel getDataModel() {
        return getAlgorithmRunner().getDataModel();
    }

    public Object getSourceGraph() {
        return getAlgorithmRunner().getParams().get("sourceGraph", null);
    }
}





