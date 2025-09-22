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

package edu.cmu.tetrad.algcomparison.simulation;

/**
 * Jun 4, 2019 3:10:49 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public final class SimulationTypes {

    /**
     * Constant <code>BAYS_NET="Bayes Net (Multinomial)"</code>
     */
    public static final String BAYS_NET = "Bayes Net (Multinomial)";
    /**
     * Constant <code>STRUCTURAL_EQUATION_MODEL="Linear Structural Equation Model"</code>
     */
    public static final String STRUCTURAL_EQUATION_MODEL = "Linear Structural Equation Model";
    /**
     * Constant <code>LINEAR_FISHER_MODEL="Linear Fisher Model"</code>
     */
    public static final String LINEAR_FISHER_MODEL = "Linear Fisher Model";
    /**
     * Constant <code>NON_LINEAR_STRUCTURAL_EQUATION_MODEL="GP Nonlinear Structural Equation Model"</code>
     */
    public static final String GAUSSIAN_PROCESS_STRUCTURAL_EQUATION_MODEL = "Gaussian Process Structural Equation Model";
    /**
     * Constant <code>NONLINEAR_ADDITIVE_NOISE_MODEL="Nonlinear Additive Noise Model"</code>
     */
    public static final String NONLINEAR_ADDITIVE_NOISE_MODEL = "Nonlinear Additive Noise Model";
    /**
     * Constant <code>POST_NONLINEAR_MODEL="Post-nonlinear causal model"</code>
     */
    public static final String POST_NONLINEAR_MODEL = "Post-nonlinear causal Model";
//    /**
//     * Constant <code>POST_NONLINEAR_MODEL="Post-nonlinear causal model"</code>
//     */
//    public static final String NONLINEAR_FUNCTIONS_OF_LINEAR = "Nonlinear Functions of Linear (NFL) Model";
    /**
     * Constant <code>CAUSAL_PERCEPTRON_NETWORK="Causal Peceptron Network (CPN)"</code>
     */
    public static final String CAUSAL_PERCEPTRON_NETWORK = "Nonlinear SEM (Neural Net)";
    /**
     * Constant <code>CAUSAL_PERCEPTRON_NETWORK="Causal Peceptron Network (CPN)"</code>
     */
    public static final String ADDITIVE_ANM_NETWORK = "Additive Nonlinear SEM (ANM)";
    /**
     * Constant <code>LG_MNAR_SIMULATION="LG MNAR Simulation"</code>
     */
    public static final String LG_MNAR_SIMULATION = "LG MNAR Simulation";
    /**
     * Constant <code>LEE_AND_HASTIE="Mixed Lee &amp; Hastie"</code>
     */
    public static final String LEE_AND_HASTIE = "Mixed Lee & Hastie";
    /**
     * Constant <code>CONDITIONAL_GAUSSIAN="Mixed Conditional Gaussian"</code>
     */
    public static final String CONDITIONAL_GAUSSIAN = "Mixed Conditional Gaussian";
    /**
     * Constant <code>TIME_SERIES="Time Series"</code>
     */
    public static final String TIME_SERIES = "Time Series";
    /**
     * Constant <code>STANDARDIZED_STRUCTURAL_EQUATION_MODEL="Standardized Structural Equation Model"</code>
     */
    public static final String STANDARDIZED_STRUCTURAL_EQUATION_MODEL = "Standardized Structural Equation Model";
    /**
     * Constant <code>GENERAL_STRUCTURAL_EQUATION_MODEL="General Structural Equation Model"</code>
     */
    public static final String GENERAL_STRUCTURAL_EQUATION_MODEL = "General Structural Equation Model";
    /**
     * Constant <code>LOADED_FROM_FILES="Loaded From Files"</code>
     */
    public static final String LOADED_FROM_FILES = "Loaded From Files";
    /**
     * Constant <code>BOOLEAN_GLASS_SIMULATION="Boolean Glass Simulation"</code>
     */
    public static final String BOOLEAN_GLASS_SIMULATION = "Boolean Glass Simulation";

    private SimulationTypes() {
    }

}

