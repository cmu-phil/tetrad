///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.ui.model;

import edu.cmu.tetrad.annotation.AnnotatedClass;
import edu.cmu.tetrad.annotation.Score;
import edu.cmu.tetrad.util.ScoreDescriptions;

import java.io.Serial;
import java.io.Serializable;

/**
 * Dec 1, 2017 11:37:56 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class ScoreModel implements Serializable, Comparable<ScoreModel> {

    @Serial
    private static final long serialVersionUID = 2755370016466432455L;

    /**
     * The annotated class of the score.
     */
    private final AnnotatedClass<Score> score;

    /**
     * The name and description of the score.
     */
    private final String name;

    /**
     * The description of the score.
     */
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

    /**
     * Compares this ScoreModel object with the specified ScoreModel object for order. Returns a negative integer, zero,
     * or a positive integer as this ScoreModel object is less than, equal to, or greater than the specified object.
     *
     * @param other the object to be compared.
     * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than
     * the specified object.
     */
    @Override
    public int compareTo(ScoreModel other) {
        return this.score.annotation().name().compareTo(other.score.annotation().name());
    }

    /**
     * Returns a string representation of the ScoreModel.
     *
     * @return the name of the score.
     */
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

