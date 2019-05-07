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
package edu.cmu.tetrad.util;

/**
 *
 * May 7, 2019 12:00:15 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public enum BootstrapParams {

    ADD_ORIGINAL_DATASET("addOriginalDataset"),
    NUMBER_RESAMPLING("numberResampling"),
    PERCENT_RESAMPLE_SIZE("percentResampleSize"),
    RESAMPLING_ENSEMBLE("resamplingEnsemble"),
    RESAMPLING_WITH_REPLACEMENT("resamplingWithReplacement");

    private final String name;

    private BootstrapParams(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
