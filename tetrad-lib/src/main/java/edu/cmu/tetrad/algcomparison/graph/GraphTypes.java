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

package edu.cmu.tetrad.algcomparison.graph;

/**
 * Jun 4, 2019 3:21:47 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public final class GraphTypes {

    /**
     * Constant <code>RANDOM_FOWARD_DAG="Random Foward DAG (Fixed Average Degree"{trunked}</code>
     */
    public static final String RANDOM_FOWARD_DAG = "Random Foward DAG (Fixed Average Degree)";
    /**
     * Constant <code>ERDOS_RENYI_DAG="Erdos-Renyi DAG (Fixed Edge Probability"{trunked}</code>
     */
    public static final String ERDOS_RENYI_DAG = "Erdos-Renyi DAG (Fixed Edge Probability)";
    /**
     * Constant <code>SCALE_FREE_DAG="Scale Free DAG (Bollobas et al.)"</code>
     */
    public static final String SCALE_FREE_DAG = "Scale Free DAG (Bollobas et al.)";
    /**
     * Constant <code>CYCLIC_CONSTRUCTED_FROM_SMALL_LOOPS="Cyclic (Constructed From Small Loops)"</code>
     */
    public static final String CYCLIC_CONSTRUCTED_FROM_SMALL_LOOPS = "Cyclic (Constructed From Small Loops)";
    /**
     * Constant <code>RANDOM_MIM="Random MIM"</code>
     */
    public static final String RANDOM_MIM = "Random MIM";
    /**
     * Constant <code>RANDOM_ONE_FACTOR_MIM="Random One Factor MIM"</code>
     */
    public static final String RANDOM_ONE_FACTOR_MIM = "Random One Factor MIM";
    /**
     * Constant <code>RANDOM_TWO_FACTOR_MIM="Random Two Factor MIM"</code>
     */
    public static final String RANDOM_TWO_FACTOR_MIM = "Random Two Factor MIM";

    private GraphTypes() {
    }

}

