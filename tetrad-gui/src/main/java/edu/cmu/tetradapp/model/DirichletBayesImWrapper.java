///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.DirichletBayesIm;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.List;

/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class DirichletBayesImWrapper implements KnowledgeBoxInput {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The Dirichlet Bayes IM.
     */
    private final DirichletBayesIm dirichletBayesIm;

    /**
     * The name of the model.
     */
    private String name;

    //===========================CONSTRUCTORS=============================//

    /**
     * <p>Constructor for DirichletBayesImWrapper.</p>
     *
     * @param simulation a {@link edu.cmu.tetradapp.model.Simulation} object
     */
    public DirichletBayesImWrapper(Simulation simulation) {
        throw new NullPointerException("Sorry, that was not a Dirichlet Bayes IM simulation.");
    }

    /**
     * <p>Constructor for DirichletBayesImWrapper.</p>
     *
     * @param bayesPmWrapper a {@link edu.cmu.tetradapp.model.BayesPmWrapper} object
     * @param params         a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public DirichletBayesImWrapper(BayesPmWrapper bayesPmWrapper,
                                   Parameters params) {
        if (bayesPmWrapper == null) {
            throw new NullPointerException("BayesPmWrapper must not be null.");
        }

        if (params == null) {
            throw new NullPointerException("Parameters must not be null.");
        }

        BayesPm bayesPm = new BayesPm(bayesPmWrapper.getBayesPm());

        if (params.getString("initializationMode", "manual").equals("manual")) {
            this.dirichletBayesIm = DirichletBayesIm.blankDirichletIm(bayesPm);
        } else if (params.getString("initializationMode", "manual").equals("symmetricPrior")) {
            this.dirichletBayesIm = DirichletBayesIm.symmetricDirichletIm(
                    bayesPm, params.getDouble("symmetricAlpha", 1.0));
        } else {
            throw new IllegalStateException("Expecting 'manual' or 'symmetricPrior");
        }

        log(this.dirichletBayesIm);

    }

    /**
     * <p>Constructor for DirichletBayesImWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DirichletEstimatorWrapper} object
     */
    public DirichletBayesImWrapper(DirichletEstimatorWrapper wrapper) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        this.dirichletBayesIm = wrapper.getEstimatedBayesIm();
        log(this.dirichletBayesIm);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.DirichletBayesImWrapper} object
     * @see TetradSerializableUtils
     */
    public static DirichletBayesImWrapper serializableInstance() {
        return new DirichletBayesImWrapper(
                BayesPmWrapper.serializableInstance(),
                new Parameters());
    }

    //================================PUBLIC METHODS=======================//

    /**
     * <p>Getter for the field <code>dirichletBayesIm</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.bayes.DirichletBayesIm} object
     */
    public DirichletBayesIm getDirichletBayesIm() {
        return this.dirichletBayesIm;
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to serialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to deserialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return this.dirichletBayesIm.getBayesPm().getDag();
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
     * {@inheritDoc}
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * <p>getSourceGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getSourceGraph() {
        return getGraph();
    }

    /**
     * <p>getResultGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getResultGraph() {
        return getGraph();
    }

    /**
     * <p>getVariableNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getVariableNames() {
        return getGraph().getNodeNames();
    }

    /**
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getVariables() {
        return getGraph().getNodes();
    }

    private void log(DirichletBayesIm im) {
        TetradLogger.getInstance().forceLogMessage("Dirichlet Bayes IM");
        String message = im.toString();
        TetradLogger.getInstance().forceLogMessage(message);
    }
}
