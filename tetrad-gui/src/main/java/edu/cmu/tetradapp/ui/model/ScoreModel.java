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
import edu.cmu.tetrad.util.ScoreDescriptions;

import java.io.Serializable;

/**
 * Dec 1, 2017 11:37:56 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class ScoreModel implements Serializable, Comparable<ScoreModel> {

    private static final long serialVersionUID = 2755370016466432455L;

    private final AnnotatedClass<Score> score;
    private final String name;
    private final String description;

    /**
     * <p>Constructor for ScoreModel.</p>
     *
     * @param score a {@link edu.cmu.tetrad.annotation.AnnotatedClass} object
     */
    public ScoreModel(AnnotatedClass<Score> score) {
        this.score = score;
        this.name = score.annotation().name();
        this.description = ScoreDescriptions.getInstance().get(score.annotation().command());
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(ScoreModel other) {
        return this.score.annotation().name().compareTo(other.score.annotation().name());
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return this.name;
    }

    /**
     * <p>Getter for the field <code>score</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.annotation.AnnotatedClass} object
     */
    public AnnotatedClass<Score> getScore() {
        return this.score;
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /**
     * <p>Getter for the field <code>description</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getDescription() {
        return this.description;
    }

}
