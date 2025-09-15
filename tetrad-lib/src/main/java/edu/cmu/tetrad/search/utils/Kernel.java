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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

/**
 * Gives an implemented  that is implemented by classes that evaluate scalar valued kernels
 *
 * @author Robert Tillman
 * @version $Id: $Id
 */
public interface Kernel {

    /**
     * Evaluates the kernel at two points in the input space
     *
     * @param i a double
     * @param j a double
     * @return a double
     */
    double eval(double i, double j);

    /**
     * Sets bandwidth from data using default method
     *
     * @param dataset a {@link edu.cmu.tetrad.data.DataSet} object
     * @param node    a {@link edu.cmu.tetrad.graph.Node} object
     */
    void setDefaultBw(DataSet dataset, Node node);

    /**
     * Gets kernel bandwidth
     *
     * @return a double
     */
    double getBandwidth();

}




