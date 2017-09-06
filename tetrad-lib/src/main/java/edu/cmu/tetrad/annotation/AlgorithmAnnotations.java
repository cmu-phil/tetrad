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

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * Sep 6, 2017 11:23:17 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class AlgorithmAnnotations extends AbstractAnnotations<Algorithm> {

    private static final AlgorithmAnnotations INSTANCE = new AlgorithmAnnotations();

    private AlgorithmAnnotations() {
        String packageName = "edu.cmu.tetrad.algcomparison.algorithm";
        List<AnnotatedClass> list = AnnotatedClassUtils.getAnnotatedClasses(packageName, Algorithm.class);
        list.stream().forEach(e -> {
            Algorithm annotation = (Algorithm) e.getAnnotation();
            annoClassByName.put(annotation.name(), e);
            nameByCommand.put(annotation.command(), annotation.name());
        });
    }

    public static AlgorithmAnnotations getInstance() {
        return INSTANCE;
    }

    public List<String> getAcceptKnowledge() {
        List<String> list = annoClassByName.entrySet().stream()
                .filter(e -> HasKnowledge.class.isAssignableFrom(e.getValue().getClazz()))
                .map(e -> ((Algorithm) e.getValue().getAnnotation()).name())
                .collect(Collectors.toList());

        return Collections.unmodifiableList(list);
    }

    public List<String> getAcceptMultiDataset() {
        List<String> list = annoClassByName.entrySet().stream()
                .filter(e -> MultiDataSetAlgorithm.class.isAssignableFrom(e.getValue().getClazz()))
                .map(e -> ((Algorithm) e.getValue().getAnnotation()).name())
                .collect(Collectors.toList());

        return Collections.unmodifiableList(list);
    }

}
