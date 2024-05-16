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

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.List;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * Represents an action that performs calculations on paths in a graph.
 */
public class PathsAction extends AbstractAction implements ClipboardOwner {

    /**
     * The workbench.
     */
    private final GraphWorkbench workbench;

    /**
     * The nodes to show paths from.
     */
    private List<Node> nodes1;

    /**
     * The nodes to show paths to.
     */
    private List<Node> nodes2;

    /**
     * The text area for the paths.
     */
    private JTextArea textArea;

    /**
     * The method for showing paths.
     */
    private String method;

    /**
     * The conditioning set.
     */
    private Set<Node> conditioningSet = new HashSet<>();

    /**
     * Represents an action that performs calculations on paths in a graph.
     */
    public PathsAction(GraphWorkbench workbench) {
        super("Paths");
        this.workbench = workbench;
    }

    /**
     * Performs the action when an event occurs.
     *
     * @param e The action event.
     */
    public void actionPerformed(ActionEvent e) {
        Graph graph = this.workbench.getGraph();

        this.textArea = new JTextArea();
        JScrollPane scroll = new JScrollPane(this.textArea);
        scroll.setPreferredSize(new Dimension(600, 400));

        List<Node> allNodes = graph.getNodes();
        allNodes.sort(Comparator.naturalOrder());
        allNodes.add(new GraphNode("SELECT_ALL"));
        Node[] array = allNodes.toArray(new Node[0]);

        JComboBox<Node> node1Box = new JComboBox<>(array);

        node1Box.addActionListener(e1 -> {
            JComboBox<Node> box = (JComboBox) e1.getSource();
            Node node = (Node) box.getSelectedItem();

            if (node == null) return;

            if ("SELECT_ALL".equals(node.getName())) {
                PathsAction.this.nodes1 = new ArrayList<>(graph.getNodes());
            } else {
                PathsAction.this.nodes1 = Collections.singletonList(node);
            }

            Preferences.userRoot().put("pathFrom", node.getName());

            update(graph, textArea, nodes1, nodes2, method);
        });

        node1Box.setSelectedItem(Preferences.userRoot().get("pathFrom", null));
        if (node1Box.getSelectedItem() == null) {
            node1Box.setSelectedItem(node1Box.getItemAt(0));
        }
        nodes1 = Collections.singletonList((Node) node1Box.getSelectedItem());

        JComboBox<Node> node2Box = new JComboBox<>(array);

        node2Box.addActionListener(e12 -> {
            JComboBox<Node> box = (JComboBox) e12.getSource();
            Node node = (Node) box.getSelectedItem();

            if (node == null) return;

            if ("SELECT_ALL".equals(node.getName())) {
                PathsAction.this.nodes2 = new ArrayList<>(graph.getNodes());
            } else {
                PathsAction.this.nodes2 = Collections.singletonList(node);
            }

            Preferences.userRoot().put("pathMethod", PathsAction.this.method);

            update(graph, textArea, nodes1, nodes2, method);
        });

        node2Box.setSelectedItem(Preferences.userRoot().get("pathFrom", null));
        if (node2Box.getSelectedItem() == null) {
            node2Box.setSelectedItem(node1Box.getItemAt(0));
        }
        nodes2 = Collections.singletonList((Node) node2Box.getSelectedItem());

        JComboBox<String> methodBox = new JComboBox<>(new String[]{"Directed Paths", "Semidirected Paths",
                "Treks", "Confounder Paths", "Latent Confounder Paths",
                "All Paths", "Adjacents", "Adjustment Sets",
                "Amenable paths (DAG, CPDAG, MPDAG, MAG)",
                "Non-amenable paths (DAG, CPDAG, MPDAG, MAG)"});

        methodBox.setSelectedItem(Preferences.userRoot().get("pathMethod", null));
        if (methodBox.getSelectedItem() == null) {
            methodBox.setSelectedItem(node1Box.getItemAt(0));
        }
        method = (String) methodBox.getSelectedItem();

        methodBox.addActionListener(e13 -> {
            JComboBox<String> box = (JComboBox) e13.getSource();
            PathsAction.this.method = (String) box.getSelectedItem();
            Preferences.userRoot().put("pathMethod", PathsAction.this.method);
            update(graph, textArea, nodes1, nodes2, method);
        });

        methodBox.setSelectedItem(this.method);

        IntTextField maxField = new IntTextField(Preferences.userRoot().getInt("pathMaxLength", 8), 2);

        maxField.setFilter((value, oldValue) -> {
            try {

                // Disallow unlimited path option. Also insist the max path length be at least 1.
                if (value >= 2) setMaxLength(value);
                update(graph, textArea, nodes1, nodes2, method);
                return Preferences.userRoot().getInt("pathMaxLength", 8);
            } catch (Exception e14) {
                return oldValue;
            }
        });

        Box b = Box.createVerticalBox();

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("From "));
        b1.add(node1Box);
        b1.add(Box.createHorizontalGlue());
        b1.add(new JLabel(" To "));
        b1.add(node2Box);
        b1.add(Box.createHorizontalGlue());
        b1.add(methodBox);
        b1.add(new JLabel("Max length"));
        b1.add(maxField);
        b.setBorder(new EmptyBorder(2, 3, 2, 2));
        b.add(b1);

