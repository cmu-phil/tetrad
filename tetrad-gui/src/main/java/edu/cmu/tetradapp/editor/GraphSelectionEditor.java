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

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.model.GraphSelectionWrapper;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.*;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.CharArrayReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Allows the user to execute a multiple linear regression in the GUI. Contains
 * a panel that lets the user specify a target variable and a list of continuous
 * regressors, plus a tabbed pane that includes (a) a display to show the result
 * of the regression and (b) a graph workbench to show the graph of the target
 * with significant regressors from the regression as parents.
 *
 * @author Tyler Gibosn
 * @author Aaron Powers
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 * @author Frank Wimberly - adapted for EM Bayes estimator and Strucural EM
 *         Bayes estimator
 */
public class GraphSelectionEditor extends JPanel implements GraphEditable {

    private final GraphSelectionEditorPanel editorPanel;

    /**
     * The workbench used to display the graph of significant regression into
     * the target.
     */
    private GraphWorkbench workbench;

    /**
     * The gadget that does the regression.
     */
    private GraphSelectionWrapper wrapper;

    /**
     * Constructs a graph selection editor.
     *
     * @throws NullPointerException if <code>wrapper</code> is null.
     */
    public GraphSelectionEditor(final GraphSelectionWrapper wrapper) {
        if (wrapper == null) {
            throw new NullPointerException("The regression wrapper is required.");
        }

        setLayout(new BorderLayout());

        this.wrapper = wrapper;
        Graph outGraph = wrapper.getSelectionGraph();

        workbench = new GraphWorkbench(outGraph);

        for (Node node : wrapper.getSelectedVariables()) {
            workbench.selectNode(node);
        }

        getWorkbench().addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("modelChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("modelChanged", null, null);
                }
            }
        });

        JScrollPane workbenchScroll = new JScrollPane(workbench);
        workbenchScroll.setPreferredSize(new Dimension(600, 400));

        final JButton executeButton = new JButton("Graph It!");
        executeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Window owner = (Window) getTopLevelAncestor();

                new WatchedProcess(owner) {
                    public void watch() {

                        wrapper.calculateSelection();
                        Graph selection = wrapper.getSelectionGraph();

                        GraphUtils.circleLayout(selection, 200, 200, 150);
                        GraphUtils.fruchtermanReingoldLayout(selection);

                        workbench.setGraph(selection);

                        for (Node node : wrapper.getSelectedVariables()) {
                            if (wrapper.getHighlightInEditor().contains(node) && workbench.getGraph().containsNode(node)) {
                                workbench.selectNode(node);
                            }
                        }
                    }
                };
            }
        });


        JMenuBar bar = new JMenuBar();
        JMenu file = new GraphFileMenu(this, workbench);

        bar.add(file);

        bar.add(createEditMenu());

        bar.add(createGraphMenu());

        JMenu select = new JMenu("Select");

        JCheckBoxMenuItem subgraph = new JCheckBoxMenuItem("Subgraph");
        JCheckBoxMenuItem adjacents = new JCheckBoxMenuItem("Adjacents");
        JCheckBoxMenuItem adjacentsOfAdjacents = new JCheckBoxMenuItem("Adjacents of Adjacents");
        JCheckBoxMenuItem adjacentsOfAdjacentsOfAdjacents = new JCheckBoxMenuItem("Adjacents of Adjacents of Adjacents");
        JCheckBoxMenuItem markovBlankets = new JCheckBoxMenuItem("Markov Blankets");
        JCheckBoxMenuItem yStructures = new JCheckBoxMenuItem("Y Structures");
        JCheckBoxMenuItem pagYStructures = new JCheckBoxMenuItem("PAG Y Structures");
        JCheckBoxMenuItem treks = new JCheckBoxMenuItem("Treks");
        JCheckBoxMenuItem trekEdges = new JCheckBoxMenuItem("Trek Edges");
        JCheckBoxMenuItem paths = new JCheckBoxMenuItem("Undirected Paths");
        JCheckBoxMenuItem pathEdges = new JCheckBoxMenuItem("Undirected Paths Edges");
        JCheckBoxMenuItem directedPaths = new JCheckBoxMenuItem("Directed Paths");
        JCheckBoxMenuItem directedPathEdges = new JCheckBoxMenuItem("Directed Paths Edges");
        JCheckBoxMenuItem indegree = new JCheckBoxMenuItem("Indegree");
        JCheckBoxMenuItem outdegree = new JCheckBoxMenuItem("Outdegree");
        JCheckBoxMenuItem degree = new JCheckBoxMenuItem("Degree");

        ButtonGroup group = new ButtonGroup();
        group.add(subgraph);
        group.add(adjacents);
        group.add(adjacentsOfAdjacents);
        group.add(adjacentsOfAdjacentsOfAdjacents);
        group.add(markovBlankets);
        group.add(yStructures);
        group.add(pagYStructures);
        group.add(treks);
        group.add(trekEdges);
        group.add(paths);
        group.add(pathEdges);
        group.add(directedPaths);
        group.add(directedPathEdges);
        group.add(indegree);
        group.add(outdegree);
        group.add(degree);

        editorPanel = new GraphSelectionEditorPanel(this.wrapper);

        if (wrapper.getType() == GraphSelectionWrapper.Type.subgraph) {
            subgraph.setSelected(true);
            editorPanel.setNLabel("");
        }

        if (wrapper.getType() == GraphSelectionWrapper.Type.adjacents) {
            adjacents.setSelected(true);
            editorPanel.setNLabel("");
        }

        if (wrapper.getType() == GraphSelectionWrapper.Type.adjacentsOfAdjacents) {
            adjacentsOfAdjacents.setSelected(true);
            editorPanel.setNLabel("");
        }

        if (wrapper.getType() == GraphSelectionWrapper.Type.adjacentsOfAdjacentsOfAdjacents) {
            adjacentsOfAdjacentsOfAdjacents.setSelected(true);
            editorPanel.setNLabel("");
        }

        if (wrapper.getType() == GraphSelectionWrapper.Type.yStructures) {
            yStructures.setSelected(true);
            editorPanel.setNLabel("");
        }

        if (wrapper.getType() == GraphSelectionWrapper.Type.pagYStructures) {
            pagYStructures.setSelected(true);
            editorPanel.setNLabel("");
        }

        if (wrapper.getType() == GraphSelectionWrapper.Type.markovBlankets) {
            markovBlankets.setSelected(true);
            editorPanel.setNLabel("");
        }

        if (wrapper.getType() == GraphSelectionWrapper.Type.yStructures) {
            yStructures.setSelected(true);
            editorPanel.setNLabel("");
        }

        if (wrapper.getType() == GraphSelectionWrapper.Type.pagYStructures) {
            pagYStructures.setSelected(true);
            editorPanel.setNLabel("");
        }

        if (wrapper.getType() == GraphSelectionWrapper.Type.treks) {
            treks.setSelected(true);
            editorPanel.setNLabel("Path Length");
        }

        if (wrapper.getType() == GraphSelectionWrapper.Type.trekEdges) {
            trekEdges.setSelected(true);
            editorPanel.setNLabel("Path Length");
        }

        if (wrapper.getType() == GraphSelectionWrapper.Type.paths) {
            paths.setSelected(true);
            editorPanel.setNLabel("Path Length");
        }

        if (wrapper.getType() == GraphSelectionWrapper.Type.pathEdges) {
            pathEdges.setSelected(true);
            editorPanel.setNLabel("Path Length");
        }

        if (wrapper.getType() == GraphSelectionWrapper.Type.directedPaths) {
            directedPaths.setSelected(true);
            editorPanel.setNLabel("Path Length");
        }

        if (wrapper.getType() == GraphSelectionWrapper.Type.directedPathEdges) {
            directedPathEdges.setSelected(true);
            editorPanel.setNLabel("Path Length");
        }

        if (wrapper.getType() == GraphSelectionWrapper.Type.indegree) {
            indegree.setSelected(true);
            editorPanel.setNLabel("Indegree");
        }

        if (wrapper.getType() == GraphSelectionWrapper.Type.outdegree) {
            outdegree.setSelected(true);
            editorPanel.setNLabel("Outdegree");
        }

        if (wrapper.getType() == GraphSelectionWrapper.Type.degree) {
            degree.setSelected(true);
            editorPanel.setNLabel("");
        }

        subgraph.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                wrapper.setType(GraphSelectionWrapper.Type.subgraph);
                editorPanel.setNLabel("");
            }
        });

        adjacents.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                wrapper.setType(GraphSelectionWrapper.Type.adjacents);
                editorPanel.setNLabel("");
            }
        });

        adjacentsOfAdjacents.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                wrapper.setType(GraphSelectionWrapper.Type.adjacentsOfAdjacents);
                editorPanel.setNLabel("");
            }
        });

        adjacentsOfAdjacentsOfAdjacents.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                wrapper.setType(GraphSelectionWrapper.Type.adjacentsOfAdjacentsOfAdjacents);
                editorPanel.setNLabel("");
            }
        });

        markovBlankets.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                wrapper.setType(GraphSelectionWrapper.Type.markovBlankets);
                editorPanel.setNLabel("");
            }
        });

        yStructures.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                wrapper.setType(GraphSelectionWrapper.Type.yStructures);
                editorPanel.setNLabel("");
            }
        });

        pagYStructures.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                wrapper.setType(GraphSelectionWrapper.Type.pagYStructures);
                editorPanel.setNLabel("");
            }
        });

        treks.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                wrapper.setType(GraphSelectionWrapper.Type.treks);
                editorPanel.setNLabel("Path Length");
            }
        });

        trekEdges.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                wrapper.setType(GraphSelectionWrapper.Type.trekEdges);
                editorPanel.setNLabel("Path Length");
            }
        });

        paths.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                wrapper.setType(GraphSelectionWrapper.Type.paths);
                editorPanel.setNLabel("Path Length");
            }
        });

        pathEdges.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                wrapper.setType(GraphSelectionWrapper.Type.pathEdges);
                editorPanel.setNLabel("Path Length");
            }
        });

        directedPaths.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                wrapper.setType(GraphSelectionWrapper.Type.directedPaths);
                editorPanel.setNLabel("Path Length");
            }
        });

        directedPathEdges.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                wrapper.setType(GraphSelectionWrapper.Type.directedPathEdges);
                editorPanel.setNLabel("Path Length");
            }
        });

        indegree.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                wrapper.setType(GraphSelectionWrapper.Type.indegree);
                editorPanel.setNLabel("Indegree");
            }
        });

        outdegree.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                wrapper.setType(GraphSelectionWrapper.Type.outdegree);
                editorPanel.setNLabel("Outdegree");
            }
        });

        degree.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                wrapper.setType(GraphSelectionWrapper.Type.degree);
                editorPanel.setNLabel("Degree");
            }
        });

        select.add(subgraph);
        select.add(adjacents);
        select.add(adjacentsOfAdjacents);
        select.add(adjacentsOfAdjacentsOfAdjacents);
        select.add(markovBlankets);
        select.add(yStructures);
        select.add(pagYStructures);
        select.add(treks);
        select.add(trekEdges);
        select.add(paths);
        select.add(pathEdges);
        select.add(directedPaths);
        select.add(directedPathEdges);
        select.add(indegree);
        select.add(outdegree);
        select.add(degree);

        select.addSeparator();

        JMenuItem selectInGraph = new JMenuItem("Use Selected Variables in Graph");
