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
import java.io.Serializable;

/**
 *
 * Dec 1, 2017 11:37:56 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class ScoreModel implements Serializable, Comparable<ScoreModel> {

    private static final long serialVersionUID = 2755370016466432455L;

    private final AnnotatedClass<Score> score;

    public ScoreModel(AnnotatedClass<Score> score) {
        this.score = score;
    }

    @Override
    public int compareTo(ScoreModel other) {
        return score.getAnnotation().name().compareTo(other.score.getAnnotation().name());
    }

    @Override
    public String toString() {
        return score.getAnnotation().name();
    }

    public AnnotatedClass<Score> getScore() {
        return score;
    }

}
