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
package edu.cmu.tetrad.algcomparison.independence.description;

import edu.cmu.tetrad.annotation.IndTestDescription;
import edu.cmu.tetrad.data.DataType;
import static edu.cmu.tetrad.data.DataType.Continuous;
import static edu.cmu.tetrad.data.DataType.Discrete;
import static edu.cmu.tetrad.data.DataType.Mixed;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.reflections.Reflections;

/**
 *
 * Sep 1, 2017 3:02:38 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class IndTestDescriptions {

    private static final IndTestDescriptions INSTANCE = new IndTestDescriptions();

    private final Map<String, IndTestDescriptionClass> descClasses = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    private IndTestDescriptions() {
        Reflections reflections = new Reflections("edu.cmu.tetrad.algcomparison.score");
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(IndTestDescription.class);
        classes.forEach(clazz -> {
            IndTestDescription scoreDesc = clazz.getAnnotation(IndTestDescription.class);

            String key = scoreDesc.name();
            descClasses.put(key, new IndTestDescriptionClass(clazz, scoreDesc));
        });
    }

    /**
     * Get by name.
     *
     * @param name annotated score name
     * @return a IndTestDescriptionClass
     */
    public IndTestDescriptionClass get(String name) {
        return descClasses.get(name);
    }

    /**
     * Get a list of IndTestDescriptionClass based on a specific dataType.
     *
     * @param dataType annotated score data type
     * @return list of IndTestDescriptionClasses
     */
    public List<IndTestDescriptionClass> get(DataType dataType) {
        List<IndTestDescriptionClass> list = descClasses.entrySet().stream()
                .filter(e -> e.getValue().getIndTestDescription().dataType().equals(dataType))
                .map(e -> e.getValue())
                .collect(Collectors.toList());

        return Collections.unmodifiableList(list);
    }

    /**
     * Get a list of IndTestDescriptionClass related to a given dataType.
     *
     * @param dataType annotated score data type
     * @return list of IndTestDescriptionClasses
     */
    public List<IndTestDescriptionClass> getAll(DataType dataType) {
        List<IndTestDescriptionClass> list = new LinkedList<>();

        switch (dataType) {
            case Discrete:
                get(DataType.Discrete).stream().collect(Collectors.toCollection(() -> list));
                get(DataType.Mixed).stream().collect(Collectors.toCollection(() -> list));
                break;
            case Continuous:
                get(DataType.Continuous).stream().collect(Collectors.toCollection(() -> list));
                get(DataType.Mixed).stream().collect(Collectors.toCollection(() -> list));
                break;
            case Mixed:
                get(DataType.Mixed).stream().collect(Collectors.toCollection(() -> list));
                break;
            case Graph:
                get(DataType.Graph).stream().collect(Collectors.toCollection(() -> list));
                break;
        }

        return Collections.unmodifiableList(list);
    }

    /**
     * List all the names of algorithm.
     *
     * @return unmodifiable list of algorithm names
     */
    public List<String> getNames() {
        List<String> list = descClasses.keySet().stream()
                .collect(Collectors.toList());

        return Collections.unmodifiableList(list);
    }

    /**
     * Get a list of descriptions based on a specific dataType.
     *
     * @param dataType
     * @return
     */
    public List<String> getDescriptions(DataType dataType) {
        List<String> list = new LinkedList<>();

        get(dataType).stream()
                .map(e -> e.getIndTestDescription().description())
                .collect(Collectors.toCollection(() -> list));

        return Collections.unmodifiableList(list);
    }

    /**
     * Get a list of descriptions related to a given dataType.
     *
     * @param dataType
     * @return
     */
    public List<String> getAllDescriptions(DataType dataType) {
        List<String> list = new LinkedList<>();

        get(dataType).stream()
                .map(e -> e.getIndTestDescription().description())
                .collect(Collectors.toCollection(() -> list));

        return Collections.unmodifiableList(list);
    }

    public static IndTestDescriptions getInstance() {
        return INSTANCE;
    }

}
