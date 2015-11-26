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

package edu.cmu.tetrad.sem;

/**
 * An enumeration of the types of distributions used by SEM2 for exogenous
 * terms, together with some descriptive informationa about them.
 *
 * @author Joseph Ramsey
 */
public enum DistributionType {
    ZERO_CENTERED_NORMAL("Zero-centered Normal", "N", 1),
    NORMAL("Normal", "N", 2),
    UNIFORM("Uniform", "U", 2),
    BETA("Beta", "Beta", 2),
    GAUSSIAN_POWER("GaussianPower", "GP", 1);

    /**
     * The name of the distribution (for example, "Normal").
     */
    private String name;

    /**
     * The function symbol for the distribution (for example, "N").
     */
    private String functionSymbol;

    /**
     * The number of arguments for the distribution (for example, 2).
     */
    private int numArgs;

    /**
     * Constructs a distribution type. Private.
     * @param name The name of the distribution.
     * @param functionSymbol The function symbol of the distribution.
     * @param numArgs The number of arguments of the distribution.
     */
    private DistributionType(String name, String functionSymbol, int numArgs) {
        this.name = name;
        this.functionSymbol = functionSymbol;
        this.numArgs = numArgs;
    }

    /**
     * @return the name of the distribution. E.g. "Normal."
     * @return the name of the distribution.
     */
    public String getName() {
        return name;
    }

    /**
     * @return the function symbol. (For normal this is "N.")
     * @return the function symbol.
     */
    public String getFunctionSymbol() {
        return functionSymbol;
    }

    /**
     * @return the number of argument of the function. (For normal, this is 2.)
     * @return The number of arguments.
     */
    public int getNumArgs() {
        return numArgs;
    }
}



