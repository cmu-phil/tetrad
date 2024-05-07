/*
 * Copyright (C) 2018 University of Pittsburgh.
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
package edu.pitt.dbmi.data.reader.covariance;

import edu.pitt.dbmi.data.reader.Data;

import java.util.List;

/**
 * Nov 19, 2018 2:20:52 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public interface CovarianceData extends Data {

    /**
     * <p>getNumberOfCases.</p>
     *
     * @return the number of cases in the data.
     */
    int getNumberOfCases();

    /**
     * <p>getVariables.</p>
     *
     * @return the number of variables in the data.
     */
    List<String> getVariables();

    /**
     * <p>getData.</p>
     *
     * @return the data in a 2D array.
     */
    double[][] getData();

}
