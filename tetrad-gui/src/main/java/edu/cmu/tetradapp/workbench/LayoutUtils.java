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

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.util.GraphEditorUtils;
import edu.cmu.tetradapp.util.LayoutEditable;
import org.apache.commons.math3.util.FastMath;

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
        int ySpace = FastMath.max(lag0YDiff + 25, 100);

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
        int ySpace = FastMath.max(lag0YDiff + 25, 100);

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
        int xSpace = FastMath.max(lag0XDiff + 25, 90);

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
        int xSpace = FastMath.max(lag0XDiff + 25, 90);

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
    public static void knowledgeLayout(LayoutEditable layoutEditable) {
        Graph graph = new EdgeListGraph(layoutEditable.getGraph());

        try {

            for (Node node : new ArrayList<>(graph.getNodes())) {
                if (node.getNodeType() == NodeType.ERROR) {
                    graph.removeNode(node);
                }
            }

            GraphSearchUtils.arrangeByKnowledgeTiers(graph);
            layoutEditable.layoutByGraph(graph);
        } catch (Exception e1) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    e1.getMessage());
        }
        LayoutUtils.layout = Layout.knowledge;
    }

    /**
     * <p>circleLayout.</p>
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

        Rectangle r = layoutEditable.getVisibleRect();

        int m = FastMath.min(r.width, r.height) / 2;

        LayoutUtil.circleLayout(graph);
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

        Rectangle r = layoutEditable.getVisibleRect();

        LayoutUtil.squareLayout(graph);
        layoutEditable.layoutByGraph(graph);
        LayoutUtils.layout = Layout.circle;
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
                LayoutUtils.knowledgeLayout(layoutEditable);
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





