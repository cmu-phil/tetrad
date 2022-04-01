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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetradapp.util.CopyLayoutAction;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.util.PasteLayoutAction;

import javax.swing.*;
import java.util.ArrayList;

/**
 * Builds a menu for layout operations on graphs. Interacts with classes that
 * implement the LayoutEditable interface.
 *
 * @author Joseph Ramsey
 */
public class LayoutMenu extends JMenu {
    private final LayoutEditable layoutEditable;
    private final CopyLayoutAction copyLayoutAction;

    public LayoutMenu(LayoutEditable layoutEditable) {
        super("Layout");
        this.layoutEditable = layoutEditable;

        if (layoutEditable.getGraph().isTimeLagModel()) {

            JMenuItem topToBottom = new JMenuItem("Top to bottom");
            add(topToBottom);

            topToBottom.addActionListener(e -> {
                LayoutUtils.topToBottomLayout(getLayoutEditable());

                // Copy the laid out graph to the clipboard.
                getCopyLayoutAction().actionPerformed(null);
            });

            JMenuItem leftToRight = new JMenuItem("Left to right");
            add(leftToRight);

            leftToRight.addActionListener(e -> {
                LayoutUtils.leftToRightLayout(getLayoutEditable());

                // Copy the laid out graph to the clipboard.
                getCopyLayoutAction().actionPerformed(null);
            });

            JMenuItem bottomToTop = new JMenuItem("Bottom to top");
            add(bottomToTop);

            bottomToTop.addActionListener(e -> {
                LayoutUtils.bottomToTopLayout(getLayoutEditable());

                // Copy the laid out graph to the clipboard.
                getCopyLayoutAction().actionPerformed(null);
            });

            JMenuItem rightToLeft = new JMenuItem("Right to left");
            add(rightToLeft);

            rightToLeft.addActionListener(e -> {
                LayoutUtils.rightToLeftLayout(getLayoutEditable());

                // Copy the laid out graph to the clipboard.
                getCopyLayoutAction().actionPerformed(null);
            });

            JMenuItem likeLag0 = new JMenuItem("Copy lag 0");
            add(likeLag0);

            likeLag0.addActionListener(e -> {
                if (LayoutUtils.getLayout() == LayoutUtils.Layout.topToBottom
                        || LayoutUtils.getLayout() == LayoutUtils.Layout.lag0TopToBottom) {
                    LayoutUtils.copyLag0LayoutTopToBottom(LayoutMenu.this.getLayoutEditable());
                } else if (LayoutUtils.getLayout() == LayoutUtils.Layout.bottomToTop
                        || LayoutUtils.getLayout() == LayoutUtils.Layout.lag0BottomToTop) {
                    LayoutUtils.copyLag0LayoutBottomToTop(LayoutMenu.this.getLayoutEditable());
                } else if (LayoutUtils.getLayout() == LayoutUtils.Layout.leftToRight
                        || LayoutUtils.getLayout() == LayoutUtils.Layout.lag0LeftToRight) {
                    LayoutUtils.copyLag0LayoutLeftToRight(LayoutMenu.this.getLayoutEditable());
                } else if (LayoutUtils.getLayout() == LayoutUtils.Layout.rightToLeft
                        || LayoutUtils.getLayout() == LayoutUtils.Layout.lag0RightToLeft) {
                    LayoutUtils.copyLag0LayoutRightToLeft(LayoutMenu.this.getLayoutEditable());
                }

                // Copy the laid out graph to the clipboard.
                LayoutMenu.this.getCopyLayoutAction().actionPerformed(null);
            });

            this.addSeparator();
        }


        JMenuItem circleLayout = new JMenuItem("Circle");
        this.add(circleLayout);

        circleLayout.addActionListener(e -> {
            LayoutUtils.circleLayout(LayoutMenu.this.getLayoutEditable());

            // Copy the laid out graph to the clipboard.
            LayoutMenu.this.getCopyLayoutAction().actionPerformed(null);
        });

        if (this.getLayoutEditable().getKnowledge() != null) {
            JMenuItem knowledgeTiersLayout = new JMenuItem("Knowledge Tiers");
            this.add(knowledgeTiersLayout);

            knowledgeTiersLayout.addActionListener(e -> {
                LayoutUtils.knowledgeLayout(LayoutMenu.this.getLayoutEditable());

                // Copy the laid out graph to the clipboard.
                LayoutMenu.this.getCopyLayoutAction().actionPerformed(null);
            });
        }


        JMenuItem fruchtermanReingold = new JMenuItem("Fruchterman-Reingold");
        this.add(fruchtermanReingold);

        fruchtermanReingold.addActionListener(e -> {
            LayoutUtils.fruchtermanReingoldLayout(LayoutMenu.this.getLayoutEditable());

            // Copy the laid out graph to the clipboard.
            LayoutMenu.this.getCopyLayoutAction().actionPerformed(null);
        });

        JMenuItem kamadaKawai = new JMenuItem("Kamada-Kawai");
        this.add(kamadaKawai);

        kamadaKawai.addActionListener(e -> {
            LayoutEditable layoutEditable1 = LayoutMenu.this.getLayoutEditable();
            LayoutUtils.kamadaKawaiLayout(layoutEditable1);

            // Copy the laid out graph to the clipboard.
            LayoutMenu.this.getCopyLayoutAction().actionPerformed(null);
        });

        JMenuItem distanceFromSelected = new JMenuItem("Distance From Selected");
        this.add(distanceFromSelected);

        distanceFromSelected.addActionListener(e -> {
            LayoutEditable layoutEditable12 = LayoutMenu.this.getLayoutEditable();
            LayoutUtils.distanceFromSelectedLayout(layoutEditable12);

            // Copy the laid out graph to the clipboard.
            LayoutMenu.this.getCopyLayoutAction().actionPerformed(null);
        });

        JMenuItem causalOrder = new JMenuItem("Causal Order");
        this.add(causalOrder);

        causalOrder.addActionListener(e -> {
            LayoutEditable layoutEditable13 = LayoutMenu.this.getLayoutEditable();
            Graph graph = layoutEditable13.getGraph();

            for (Node node : new ArrayList<>(graph.getNodes())) {
                if (node.getNodeType() == NodeType.ERROR) {
                    graph.removeNode(node);
                }
            }

            CausalOrder layout1 = new CausalOrder(layoutEditable13);
            layout1.doLayout();
            layoutEditable13.layoutByGraph(graph);
            LayoutUtils.layout = LayoutUtils.Layout.distanceFromSelected;

            // Copy the laid out graph to the clipboard.
            getCopyLayoutAction().actionPerformed(null);
        });

        addSeparator();

        this.copyLayoutAction = new CopyLayoutAction(getLayoutEditable());
        add(getCopyLayoutAction());
        add(new PasteLayoutAction(getLayoutEditable()));
    }

    private LayoutEditable getLayoutEditable() {
        return this.layoutEditable;
    }

    private CopyLayoutAction getCopyLayoutAction() {
        return this.copyLayoutAction;
    }


}





