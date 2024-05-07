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

package edu.cmu.tetradapp.workbench;

import java.awt.*;

/**
 * Created by IntelliJ IDEA. User: jdramsey Date: Apr 1, 2006 Time: 5:19:32 PM To change this template use File |
 * Settings | File Templates.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class DisplayNodeUtils {
    // Note that this component must be a JComponent, since non-rectangular
// shapes are used for some extensions.
    private static final Color NODE_FILL_COLOR = new Color(148, 198, 226);
    private static final Color NODE_EDGE_COLOR = new Color(146, 154, 166);
    private static final Color NODE_SELECTED_FILL_COLOR = new Color(244, 219, 110);
    private static final Color NODE_SELECTED_EDGE_COLOR = new Color(215, 193, 97);
    private static final Color NODE_TEXT_COLOR = new Color(0, 1, 53);


    private static final Font FONT = new Font("Dialog", Font.BOLD, 12);
    private static final int PIXEL_GAP = 7;

    /**
     * <p>getNodeFillColor.</p>
     *
     * @return a {@link java.awt.Color} object
     */
    public static Color getNodeFillColor() {
        return DisplayNodeUtils.NODE_FILL_COLOR;
    }

    /**
     * <p>getNodeEdgeColor.</p>
     *
     * @return a {@link java.awt.Color} object
     */
    public static Color getNodeEdgeColor() {
        return DisplayNodeUtils.NODE_EDGE_COLOR;
    }

    /**
     * <p>getNodeSelectedFillColor.</p>
     *
     * @return a {@link java.awt.Color} object
     */
    public static Color getNodeSelectedFillColor() {
        return DisplayNodeUtils.NODE_SELECTED_FILL_COLOR;
    }

    /**
     * <p>getNodeSelectedEdgeColor.</p>
     *
     * @return a {@link java.awt.Color} object
     */
    public static Color getNodeSelectedEdgeColor() {
        return DisplayNodeUtils.NODE_SELECTED_EDGE_COLOR;
    }

    /**
     * <p>getNodeTextColor.</p>
     *
     * @return a {@link java.awt.Color} object
     */
    public static Color getNodeTextColor() {
        return DisplayNodeUtils.NODE_TEXT_COLOR;
    }

    /**
     * <p>getFont.</p>
     *
     * @return a {@link java.awt.Font} object
     */
    public static Font getFont() {
        return DisplayNodeUtils.FONT;
    }

    /**
     * <p>getPixelGap.</p>
     *
     * @return a int
     */
    public static int getPixelGap() {
        return DisplayNodeUtils.PIXEL_GAP;
    }
}




