///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.workbench;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.NaturalSort;
import edu.cmu.tetradapp.util.GraphEditorUtils;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetrad.util.TMath;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Utils, for layouts.
 *
 * @author Tyler
 * @version $Id: $Id
 */
public class LayoutUtils {
    static Layout layout = Layout.topToBottom;

    /**
     * <p>Getter for the field <code>layout</code>.</p>
     *
     * @return a {@link edu.cmu.tetradapp.workbench.LayoutUtils.Layout} object
     */
    public static Layout getLayout() {
        return LayoutUtils.layout;
    }

    /**
     * <p>Setter for the field <code>layout</code>.</p>
     *
     * @param _layout a {@link edu.cmu.tetradapp.workbench.LayoutUtils.Layout} object
     */
    public static void setLayout(Layout _layout) {
        LayoutUtils.layout = _layout;
    }

    /**
     * <p>setAllSizes.</p>
     *
     * @param comp a {@link java.awt.Component} object
     * @param dim  a {@link java.awt.Dimension} object
     */
    public static void setAllSizes(Component comp, Dimension dim) {
        comp.setPreferredSize(dim);
        comp.setMaximumSize(dim);
        comp.setMinimumSize(dim);
        comp.setSize(dim);
    }

    /**
     * <p>leftAlignJLabel.</p>
     *
     * @param label a {@link javax.swing.JLabel} object
     * @return a {@link javax.swing.Box} object
     */
    public static Box leftAlignJLabel(JLabel label) {
        Box box = Box.createHorizontalBox();
        box.add(label);
        box.add(Box.createHorizontalGlue());
        return box;
    }

