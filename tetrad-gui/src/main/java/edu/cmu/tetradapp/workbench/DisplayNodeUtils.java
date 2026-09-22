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

import java.awt.*;

/**
 * Shared constants and painting for graph node display components. Colors and the font are delegated to
 * {@link WorkbenchStyle} so they follow the active Look &amp; Feel; the getters are kept for existing callers.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class DisplayNodeUtils {

    // Note that this component must be a JComponent, since non-rectangular
    // shapes are used for some extensions.

    private static final int PIXEL_GAP = 7;

    /**
     * The fill color of an unselected measured node.
     *
     * @return the fill color.
     */
    public static Color getNodeFillColor() {
        return WorkbenchStyle.measuredFill();
    }

    /**
     * The border color of an unselected node.
     *
     * @return the border color.
     */
    public static Color getNodeEdgeColor() {
        return WorkbenchStyle.nodeBorder();
    }

    /**
     * The fill color of a selected measured node.
     *
     * @return the fill color.
     */
    public static Color getNodeSelectedFillColor() {
        return WorkbenchStyle.selectedFill(WorkbenchStyle.measuredFill());
    }

    /**
     * The border color of a selected node.
     *
     * @return the border color.
     */
    public static Color getNodeSelectedEdgeColor() {
        return WorkbenchStyle.selectedBorder();
    }

    /**
     * The color of node name text.
     *
     * @return the text color.
     */
    public static Color getNodeTextColor() {
        return WorkbenchStyle.nodeText();
    }

    /**
     * The font for node names.
     *
     * @return the font.
     */
    public static Font getFont() {
        return WorkbenchStyle.nodeFont();
    }

    /**
     * The vertical padding above and below node text.
     *
     * @return the gap in pixels.
     */
    public static int getPixelGap() {
        return DisplayNodeUtils.PIXEL_GAP;
    }

    /**
     * Paints a node: fills the shape, draws its border (accented and heavier when selected), and centers the name
     * in it. The caller is responsible for rendering hints.
     *
     * @param g2       the graphics to paint on.
     * @param shape    the node outline, in component coordinates.
     * @param name     the node name; may be null.
     * @param size     the node size.
     * @param fill     the unselected fill color.
     * @param selected whether the node is selected.
     */
    static void paintNode(Graphics2D g2, Shape shape, String name, Dimension size, Color fill, boolean selected) {
        if (name == null) name = "";

        g2.setColor(selected ? WorkbenchStyle.selectedFill(fill) : fill);
        g2.fill(shape);

        g2.setStroke(new BasicStroke(selected ? 2f : 1f));
        g2.setColor(selected ? WorkbenchStyle.selectedBorder() : WorkbenchStyle.nodeBorder());
        g2.draw(shape);

        Font font = WorkbenchStyle.nodeFont();
        FontMetrics fm = g2.getFontMetrics(font);
        int x = (size.width - fm.stringWidth(name)) / 2;
        int y = (size.height - fm.getHeight()) / 2 + fm.getAscent();
        g2.setFont(font);
        g2.setColor(WorkbenchStyle.nodeText());
        g2.drawString(name, x, y);
    }
}
