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
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Puts up a panel letting the user show undirectedPaths about some node in the graph.
 *
 * @author josephramsey
 * @version $Id: $Id
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
     * <p>Constructor for PathsAction.</p>
     *
     * @param workbench a {@link edu.cmu.tetradapp.workbench.GraphWorkbench} object
     */
    public PathsAction(GraphWorkbench workbench) {
        super("Paths");
        this.workbench = workbench;
    }

    /**
     * {@inheritDoc}
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

        Node pathFrom = graph.getNode(Preferences.userRoot().get("pathFrom", ""));

        if (pathFrom == null) {
            this.nodes1 = Collections.singletonList(graph.getNodes().get(0));
        } else {
            this.nodes1 = Collections.singletonList(pathFrom);
        }

        JComboBox node1Box = new JComboBox(array);

        node1Box.addActionListener(e1 -> {
            JComboBox box = (JComboBox) e1.getSource();
            Node node = (Node) box.getSelectedItem();
            System.out.println(node);

            assert node != null;
            if ("SELECT_ALL".equals(node.getName())) {
                PathsAction.this.nodes1 = new ArrayList<>(graph.getNodes());
            } else {
                PathsAction.this.nodes1 = Collections.singletonList(node);
            }

            Preferences.userRoot().put("pathFrom", node.getName());
        });

        node1Box.setSelectedItem(this.nodes1.get(0));

        Node pathTo = graph.getNode(Preferences.userRoot().get("pathTo", ""));

        if (pathTo == null) {
            this.nodes2 = Collections.singletonList(graph.getNodes().get(0));
        } else {
            this.nodes2 = Collections.singletonList(pathTo);
        }

        JComboBox node2Box = new JComboBox(array);

        node2Box.addActionListener(e12 -> {
            JComboBox box = (JComboBox) e12.getSource();
            Node node = (Node) box.getSelectedItem();
            System.out.println(node);

            if ("SELECT_ALL".equals(node.getName())) {
                PathsAction.this.nodes2 = new ArrayList<>(graph.getNodes());
            } else {
                PathsAction.this.nodes2 = Collections.singletonList(node);
            }

            Preferences.userRoot().put("pathTo", node.getName());
        });

        node2Box.setSelectedItem(this.nodes2.get(0));

        JComboBox methodBox = new JComboBox(new String[]{"Directed Paths", "Semidirected Paths",
                "Amenable paths (DAG, CPDAG, MPDAG, MAG)",
                "Non-amenable paths (DAG, CPDAG, MPDAG, MAG)",
                "Treks", "Confounder Paths", "Latent Confounder Paths",
                "All Paths", "Adjacents"});
        this.method = Preferences.userRoot().get("pathMethod", "Directed Paths");

        methodBox.addActionListener(e13 -> {
            JComboBox box = (JComboBox) e13.getSource();
            PathsAction.this.method = (String) box.getSelectedItem();
            Preferences.userRoot().put("pathMethod", PathsAction.this.method);
//                update(graph, textArea, nodes1, nodes2, method);
        });

        methodBox.setSelectedItem(this.method);

        IntTextField maxField = new IntTextField(Preferences.userRoot().getInt("pathMaxLength", 8), 2);

        maxField.setFilter((value, oldValue) -> {
            try {

                // Disallow unlimited path option. Also insist the max path length be at least 1.
                if (value >= 2) setMaxLength(value);
                return Preferences.userRoot().getInt("pathMaxLength", 8);
            } catch (Exception e14) {
                return oldValue;
            }
        });

        JButton updateButton = new JButton(("Update"));

        updateButton.addActionListener(e15 -> update(graph, PathsAction.this.textArea,
                PathsAction.this.nodes1, PathsAction.this.nodes2, PathsAction.this.method));

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
        b1.add(updateButton);
        b.add(b1);

        Box b2 = Box.createHorizontalBox();
        b2.add(scroll);
        this.textArea.setCaretPosition(0);
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
        }
    }

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
                    textArea.append("\n    " + GraphUtils.pathString(graph, path));
                }
            }
        }

        if (!pathListed) {
            textArea.append("\nNo directed paths listed.");
        }
    }

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
                    textArea.append("\n    " + GraphUtils.pathString(graph, path));
                }
            }
        }

        if (!pathListed) {
            textArea.append("\nNo semidirected paths listed.");
        }
    }

    private void allAmenablePathsMpdagMag(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.append("These are semidirected paths from X to Y that start with a directed edge out of X.\n");

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
                    textArea.append("\n    " + GraphUtils.pathString(graph, path));
                }
            }
        }

        if (!pathListed) {
            textArea.append("\nNo amenable paths listed.");
        }
    }

    private void allNonamenablePathsMpdagMag(Graph graph, JTextArea textArea, List<Node> nodes1, List<Node> nodes2) {
        textArea.append("These are paths that are not amenable paths.\n");

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
                    textArea.append("\n    " + GraphUtils.pathString(graph, path));
                }
            }
        }

        if (!pathListed) {
            textArea.append("\nNo non-amenable paths listed.");
        }
    }

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
                    textArea.append("\n    " + GraphUtils.pathString(graph, path));
                }
            }
        }

        if (!pathListed) {
            textArea.append("\nNo paths listed.");
        }
    }

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
                    textArea.append("\n    " + GraphUtils.pathString(graph, trek));
                }
            }
        }

        if (!pathListed) {
            textArea.append("\nNo treks listed.");
        }
    }

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
                    textArea.append("\n    " + GraphUtils.pathString(graph, confounderPath));
                }
            }
        }

        if (!pathListed) {
            textArea.append("\nNo confounder paths listed.");
        }
    }

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
                    textArea.append("\n    " + GraphUtils.pathString(graph, latentConfounderPath));
                }
            }
        }

        if (!pathListed) {
            textArea.append("\nNo latent confounder paths listed.");
        }
    }


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
     * {@inheritDoc}
     * <p>
     * Required by the AbstractAction interface; does nothing.
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }

    private void setMaxLength(int maxLength) {
        if (!(maxLength >= -1)) throw new IllegalArgumentException();
        Preferences.userRoot().putInt("pathMaxLength", maxLength);
    }
}



