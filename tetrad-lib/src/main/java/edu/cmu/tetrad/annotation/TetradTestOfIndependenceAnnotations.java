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
package edu.cmu.tetrad.annotation;

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
 * Sep 20, 2017 2:19:58 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TetradTestOfIndependenceAnnotations {

    private static final TetradTestOfIndependenceAnnotations INSTANCE = new TetradTestOfIndependenceAnnotations();

    private final TestOfIndependenceAnnotations testAnno = TestOfIndependenceAnnotations.getInstance();

    private final List<AnnotatedClassWrapper<TestOfIndependence>> nameWrappers;
    private final Map<DataType, List<AnnotatedClassWrapper<TestOfIndependence>>> dataTypeNameWrappers = new EnumMap<>(DataType.class);

    private TetradTestOfIndependenceAnnotations() {
        nameWrappers = testAnno.filterOutExperimental(testAnno.getAnnotatedClasses()).stream()
                .map(e -> new AnnotatedClassWrapper<>(e.getAnnotation().name(), e))
                .sorted()
                .collect(Collectors.toList());

        // initialize enum map
        DataType[] dataTypes = DataType.values();
        for (DataType dataType : dataTypes) {
            dataTypeNameWrappers.put(dataType, new LinkedList<>());
        }

        // group by datatype
        nameWrappers.stream().forEach(e -> {
            DataType[] types = e.getAnnotatedClass().getAnnotation().dataType();
            for (DataType dataType : types) {
                dataTypeNameWrappers.get(dataType).add(e);
            }
        });

        // merge continuous datatype with mixed datatype
        List<AnnotatedClassWrapper<TestOfIndependence>> mergeList = Stream.concat(dataTypeNameWrappers.get(DataType.Continuous).stream(), dataTypeNameWrappers.get(DataType.Mixed).stream())
                .sorted()
                .collect(Collectors.toList());
        dataTypeNameWrappers.put(DataType.Continuous, mergeList);

        // merge discrete datatype with mixed datatype
        mergeList = Stream.concat(dataTypeNameWrappers.get(DataType.Discrete).stream(), dataTypeNameWrappers.get(DataType.Mixed).stream())
                .sorted()
                .collect(Collectors.toList());
        dataTypeNameWrappers.put(DataType.Discrete, mergeList);
    }

    public static TetradTestOfIndependenceAnnotations getInstance() {
        return INSTANCE;
    }

    public List<AnnotatedClassWrapper<TestOfIndependence>> getNameWrappers() {
        return Collections.unmodifiableList(nameWrappers);
    }

    public List<AnnotatedClassWrapper<TestOfIndependence>> getNameWrappers(DataType dataType) {
        if (dataType == null) {
            return Collections.EMPTY_LIST;
        }

        return dataTypeNameWrappers.containsKey(dataType)
                ? Collections.unmodifiableList(dataTypeNameWrappers.get(dataType))
                : Collections.EMPTY_LIST;
    }

    public Map<DataType, List<AnnotatedClassWrapper<TestOfIndependence>>> getDataTypeNameWrappers() {
        return Collections.unmodifiableMap(dataTypeNameWrappers);
    }

    public AnnotatedClassWrapper<TestOfIndependence> getDefaultNameWrapper(DataType dataType) {
        List<AnnotatedClassWrapper<TestOfIndependence>> list = getNameWrappers(dataType);
        if (list.isEmpty()) {
            return null;
        }

        String property;
        switch (dataType) {
            case Continuous:
                property = "datatype.continuous.test.default";
                break;
            case Discrete:
                property = "datatype.discrete.test.default";
                break;
            case Mixed:
                property = "datatype.mixed.test.default";
                break;
            default:
                property = null;
        }

        String value = TetradProperties.getInstance().getValue(property);
        if (value == null) {
            return null;
        }

        Optional<AnnotatedClassWrapper<TestOfIndependence>> result = list.stream()
                .filter(e -> e.getAnnotatedClass().getClazz().getName().equals(value))
                .findFirst();

        return result.isPresent() ? result.get() : null;
    }

}