//        selectInGraph.setAccelerator(KeyStroke.getKeyStroke("ctrl G"));
        selectInGraph.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.META_MASK));

        select.add(selectInGraph);

        selectInGraph.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                List<DisplayNode> displayNodes = workbench.getSelectedNodes();
                List<Node> newSelected = new ArrayList<Node>();
                for (DisplayNode node : displayNodes) {
                    newSelected.add(node.getModelNode());
                }

                editorPanel.setSelected(newSelected);
            }
        });

        bar.add(select);

        bar.add(new LayoutMenu(workbench));

        add(bar, BorderLayout.NORTH);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("Selection", workbenchScroll);

        Box b = Box.createVerticalBox();
        Box b1 = Box.createHorizontalBox();

        editorPanel.setMaximumSize(editorPanel.getMinimumSize());

        b1.add(editorPanel);
        b1.add(tabbedPane);
        b.add(b1);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout());
        buttonPanel.add(executeButton);
        b.add(buttonPanel, BorderLayout.SOUTH);

        add(b, BorderLayout.CENTER);

        setName("Graph Selection Result:");
    }

    private JMenu createGraphMenu() {
        JMenu graph = new JMenu("Graph");

        graph.add(new GraphPropertiesAction(getWorkbench()));
        graph.add(new PathsAction(getWorkbench()));
//        graph.add(new DirectedPathsAction(getWorkbench()));
//        graph.add(new TreksAction(getWorkbench()));
//        graph.add(new AllPathsAction(getWorkbench()));
//        graph.add(new NeighborhoodsAction(getWorkbench()));

        return graph;
    }

    /**
     * Creates the "file" menu, which allows the user to load, save, and post
     * workbench models.
     *
     * @return this menu.
     */
    private JMenu createEditMenu() {
        JMenu edit = new JMenu("Edit");

        JMenuItem copySubgraph = new JMenuItem(new CopySubgraphAction(GraphSelectionEditor.this));
        JMenuItem pasteSubgraph = new JMenuItem(new PasteSubgraphAction(GraphSelectionEditor.this));

        copySubgraph.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
        pasteSubgraph.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK));

        edit.add(copySubgraph);
        edit.add(pasteSubgraph);

        return edit;
    }

    /**
     * Sets the name of this editor.
     */
    @Override
    public void setName(String name) {
        String oldName = getName();
        super.setName(name);
        this.firePropertyChange("name", oldName, getName());
    }

    @Override
    public List getSelectedModelComponents() {
        List<Component> selectedComponents =
                getWorkbench().getSelectedComponents();
        List<TetradSerializable> selectedModelComponents =
                new ArrayList<TetradSerializable>();

        for (Component comp : selectedComponents) {
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

    @Override
    public void pasteSubsession(List sessionElements, Point upperLeft) {
        getWorkbench().pasteSubgraph(sessionElements, upperLeft);
        getWorkbench().deselectAll();

        for (Object o : sessionElements) {
            if (o instanceof GraphNode) {
                Node modelNode = (Node) o;
                getWorkbench().selectNode(modelNode);
            }
        }

        getWorkbench().selectConnectingEdges();

    }

    @Override
    public GraphWorkbench getWorkbench() {
        return workbench;
    }

    @Override
    public Graph getGraph() {
        return wrapper.getSelectionGraph();
    }

    @Override
    public void setGraph(Graph graph) {
        wrapper.setGraph(graph);
        editorPanel.reset();
        getWorkbench().setGraph(new EdgeListGraphSingleConnections());
    }

    /**
     * Allows one to drop/drap variables from a source list to a response area and
     * a selected list. Also lets one specify an alpha level.
     *
     * @author Tyler Gibson
     */
    public class GraphSelectionEditorPanel extends JPanel {

        // Stores the length of a path or the degree of a node, depending.
        private final JLabel nLabel;

        // Selected if one wants to know that the length of a path is equal to n, etc.
        private final JRadioButton equals;

        // Selected if one wants to know that the length of a path is at most n.
        private final JRadioButton atMost;

        // Selected if one wants to know that the length of a path is at most n.
        private final JRadioButton atLeast;

        //The list of source variables.
        private JList<Node> sourceList;

        // The list of selected.
        private JList<Node> selectedList;

        // The font to render fields in.
        private Font _font = new Font("Dialog", Font.PLAIN, 12);

        // Stores all information for this component.
        private final GraphSelectionWrapper wrapper;

        /**
         * Constructs the editor given the <code>RegressionParams</code> and the <code>DataModel</code>
         * that should be used.
         */
        public GraphSelectionEditorPanel(GraphSelectionWrapper graphSelectionWrapper) {
            this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            this.wrapper = graphSelectionWrapper;
            // if null get the variables from the parent data set.
            if (graphSelectionWrapper == null) {
                throw new NullPointerException("Graph wrapper must not be null");
            }

            // create components
            selectedList = createList();
            VariableListModel selectedModel = (VariableListModel) getSelectedList().getModel();
            sourceList = createList();
            VariableListModel variableModel = (VariableListModel) getSourceList().getModel();

            // if selected are already set use'em.
            List<Node> selectedNodes = wrapper.getSelectedVariables();
            List<Node> initVars = new ArrayList<>(wrapper.getVariables());
            initVars.removeAll(selectedNodes);
            variableModel.addAll(initVars);
            selectedModel.addAll(selectedNodes);

            // deal with drag and drop
            new DropTarget(getSourceList(), DnDConstants.ACTION_MOVE, new TargetListener(), true);
            new DropTarget(getSelectedList(), DnDConstants.ACTION_MOVE, new TargetListener(), true);

            DragSource dragSource = DragSource.getDefaultDragSource();
            dragSource.createDefaultDragGestureRecognizer(getSourceList(), DnDConstants.ACTION_MOVE, new SourceListener());
            dragSource = DragSource.getDefaultDragSource();
            dragSource.createDefaultDragGestureRecognizer(getSelectedList(), DnDConstants.ACTION_MOVE, new SourceListener());

            nLabel = new JLabel("Degree");
            equals = new JRadioButton("Equals");
            atMost = new JRadioButton("At Most");
            atLeast = new JRadioButton("At Least");

            ButtonGroup group2 = new ButtonGroup();

            group2.add(equals);
            group2.add(atMost);
            group2.add(atLeast);

            if (wrapper.getNType() == GraphSelectionWrapper.nType.equals) {
                equals.setSelected(true);
            } else if (wrapper.getNType() == GraphSelectionWrapper.nType.atMost) {
                atMost.setSelected(true);
            } else {
                atLeast.setSelected(true);
            }

            equals.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    wrapper.setNType(GraphSelectionWrapper.nType.equals);
                }
            });

            atMost.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    wrapper.setNType(GraphSelectionWrapper.nType.atMost);
                }
            });

            atLeast.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    wrapper.setNType(GraphSelectionWrapper.nType.atLeast);
                }
            });

            IntTextField nField = new IntTextField(wrapper.getN(), 2);

            nField.setFilter(new IntTextField.Filter() {
                public int filter(int value, int oldValue) {
                    try {
                        wrapper.setN(value);
                        return value;
                    } catch (Exception e) {
                        return oldValue;
                    }
                }
            });

            // build the gui
            Box box = Box.createHorizontalBox();
            box.add(Box.createHorizontalStrut(10));

            Box vBox1 = Box.createVerticalBox();
            vBox1.add(createLabel("Variables:"));
            JScrollPane pane = createScrollPane(getSourceList(), new Dimension(100, 350));
            vBox1.add(pane);
            vBox1.add(Box.createVerticalStrut(10));
            vBox1.add(buildSortButton());
            vBox1.add(Box.createVerticalGlue());
            box.add(vBox1);

            box.add(Box.createHorizontalStrut(4));
            box.add(buildSelectorArea(createLabel("Variables:").getPreferredSize().height));
            box.add(Box.createHorizontalStrut(4));

            Box vBox = Box.createVerticalBox();
            vBox.add(createLabel("Selected:"));
            vBox.add(createScrollPane(getSelectedList(), new Dimension(100, 350)));

            vBox.add(Box.createVerticalStrut(10));
            vBox.add(buildTextButton());
            vBox.add(Box.createVerticalGlue());

            box.add(vBox);
            box.add(Box.createHorizontalStrut(10));
