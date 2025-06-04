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

import edu.cmu.tetrad.annotation.AnnotatedClass;
import edu.cmu.tetrad.annotation.Score;
import edu.cmu.tetrad.annotation.ScoreAnnotations;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.util.TetradProperties;
import edu.cmu.tetradapp.Tetrad;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Dec 1, 2017 11:41:53 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public final class ScoreModels {

    private static final ScoreModels INSTANCE = new ScoreModels();

    private final List<ScoreModel> models;
    private final Map<DataType, List<ScoreModel>> modelMap = new EnumMap<>(DataType.class);
    private final Map<DataType, ScoreModel> defaultModelMap = new EnumMap<>(DataType.class);

    private ScoreModels() {
        ScoreAnnotations scoreAnno = ScoreAnnotations.getInstance();
        List<AnnotatedClass<Score>> list = Tetrad.enableExperimental
                ? scoreAnno.getAnnotatedClasses()
                : scoreAnno.filterOutExperimental(scoreAnno.getAnnotatedClasses());

        this.models = Collections.unmodifiableList(
                list.stream()
                        .map(ScoreModel::new)
                        .sorted()
                        .collect(Collectors.toList()));

        initModelMap();
        initDefaultModelMap();
    }

    /**
     * <p>getInstance.</p>
     *
     * @return a {@link edu.cmu.tetradapp.ui.model.ScoreModels} object
     */
    public static ScoreModels getInstance() {
        return ScoreModels.INSTANCE;
    }

    private void initModelMap() {
        // initialize enum map
        DataType[] dataTypes = DataType.values();
        for (DataType dataType : dataTypes) {
            this.modelMap.put(dataType, new LinkedList<>());
        }

        // group by datatype
        this.models.forEach(e -> {
            DataType[] types = e.getScore().annotation().dataType();
            for (DataType dataType : types) {
                this.modelMap.get(dataType).add(e);
            }
        });

        // merge continuous datatype with mixed datatype
        List<ScoreModel> mergedModels = Stream.concat(this.modelMap.get(DataType.Continuous).stream(), this.modelMap.get(DataType.Mixed).stream())
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
            List<ScoreModel> list = getModels(dataType);
            if (!list.isEmpty()) {
                String property = getProperty(dataType);
                if (property == null) {
                    this.defaultModelMap.put(dataType, list.getFirst());
                } else {
                    String value = TetradProperties.getInstance().getValue(property);
                    if (value == null) {
                        this.defaultModelMap.put(dataType, list.getFirst());
                    } else {
                        Optional<ScoreModel> result = list.stream()
                                .filter(e -> e.getScore().clazz().getName().equals(value))
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
                return "datatype.continuous.score.default";
            case Discrete:
                return "datatype.discrete.score.default";
            case Mixed:
                return "datatype.mixed.score.default";
            default:
                return null;
        }
    }

    /**
     * <p>Getter for the field <code>models</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<ScoreModel> getModels() {
        return this.models;
    }

    /**
     * <p>Getter for the field <code>models</code>.</p>
     *
     * @param dataType a {@link edu.cmu.tetrad.data.DataType} object
     * @return a {@link java.util.List} object
     */
    public List<ScoreModel> getModels(DataType dataType) {
        return this.modelMap.getOrDefault(dataType, Collections.EMPTY_LIST);
    }

    /**
     * <p>getDefaultModel.</p>
     *
     * @param dataType a {@link edu.cmu.tetrad.data.DataType} object
     * @return a {@link edu.cmu.tetradapp.ui.model.ScoreModel} object
     */
    public ScoreModel getDefaultModel(DataType dataType) {
        return this.defaultModelMap.get(dataType);
    }

}
