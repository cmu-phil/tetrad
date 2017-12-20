/*
 * Copyright (C) 2017 University of Pittsburgh.
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
package edu.cmu.tetradapp.ui.model;

import edu.cmu.tetrad.annotation.TestOfIndependenceAnnotations;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.util.TetradProperties;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * Dec 1, 2017 11:49:55 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class IndependenceTestModels {

    private static final IndependenceTestModels INSTANCE = new IndependenceTestModels();

    private final List<IndependenceTestModel> models;
    private final Map<DataType, List<IndependenceTestModel>> modelMap = new EnumMap<>(DataType.class);
    private final Map<DataType, IndependenceTestModel> defaultModelMap = new EnumMap<>(DataType.class);

    private IndependenceTestModels() {
        TestOfIndependenceAnnotations indTestAnno = TestOfIndependenceAnnotations.getInstance();
        List<IndependenceTestModel> list = indTestAnno.filterOutExperimental(indTestAnno.getAnnotatedClasses()).stream()
                .map(e -> new IndependenceTestModel(e))
                .sorted()
                .collect(Collectors.toList());
        models = Collections.unmodifiableList(list);

        initModelMap();
        initDefaultModelMap();
    }

    private void initModelMap() {
        // initialize enum map
        DataType[] dataTypes = DataType.values();
        for (DataType dataType : dataTypes) {
            modelMap.put(dataType, new LinkedList<>());
        }

        // group by datatype
        models.stream().forEach(e -> {
            DataType[] types = e.getIndependenceTest().getAnnotation().dataType();
            for (DataType dataType : types) {
                modelMap.get(dataType).add(e);
            }
        });

        // merge continuous datatype with mixed datatype
        List<IndependenceTestModel> mergedModels = Stream.concat(modelMap.get(DataType.Continuous).stream(), modelMap.get(DataType.Mixed).stream())
                .sorted()
                .collect(Collectors.toList());
        modelMap.put(DataType.Continuous, mergedModels);

        // merge discrete datatype with mixed datatype
        mergedModels = Stream.concat(modelMap.get(DataType.Discrete).stream(), modelMap.get(DataType.Mixed).stream())
                .sorted()
                .collect(Collectors.toList());
        modelMap.put(DataType.Discrete, mergedModels);

        // make map values unmodifiable
        modelMap.forEach((k, v) -> modelMap.put(k, Collections.unmodifiableList(v)));
    }

    private void initDefaultModelMap() {
        DataType[] dataTypes = DataType.values();
        for (DataType dataType : dataTypes) {
            List<IndependenceTestModel> list = getModels(dataType);
            if (!list.isEmpty()) {
                String property = getProperty(dataType);
                if (property == null) {
                    defaultModelMap.put(dataType, list.get(0));
                } else {
                    String value = TetradProperties.getInstance().getValue(property);
                    if (value == null) {
                        defaultModelMap.put(dataType, list.get(0));
                    } else {
                        Optional<IndependenceTestModel> result = list.stream()
                                .filter(e -> e.getIndependenceTest().getClazz().getName().equals(value))
                                .findFirst();
                        defaultModelMap.put(dataType, result.isPresent() ? result.get() : list.get(0));
                    }
                }
            }
        }
    }

    private String getProperty(DataType dataType) {
        switch (dataType) {
            case Continuous:
                return "datatype.continuous.test.default";
            case Discrete:
                return "datatype.discrete.test.default";
            case Mixed:
                return "datatype.mixed.test.default";
            default:
                return null;
        }
    }

    public static IndependenceTestModels getInstance() {
        return INSTANCE;
    }

    public List<IndependenceTestModel> getModels() {
        return models;
    }

    public List<IndependenceTestModel> getModels(DataType dataType) {
        return modelMap.containsKey(dataType)
                ? modelMap.get(dataType)
                : Collections.EMPTY_LIST;
    }

    public IndependenceTestModel getDefaultModel(DataType dataType) {
        return defaultModelMap.get(dataType);
    }

}
