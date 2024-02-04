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
package edu.pitt.dbmi.data.reader.tabular;

import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.DiscreteDataColumn;

import java.util.*;

import static edu.pitt.dbmi.data.reader.DatasetReader.DISCRETE_MISSING_VALUE;

/**
 * Dec 31, 2018 1:50:19 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class MixedTabularDataColumn implements DiscreteDataColumn {

    private final DataColumn dataColumn;
    private final Map<String, Integer> values;
    private List<String> categories;

    /**
     * Constructor.
     *
     * @param dataColumn The data column.
     */
    public MixedTabularDataColumn(DataColumn dataColumn) {
        this.dataColumn = dataColumn;
        this.values = dataColumn.isDiscrete() ? new TreeMap<>() : null;
    }

    /**
     * Returns a string representation of the object.
     *
     * @return a string representation of the object.
     */
    @Override
    public String toString() {
        return "MixedTabularDataColumn{" + "dataColumn=" + this.dataColumn + ", values=" + this.values + ", categories=" + this.categories + '}';
    }

    /**
     * Gets the encode value of the given value.
     *
     * @param value The value.
     * @return the encode value of the given value.
     */
    @Override
    public Integer getEncodeValue(String value) {
        return (this.values == null)
                ? DISCRETE_MISSING_VALUE
                : this.values.get(value);
    }

    /**
     * Does a recategorization of the data column.
     */
    @Override
    public void recategorize() {
        if (this.values != null) {
            Set<String> keyset = this.values.keySet();
            this.categories = new ArrayList<>(keyset.size());
            int count = 0;
            for (String key : keyset) {
                this.values.put(key, count++);
                this.categories.add(key);
            }
        }
    }

    /**
     * Sets the value.
     *
     * @param value The value.
     */
    @Override
    public void setValue(String value) {
        if (this.values != null) {
            this.values.put(value, null);
        }
    }

    /**
     * Gets the data column.
     *
     * @return the data column.
     */
    @Override
    public DataColumn getDataColumn() {
        return this.dataColumn;
    }

    /**
     * Gets the values as a map.
     *
     * @return the values.
     */
    public Map<String, Integer> getValues() {
        return this.values;
    }

    /**
     * Gets the categories.
     *
     * @return the categories.
     */
    @Override
    public List<String> getCategories() {
        return (this.categories == null) ? Collections.EMPTY_LIST : this.categories;
    }

}