    /**
     * <p>copyLag0LayoutTopToBottom.</p>
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void copyLag0LayoutTopToBottom(LayoutEditable layoutEditable) {
        TimeLagGraph graph = layoutEditable.getGraph().getTimeLagGraph();

        java.util.List<Node> lag0Nodes = graph.getLag0Nodes();

        lag0Nodes.sort(Comparator.comparingInt(Node::getCenterX));

        int minLag0Y = Integer.MAX_VALUE;
        int maxLag0Y = Integer.MIN_VALUE;

        for (Node node : lag0Nodes) {
            if (node.getCenterY() < minLag0Y) minLag0Y = node.getCenterY();
            if (node.getCenterY() > maxLag0Y) maxLag0Y = node.getCenterY();
        }

        int lag0YDiff = maxLag0Y - minLag0Y;
        int ySpace = TMath.max(lag0YDiff + 25, 100);

        int minY = Integer.MAX_VALUE;

        for (Node node : lag0Nodes) {
            int x = node.getCenterX();
            int y = node.getCenterY();
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = 0; lag <= graph.getMaxLag(); lag++) {
                y -= ySpace;
                Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find node.");
                    continue;
                }

                _node.setCenterX(x);
                _node.setCenterY(y);

                if (y < minY) minY = y;
            }
        }

        int diffY = 50 - minY;

        for (Node node : lag0Nodes) {
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = 0; lag <= graph.getMaxLag(); lag++) {
                Node _node = graph.getNode(id.getName(), lag);
                _node.setCenterY(_node.getCenterY() + diffY);
            }
        }


        layoutEditable.layoutByGraph(graph);
        layout = Layout.lag0TopToBottom;
    }

    /**
     * <p>copyLag0LayoutBottomToTop.</p>
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void copyLag0LayoutBottomToTop(LayoutEditable layoutEditable) {
        TimeLagGraph graph = layoutEditable.getGraph().getTimeLagGraph();

        java.util.List<Node> lag0Nodes = graph.getLag0Nodes();

        lag0Nodes.sort(Comparator.comparingInt(Node::getCenterX));

        int minLag0Y = Integer.MAX_VALUE;
        int maxLag0Y = Integer.MIN_VALUE;

        for (Node node : lag0Nodes) {
            if (node.getCenterY() < minLag0Y) minLag0Y = node.getCenterY();
            if (node.getCenterY() > maxLag0Y) maxLag0Y = node.getCenterY();
        }

        int lag0YDiff = maxLag0Y - minLag0Y;
        int ySpace = TMath.max(lag0YDiff + 25, 100);

        int minY = Integer.MAX_VALUE;

        for (Node node : lag0Nodes) {
            int x = node.getCenterX();
            int y = node.getCenterY();
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = graph.getMaxLag(); lag >= 0; lag--) {
                y -= ySpace;
                Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find node.");
                    continue;
                }

                _node.setCenterX(x);
                _node.setCenterY(y);

                if (y < minY) minY = y;
            }
        }

        int diffY = 50 - minY;

        for (Node node : lag0Nodes) {
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = graph.getMaxLag(); lag >= 0; lag--) {
                Node _node = graph.getNode(id.getName(), lag);
                _node.setCenterY(_node.getCenterY() + diffY);
            }
        }


        layoutEditable.layoutByGraph(graph);
        layout = Layout.lag0BottomToTop;
    }

    /**
     * <p>copyLag0LayoutLeftToRight.</p>
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void copyLag0LayoutLeftToRight(LayoutEditable layoutEditable) {
        TimeLagGraph graph = layoutEditable.getGraph().getTimeLagGraph();

        java.util.List<Node> lag0Nodes = graph.getLag0Nodes();

        lag0Nodes.sort(Comparator.comparingInt(Node::getCenterX));

        int minLag0X = Integer.MAX_VALUE;
        int maxLag0X = Integer.MIN_VALUE;

        for (Node node : lag0Nodes) {
            if (node.getCenterX() < minLag0X) minLag0X = node.getCenterX();
            if (node.getCenterX() > maxLag0X) maxLag0X = node.getCenterX();
        }

        int lag0XDiff = maxLag0X - minLag0X;
        int xSpace = TMath.max(lag0XDiff + 25, 90);

        int minX = Integer.MAX_VALUE;

        for (Node node : lag0Nodes) {
            int x = node.getCenterX();
            int y = node.getCenterY();
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = 0; lag <= graph.getMaxLag(); lag++) {
                x -= xSpace;
                Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find node.");
                    continue;
                }

                _node.setCenterX(x);
                _node.setCenterY(y);

                if (x < minX) minX = x;
            }
        }

        int diffX = 50 - minX;

        for (Node node : lag0Nodes) {
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = 0; lag <= graph.getMaxLag(); lag++) {
                Node _node = graph.getNode(id.getName(), lag);
                _node.setCenterX(_node.getCenterX() + diffX);
            }
        }


        layoutEditable.layoutByGraph(graph);
        layout = Layout.lag0LeftToRight;
    }

    /**
     * <p>copyLag0LayoutRightToLeft.</p>
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void copyLag0LayoutRightToLeft(LayoutEditable layoutEditable) {
        TimeLagGraph graph = layoutEditable.getGraph().getTimeLagGraph();

        java.util.List<Node> lag0Nodes = graph.getLag0Nodes();

        lag0Nodes.sort(Comparator.comparingInt(Node::getCenterX));

        int minLag0X = Integer.MAX_VALUE;
        int maxLag0X = Integer.MIN_VALUE;

        for (Node node : lag0Nodes) {
            if (node.getCenterX() < minLag0X) minLag0X = node.getCenterX();
            if (node.getCenterX() > maxLag0X) maxLag0X = node.getCenterX();
        }

        int lag0XDiff = maxLag0X - minLag0X;
        int xSpace = TMath.max(lag0XDiff + 25, 90);

        int minX = Integer.MAX_VALUE;

        for (Node node : lag0Nodes) {
            int x = node.getCenterX();
            int y = node.getCenterY();
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = graph.getMaxLag(); lag >= 0; lag--) {
                x -= xSpace;
                Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find node.");
                    continue;
                }

                _node.setCenterX(x);
                _node.setCenterY(y);

                if (x < minX) minX = x;
            }
        }

        int diffX = 50 - minX;

        for (Node node : lag0Nodes) {
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = graph.getMaxLag(); lag >= 0; lag--) {
                Node _node = graph.getNode(id.getName(), lag);
                _node.setCenterX(_node.getCenterX() + diffX);
            }
        }


        layoutEditable.layoutByGraph(graph);
        layout = Layout.lag0RightToLeft;
    }

    /**
     * <p>topToBottomLayout.</p>
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void topToBottomLayout(LayoutEditable layoutEditable) {
        TimeLagGraph graph = layoutEditable.getGraph().getTimeLagGraph();

        final int xStart = 65;
        final int yStart = 50;
        final int xSpace = 100;
        final int ySpace = 100;
        List<Node> lag0Nodes = graph.getLag0Nodes();

        lag0Nodes.sort(Comparator.comparingInt(Node::getCenterX));

        int x = xStart - xSpace;

        for (Node node : lag0Nodes) {
            x += xSpace;
            int y = yStart - ySpace;
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = graph.getMaxLag(); lag >= 0; lag--) {
                y += ySpace;
                Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find node.");
                    continue;
                }

                _node.setCenterX(x);
                _node.setCenterY(y);
            }
        }

        layoutEditable.layoutByGraph(graph);
        layout = Layout.topToBottom;
    }

    /**
     * <p>leftToRightLayout.</p>
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void leftToRightLayout(LayoutEditable layoutEditable) {
        TimeLagGraph graph = layoutEditable.getGraph().getTimeLagGraph();

        final int xStart = 65;
        final int yStart = 50;
        final int xSpace = 100;
        final int ySpace = 100;
        List<Node> lag0Nodes = graph.getLag0Nodes();

        lag0Nodes.sort(Comparator.comparingInt(Node::getCenterX));

        int y = yStart - ySpace;

        for (Node node : lag0Nodes) {
            y += ySpace;
            int x = xStart - xSpace;
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = graph.getMaxLag(); lag >= 0; lag--) {
                x += xSpace;
                Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find node.");
                    continue;
                }

                _node.setCenterX(x);
                _node.setCenterY(y);
            }
        }

        layoutEditable.layoutByGraph(graph);
        layout = Layout.leftToRight;
    }

    /**
     * <p>bottomToTopLayout.</p>
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void bottomToTopLayout(LayoutEditable layoutEditable) {
        TimeLagGraph graph = layoutEditable.getGraph().getTimeLagGraph();

        final int xStart = 65;
        final int yStart = 50;
        final int xSpace = 100;
        final int ySpace = 100;
        List<Node> lag0Nodes = graph.getLag0Nodes();

        lag0Nodes.sort(Comparator.comparingInt(Node::getCenterX));

        int x = xStart - xSpace;

        for (Node node : lag0Nodes) {
            x += xSpace;
            int y = yStart - ySpace;
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = 0; lag <= graph.getMaxLag(); lag++) {
                y += ySpace;
                Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find node.");
                    continue;
                }

                _node.setCenterX(x);
                _node.setCenterY(y);
            }
        }

        layoutEditable.layoutByGraph(graph);
        layout = Layout.bottomToTop;
    }

    /**
     * <p>rightToLeftLayout.</p>
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void rightToLeftLayout(LayoutEditable layoutEditable) {
        TimeLagGraph graph = layoutEditable.getGraph().getTimeLagGraph();

        final int xStart = 65;
        final int yStart = 50;
        final int xSpace = 100;
        final int ySpace = 100;
        List<Node> lag0Nodes = graph.getLag0Nodes();

        lag0Nodes.sort(Comparator.comparingInt(Node::getCenterX));

        int y = yStart - ySpace;

        for (Node node : lag0Nodes) {
            y += ySpace;
            int x = xStart - xSpace;
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = 0; lag <= graph.getMaxLag(); lag++) {
                x += xSpace;
                Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find node.");
                    continue;
                }

                _node.setCenterX(x);
                _node.setCenterY(y);
            }
        }

        layoutEditable.layoutByGraph(graph);
        LayoutUtils.layout = Layout.rightToLeft;
    }

    /**
     * <p>layeredDrawingLayout.</p>
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void layeredDrawingLayout(LayoutEditable layoutEditable) {
        Graph graph = layoutEditable.getGraph();

        for (Node node : new ArrayList<>(graph.getNodes())) {
            if (node.getNodeType() == NodeType.ERROR) {
                graph.removeNode(node);
            }
        }

        LayoutUtil.defaultLayout(graph);
        layoutEditable.layoutByGraph(graph);
        LayoutUtils.layout = Layout.layered;
    }

    private static void sourceGraphLayout(LayoutEditable layoutEditable) {
        Graph graph = new EdgeListGraph(layoutEditable.getGraph());

        for (Node node : new ArrayList<>(graph.getNodes())) {
            if (node.getNodeType() == NodeType.ERROR) {
                graph.removeNode(node);
            }
        }

        Graph sourceGraph = layoutEditable.getSourceGraph();
        LayoutUtil.arrangeBySourceGraph(graph, sourceGraph);
        layoutEditable.layoutByGraph(graph);
        LayoutUtils.layout = Layout.source;
    }

    /**
     * <p>knowledgeLayout.</p>
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void layoutByKnowledgeTiers(LayoutEditable layoutEditable, Knowledge knowledge) {
        Graph graph = new EdgeListGraph(layoutEditable.getGraph());

        try {

            for (Node node : new ArrayList<>(graph.getNodes())) {
                if (node.getNodeType() == NodeType.ERROR) {
                    graph.removeNode(node);
                }
            }

            LayoutUtil.layoutByKnowledgeTiers(graph, knowledge);
            layoutEditable.layoutByGraph(graph);
        } catch (Exception e1) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    e1.getMessage());
        }
        LayoutUtils.layout = Layout.knowledge;
    }

    /**
     * <p>knowledgeLayout.</p>
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void layoutByKnowledgeIndices(LayoutEditable layoutEditable) {
        Graph graph = new EdgeListGraph(layoutEditable.getGraph());

        try {

            for (Node node : new ArrayList<>(graph.getNodes())) {
                if (node.getNodeType() == NodeType.ERROR) {
                    graph.removeNode(node);
                }
            }

            LayoutUtil.layoutByKnowledgeIndices(graph);
            layoutEditable.layoutByGraph(graph);
        } catch (Exception e1) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    e1.getMessage());
        }
        LayoutUtils.layout = Layout.knowledge;
    }

    /**
     * A {@link LayoutUtil.NodeSize} backed by the editable's display nodes, so layouts space nodes by their real
     * rendered boxes. Nodes with no display node fall back to the lib's estimate.
     *
     * @param layoutEditable The editable whose display nodes supply the sizes.
     * @return The size provider.
     */
    public static LayoutUtil.NodeSize displayNodeSizes(LayoutEditable layoutEditable) {
        LayoutUtil.NodeSize fallback = LayoutUtil.estimatedNodeSize();

        return new LayoutUtil.NodeSize() {
            private Dimension dim(Node node) {
                Object o = layoutEditable.getModelNodesToDisplay().get(node);
                if (!(o instanceof DisplayNode d)) return null;
                Dimension dim = d.getSize();
                if (dim.width <= 0 || dim.height <= 0) dim = d.getPreferredSize();
                return dim;
            }

            @Override
            public double width(Node node) {
                Dimension d = dim(node);
                return d == null ? fallback.width(node) : d.width;
            }

            @Override
            public double height(Node node) {
                Dimension d = dim(node);
                return d == null ? fallback.height(node) : d.height;
            }
        };
    }

