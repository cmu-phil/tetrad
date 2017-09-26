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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * Sep 20, 2017 12:01:12 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TetradAlgorithmAnnotations {

    private static final TetradAlgorithmAnnotations INSTANCE = new TetradAlgorithmAnnotations();

    protected final List<AnnotatedClassWrapper<Algorithm>> nameWrappers;
    protected final List<AnnotatedClassWrapper<Algorithm>> acceptKnowledgeNameWrappers;
    protected final List<AnnotatedClassWrapper<Algorithm>> acceptMultiDatasetNameWrappers;

    private TetradAlgorithmAnnotations() {

        nameWrappers = AlgorithmAnnotations.getInstance().getAnnotatedClasses().stream()
                .map(e -> new AnnotatedClassWrapper<>(e.getAnnotation().name(), e))
                .sorted()
                .collect(Collectors.toList());
        acceptKnowledgeNameWrappers = nameWrappers.stream()
                .filter(e -> acceptKnowledge(e.annotatedClass.getClazz()))
                .collect(Collectors.toList());
        acceptMultiDatasetNameWrappers = nameWrappers.stream()
                .filter(e -> acceptMultipleDataset(e.annotatedClass.getClazz()))
                .collect(Collectors.toList());
    }

    public boolean acceptMultipleDataset(Class clazz) {
        return AlgorithmAnnotations.getInstance().acceptMultipleDataset(clazz);
    }

    public boolean acceptKnowledge(Class clazz) {
        return AlgorithmAnnotations.getInstance().acceptKnowledge(clazz);
    }

    public boolean requireIndependenceTest(Class clazz) {
        return AlgorithmAnnotations.getInstance().requireIndependenceTest(clazz);
    }

    public boolean requireScore(Class clazz) {
        return AlgorithmAnnotations.getInstance().requireScore(clazz);
    }

    public static TetradAlgorithmAnnotations getInstance() {
        return INSTANCE;
    }

    public List<AnnotatedClassWrapper<Algorithm>> getNameWrappers() {
        return Collections.unmodifiableList(nameWrappers);
    }

    public List<AnnotatedClassWrapper<Algorithm>> getAcceptKnowledgeNameWrappers() {
        return Collections.unmodifiableList(acceptKnowledgeNameWrappers);
    }

    public List<AnnotatedClassWrapper<Algorithm>> getAcceptMultipleDatasetNameWrappers() {
        return Collections.unmodifiableList(acceptMultiDatasetNameWrappers);
    }

}
