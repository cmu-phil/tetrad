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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * An enumeration of the test types for BuildPureClusters, and Purify.
 */
public enum TestType implements TetradSerializable {
    /*POPULATION,*/

    // This one will work and does a good job for medium sized models.
    GAUSSIAN_PVALUE,

    // This will work and does a good job for small models, no more than 4 latents.
    GAUSSIAN_SCORE_MARKS,

    // This is very slow.
    GAUSSIAN_SCORE,

    // Even slower.
    GAUSSIAN_SCORE_ITERATE,

    // This option does't do any    purify.
    NONE,

    DISCRETE_LRT, DISCRETE_VARIATIONAL,


    // TETRAD_DELTA is kept for purpospes of serialization.
    GAUSSIAN_FACTOR, DISCRETE, TETRAD_BOLLEN, TETRAD_DELTA, TETRAD_WISHART,
    TETRAD_BASED, TETRAD_BASED2, POPULATION,

    // For FTFC
    SAG, GAP;


    static final long serialVersionUID = 23L;

    public static TestType serializableInstance() {
        return TestType.GAUSSIAN_PVALUE;
    }


    public static TestType[] getPurifyTestDescriptions() {

        return new TestType[]{
                TestType.NONE,
                TestType.TETRAD_BASED,
                TestType.TETRAD_BASED2,
                TestType.GAUSSIAN_PVALUE,
                TestType.GAUSSIAN_SCORE_MARKS,
//                TestType.GAUSSIAN_SCORE,
//                TestType.POPULATION,
        };
    }

    public static TestType[] getTestDescriptions() {
        return new TestType[]{
                TestType.TETRAD_WISHART,
//                TestType.TETRAD_BOLLEN,
                TestType.TETRAD_DELTA
//                TestType.GAUSSIAN_FACTOR
        };

        /**
         * Note: I'm not allowing the user to see the Gaussian Factors Test
         * because it seems to be unreliable. It seems to be working, but not
         * very well, and I can't tell why right now. In the future, I might
         * take a look at it again. The code itself seems to be bug-free.
         *
         * Concerning the first method, TEST_POPULATION, I use it primarily
         * for debugging variations of this algorithm (it assumes the covariance
         * matrix is the true one, which unfortunately requires a parameter,
         * epsilon, to take care of rounding errors when computing tetrad
         * constraints). Eventually, we may implement a variation that takes
         * the true graph and tests tetrad constraints in this graph, and let
         * the user plays with it.
         *
         *                   Ricardo, June 22 2003
         */
    }
}


