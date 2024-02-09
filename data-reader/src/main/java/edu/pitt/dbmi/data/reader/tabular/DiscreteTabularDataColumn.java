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

/**
 * Dec 31, 2018 1:20:24 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class DiscreteTabularDataColumn implements DiscreteDataColumn {

    private final DataColumn dataColumn;
    private final Map<String, Integer> values;
    private List<String> categories;

    /**
     * Constructor.
     *
     * @param dataColumn The data column.
     */
    public DiscreteTabularDataColumn(DataColumn dataColumn) {
        this.dataColumn = dataColumn;
        this.values = new TreeMap<>();
    }

    /**
     * {@inheritDoc}
     *
     * Returns a string representation of the object.
     */
    @Override
    public String toString() {
        return "DiscreteTabularDataColumn{" + "dataColumn=" + this.dataColumn + ", values=" + this.values + ", categories=" + this.categories + '}';
    }

    /**
     * {@inheritDoc}
     *
     * Gets the encode value of the given value.
     */
    @Override
    public Integer getEncodeValue(String value) {
        return this.values.get(value);
    }

    /**
     * {@inheritDoc}
     *
     * Does a recategorization of the data column.
     */
    @Override
    public void recategorize() {
        Set<String> keyset = this.values.keySet();
        this.categories = new ArrayList<>(keyset.size());
        int count = 0;
        for (String key : keyset) {
            this.values.put(key, count++);
            this.categories.add(key);
        }
    }

    /**
     * {@inheritDoc}
     *
     * Gets the value of the given encode value.
     */
    @Override
    public void setValue(String value) {
        this.values.put(value, null);
    }

    /**
     * {@inheritDoc}
     *
     * Gets the data column.
     */
    @Override
    public DataColumn getDataColumn() {
        return this.dataColumn;
    }

    /**
     * Gets the values as a map
     *
     * @return the values.
     */
    public Map<String, Integer> getValues() {
        return this.values;
    }

    /**
     * {@inheritDoc}
     *
     * Gets the categories.
     */
    @Override
    public List<String> getCategories() {
        return (this.categories == null)
                ? Collections.EMPTY_LIST
                : this.categories;
    }

    /**
     * Sets the categories.
     *
     * @param categories the categories.
     */
    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

}
