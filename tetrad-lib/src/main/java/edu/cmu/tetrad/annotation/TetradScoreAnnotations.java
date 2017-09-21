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
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 * Sep 20, 2017 12:32:15 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TetradScoreAnnotations extends AbstractTetradAnnotations<Score> {

    private static final TetradScoreAnnotations INSTANCE = new TetradScoreAnnotations();

    protected final List<AnnotatedClassWrapper<Score>> nameWrappers = new LinkedList<>();
    protected final Map<DataType, List<AnnotatedClassWrapper<Score>>> dataTypeNameWrappers = new EnumMap<>(DataType.class);

    private TetradScoreAnnotations() {
        super("edu.cmu.tetrad.algcomparison.score", Score.class);

        annotatedClasses.stream()
                .map(e -> new AnnotatedClassWrapper<>(e.getAnnotation().name(), e))
                .collect(Collectors.toCollection(() -> nameWrappers));

        DataType[] dataTypes = DataType.values();
        for (DataType dataType : dataTypes) {
            dataTypeNameWrappers.put(dataType, new LinkedList<>());
        }
        nameWrappers.forEach(e -> {
            dataTypeNameWrappers.get(e.annotatedClass.getAnnotation().dataType()).add(e);
        });
    }

    public static TetradScoreAnnotations getInstance() {
        return INSTANCE;
    }

    public List<AnnotatedClassWrapper<Score>> getNameWrappers() {
        return Collections.unmodifiableList(nameWrappers);
    }

    public List<AnnotatedClassWrapper<Score>> getNameAttributes(DataType dataType) {
        List<AnnotatedClassWrapper<Score>> list = new LinkedList<>();

        if (dataType != null) {
            list.addAll(dataTypeNameWrappers.get(dataType));
            if (dataType == DataType.Discrete || dataType == DataType.Continuous) {
                list.addAll(dataTypeNameWrappers.get(DataType.Mixed));
            }
        }

        return Collections.unmodifiableList(list);
    }

}
