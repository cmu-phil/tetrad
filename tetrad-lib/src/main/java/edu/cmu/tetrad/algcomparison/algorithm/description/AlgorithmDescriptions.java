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
package edu.cmu.tetrad.algcomparison.algorithm.description;

import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgorithmDescription;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.reflections.Reflections;

/**
 * This class handles all the algorithm classes that is annotated with
 * AlgorithmDescription.
 *
 * Aug 28, 2017 2:41:23 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @author Jeremy Espino MD Created 6/30/17 11:20 AM
 */
public class AlgorithmDescriptions {

    private static final AlgorithmDescriptions INSTANCE = new AlgorithmDescriptions();

    private final Map<String, AlgorithmDescriptionClass> algoDescClasses = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    private AlgorithmDescriptions() {
        Reflections reflections = new Reflections("edu.cmu.tetrad.algcomparison");
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(AlgorithmDescription.class);
        classes.forEach(clazz -> {
            AlgorithmDescription algoDesc = clazz.getAnnotation(AlgorithmDescription.class);

            String key = algoDesc.name();
            boolean acceptKnowledge = HasKnowledge.class.isAssignableFrom(clazz);
            algoDescClasses.put(key, new AlgorithmDescriptionClass(clazz, algoDesc, acceptKnowledge));
        });
    }

    public AlgorithmDescriptionClass get(String name) {
        return algoDescClasses.get(name);
    }

    /**
     * List all the names of algorithm.
     *
     * @return unmodifiable list of algorithm names
     */
    public List<String> getNames() {
        List<String> list = algoDescClasses.keySet().stream()
                .collect(Collectors.toList());

        return Collections.unmodifiableList(list);
    }

    /**
     * List the names of the algorithm that can accept knowledge.
     *
     * @return unmodifiable list of algorithm names
     */
    public List<String> getAcceptKnowledgeAlgorithms() {
        List<String> list = algoDescClasses.entrySet().stream() // get stream of entries
                .filter(e -> e.getValue().isAcceptKnowledge()) // get entry that only accepts knowledge
                .map(e -> e.getValue().getAlgorithmDescription().name()) // extract the name of that entry
                .collect(Collectors.toList()); // collect all the names

        return Collections.unmodifiableList(list);
    }

    public static AlgorithmDescriptions getInstance() {
        return INSTANCE;
    }

}