    /**
     * Lays the graph out in a circle sized from the display nodes' real boxes; see
     * {@link LayoutUtil#circleLayout(Graph, LayoutUtil.NodeSize)}.
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void circleLayout(LayoutEditable layoutEditable) {
        Graph graph = layoutEditable.getGraph();

        for (Node node : new ArrayList<>(graph.getNodes())) {
            if (node.getNodeType() == NodeType.ERROR) {
                ((SemGraph) graph).setShowErrorTerms(false);
            }
        }

        LayoutUtil.circleLayout(graph, displayNodeSizes(layoutEditable));
        layoutEditable.layoutByGraph(graph);
        LayoutUtils.layout = Layout.circle;
    }

    /**
     * <p>squareLayout.</p>
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void squareLayout(LayoutEditable layoutEditable) {
        Graph graph = layoutEditable.getGraph();

        for (Node node : new ArrayList<>(graph.getNodes())) {
            if (node.getNodeType() == NodeType.ERROR) {
                ((SemGraph) graph).setShowErrorTerms(false);
            }
        }

        LayoutUtil.squareLayout(graph);
        respaceSquareForLabels(graph, layoutEditable);
        layoutEditable.layoutByGraph(graph);
        LayoutUtils.layout = Layout.circle;
    }

    /**
     * Respaces a square layout (see {@link LayoutUtil#squareLayout(Graph)}) for the display nodes' label sizes,
     * keeping it a rectangle with aligned corners: the lib places the nodes, in natural order, along the top row
     * (left to right), the right column (top to bottom), the bottom row (right to left), and the left column
     * (bottom to top), on a grid with fixed 70 by 50 spacing. Here the grid's column positions are recomputed from
     * the widest node in each column (the top node and the bottom node sharing that column) and its row positions
     * from the tallest node in each row, each position just far enough from the previous to clear plus the
     * workbench's overlap margin, but never closer than the lib's own spacing. A square already wide enough is left
     * as is.
     *
     * @param graph          The graph, already laid out as a square by the lib.
     * @param layoutEditable The editable, for the display node sizes.
     */
    static void respaceSquareForLabels(Graph graph, LayoutEditable layoutEditable) {
        List<Node> nodes = new ArrayList<>(graph.getNodes());
        nodes.removeIf(node -> !(layoutEditable.getModelNodesToDisplay().get(node) instanceof DisplayNode));
        nodes.sort(NaturalSort.naturalComparator());

        int n = nodes.size();
        if (n < 2) return;

        int side = n / 4;
        if (n % 4 != 0) side++;

        // Grid coordinates (column index cIdx in 0..side, row index rIdx in 0..side) of each node, as the lib
        // assigns them.
        int[] cIdx = new int[n];
        int[] rIdx = new int[n];
        for (int i = 0; i < n; i++) {
            if (i < side) {
                cIdx[i] = i;
                rIdx[i] = 0;
            } else if (i < 2 * side) {
                cIdx[i] = side;
                rIdx[i] = i - side;
            } else if (i < 3 * side) {
                cIdx[i] = side - (i - 2 * side);
                rIdx[i] = side;
            } else {
                cIdx[i] = 0;
                rIdx[i] = side - (i - 3 * side);
            }
        }

        int[] colW = new int[side + 1];
        int[] rowH = new int[side + 1];
        for (int i = 0; i < n; i++) {
            Dimension dim = ((DisplayNode) layoutEditable.getModelNodesToDisplay().get(nodes.get(i))).getPreferredSize();
            colW[cIdx[i]] = Math.max(colW[cIdx[i]], dim.width);
            rowH[rIdx[i]] = Math.max(rowH[rIdx[i]], dim.height);
        }

        final int libSpaceX = 70;
        final int libSpaceY = 50;
        int margin = AbstractWorkbench.NODE_OVERLAP_MARGIN;

        // The lib's origin (70, 50) is kept unless a wide first column or tall first row needs more room, so
        // that a square that already fits is left exactly where the lib put it.
        int[] colX = new int[side + 1];
        int[] rowY = new int[side + 1];
        colX[0] = Math.max(70, colW[0] / 2 + margin);
        rowY[0] = Math.max(50, rowH[0] / 2 + margin);
        for (int k = 1; k <= side; k++) {
            colX[k] = colX[k - 1] + Math.max(libSpaceX, (colW[k - 1] + colW[k]) / 2 + margin);
            rowY[k] = rowY[k - 1] + Math.max(libSpaceY, (rowH[k - 1] + rowH[k]) / 2 + margin);
        }

        for (int i = 0; i < n; i++) {
            nodes.get(i).setCenter(colX[cIdx[i]], rowY[rIdx[i]]);
        }
    }

