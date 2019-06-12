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
import static edu.pitt.dbmi.data.reader.DatasetReader.DISCRETE_MISSING_VALUE;
import edu.pitt.dbmi.data.reader.DiscreteDataColumn;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 *
 * Dec 31, 2018 1:50:19 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class MixedTabularDataColumn implements DiscreteDataColumn {

    private final DataColumn dataColumn;
    private final Map<String, Integer> values;
    private List<String> categories;

    public MixedTabularDataColumn(DataColumn dataColumn) {
        this.dataColumn = dataColumn;
        this.values = dataColumn.isDiscrete() ? new TreeMap<>() : null;
    }

    @Override
    public String toString() {
        return "MixedTabularDataColumn{" + "dataColumn=" + dataColumn + ", values=" + values + ", categories=" + categories + '}';
    }

    @Override
    public Integer getEncodeValue(String value) {
        return (values == null)
                ? DISCRETE_MISSING_VALUE
                : values.get(value);
    }

    @Override
    public void recategorize() {
        if (values != null) {
            Set<String> keyset = values.keySet();
            categories = new ArrayList<>(keyset.size());
            int count = 0;
            for (String key : keyset) {
                values.put(key, count++);
                categories.add(key);
            }
        }
    }

    @Override
    public void setValue(String value) {
        if (this.values != null) {
            this.values.put(value, null);
        }
    }

    @Override
    public DataColumn getDataColumn() {
        return dataColumn;
    }

    public Map<String, Integer> getValues() {
        return values;
    }

    @Override
    public List<String> getCategories() {
        return (categories == null) ? Collections.EMPTY_LIST : categories;
    }

}
