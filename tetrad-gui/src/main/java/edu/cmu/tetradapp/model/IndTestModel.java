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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Stores a list of independence facts.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class IndTestModel implements SessionModel {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test producers.
     */
    private final List<IndTestProducer> indTestProducers;

    /**
     * The name of the model.
     */
    private String name = "";

    /**
     * The variables.
     */
    private LinkedList<String> vars = new LinkedList<>();

    /**
     * The results.
     */
    private List<List<IndependenceResultIndFacts>> results;

    /**
     * <p>Constructor for IndTestModel.</p>
     *
     * @param producers  an array of {@link edu.cmu.tetradapp.model.IndTestProducer} objects
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public IndTestModel(IndTestProducer[] producers, Parameters parameters) {
        this.indTestProducers = new ArrayList<>();

        this.indTestProducers.addAll(Arrays.asList(producers));
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     * @see TetradSerializableUtils
     */
    public static Knowledge serializableInstance() {
        return new Knowledge();
    }

    /**
     * <p>Getter for the field <code>indTestProducers</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<IndTestProducer> getIndTestProducers() {
        return this.indTestProducers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * <p>Getter for the field <code>vars</code>.</p>
     *
     * @return a {@link java.util.LinkedList} object
     */
    public LinkedList<String> getVars() {
        return this.vars;
    }

    /**
     * <p>Setter for the field <code>vars</code>.</p>
     *
     * @param vars a {@link java.util.LinkedList} object
     */
    public void setVars(LinkedList<String> vars) {
        this.vars = vars;
    }

    /**
     * <p>Getter for the field <code>results</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<List<IndependenceResultIndFacts>> getResults() {
        return this.results;
    }

    /**
     * <p>Setter for the field <code>results</code>.</p>
     *
     * @param results a {@link java.util.List} object
     */
    public void setResults(List<List<IndependenceResultIndFacts>> results) {
        this.results = results;
    }
}




