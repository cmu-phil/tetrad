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

package edu.cmu.tetrad.study.gene.tetrad.gene.graph;

import edu.cmu.tetrad.study.gene.tetrad.gene.history.LaggedFactor;

/**
 * Translates display names of lagged variables (e.g. "V1:L1") into model names (e.g. "V1:1") and vice-versa.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class DisplayNameHandler {

    /**
     * Private constructor to prevent instantiation.
     */
    private DisplayNameHandler() {

    }

    /**
     * Converts the given lagged factor into a display string.
     *
     * @param laggedFactor a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.LaggedFactor} object
     * @return a {@link java.lang.String} object
     */
    public static String getDisplayString(LaggedFactor laggedFactor) {
        return DisplayNameHandler.getDisplayString(laggedFactor.getFactor(),
                laggedFactor.getLag());
    }

    /**
     * Uses the given factor and lag information to construct a display string.
     *
     * @param factor a {@link java.lang.String} object
     * @param lag    a int
     * @return a {@link java.lang.String} object
     */
    public static String getDisplayString(String factor, int lag) {
        return factor + ":L" + lag;
    }

    /**
     * Parses the given string and returns the LaggedFactor it represents.
     *
     * @param displayString a {@link java.lang.String} object
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.LaggedFactor} object
     */
    public static LaggedFactor getLaggedFactor(String displayString) {

        String factor = DisplayNameHandler.extractFactor_Display(displayString);
        int lag = DisplayNameHandler.extractLag_Display(displayString);

        return new LaggedFactor(factor, lag);
    }

    /**
     * Parses the given string representing a lagged factor and return the part that represents the factor.
     *
     * @param laggedFactor a {@link java.lang.String} object
     * @return a {@link java.lang.String} object
     */
    public static String extractFactor_Display(String laggedFactor) {

        int colonIndex = laggedFactor.indexOf(":L");

        return laggedFactor.substring(0, colonIndex);
    }

    /**
     * Extracts the lag from the lagged factor name string. precondition laggedFactor is a legal lagged factor.
     *
     * @param laggedFactor the lagged factor whose lag is wanted.
     * @return the lag of this lagged factor.
     */
    public static int extractLag_Display(String laggedFactor) {

        int colonIndex = laggedFactor.indexOf(":L");

        return Integer.parseInt(
                laggedFactor.substring(colonIndex + 2));
    }
}





