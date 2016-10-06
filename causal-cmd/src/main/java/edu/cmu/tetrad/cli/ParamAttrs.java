/*
 * Copyright (C) 2016 University of Pittsburgh.
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
package edu.cmu.tetrad.cli;

/**
 *
 * Sep 20, 2016 4:33:58 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public interface ParamAttrs {

    public static final String SAMPLE_PRIOR = "samplePrior";
    public static final String STRUCTURE_PRIOR = "structurePrior";

    public static final String ALPHA = "alpha";
    public static final String PENALTY_DISCOUNT = "penaltyDiscount";

    public static final String SAMPLE_SIZE = "sampleSize";

    public static final String NUM_MEASURES = "numMeasures";
    public static final String NUM_LATENTS = "numLatents";
    public static final String AVG_DEGREE = "avgDegree";
    public static final String MAX_DEGREE = "maxDegree";
    public static final String MAX_INDEGREE = "maxIndegree";
    public static final String MAX_OUTDEGREE = "maxOutdegree";
    public static final String CONNECTED = "connected";
    public static final String NUM_RUNS = "numRuns";
    public static final String STANDARDIZE = "standardize";

    public static final String FAITHFULNESS_ASSUMED = "faithfulnessAssumed";

    public static final String MIN_CATEGORIES = "minCategories";
    public static final String MAX_CATEGORIES = "maxCategories";

    public static final String DIFFERENT_GRAPHS = "differentGraphs";
    public static final String MEASUREMENT_VARIANCE = "measurementVariance";
    public static final String VERBOSE = "verbose";

    public static final String PRINT_STREAM = "printStream";

}
