/// ////////////////////////////////////////////////////////////////////////////
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

import edu.cmu.tetrad.graph.Edge;

import java.awt.*;

/**
 * Interface for a display edge.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface IDisplayEdge {
    /**
     * <p>isSelected.</p>
     *
     * @return a boolean
     */
    boolean isSelected();

    /**
     * <p>setSelected.</p>
     *
     * @param selected a boolean
     */
    void setSelected(boolean selected);

    /**
     * <p>launchAssociatedEditor.</p>
     */
    void launchAssociatedEditor();

    /**
     * <p>updateTrackPoint.</p>
     *
     * @param p a {@link java.awt.Point} object
     */
    void updateTrackPoint(Point p);

    /**
     * <p>getNode1.</p>
     *
     * @return a {@link edu.cmu.tetradapp.workbench.DisplayNode} object
     */
    DisplayNode getNode1();

    /**
     * <p>getNode2.</p>
     *
     * @return a {@link edu.cmu.tetradapp.workbench.DisplayNode} object
     */
    DisplayNode getNode2();

    /**
     * <p>getConnectedPoints.</p>
     *
     * @return a {@link edu.cmu.tetradapp.workbench.PointPair} object
     */
    PointPair getConnectedPoints();

    /**
     * <p>setConnectedPoints.</p>
     *
     * @param connectedPoints a {@link edu.cmu.tetradapp.workbench.PointPair} object
     */
    void setConnectedPoints(PointPair connectedPoints);

    /**
     * <p>getRelativeMouseTrackPoint.</p>
     *
     * @return a {@link java.awt.Point} object
     */
    Point getRelativeMouseTrackPoint();

    /**
     * <p>getModelEdge.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Edge} object
     */
    Edge getModelEdge();

    /**
     * <p>getOffset.</p>
     *
     * @return a double
     */
    double getOffset();

    /**
     * <p>setOffset.</p>
     *
     * @param offset a double
     */
    void setOffset(double offset);

    /**
     * <p>getPointPair.</p>
     *
     * @return a {@link edu.cmu.tetradapp.workbench.PointPair} object
     */
    PointPair getPointPair();

    /**
     * <p>getTrackPoint.</p>
     *
     * @return a {@link java.awt.Point} object
     */
    Point getTrackPoint();

    /**
     * <p>getComp1.</p>
     *
     * @return a {@link edu.cmu.tetradapp.workbench.DisplayNode} object
     */
    DisplayNode getComp1();

    /**
     * <p>getComp2.</p>
     *
     * @return a {@link edu.cmu.tetradapp.workbench.DisplayNode} object
     */
    DisplayNode getComp2();

    /**
     * <p>getLineColor.</p>
     *
     * @return a {@link java.awt.Color} object
     */
    Color getLineColor();

    /**
     * <p>setLineColor.</p>
     *
     * @param lineColor a {@link java.awt.Color} object
     */
    void setLineColor(Color lineColor);

    /**
     * <p>getSolid.</p>
     *
     * @return a boolean
     */
    boolean getSolid();

    /**
     * <p>setSolid.</p>
     *
     * @param solid a boolean
     */
    void setSolid(boolean solid);

    /**
     * <p>setThick.</p>
     *
     * @param thick a boolean
     */
    void setThick(boolean thick);

    /**
     * <p>getSelectedColor.</p>
     *
     * @return a {@link java.awt.Color} object
     */
    Color getSelectedColor();

    /**
     * <p>setSelectedColor.</p>
     *
     * @param selectedColor a {@link java.awt.Color} object
     */
    void setSelectedColor(Color selectedColor);

    /**
     * <p>getHighlightedColor.</p>
     *
     * @return a {@link java.awt.Color} object
     */
    Color getHighlightedColor();

    /**
     * <p>setHighlightedColor.</p>
     *
     * @param selectedColor a {@link java.awt.Color} object
     */
    void setHighlightedColor(Color selectedColor);

    /**
     * <p>getStrokeWidth.</p>
     *
     * @return a float
     */
    float getStrokeWidth();

    /**
     * <p>setStrokeWidth.</p>
     *
     * @param strokeWidth a float
     */
    void setStrokeWidth(float strokeWidth);

    /**
     * <p>setHighlighted.</p>
     *
     * @param highlighted a boolean
     */
    void setHighlighted(boolean highlighted);
}






