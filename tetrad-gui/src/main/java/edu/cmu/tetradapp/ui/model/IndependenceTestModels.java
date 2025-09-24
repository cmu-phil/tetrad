///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.ui.model;

import edu.cmu.tetrad.annotation.AnnotatedClass;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.annotation.TestOfIndependenceAnnotations;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.util.TetradProperties;
import edu.cmu.tetradapp.Tetrad;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Dec 1, 2017 11:49:55 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public final class IndependenceTestModels {

    private static final IndependenceTestModels INSTANCE = new IndependenceTestModels();

    private final List<IndependenceTestModel> models;
    private final Map<DataType, List<IndependenceTestModel>> modelMap = new EnumMap<>(DataType.class);
    private final Map<DataType, IndependenceTestModel> defaultModelMap = new EnumMap<>(DataType.class);

    private IndependenceTestModels() {
        TestOfIndependenceAnnotations indTestAnno = TestOfIndependenceAnnotations.getInstance();
        List<AnnotatedClass<TestOfIndependence>> list = Tetrad.enableExperimental
                ? indTestAnno.getAnnotatedClasses()
                : indTestAnno.filterOutExperimental(indTestAnno.getAnnotatedClasses());
        this.models = Collections.unmodifiableList(
                list.stream()
                        .map(IndependenceTestModel::new)
                        .sorted()
                        .collect(Collectors.toList()));

        initModelMap();
        initDefaultModelMap();
    }

    /**
     * <p>getInstance.</p>
     *
     * @return a {@link edu.cmu.tetradapp.ui.model.IndependenceTestModels} object
     */
    public static IndependenceTestModels getInstance() {
        return IndependenceTestModels.INSTANCE;
    }

    private void initModelMap() {
        // initialize enum map
        DataType[] dataTypes = DataType.values();
        for (DataType dataType : dataTypes) {
            this.modelMap.put(dataType, new LinkedList<>());
        }

        // group by datatype
        this.models.forEach(e -> {
            DataType[] types = e.getIndependenceTest().annotation().dataType();
            for (DataType dataType : types) {
                this.modelMap.get(dataType).add(e);
            }
        });

        // merge continuous datatype with mixed datatype
        List<IndependenceTestModel> mergedModels = Stream.concat(this.modelMap.get(DataType.Continuous).stream(), this.modelMap.get(DataType.Mixed).stream())
                .sorted()
                .collect(Collectors.toList());
        this.modelMap.put(DataType.Continuous, mergedModels);

        // merge discrete datatype with mixed datatype
        mergedModels = Stream.concat(this.modelMap.get(DataType.Discrete).stream(), this.modelMap.get(DataType.Mixed).stream())
                .sorted()
                .collect(Collectors.toList());
        this.modelMap.put(DataType.Discrete, mergedModels);

        // make map values unmodifiable
        this.modelMap.forEach((k, v) -> this.modelMap.put(k, Collections.unmodifiableList(v)));
    }

    private void initDefaultModelMap() {
        DataType[] dataTypes = DataType.values();
        for (DataType dataType : dataTypes) {
            List<IndependenceTestModel> list = getModels(dataType);
            if (!list.isEmpty()) {
                String property = getProperty(dataType);
                if (property == null) {
                    this.defaultModelMap.put(dataType, list.getFirst());
                } else {
                    String value = TetradProperties.getInstance().getValue(property);
                    if (value == null) {
                        this.defaultModelMap.put(dataType, list.getFirst());
                    } else {
                        Optional<IndependenceTestModel> result = list.stream()
                                .filter(e -> e.getIndependenceTest().clazz().getName().equals(value))
                                .findFirst();
                        this.defaultModelMap.put(dataType, result.orElseGet(() -> list.getFirst()));
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
            case Blocks:
                return "datatype.blocks.test.default";
            default:
                return null;
        }
    }

    /**
     * <p>Getter for the field <code>models</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<IndependenceTestModel> getModels() {
        return this.models;
    }

    /**
     * <p>Getter for the field <code>models</code>.</p>
     *
     * @param dataType a {@link edu.cmu.tetrad.data.DataType} object
     * @return a {@link java.util.List} object
     */
    public List<IndependenceTestModel> getModels(DataType dataType) {
        return this.modelMap.getOrDefault(dataType, Collections.EMPTY_LIST);
    }

    /**
     * <p>getDefaultModel.</p>
     *
     * @param dataType a {@link edu.cmu.tetrad.data.DataType} object
     * @return a {@link edu.cmu.tetradapp.ui.model.IndependenceTestModel} object
     */
    public IndependenceTestModel getDefaultModel(DataType dataType) {
        return this.defaultModelMap.get(dataType);
    }

}

