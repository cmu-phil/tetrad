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

import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetradapp.util.CopyLayoutAction;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.util.PasteLayoutAction;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Builds a menu for layout operations on graphs. Interacts with classes that implement the LayoutEditable interface.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LayoutMenu extends JMenu {

    /**
     * The layout editable object.
     */
    private final LayoutEditable layoutEditable;

    /**
     * The copy layout action.
     */
    private final CopyLayoutAction copyLayoutAction;

    /**
     * <p>Constructor for LayoutMenu.</p>
     *
     * @param layoutEditable a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     */
    public LayoutMenu(LayoutEditable layoutEditable) {
        this(layoutEditable, true);
    }

    /**
     * <p>Constructor for LayoutMenu.</p>
     *
     * @param layoutEditable    a {@link edu.cmu.tetradapp.util.LayoutEditable} object
     * @param synchronizeOnOpen True if constructing the menu should synchronize the layout with the layout it is
     *                          inherited from, if that has not been done yet for this opening of the editor (see
     *                          {@link TieLayoutMenu}). False for a menu built on demand, such as the workbench's
     *                          right-click popup, where that would move the nodes under the user's hands.
     */
    public LayoutMenu(LayoutEditable layoutEditable, boolean synchronizeOnOpen) {
        super("Layout");
        this.layoutEditable = layoutEditable;

        // Undo and redo for node positions, whatever changed them: a layout from this menu, a pasted or inherited
        // layout, or dragging. Always enabled, since the accelerators must work without the menu having been opened
        // to refresh them; with nothing to undo or redo they beep, as the graph editor's undo does.
        JMenuItem undoLayout = new JMenuItem("Undo Layout Change");
        undoLayout.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        add(undoLayout);

        undoLayout.addActionListener(e -> {
            AbstractWorkbench workbench = LayoutMenu.workbenchOf(getLayoutEditable());

            if (workbench == null || !workbench.undoLayout()) {
                Toolkit.getDefaultToolkit().beep();
            }
        });

        JMenuItem redoLayout = new JMenuItem("Redo Layout Change");
        redoLayout.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        add(redoLayout);

        redoLayout.addActionListener(e -> {
            AbstractWorkbench workbench = LayoutMenu.workbenchOf(getLayoutEditable());

            if (workbench == null || !workbench.redoLayout()) {
                Toolkit.getDefaultToolkit().beep();
            }
        });

        addSeparator();

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

        JMenuItem richardsLayout = new JMenuItem("Richard's Layout");
        this.add(richardsLayout);

        richardsLayout.addActionListener(e -> {
            LayoutUtils.richardsLayout(LayoutMenu.this.getLayoutEditable());

            // Copy the laid out graph to the clipboard.
            getCopyLayoutAction().actionPerformed(null);
        });

        JMenuItem circleLayout = new JMenuItem("Circle");
        this.add(circleLayout);

        circleLayout.addActionListener(e -> {
            LayoutUtils.circleLayout(LayoutMenu.this.getLayoutEditable());

            // Copy the laid out graph to the clipboard.
            LayoutMenu.this.getCopyLayoutAction().actionPerformed(null);
        });

        JMenuItem squareLayout = new JMenuItem("Square");
        this.add(squareLayout);

        squareLayout.addActionListener(e -> {
            LayoutUtils.squareLayout(LayoutMenu.this.getLayoutEditable());

            // Copy the laid out graph to the clipboard.
            LayoutMenu.this.getCopyLayoutAction().actionPerformed(null);
        });


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
            LayoutUtils.layoutByCausalOrder(layoutEditable13);

            // Copy the laid out graph to the clipboard.
            getCopyLayoutAction().actionPerformed(null);
        });

        if (this.getLayoutEditable().getKnowledge() != null) {
            JMenuItem knowledgeTiersLayout = new JMenuItem("Layout by Knowledge");
            this.add(knowledgeTiersLayout);

            knowledgeTiersLayout.addActionListener(e -> {
                LayoutUtil.layoutByKnowledgeTiers(getLayoutEditable().getGraph(), getLayoutEditable().getKnowledge());
                getLayoutEditable().layoutByGraph(getLayoutEditable().getGraph());

                // Copy the laid out graph to the clipboard.
                LayoutMenu.this.getCopyLayoutAction().actionPerformed(null);
            });
        }

        JMenuItem knowledgeLayout = new JMenuItem("Layout by Knowledge Indices");
        this.add(knowledgeLayout);

        knowledgeLayout.addActionListener(e -> {
            LayoutUtils.layoutByKnowledgeIndices(LayoutMenu.this.getLayoutEditable());

            // Copy the laid out graph to the clipboard.
            LayoutMenu.this.getCopyLayoutAction().actionPerformed(null);
        });

        addSeparator();

        add(new TieLayoutMenu(layoutEditable));

        if (synchronizeOnOpen) {
            TieLayoutMenu.applyTieOnOpen(layoutEditable);
        }

        addSeparator();

        this.copyLayoutAction = new CopyLayoutAction(getLayoutEditable());
        add(getCopyLayoutAction());
        add(new PasteLayoutAction(getLayoutEditable()));

        addSeparator();

        // Rendering option rather than a layout proper, but this menu is the one display menu present in every
        // graph editor and in the workbench right-click popup. The preference is per user and applies to every
        // graph workbench; each edge adjusts its own bounds when it next paints, so repainting the open windows
        // is all that is needed here.
        JCheckBoxMenuItem curvedEdges = new JCheckBoxMenuItem("Curved Edges");
        curvedEdges.setSelected(DisplayEdge.isBezierEdges());
        curvedEdges.setToolTipText("Render edges as curves that bend around other nodes; "
                                   + "click a curve to select it as usual.");
        add(curvedEdges);

        curvedEdges.addActionListener(e -> {
            java.util.prefs.Preferences.userRoot().putBoolean(DisplayEdge.BEZIER_EDGES_PREF,
                    curvedEdges.isSelected());

            for (Frame frame : Frame.getFrames()) {
                frame.repaint();
            }
        });
    }

    private LayoutEditable getLayoutEditable() {
        return this.layoutEditable;
    }

    /**
     * @return the workbench that displays the graph of the given layout editable, or null if there is none. The
     * editors that implement LayoutEditable each wrap a workbench in their own way; the display nodes they hand out
     * are children of it. Looked up when needed, since an editor may replace its workbench.
     */
    private static AbstractWorkbench workbenchOf(LayoutEditable layoutEditable) {
        if (layoutEditable instanceof AbstractWorkbench workbench) {
            return workbench;
        }

        java.util.Map<edu.cmu.tetrad.graph.Node, Object> displayNodes = layoutEditable.getModelNodesToDisplay();

        if (displayNodes == null) {
            return null;
        }

        for (Object displayNode : displayNodes.values()) {
            if (displayNode instanceof Component component) {
                return (AbstractWorkbench) SwingUtilities.getAncestorOfClass(AbstractWorkbench.class, component);
            }
        }

        return null;
    }

    private CopyLayoutAction getCopyLayoutAction() {
        return this.copyLayoutAction;
    }


}






