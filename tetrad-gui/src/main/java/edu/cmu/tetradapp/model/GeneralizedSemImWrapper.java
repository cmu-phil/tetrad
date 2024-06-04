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

import edu.cmu.tetrad.algcomparison.simulation.GeneralSemSimulation;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GeneralizedSemImWrapper implements KnowledgeBoxInput {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The name of the model.
     */
    private String name;

    /**
     * The wrapped SemPm.
     */
    private List<GeneralizedSemIm> semIms = new ArrayList<>();

    /**
     * True just in case errors should be shown in the interface.
     */
    private boolean showErrors;

    //==============================CONSTRUCTORS==========================//

    /**
     * <p>Constructor for GeneralizedSemImWrapper.</p>
     *
     * @param simulation a {@link edu.cmu.tetradapp.model.Simulation} object
     */
    public GeneralizedSemImWrapper(Simulation simulation) {
        List<GeneralizedSemIm> semIms = new ArrayList<>();

        if (simulation == null) {
            throw new NullPointerException("The Simulation box does not contain a simulation.");
        }

        edu.cmu.tetrad.algcomparison.simulation.Simulation _simulation = simulation.getSimulation();

        if (_simulation == null) {
            throw new NullPointerException("No data sets have been simulated.");
        }

        if (!(_simulation instanceof GeneralSemSimulation)) {
            throw new IllegalArgumentException("That was not Generalized SEM simulation. Sorry.");
        }

        semIms = ((GeneralSemSimulation) _simulation).getIms();

        if (semIms.isEmpty()) {
            throw new NullPointerException("It looks like you have not done a simulation.");
        }

        this.semIms = semIms;

        if (semIms.size() > 1) {
            throw new IllegalArgumentException("I'm sorry; this editor can only edit a single generalized SEM IM.");
        }
    }

    private GeneralizedSemImWrapper(GeneralizedSemPm semPm) {
        if (semPm == null) {
            throw new NullPointerException("SEM PM must not be null.");
        }

        this.semIms.add(new GeneralizedSemIm(semPm));
    }

    /**
     * Creates a new BayesPm from the given workbench and uses it to construct a new BayesPm.
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.GeneralizedSemPmWrapper} object
     */
    public GeneralizedSemImWrapper(GeneralizedSemPmWrapper wrapper) {
        this(wrapper.getSemPm());
    }

    /**
     * <p>Constructor for GeneralizedSemImWrapper.</p>
     *
     * @param genSemPm  a {@link edu.cmu.tetradapp.model.GeneralizedSemPmWrapper} object
     * @param imWrapper a {@link edu.cmu.tetradapp.model.SemImWrapper} object
     */
    public GeneralizedSemImWrapper(GeneralizedSemPmWrapper genSemPm, SemImWrapper imWrapper) {
        this.semIms.add(new GeneralizedSemIm(genSemPm.getSemPm(), imWrapper.getSemIm()));
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.GeneralizedSemImWrapper} object
     * @see TetradSerializableUtils
     */
    public static GeneralizedSemImWrapper serializableInstance() {
        return new GeneralizedSemImWrapper(GeneralizedSemPmWrapper.serializableInstance());
    }

    //============================PUBLIC METHODS=========================//

    /**
     * <p>Getter for the field <code>semIms</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<GeneralizedSemIm> getSemIms() {
        return this.semIms;
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
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
        return this.semIms.get(0).getSemPm().getGraph();
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
     * <p>isShowErrors.</p>
     *
     * @return a boolean
     */
    public boolean isShowErrors() {
        return this.showErrors;
    }

    /**
     * <p>Setter for the field <code>showErrors</code>.</p>
     *
     * @param showErrors a boolean
     */
    public void setShowErrors(boolean showErrors) {
        this.showErrors = showErrors;
    }

    //======================= Private methods ====================//
    private void log(GeneralizedSemIm im) {
        TetradLogger.getInstance().log("Generalized SEM IM");
        String message = im.toString();
        TetradLogger.getInstance().log(message);
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

}
