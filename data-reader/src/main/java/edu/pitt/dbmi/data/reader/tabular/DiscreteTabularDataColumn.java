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
 */
public class DiscreteTabularDataColumn implements DiscreteDataColumn {

    private final DataColumn dataColumn;
    private final Map<String, Integer> values;
    private List<String> categories;

    public DiscreteTabularDataColumn(final DataColumn dataColumn) {
        this.dataColumn = dataColumn;
        this.values = new TreeMap<>();
    }

    @Override
    public String toString() {
        return "DiscreteTabularDataColumn{" + "dataColumn=" + this.dataColumn + ", values=" + this.values + ", categories=" + this.categories + '}';
    }

    @Override
    public Integer getEncodeValue(final String value) {
        return this.values.get(value);
    }

    @Override
    public void recategorize() {
        final Set<String> keyset = this.values.keySet();
        this.categories = new ArrayList<>(keyset.size());
        int count = 0;
        for (final String key : keyset) {
            this.values.put(key, count++);
            this.categories.add(key);
        }
    }

    @Override
    public void setValue(final String value) {
        this.values.put(value, null);
    }

    @Override
    public DataColumn getDataColumn() {
        return this.dataColumn;
    }

    public Map<String, Integer> getValues() {
        return this.values;
    }

    @Override
    public List<String> getCategories() {
        return (this.categories == null)
                ? Collections.EMPTY_LIST
                : this.categories;
    }

    public void setCategories(final List<String> categories) {
        this.categories = categories;
    }

}
