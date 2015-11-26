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

package edu.cmu.tetradapp.workbench;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.util.GraphEditorUtils;
import edu.cmu.tetradapp.util.LayoutEditable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Utils, for layouts.
 *
 * @author Tyler
 */
public class LayoutUtils {
    public static enum Layout {
        lag0TopToBottom, lag0BottomToTop, lag0LeftToRight, lag0RightToLeft,
        topToBottom, bottomToTop, leftToRight, rightToLeft, layered, source, knowledge, circle,
        kamadaKawai, fruchtermReingold, distanceFromSelected
    }

    static Layout layout = Layout.bottomToTop;

    public static void setLayout(Layout _layout) {
        layout = _layout;
    }

    public static Layout getLayout() {
        return layout;
    }

    public static void setPreferredAsSize(Component comp) {
        Dimension pref = comp.getPreferredSize();
        comp.setMaximumSize(pref);
        comp.setMinimumSize(pref);
    }


    public static void setAllSizes(Component comp, Dimension dim) {
        comp.setPreferredSize(dim);
        comp.setMaximumSize(dim);
        comp.setMinimumSize(dim);
        comp.setSize(dim);
    }


    public static Box leftAlignJLabel(JLabel label) {
        Box box = Box.createHorizontalBox();
        box.add(label);
        box.add(Box.createHorizontalGlue());
        return box;
    }


