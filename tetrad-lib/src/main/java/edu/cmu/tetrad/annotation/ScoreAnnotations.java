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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * Sep 6, 2017 1:54:03 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class ScoreAnnotations extends AbstractAnnotations<Score> {

    private static final ScoreAnnotations INSTANCE = new ScoreAnnotations();

    private ScoreAnnotations() {
        String packageName = "edu.cmu.tetrad.algcomparison.score";
        List<AnnotatedClass> list = AnnotatedClassUtils.getAnnotatedClasses(packageName, Score.class);
        list.stream().forEach(e -> {
            Score annotation = (Score) e.getAnnotation();
            annoClassByName.put(annotation.name(), e);
            nameByCommand.put(annotation.command(), annotation.name());
        });
    }

    public static ScoreAnnotations getInstance() {
        return INSTANCE;
    }

    /**
     * Get a list of annotation attribute names for a particular data type.
     *
     * @param dataType
     * @return annotation attribute names.
     */
    public List<String> getNames(DataType dataType) {
        List<String> list = annoClassByName.entrySet().stream()
                .filter(e -> e.getValue().getAnnotation().dataType().equals(dataType))
                .map(e -> e.getValue().getAnnotation().name())
                .collect(Collectors.toList());

        return Collections.unmodifiableList(list);
    }

    /**
     * Get a list of annotation attribute names for a class associated with data
     * type.
     *
     * @param dataType
     * @return annotation attribute names.
     */
    public List<String> getNamesAssociatedWith(DataType dataType) {
        List<String> list = new LinkedList<>();

        switch (dataType) {
            case Discrete:
                getNames(DataType.Discrete).stream().collect(Collectors.toCollection(() -> list));
                getNames(DataType.Mixed).stream().collect(Collectors.toCollection(() -> list));
                break;
            case Continuous:
                getNames(DataType.Continuous).stream().collect(Collectors.toCollection(() -> list));
                getNames(DataType.Mixed).stream().collect(Collectors.toCollection(() -> list));
                break;
            case Mixed:
                getNames(DataType.Mixed).stream().collect(Collectors.toCollection(() -> list));
                break;
            case Graph:
                getNames(DataType.Graph).stream().collect(Collectors.toCollection(() -> list));
                break;
        }

        return Collections.unmodifiableList(list);
    }

}
