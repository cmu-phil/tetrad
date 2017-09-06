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

import java.util.List;

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

}