    public static void copyLag0LayoutTopToBottom(LayoutEditable layoutEditable) {
        TimeLagGraph graph = layoutEditable.getGraph().getTimeLagGraph();

        java.util.List<Node> lag0Nodes = graph.getLag0Nodes();

        Collections.sort(lag0Nodes, new Comparator<Node>() {
            public int compare(Node o1, Node o2) {
                return o1.getCenterX() - o2.getCenterX();
            }
        });

        int minLag0Y = Integer.MAX_VALUE;
        int maxLag0Y = Integer.MIN_VALUE;

        for (Node node : lag0Nodes) {
            if (node.getCenterY() < minLag0Y) minLag0Y = node.getCenterY();
            if (node.getCenterY() > maxLag0Y) maxLag0Y = node.getCenterY();
        }

        int lag0YDiff = maxLag0Y - minLag0Y;
        int ySpace = lag0YDiff + 25 < 100 ? 100 : lag0YDiff + 25;

        int minY = Integer.MAX_VALUE;

        for (Node node : lag0Nodes) {
            int x = node.getCenterX();
            int y = node.getCenterY();
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = 0; lag <= graph.getMaxLag(); lag++) {
                y -= ySpace;
                Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find " + _node);
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

    public static void copyLag0LayoutBottomToTop(LayoutEditable layoutEditable) {
        TimeLagGraph graph = layoutEditable.getGraph().getTimeLagGraph();

        java.util.List<Node> lag0Nodes = graph.getLag0Nodes();

        Collections.sort(lag0Nodes, new Comparator<Node>() {
            public int compare(Node o1, Node o2) {
                return o1.getCenterX() - o2.getCenterX();
            }
        });

        int minLag0Y = Integer.MAX_VALUE;
        int maxLag0Y = Integer.MIN_VALUE;

        for (Node node : lag0Nodes) {
            if (node.getCenterY() < minLag0Y) minLag0Y = node.getCenterY();
            if (node.getCenterY() > maxLag0Y) maxLag0Y = node.getCenterY();
        }

        int lag0YDiff = maxLag0Y - minLag0Y;
        int ySpace = lag0YDiff + 25 < 100 ? 100 : lag0YDiff + 25;

        int minY = Integer.MAX_VALUE;

        for (Node node : lag0Nodes) {
            int x = node.getCenterX();
            int y = node.getCenterY();
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = graph.getMaxLag(); lag >= 0; lag--) {
                y -= ySpace;
                Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find " + _node);
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

    public static void copyLag0LayoutLeftToRight(LayoutEditable layoutEditable) {
        TimeLagGraph graph = layoutEditable.getGraph().getTimeLagGraph();

        java.util.List<Node> lag0Nodes = graph.getLag0Nodes();

        Collections.sort(lag0Nodes, new Comparator<Node>() {
            public int compare(Node o1, Node o2) {
                return o1.getCenterX() - o2.getCenterX();
            }
        });

        int minLag0X = Integer.MAX_VALUE;
        int maxLag0X = Integer.MIN_VALUE;

        for (Node node : lag0Nodes) {
            if (node.getCenterX() < minLag0X) minLag0X = node.getCenterX();
            if (node.getCenterX() > maxLag0X) maxLag0X = node.getCenterX();
        }

        int lag0XDiff = maxLag0X - minLag0X;
        int xSpace = lag0XDiff + 25 < 90 ? 90 : lag0XDiff + 25;

        int minX = Integer.MAX_VALUE;

        for (Node node : lag0Nodes) {
            int x = node.getCenterX();
            int y = node.getCenterY();
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = 0; lag <= graph.getMaxLag(); lag++) {
                x -= xSpace;
                Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find " + _node);
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

    public static void copyLag0LayoutRightToLeft(LayoutEditable layoutEditable) {
        TimeLagGraph graph = layoutEditable.getGraph().getTimeLagGraph();

        java.util.List<Node> lag0Nodes = graph.getLag0Nodes();

        Collections.sort(lag0Nodes, new Comparator<Node>() {
            public int compare(Node o1, Node o2) {
                return o1.getCenterX() - o2.getCenterX();
            }
        });

        int minLag0X = Integer.MAX_VALUE;
        int maxLag0X = Integer.MIN_VALUE;

        for (Node node : lag0Nodes) {
            if (node.getCenterX() < minLag0X) minLag0X = node.getCenterX();
            if (node.getCenterX() > maxLag0X) maxLag0X = node.getCenterX();
        }

        int lag0XDiff = maxLag0X - minLag0X;
        int xSpace = lag0XDiff + 25 < 90 ? 90 : lag0XDiff + 25;

        int minX = Integer.MAX_VALUE;

        for (Node node : lag0Nodes) {
            int x = node.getCenterX();
            int y = node.getCenterY();
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = graph.getMaxLag(); lag >= 0; lag--) {
                x -= xSpace;
                Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find " + _node);
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

    public static void topToBottomLayout(LayoutEditable layoutEditable) {
        TimeLagGraph graph = layoutEditable.getGraph().getTimeLagGraph();

        int xStart = 65;
        int yStart = 50;
        int xSpace = 100;
        int ySpace = 100;
        List<Node> lag0Nodes = graph.getLag0Nodes();

        Collections.sort(lag0Nodes, new Comparator<Node>() {
            public int compare(Node o1, Node o2) {
                return o1.getCenterX() - o2.getCenterX();
            }
        });

        int x = xStart - xSpace;

        for (Node node : lag0Nodes) {
            x += xSpace;
            int y = yStart - ySpace;
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = graph.getMaxLag(); lag >= 0; lag--) {
                y += ySpace;
                Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find " + _node);
                    continue;
                }

                _node.setCenterX(x);
                _node.setCenterY(y);
            }
        }

        layoutEditable.layoutByGraph(graph);
        layout = Layout.topToBottom;
    }

    public static void leftToRightLayout(LayoutEditable layoutEditable) {
        TimeLagGraph graph = layoutEditable.getGraph().getTimeLagGraph();

        int xStart = 65;
        int yStart = 50;
        int xSpace = 100;
        int ySpace = 100;
        List<Node> lag0Nodes = graph.getLag0Nodes();

        Collections.sort(lag0Nodes, new Comparator<Node>() {
            public int compare(Node o1, Node o2) {
                return o1.getCenterX() - o2.getCenterX();
            }
        });

        int y = yStart - ySpace;

        for (Node node : lag0Nodes) {
            y += ySpace;
            int x = xStart - xSpace;
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = graph.getMaxLag(); lag >= 0; lag--) {
                x += xSpace;
                Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find " + _node);
                    continue;
                }

                _node.setCenterX(x);
                _node.setCenterY(y);
            }
        }

        layoutEditable.layoutByGraph(graph);
        layout = Layout.leftToRight;
    }

    public static void bottomToTopLayout(LayoutEditable layoutEditable) {
        TimeLagGraph graph = layoutEditable.getGraph().getTimeLagGraph();

        int xStart = 65;
        int yStart = 50;
        int xSpace = 100;
        int ySpace = 100;
        List<Node> lag0Nodes = graph.getLag0Nodes();

        Collections.sort(lag0Nodes, new Comparator<Node>() {
            public int compare(Node o1, Node o2) {
                return o1.getCenterX() - o2.getCenterX();
            }
        });

        int x = xStart - xSpace;

        for (Node node : lag0Nodes) {
            x += xSpace;
            int y = yStart - ySpace;
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = 0; lag <= graph.getMaxLag(); lag++) {
                y += ySpace;
                Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find " + _node);
                    continue;
                }

                _node.setCenterX(x);
                _node.setCenterY(y);
            }
        }

        layoutEditable.layoutByGraph(graph);
        layout = Layout.bottomToTop;
    }

    public static void rightToLeftLayout(LayoutEditable layoutEditable) {
        TimeLagGraph graph = layoutEditable.getGraph().getTimeLagGraph();

        int xStart = 65;
        int yStart = 50;
        int xSpace = 100;
        int ySpace = 100;
        List<Node> lag0Nodes = graph.getLag0Nodes();

        Collections.sort(lag0Nodes, new Comparator<Node>() {
            public int compare(Node o1, Node o2) {
                return o1.getCenterX() - o2.getCenterX();
            }
        });

        int y = yStart - ySpace;

        for (Node node : lag0Nodes) {
            y += ySpace;
            int x = xStart - xSpace;
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = 0; lag <= graph.getMaxLag(); lag++) {
                x += xSpace;
                Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find " + _node);
                    continue;
                }

                _node.setCenterX(x);
                _node.setCenterY(y);
            }
        }

        layoutEditable.layoutByGraph(graph);
        layout = Layout.rightToLeft;
    }