//            box.add(Box.createHorizontalGlue());

            Box b3 = Box.createVerticalBox();
            b3.add(box);

            Box b5 = Box.createHorizontalBox();
            b5.add(nLabel);
            b5.add(equals);
            b5.add(atMost);
            b5.add(atLeast);
            b5.add(nField);

            b3.add(b5);

            this.add(Box.createVerticalStrut(20));
            this.add(b3);
        }

        public void reset() {
            VariableListModel selectedModel = (VariableListModel) getSelectedList().getModel();
            VariableListModel variableModel = (VariableListModel) getSourceList().getModel();
            List<Node> variableNames = wrapper.getVariables();

            // if regressors are already set use'em.
            selectedModel.removeAll();
            variableModel.removeAll();
            variableModel.addAll(variableNames);
            variableModel.removeAll(wrapper.getSelectedVariables());
            selectedModel.addAll(wrapper.getSelectedVariables());

            getSelectedList().setSelectedIndices(new int[0]);
            getSourceList().setSelectedIndices(new int[0]);
        }

        //============================= Private Methods =================================//

        private List<Node> getSelected(JList list) {
            List selected = list.getSelectedValuesList();
            List<Node> selectedList = new ArrayList<Node>(selected == null ? 0 : selected.size());
            if (selected != null) {
                for (Object o : selected) {
                    selectedList.add((Node) o);
                }
            }
            return selectedList;
        }

        /**
         * Bulids the arrows that allow one to move variables around (can also use drag and drop)
         */
        private Box buildSelectorArea(int startHeight) {
            Box box = Box.createVerticalBox();
            JButton moveToselector = new JButton(">");
            JButton moveToSource = new JButton("<");

            moveToselector.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    VariableListModel selectedModel = (VariableListModel) getSelectedList().getModel();
                    VariableListModel sourceModel = (VariableListModel) getSourceList().getModel();
                    List<Node> selected = getSelected(getSourceList());
                    sourceModel.removeAll(selected);
                    selectedModel.addAll(selected);

                    getSelectedList().setSelectedIndices(new int[0]);
                    getSourceList().setSelectedIndices(new int[0]);
                    wrapper.setSelectedVariables(getSelected());
                }
            });

            moveToSource.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    VariableListModel selectedModel = (VariableListModel) getSelectedList().getModel();
                    VariableListModel sourceModel = (VariableListModel) getSourceList().getModel();
                    List<Node> selected = getSelected(getSelectedList());
                    // if not empty remove/add, otherwise try the response list.
                    if (!selected.isEmpty()) {
                        selectedModel.removeAll(selected);
                        sourceModel.addAll(selected);
                        wrapper.setSelectedVariables(getSelected());
                    }
                    getSelectedList().setSelectedIndices(new int[0]);
                    getSourceList().setSelectedIndices(new int[0]);
                }
            });

            box.add(Box.createVerticalStrut(startHeight));
            box.add(Box.createVerticalStrut(150));
            box.add(moveToselector);
            box.add(Box.createVerticalStrut(10));
            box.add(moveToSource);
            box.add(Box.createVerticalGlue());

            return box;
        }

        private Box buildSortButton() {
            JButton sort = new JButton("Sort Variables");
            sort.setFont(sort.getFont().deriveFont(11f));
            sort.setMargin(new Insets(3, 3, 3, 3));
            sort.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    VariableListModel selectedModel = (VariableListModel) getSelectedList().getModel();
                    VariableListModel sourceModel = (VariableListModel) getSourceList().getModel();
                    selectedModel.sort();
                    sourceModel.sort();
                }
            });
            Box box = Box.createHorizontalBox();
            box.add(sort);
            box.add(Box.createHorizontalGlue());

            return box;
        }

        private Box buildTextButton() {
            GraphSelectionTextInputAction action =
                    new GraphSelectionTextInputAction(GraphSelectionEditorPanel.this,
                            wrapper, sourceList, selectedList);
            JButton sort = new JButton(action);
            sort.setFont(sort.getFont().deriveFont(11f));
            sort.setMargin(new Insets(3, 3, 3, 3));

            Box box = Box.createHorizontalBox();
            box.add(sort);
            box.add(Box.createHorizontalGlue());

            return box;
        }

        private JScrollPane createScrollPane(JList comp, Dimension dim) {
            JScrollPane pane = new JScrollPane(comp);
            LayoutUtils.setAllSizes(pane, dim);
            return pane;
        }


        private Box createLabel(String text) {
            JLabel label = new JLabel(text);
            label.setAlignmentX(JLabel.LEFT_ALIGNMENT);
            Box box = Box.createHorizontalBox();
            box.add(label);
            box.add(Box.createHorizontalGlue());
            return box;
        }

        private JList createList() {
            JList list = new JList(new VariableListModel());
            list.setFont(getFONT());
            list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            list.setVisibleRowCount(10);
            return list;
        }


        private DataFlavor getListDataFlavor() {
            try {
                return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + "; class=java.lang.Object",
                        "Local Variable List");
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        private List<Node> getSelected() {
            ListModel model = getSelectedList().getModel();
            List<Node> selected = new ArrayList<>(model.getSize());
            for (int i = 0; i < model.getSize(); i++) {
                Node node = (Node) model.getElementAt(i);
                selected.add(node);
            }

            return selected;
        }

        private JList getSelectedList() {
            return selectedList;
        }

        private JList getSourceList() {
            return sourceList;
        }

        public Font getFONT() {
            return _font;
        }

        public void setNLabel(String label) {
            if (label.equals("")) {
                nLabel.setEnabled(false);
                equals.setEnabled(false);
                atMost.setEnabled(false);
                atLeast.setEnabled(false);
            } else {
                nLabel.setEnabled(true);
                equals.setEnabled(true);
                atMost.setEnabled(true);
                atLeast.setEnabled(true);
                nLabel.setText(label);
            }
        }

        public void setSelected(List<Node> selected) {
            VariableListModel selectedModel = (VariableListModel) selectedList.getModel();
            VariableListModel sourceModel = (VariableListModel) sourceList.getModel();
            List<Node> oldSelected = wrapper.getSelectedVariables();
            selectedModel.removeAll(oldSelected);
            sourceModel.addAll(oldSelected);
            selectedModel.addAll(selected);
            sourceModel.removeAll(selected);
            wrapper.setSelectedVariables(selected);
            selectedList.setSelectedIndices(new int[0]);
            sourceList.setSelectedIndices(new int[0]);
        }

        //========================== Inner classes (a lot of'em) =========================================//


        private class TargetListener extends DropTargetAdapter {

            public void drop(DropTargetDropEvent dtde) {
                Transferable t = dtde.getTransferable();
                Component comp = dtde.getDropTargetContext().getComponent();
                if (comp instanceof JList || comp instanceof JTextField) {
                    try {
                        // if response, remove everything first
                        JList list = (JList) comp;
                        VariableListModel model = (VariableListModel) list.getModel();
                        List<Node> vars = (List<Node>) t.getTransferData(new ListTransferable(new ArrayList()).getDataFlavor());
                        model.addAll(vars);

                        wrapper.setSelectedVariables(getSelected());
                        dtde.getDropTargetContext().dropComplete(true);
                    } catch (Exception ex) {
                        dtde.rejectDrop();
                        ex.printStackTrace();
                    }
                } else {
                    dtde.rejectDrop();
                }
            }
        }


        /**
         * A source/gesture listener for the JLists
         */
        private class SourceListener extends DragSourceAdapter implements DragGestureListener {

            public void dragDropEnd(DragSourceDropEvent evt) {
                if (evt.getDropSuccess()) {
                    Component comp = evt.getDragSourceContext().getComponent();
                    Transferable t = evt.getDragSourceContext().getTransferable();
                    if (t instanceof ListTransferable) {
                        try {
                            //noinspection unchecked
                            List<Node> o = (List<Node>) t.getTransferData(new ListTransferable(new ArrayList()).getDataFlavor());
                            if (comp instanceof JList) {
                                JList list = (JList) comp;
                                VariableListModel model = (VariableListModel) list.getModel();
                                for (Node c : o) {
                                    model.removeFirst(c);
                                }
                            } else {
                                JTextField pane = (JTextField) comp;
                                pane.setText(null);
                            }

                            wrapper.setSelectedVariables(getSelected());
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }

            public void dragGestureRecognized(DragGestureEvent dge) {
                Component comp = dge.getComponent();
                List selected = null;
                if (comp instanceof JList) {
                    JList list = (JList) comp;
                    selected = list.getSelectedValuesList();
                } else {
                    JTextField pane = (JTextField) comp;
                    String text = pane.getText();
                    if (text != null && text.length() != 0) {
                        selected = Collections.singletonList(text);
                    }
                }
                if (selected != null) {
                    ListTransferable t = new ListTransferable(Arrays.asList(selected));
                    dge.startDrag(DragSource.DefaultMoveDrop, t, this);
                }
            }
        }


        /**
         * A basic model for the list (needed an addAll feature, which the detault model didn't have)
         */
        public class VariableListModel extends AbstractListModel {

            private List<Node> delegate = new ArrayList<>();

            public int getSize() {
                return this.delegate.size();
            }

            public Object getElementAt(int index) {
                return this.delegate.get(index);
            }

            public void remove(Node element) {
                int index = this.delegate.indexOf(element);
                if (0 <= index) {
                    this.delegate.remove(index);
                    this.fireIntervalRemoved(this, index, index);
                }
            }

            public void add(Node element) {
                this.delegate.add(element);
                this.fireIntervalAdded(this, this.delegate.size(), this.delegate.size());
            }


            public void removeFirst(Node element) {
                this.delegate.remove(element);
                this.fireContentsChanged(this, 0, this.delegate.size());
            }

            public void removeAll(List<? extends Node> elements) {
                this.delegate.removeAll(elements);
                this.fireContentsChanged(this, 0, this.delegate.size());
            }

            public void addAll(List<? extends Node> elements) {
                this.delegate.addAll(elements);
                this.fireContentsChanged(this, 0, this.delegate.size());
            }

            public void removeAll() {
                this.delegate.clear();
                this.fireContentsChanged(this, 0, 0);
            }

            public void sort() {
                Collections.sort(this.delegate);
                this.fireContentsChanged(this, 0, this.delegate.size());
            }
        }

        /**
         * A basic transferable.
         */
        private class ListTransferable implements Transferable {

            private final DataFlavor FLAVOR = getListDataFlavor();

            private List object;

            public DataFlavor getDataFlavor() {
                return FLAVOR;
            }

            public ListTransferable(List object) {
                if (object == null) {
                    throw new NullPointerException();
                }
                this.object = object;
            }

            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{FLAVOR};
            }

            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return flavor == FLAVOR;
            }

            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
//                if (FLAVOR != flavor) {
//                    throw new UnsupportedFlavorException(flavor);
//                }
                return this.object;
            }
        }
    }

    /**
     * Puts up a panel showing some graph properties, e.g., number of nodes and
     * edges in the graph, etc.
     *
     * @author Joseph Ramsey jdramsey@andrew.cmu.edu
     */
    public static class GraphSelectionTextInputAction extends AbstractAction implements ClipboardOwner {
        private final GraphSelectionWrapper wrapper;
        private final JList sourceList;
        private final JList selectedList;
        private JComponent component;
        private JTextArea textArea;

        /**
         * Creates a new copy subsession action for the given LayoutEditable and
         * clipboard.
         */
        public GraphSelectionTextInputAction(JComponent component, GraphSelectionWrapper wrapper,
                                             JList sourceList, JList selectedList) {
            super("Text Input...");
            this.component = component;
            this.wrapper = wrapper;
            this.sourceList = sourceList;
            this.selectedList = selectedList;
        }

        /**
         * Copies a parentally closed selection of session nodes in the frontmost
         * session editor to the clipboard.
         */
        public void actionPerformed(ActionEvent e) {
            Box b = Box.createVerticalBox();

            textArea = new JTextArea();

            textArea.setText(wrapper.getDialogText());

            JScrollPane scroll = new JScrollPane(textArea);
            scroll.setPreferredSize(new Dimension(150, 300));
            scroll.setBorder(new CompoundBorder(new LineBorder(Color.GRAY, 1), new EmptyBorder(5, 5, 5, 5)));

            Box b2 = Box.createVerticalBox();
            b2.add(Box.createVerticalStrut(10));
            b2.add(new JLabel("Please enter a list of variable names you'd like to have"));
            b2.add(new JLabel("selected, one per line. Those that exist in the graph will"));
            b2.add(new JLabel("be selected; the rest will be disgarded. You can paste a"));
            b2.add(new JLabel("list in from the clipboard or type it. This list will be"));
            b2.add(new JLabel("saved."));
            b2.add(Box.createVerticalStrut(10));
            b2.add(scroll);
            textArea.setCaretPosition(0);
            b.add(b2);

            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());
            panel.add(b);

            EditorWindow window = new EditorWindow(panel,
                    "Input Variable Names as Text", "Select", false, component);
            DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
            window.setVisible(true);


            window.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    wrapper.setDialogText(textArea.getText());
                    GraphSelectionEditorPanel.VariableListModel selectedModel = (GraphSelectionEditorPanel.VariableListModel) selectedList.getModel();
                    GraphSelectionEditorPanel.VariableListModel sourceModel = (GraphSelectionEditorPanel.VariableListModel) sourceList.getModel();
                    List<Node> oldSelected = wrapper.getSelectedVariables();
                    selectedModel.removeAll(oldSelected);
                    sourceModel.addAll(oldSelected);
                    List<Node> newSelected = selectedVars();
                    selectedModel.addAll(newSelected);
                    sourceModel.removeAll(newSelected);
                    wrapper.setSelectedVariables(newSelected);
                    selectedList.setSelectedIndices(new int[0]);
                    sourceList.setSelectedIndices(new int[0]);
                }
            });
        }

        public List<Node> selectedVars() {
            List<Node> nodes = new ArrayList<>();

            try {
                String text = textArea.getText();

                BufferedReader r = new BufferedReader(new CharArrayReader(text.toCharArray()));
                String line = null;

                while ((line = r.readLine()) != null) {
                    Node node = wrapper.getOriginalGraph().getNode(line);

                    if (node != null) {
                        nodes.add(node);
                    }
                }

                return nodes;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Required by the AbstractAction interface; does nothing.
         */
        public void lostOwnership(Clipboard clipboard, Transferable contents) {
        }
    }

}





