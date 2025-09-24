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

package edu.cmu.tetrad.classify;


/**
 * Interface implemented by classes that do discrete classification.
 *
 * @author Frank Wimberly
 * @version $Id: $Id
 */
public interface ClassifierDiscrete {

    /**
     * <p>classify.</p>
     *
     * @return an array with a classification (estimated value) of a target variable for each case in a DataSet.
     * @throws java.lang.InterruptedException if any.
     */
    int[] classify() throws InterruptedException;

    /**
     * <p>crossTabulation.</p>
     *
     * @return the double subscripted int array containing the "confusion matrix" of coefs of estimated versus observed
     * values of the target variable.
     */
    int[][] crossTabulation();

    /**
     * <p>getPercentCorrect.</p>
     *
     * @return the percentage of cases where the target variable is correctly classified.
     */
    double getPercentCorrect();

}






