/*
 * Copyright (C) 2019 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
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
     * Constant <code>NONLINEAR_ADDITIVE_CAUSAL_MODEL="Nonlinear Additive Causal (NAC) Model"</code>
     */
    public static final String NONLINEAR_ADDITIVE_CAUSAL_MODEL = "Nonlinear Additive Causal (NAC) Model";
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
