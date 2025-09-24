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

package edu.pitt.dbmi.algo.bayesian.constraint.inference;

/**
 * Feb 22, 2014 3:35:38 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class BayesianConstraintInferenceTest {

    /**
     * Constructor.
     */
    public BayesianConstraintInferenceTest() {
    }

    /**
     * Main method.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        new BayesianConstraintInferenceTest().testMain();
    }

    /**
     * Test of main method, of class BayesianConstraintInference.
     */
    public void testMain() {
        final String casFile = "sample_data/cooper.data/small_data.cas";
        String[] args = {
                "--cas", casFile
        };
        BayesianConstraintInference.main(args);
    }
}