    /**
     * <p>kamadaKawaiLayout.</p>
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void kamadaKawaiLayout(LayoutEditable layoutEditable) {
        Graph graph = layoutEditable.getGraph();

        Runnable runnable = () -> {

            for (Node node : new ArrayList<>(graph.getNodes())) {
                if (node.getNodeType() == NodeType.ERROR) {
                    ((SemGraph) graph).setShowErrorTerms(false);
//                        graph.removeNode(node);
                }
            }

            GraphEditorUtils.editkamadaKawaiLayoutParams();

            boolean initializeRandomly = Preferences.userRoot()
                    .getBoolean(
                            "kamadaKawaiLayoutInitializeRandomly",
                            false);
            double naturalEdgeLength = Preferences.userRoot()
                    .getDouble("kamadaKawaiLayoutNaturalEdgeLength",
                            80.0);
            double springConstant = Preferences.userRoot()
                    .getDouble("kamadaKawaiLayoutSpringConstant",
                            0.2);
            double stopEnergy = Preferences.userRoot().getDouble(
                    "kamadaKawaiLayoutStopEnergy", 1.0);

            LayoutUtil.kamadaKawaiLayout(graph, initializeRandomly,
                    naturalEdgeLength, springConstant, stopEnergy);
            layoutEditable.layoutByGraph(graph);
            LayoutUtils.layout = Layout.kamadaKawai;
        };

        Thread thread = new Thread(runnable);
        thread.start();
    }

    /**
     * <p>fruchtermanReingoldLayout.</p>
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void fruchtermanReingoldLayout(LayoutEditable layoutEditable) {
        Graph graph = layoutEditable.getGraph();

        for (Node node : new ArrayList<>(graph.getNodes())) {
            if (node.getNodeType() == NodeType.ERROR) {
                ((SemGraph) graph).setShowErrorTerms(false);
            }
        }

        LayoutUtil.fruchtermanReingoldLayout(graph);
        layoutEditable.layoutByGraph(graph);
        LayoutUtils.layout = Layout.fruchtermReingold;
    }

    /**
     * <p>distanceFromSelectedLayout.</p>
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void distanceFromSelectedLayout(LayoutEditable layoutEditable) {
        Graph graph = layoutEditable.getGraph();

        for (Node node : new ArrayList<>(graph.getNodes())) {
            if (node.getNodeType() == NodeType.ERROR) {
                graph.removeNode(node);
            }
        }

        DistanceFromSelected layout1 = new DistanceFromSelected(layoutEditable);
        layout1.doLayout();
        layoutEditable.layoutByGraph(graph);
        LayoutUtils.layout = Layout.distanceFromSelected;
    }

    /**
     * <p>lastLayout.</p>
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void lastLayout(LayoutEditable layoutEditable) {
        switch (LayoutUtils.layout) {
            case lag0TopToBottom:
                LayoutUtils.copyLag0LayoutTopToBottom(layoutEditable);
                break;
            case lag0BottomToTop:
                LayoutUtils.copyLag0LayoutBottomToTop(layoutEditable);
                break;
            case lag0LeftToRight:
                LayoutUtils.copyLag0LayoutLeftToRight(layoutEditable);
                break;
            case lag0RightToLeft:
                LayoutUtils.copyLag0LayoutRightToLeft(layoutEditable);
                break;
            case topToBottom:
                LayoutUtils.topToBottomLayout(layoutEditable);
                break;
            case bottomToTop:
                LayoutUtils.bottomToTopLayout(layoutEditable);
                break;
            case leftToRight:
                LayoutUtils.leftToRightLayout(layoutEditable);
                break;
            case rightToLeft:
                LayoutUtils.rightToLeftLayout(layoutEditable);
                break;
            case layered:
                LayoutUtils.layeredDrawingLayout(layoutEditable);
                break;
            case source:
                LayoutUtils.sourceGraphLayout(layoutEditable);
                break;
            case knowledge:
                LayoutUtils.layoutByKnowledgeIndices(layoutEditable);
                break;
            case circle:
                LayoutUtils.circleLayout(layoutEditable);
                break;
            case kamadaKawai:
                LayoutUtils.kamadaKawaiLayout(layoutEditable);
                break;
            case fruchtermReingold:
                LayoutUtils.fruchtermanReingoldLayout(layoutEditable);
                break;
            default:
        }
    }

    /**
     * <p>layoutByCausalOrder.</p>
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public static void layoutByCausalOrder(LayoutEditable layoutEditable) {
        Graph graph = layoutEditable.getGraph();

        for (Node node : new ArrayList<>(graph.getNodes())) {
            if (node.getNodeType() == NodeType.ERROR) {
                graph.removeNode(node);
            }
        }

        LayoutUtil.layoutByCausalOrder(graph);
        layoutEditable.layoutByGraph(graph);
        LayoutUtils.layout = Layout.layered;
    }


    /**
     * An anum of layout options
     */
    public enum Layout {

        /**
         * lag0TopToBottom
         */
        lag0TopToBottom,

        /**
         * lag0BottomToTop
         */
        lag0BottomToTop,

        /**
         * lag0LeftToRight
         */
        lag0LeftToRight,

        /**
         * lag0RightToLeft
         */
        lag0RightToLeft,

        /**
         * topToBottom
         */
        topToBottom,

        /**
         * bottomToTop
         */
        bottomToTop,

        /**
         * leftToRight
         */
        leftToRight,

        /**
         * rightToLeft
         */
        rightToLeft,

        /**
         * layered
         */
        layered,

        /**
         * source
         */
        source,

        /**
         * knowledge
         */
        knowledge,

        /**
         * circle
         */
        circle,

        /**
         * kamadaKawai
         */
        kamadaKawai,

        /**
         * fruchtermReingold
         */
        fruchtermReingold,

        /**
         * distanceFromSelected
         */
        distanceFromSelected,

        /**
         * square
         */
        sqaure
    }
}