    public static void layeredDrawingLayout(LayoutEditable layoutEditable) {
        Graph graph = layoutEditable.getGraph();

        for (Node node : new ArrayList<Node>(graph.getNodes())) {
            if (node.getNodeType() == NodeType.ERROR) {
                graph.removeNode(node);
            }
        }

        GraphUtils.hierarchicalLayout(graph);
        layoutEditable.layoutByGraph(graph);
        layout = Layout.layered;
    }

    public static void sourceGraphLayout(LayoutEditable layoutEditable) {
        Graph graph = new EdgeListGraph(layoutEditable.getGraph());

        for (Node node : new ArrayList<Node>(graph.getNodes())) {
            if (node.getNodeType() == NodeType.ERROR) {
                graph.removeNode(node);
            }
        }

        Graph sourceGraph = layoutEditable.getSourceGraph();
        GraphUtils.arrangeBySourceGraph(graph, sourceGraph);
        layoutEditable.layoutByGraph(graph);
        layout = Layout.source;
    }

    public static void knowledgeLayout(LayoutEditable layoutEditable) {
        Graph graph = new EdgeListGraph(layoutEditable.getGraph());

        try {

            for (Node node : new ArrayList<Node>(graph.getNodes())) {
                if (node.getNodeType() == NodeType.ERROR) {
                    graph.removeNode(node);
                }
            }

            IKnowledge knowledge = layoutEditable.getKnowledge();
            SearchGraphUtils.arrangeByKnowledgeTiers(graph, knowledge);
            layoutEditable.layoutByGraph(graph);
        } catch (Exception e1) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    e1.getMessage());
        }
        layout = Layout.knowledge;
    }

    public static void circleLayout(LayoutEditable layoutEditable) {
        Graph graph = layoutEditable.getGraph();

        for (Node node : new ArrayList<Node>(graph.getNodes())) {
            if (node.getNodeType() == NodeType.ERROR) {
                graph.removeNode(node);
            }
        }

        Rectangle r = layoutEditable.getVisibleRect();

        int m = Math.min(r.width, r.height) / 2;
        int radius = m - 50;
        int centerx = r.x + m;
        int centery = r.y + m;

//        DataGraphUtils.circleLayout(graph, 200, 200, 150);
        GraphUtils.circleLayout(graph, centerx, centery, radius);
        layoutEditable.layoutByGraph(graph);
        layout = Layout.circle;
    }

    public static void kamadaKawaiLayout(final LayoutEditable layoutEditable) {
        final Graph graph = layoutEditable.getGraph();

        Runnable runnable = new Runnable() {
            public void run() {

                for (Node node : new ArrayList<Node>(graph.getNodes())) {
                    if (node.getNodeType() == NodeType.ERROR) {
                        graph.removeNode(node);
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

                GraphUtils.kamadaKawaiLayout(graph, initializeRandomly,
                        naturalEdgeLength, springConstant, stopEnergy);
                layoutEditable.layoutByGraph(graph);
                layout = Layout.kamadaKawai;
            }
        };

        Thread thread = new Thread(runnable);
        thread.start();
    }

    public static void fruchtermanReingoldLayout(LayoutEditable layoutEditable) {
        Graph graph = layoutEditable.getGraph();

        for (Node node : new ArrayList<Node>(graph.getNodes())) {
            if (node.getNodeType() == NodeType.ERROR) {
                graph.removeNode(node);
            }
        }

        GraphUtils.fruchtermanReingoldLayout(graph);
        layoutEditable.layoutByGraph(graph);
        layout = Layout.fruchtermReingold;
    }

    public static void distanceFromSelectedLayout(LayoutEditable layoutEditable) {
        Graph graph = layoutEditable.getGraph();

        for (Node node : new ArrayList<Node>(graph.getNodes())) {
            if (node.getNodeType() == NodeType.ERROR) {
                graph.removeNode(node);
            }
        }

        DistanceFromSelected layout1 = new DistanceFromSelected(layoutEditable);
        layout1.doLayout();
        layoutEditable.layoutByGraph(graph);
        layout = Layout.distanceFromSelected;
    }

    public static void lastLayout(LayoutEditable layoutEditable) {
        switch (layout) {
            case lag0TopToBottom:
                copyLag0LayoutTopToBottom(layoutEditable);
                break;
            case lag0BottomToTop:
                copyLag0LayoutBottomToTop(layoutEditable);
                break;
            case lag0LeftToRight:
                copyLag0LayoutLeftToRight(layoutEditable);
                break;
            case lag0RightToLeft:
                copyLag0LayoutRightToLeft(layoutEditable);
                break;
            case topToBottom:
                topToBottomLayout(layoutEditable);
                break;
            case bottomToTop:
                bottomToTopLayout(layoutEditable);
                break;
            case leftToRight:
                leftToRightLayout(layoutEditable);
                break;
            case rightToLeft:
                rightToLeftLayout(layoutEditable);
                break;
            case layered:
                layeredDrawingLayout(layoutEditable);
                break;
            case source:
                sourceGraphLayout(layoutEditable);
                break;
            case knowledge:
                knowledgeLayout(layoutEditable);
                break;
            case circle:
                circleLayout(layoutEditable);
                break;
            case kamadaKawai:
                kamadaKawaiLayout(layoutEditable);
                break;
            case fruchtermReingold:
                fruchtermanReingoldLayout(layoutEditable);
                break;
            default:
        }
    }
}