        JTextFieldWithPrompt comp = new JTextFieldWithPrompt("Enter conditioning variables...");
        comp.setBorder(new CompoundBorder(new LineBorder(Color.BLACK, 1), new EmptyBorder(1, 3, 1, 3)));

        comp.addActionListener(e16 -> {
            String text = comp.getText();
            String[] parts = text.split("[\\s,\\[\\]]");

            Set<Node> conditioningSet = new HashSet<>();

            for (String part : parts) {
                Node node = graph.getNode(part);

                if (node != null) {
                    conditioningSet.add(node);
                }
            }

            PathsAction.this.conditioningSet = conditioningSet;
            update(graph, textArea, nodes1, nodes2, method);
        });


        Box b1a = Box.createHorizontalBox();
        b1a.add(new JLabel("Enter conditioning variables:"));
        b1a.add(comp);
        b1a.setBorder(new EmptyBorder(2, 3, 2, 2));
        b.add(b1a);

        Box b2 = Box.createHorizontalBox();
        b2.add(scroll);
        this.textArea.setCaretPosition(0);
        b2.setBorder(new EmptyBorder(2, 3, 2, 2));
        b.add(b2);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(b);

        EditorWindow window = new EditorWindow(panel,
                "Directed Paths", "Close", false, this.workbench);
        DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
        window.setVisible(true);

