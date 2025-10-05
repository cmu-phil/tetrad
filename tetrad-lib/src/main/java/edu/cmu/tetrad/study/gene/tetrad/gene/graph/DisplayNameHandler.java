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






