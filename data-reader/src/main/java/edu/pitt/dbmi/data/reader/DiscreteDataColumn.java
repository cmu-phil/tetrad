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
package edu.pitt.dbmi.data.reader;

import java.util.List;

/**
 * Dec 10, 2018 3:24:22 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public interface DiscreteDataColumn {

    /**
     * Get the encode value.
     *
     * @param value the value.
     * @return the encode value.
     */
    Integer getEncodeValue(String value);

    /**
     * Recategorize the data.
     */
    void recategorize();

    /**
     * Get the categories.
     *
     * @return the categories.
     */
    List<String> getCategories();

    /**
     * Get the data column.
     *
     * @return the data column.
     */
    DataColumn getDataColumn();

    /**
     * Set the value.
     *
     * @param value the value.
     */
    void setValue(String value);

}