        update(graph, this.textArea, this.nodes1, this.nodes2, this.method);
    }

    /**
     * Updates the text area based on the selected method.
     *
     * @param graph     The graph object.
     * @param textArea  The text area object.
     * @param nodes1    The first list of nodes.
     * @param nodes2    The second list of nodes.
     * @param method    The selected method.
     * @throws IllegalArgumentException If the method is unknown.
     */
    private void update(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2, String method) {
        if ("Directed Paths".equals(method)) {
            textArea.setText("");
            allDirectedPaths(graph, textArea, nodes1, nodes2);
        } else if ("Semidirected Paths".equals(method)) {
            textArea.setText("");
            allSemidirectedPaths(graph, textArea, nodes1, nodes2);
        } else if ("Amenable paths (DAG, CPDAG, MPDAG, MAG)".equals(method)) {
            textArea.setText("");
            allAmenablePathsMpdagMag(graph, textArea, nodes1, nodes2);
        } else if ("Non-amenable paths (DAG, CPDAG, MPDAG, MAG)".equals(method)) {
            textArea.setText("");
            allNonamenablePathsMpdagMag(graph, textArea, nodes1, nodes2);
        } else if ("All Paths".equals(method)) {
            textArea.setText("");
            allPaths(graph, textArea, nodes1, nodes2);
        } else if ("Treks".equals(method)) {
            textArea.setText("");
            allTreks(graph, textArea, nodes1, nodes2);
        } else if ("Confounder Paths".equals(method)) {
            textArea.setText("");
            confounderPaths(graph, textArea, nodes1, nodes2);
        } else if ("Latent Confounder Paths".equals(method)) {
            textArea.setText("");
            latentConfounderPaths(graph, textArea, nodes1, nodes2);
        } else if ("Adjacents".equals(method)) {
            textArea.setText("");
            adjacentNodes(graph, textArea, nodes1, nodes2);
        } else if ("Adjustment Sets".equals(method)) {
            textArea.setText("");
            adjustmentSets(graph, textArea, nodes1, nodes2);
        } else {
            throw new IllegalArgumentException("Unknown method: " + method);
        }
    }

    /**
     * Appends all directed paths from nodes in list nodes1 to nodes in list nodes2 to a given text area.
     *
     * @param graph      The Graph object representing the graph.
     * @param textArea   The JTextArea object to append the paths to.
     * @param nodes1     The list of starting nodes.
     * @param nodes2     The list of ending nodes.
     */
    private void allDirectedPaths(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.append("These are paths that are causal from X to Y--i.e. paths of the form X ~~> Y.\n");

        boolean pathListed = false;

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                List<List<Node>> paths = graph.paths().directedPaths(node1, node2,
                        Preferences.userRoot().getInt("pathMaxLength", 8));

                if (paths.isEmpty()) {
                    continue;
                } else {
                    pathListed = true;
                }

                textArea.append("\n\nBetween " + node1 + " and " + node2 + ":");

                for (List<Node> path : paths) {
                    textArea.append("\n    " + GraphUtils.pathString(graph, path, conditioningSet, true));
                }
            }
        }

        if (!pathListed) {
            textArea.append("\nNo directed paths listed.");
        }
    }

    /**
     * Appends all semidirected paths from nodes in list nodes1 to nodes in list nodes2 to the given text area.
     * A semidirected path is a path that, with additional knowledge, could be causal from source to target.
     *
     * @param graph     The Graph object representing the graph.
     * @param textArea  The JTextArea object to append the paths to.
     * @param nodes1    The list of starting nodes.
     * @param nodes2    The list of ending nodes.
     */
    private void allSemidirectedPaths(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.append("These are paths that properly directed with additional knowledge could be causal from source to target.\n");

        boolean pathListed = false;

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                List<List<Node>> paths = graph.paths().semidirectedPaths(node1, node2,
                        Preferences.userRoot().getInt("pathMaxLength", 8));

                if (paths.isEmpty()) {
                    continue;
                } else {
                    pathListed = true;
                }

                textArea.append("\n\nBetween " + node1 + " and " + node2 + ":");

                for (List<Node> path : paths) {
                    textArea.append("\n    " + GraphUtils.pathString(graph, path, conditioningSet, true));
                }
            }
        }

        if (!pathListed) {
            textArea.append("\nNo semidirected paths listed.");
        }
    }

    /**
     * Appends all amenable paths from nodes in the first list to nodes in the second list to the given text area.
     * An amenable path starts with a directed edge out of the starting node and does not block any of these paths.
     *
     * @param graph     The Graph object representing the graph.
     * @param textArea  The JTextArea object to append the paths to.
     * @param nodes1    The list of starting nodes.
     * @param nodes2    The list of ending nodes.
     */
    private void allAmenablePathsMpdagMag(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.append("These are semidirected paths from X to Y that start with a directed edge out of X.\n" +
                        "And adjustmentt set should not block any of these paths");

        boolean pathListed = false;

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                List<List<Node>> amenable = graph.paths().amenablePathsMpdagMag(node1, node2,
                        Preferences.userRoot().getInt("pathMaxLength", 8));

                if (amenable.isEmpty()) {
                    continue;
                } else {
                    pathListed = true;
                }

                textArea.append("\n\nBetween " + node1 + " and " + node2 + ":");

                for (List<Node> path : amenable) {
                    textArea.append("\n    " + GraphUtils.pathString(graph, path, conditioningSet, true));
                }
            }
        }

        if (!pathListed) {
            textArea.append("\nNo amenable paths listed.");
        }
    }

    /**
     * Appends all non-amenable paths from nodes in the first list to nodes in the second list to the given text area.
     * A non-amenable path is a path that is not amenable. An adjustment set should block all of these paths.
     *
     * @param graph The Graph object representing the graph.
     * @param textArea The JTextArea object to append the paths to.
     * @param nodes1 The list of starting nodes.
     * @param nodes2 The list of ending nodes.
     */
    private void allNonamenablePathsMpdagMag(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.append("These are paths that are not amenable paths. An adjustment set should block all of these paths.\n");

        boolean pathListed = false;

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                List<List<Node>> nonamenable = graph.paths().allPaths(node1, node2,
                        Preferences.userRoot().getInt("pathMaxLength", 8));
                List<List<Node>> amenable = graph.paths().amenablePathsMpdagMag(node1, node2,
                        Preferences.userRoot().getInt("pathMaxLength", 8));
                nonamenable.removeAll(amenable);

                if (amenable.isEmpty()) {
                    continue;
                } else {
                    pathListed = true;
                }

                textArea.append("\n\nBetween " + node1 + " and " + node2 + ":");

                for (List<Node> path : nonamenable) {
                    textArea.append("\n    " + GraphUtils.pathString(graph, path, conditioningSet, true));
                }
            }
        }

        if (!pathListed) {
            textArea.append("\nNo non-amenable paths listed.");
        }
    }

    /**
     * Appends all paths from the source nodes to the target nodes to a given text area.
     *
     * @param graph The Graph object representing the graph.
     * @param textArea The JTextArea object to append the paths to.
     * @param nodes1 The list of source nodes.
     * @param nodes2 The list of target nodes.
     */
    private void allPaths(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.append("These are all paths from the source to the target, however oriented.\n");

        boolean pathListed = false;

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                List<List<Node>> paths = graph.paths().allPaths(node1, node2,
                        Preferences.userRoot().getInt("pathMaxLength", 8));

                if (paths.isEmpty()) {
                    continue;
                } else {
                    pathListed = true;
                }

                textArea.append("\n\nBetween " + node1 + " and " + node2 + ":");

                for (List<Node> path : paths) {
                    textArea.append("\n    " + GraphUtils.pathString(graph, path, conditioningSet,true));
                }
            }
        }

        if (!pathListed) {
            textArea.append("\nNo paths listed.");
        }
    }

    /**
     * Appends all treks of the form X <~~ S ~~> Y, S ~~> Y or X <~~ S for some source S
     *
     * @param graph       The Graph object representing the graph.
     * @param textArea    The JTextArea object to append the treks to.
     * @param nodes1      The list of starting nodes.
     * @param nodes2      The list of ending nodes.
     */
    private void allTreks(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.append("These paths of the form X <~~ S ~~> Y, S ~~> Y or X <~~ S for some source S.\n");

        boolean pathListed = false;

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                List<List<Node>> treks = graph.paths().treks(node1, node2, Preferences.userRoot().getInt("pathMaxLength", 8));

                if (treks.isEmpty()) {
                    continue;
                } else {
                    pathListed = true;
                }

                textArea.append("\n\nBetween " + node1 + " and " + node2 + ":");

                for (List<Node> trek : treks) {
                    textArea.append("\n    " + GraphUtils.pathString(graph, trek, conditioningSet, true));
                }
            }
        }

        if (!pathListed) {
            textArea.append("\nNo treks listed.");
        }
    }

    /**
     * Appends all confounder paths of the form X <~~ S ~~> Y, where S is the source, to the given text area.
     *
     * @param graph     The Graph object representing the graph.
     * @param textArea  The JTextArea object to append the paths to.
     * @param nodes1    The list of starting nodes.
     * @param nodes2    The list of ending nodes.
     */
    private void confounderPaths(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.append("These are paths of the form X <~~ S ~~> Y for source S.\n");

        boolean pathListed = false;

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                List<List<Node>> confounderPaths = graph.paths().treks(node1, node2, Preferences.userRoot().getInt("pathMaxLength", 8));
                List<List<Node>> directPaths1 = graph.paths().directedPaths(node1, node2, Preferences.userRoot().getInt("pathMaxLength", 8));
                List<List<Node>> directPaths2 = graph.paths().directedPaths(node2, node1, Preferences.userRoot().getInt("pathMaxLength", 8));

                confounderPaths.removeAll(directPaths1);

                for (List<Node> _path : directPaths2) {
                    Collections.reverse(_path);
                    confounderPaths.remove(_path);
                }

                confounderPaths.removeIf(path -> path.get(0).getNodeType() != NodeType.MEASURED
                                                 || path.get(path.size() - 1).getNodeType() != NodeType.MEASURED);

                if (confounderPaths.isEmpty()) {
                    continue;
                } else {
                    pathListed = true;
                }

                textArea.append("\n\nBetween " + node1 + " and " + node2 + ":");

                for (List<Node> confounderPath : confounderPaths) {
                    textArea.append("\n    " + GraphUtils.pathString(graph, confounderPath, conditioningSet, true));
                }
            }
        }

        if (!pathListed) {
            textArea.append("\nNo confounder paths listed.");
        }
    }

    /**
     * Appends all confounder paths along which all nodes except for endpoints are latent to the given text area.
     *
     * @param graph     The Graph object representing the graph.
     * @param textArea  The JTextArea object to append the paths to.
     * @param nodes1    The list of starting nodes.
     * @param nodes2    The list of ending nodes.
     */
    private void latentConfounderPaths(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        boolean pathListed = false;

        textArea.append("These are confounder paths along which all nodes except for endpoints are latent.\n");

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                List<List<Node>> latentConfounderPaths = graph.paths().treks(node1, node2, Preferences.userRoot().getInt("pathMaxLength", 8));
                List<List<Node>> directPaths1 = graph.paths().directedPaths(node1, node2, Preferences.userRoot().getInt("pathMaxLength", 8));
                List<List<Node>> directPaths2 = graph.paths().directedPaths(node2, node1, Preferences.userRoot().getInt("pathMaxLength", 8));
                latentConfounderPaths.removeAll(directPaths1);

                for (List<Node> _path : directPaths2) {
                    Collections.reverse(_path);
                    latentConfounderPaths.remove(_path);
                }

                for (List<Node> path : new ArrayList<>(latentConfounderPaths)) {
                    for (int i = 1; i < path.size() - 1; i++) {
                        Node node = path.get(i);

                        if (node.getNodeType() != NodeType.LATENT) {
                            latentConfounderPaths.remove(path);
                        }
                    }

                    if (path.get(0).getNodeType() != NodeType.MEASURED
                        || path.get(path.size() - 1).getNodeType() != NodeType.MEASURED) {
                        latentConfounderPaths.remove(path);
                    }
                }

                if (latentConfounderPaths.isEmpty()) {
                    continue;
                } else {
                    pathListed = true;
                }

                textArea.append("\n\nBetween " + node1 + " and " + node2 + ":");

                for (List<Node> latentConfounderPath : latentConfounderPaths) {
                    textArea.append("\n    " + GraphUtils.pathString(graph, latentConfounderPath, conditioningSet, true));
                }
            }
        }

        if (!pathListed) {
            textArea.append("\nNo latent confounder paths listed.");
        }
    }

    /**
     * Calculates and displays the adjacent nodes for each pair of nodes in the given lists.
     *
     * @param graph The graph object representing the graph.
     * @param textArea The JTextArea object to append the results to.
     * @param nodes1 The first list of nodes.
     * @param nodes2 The second list of nodes.
     */
    private void adjacentNodes(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                List<Node> parents = graph.getParents(node1);
                List<Node> children = graph.getChildren(node1);

                List<Node> ambiguous = new ArrayList<>(graph.getAdjacentNodes(node1));
                ambiguous.removeAll(parents);
                ambiguous.removeAll(children);

                textArea.append("\n\nAdjacents for " + node1 + ":");
                textArea.append("\n\nParents: " + niceList(parents));
                textArea.append("\nChildren: " + niceList(children));
                textArea.append("\nAmbiguous: " + niceList(ambiguous));

                List<Node> parents2 = graph.getParents(node2);
                List<Node> children2 = graph.getChildren(node2);

                List<Node> ambiguous2 = new ArrayList<>(graph.getAdjacentNodes(node2));
                ambiguous2.removeAll(parents2);
                ambiguous2.removeAll(children2);

                textArea.append("\n\nAdjacents for " + node2 + ":");
                textArea.append("\n\nParents: " + niceList(parents2));
                textArea.append("\nChildren: " + niceList(children2));
                textArea.append("\nAmbiguous: " + niceList(ambiguous2));
            }
        }
    }

    /**
     * Calculates some adjustment sets for a given set of nodes in a graph.
     *
     * @param graph    The graph to calculate the adjustment sets in.
     * @param textArea The text area to display the results in.
     * @param nodes1   The first set of nodes.
     * @param nodes2   The second set of nodes.
     */
    private void adjustmentSets(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.append("""
                               \s
                An adjustment set is a set of nodes that blocks all paths that can't be causal while\
                               \s
                leaving all possibly causal paths unblocked. There may be no adjustment set for a given\
                               \s
                source and target""");

        for (Node node1 : nodes1) {
            for (Node node2 : nodes2) {
                List<Set<Node>> adjustments = graph.paths().adjustmentSets(node1, node2, 8, 4, 3,
                        Preferences.userRoot().getInt("pathMaxLength", 8));

                textArea.append("\n\nAdjustment sets for " + node1 + " ~~> " + node2 + ":\n");

                if (adjustments.isEmpty()) {
                    textArea.append("\n    --NONE--");
                    continue;
                }

                for (Set<Node> adjustment : adjustments) {
                    textArea.append("\n    " + adjustment);
                }
            }
        }
    }

    /**
     * Converts a list of Nodes into a comma-separated string representation.
     * If the list is empty, returns "--NONE--".
     *
     * @param _nodes The list of Nodes to convert.
     * @return The comma-separated string representation of the Nodes list,
     *         or "--NONE--" if the list is empty.
     */
    private String niceList(List<Node> _nodes) {
        if (_nodes.isEmpty()) {
            return "--NONE--";
        }

        List<Node> nodes = new ArrayList<>(_nodes);

        Collections.sort(nodes);

        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < nodes.size(); i++) {
            buf.append(nodes.get(i));

            if (i < nodes.size() - 1) {
                buf.append(", ");
            }
        }

        return buf.toString();
    }

    /**
     * Notifies that the ownership of the specified clipboard contents has been lost.
     *
     * @param clipboard The clipboard object that lost ownership of the contents.
     * @param contents  The contents that were lost by the clipboard.
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }

    /**
     * Sets the maximum length for a path.
     *
     * @param maxLength The maximum length of the path. It must be greater than or equal to -1.
     * @throws IllegalArgumentException If the maxLength is less than -1.
     */
    private void setMaxLength(int maxLength) {
        if (!(maxLength >= -1)) throw new IllegalArgumentException();
        Preferences.userRoot().putInt("pathMaxLength", maxLength);
    }

    /**
     * A JTextFieldWithPrompt is a custom JTextField that displays a prompt text when no text has been entered and the
     * component does not have focus.
     */
    private static class JTextFieldWithPrompt extends JTextField {
        private final String promptText;
        private final Color promptColor;

        public JTextFieldWithPrompt(String promptText) {
            this(promptText, Color.GRAY);
        }

        public JTextFieldWithPrompt(String promptText, Color promptColor) {
            this.promptText = promptText;
            this.promptColor = promptColor;

            // Set focus listener to repaint the component when focus is gained or lost
            this.addFocusListener(new FocusListener() {

                @Override
                public void focusGained(FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    repaint();
                }
            });
        }

        /**
         * This method is responsible for painting the component. It overrides the paintComponent method from the JTextField class.
         * It checks if the text in the component is empty and if it does not have focus. If both conditions are true, it paints the
         * prompt text on the component using the specified prompt color and font style.
         *
         * @param g the Graphics object used for painting
         */
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (getText().isEmpty() && !isFocusOwner()) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setColor(promptColor);
                g2d.setFont(getFont().deriveFont(Font.ITALIC));
                int padding = (getHeight() - getFont().getSize()) / 2;
                g2d.drawString(promptText, getInsets().left, getHeight() - padding - 1);
                g2d.dispose();
            }
        }
    }
}



