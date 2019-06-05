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
 *
 * Jun 4, 2019 3:10:49 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public final class SimulationTypes {

    public static final String BAYS_NET = "Bayes Net";
    public static final String STRUCTURAL_EQUATION_MODEL = "Structural Equation Model";
    public static final String LINEAR_FISHER_MODEL = "Linear Fisher Model";
    public static final String LEE_AND_HASTIE = "Lee & Hastie";
    public static final String CONDITIONAL_GAUSSIAN = "Conditional Gaussian";
    public static final String TIME_SERIES = "Time Series";
    public static final String STANDARDIZED_STRUCTURAL_EQUATION_MODEL = "Standardized Structural Equation Model";
    public static final String GENERAL_STRUCTURAL_EQUATION_MODEL = "General Structural Equation Model";
    public static final String LOADED_FROM_FILES = "Loaded From Files";
    public static final String BOOLEAN_GLASS_SIMULATION = "Boolean Glass Simulation";

    private SimulationTypes() {
    }

}
