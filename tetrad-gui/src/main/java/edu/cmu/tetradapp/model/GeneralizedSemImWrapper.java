///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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
import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author Joseph Ramsey
 */
public class GeneralizedSemImWrapper implements SessionModel, KnowledgeBoxInput {

    static final long serialVersionUID = 23L;

    /**
     * @serial Can be null.
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

        semIms.add(new GeneralizedSemIm(semPm));
    }

    /**
     * Creates a new BayesPm from the given workbench and uses it to construct a
     * new BayesPm.
     */
    public GeneralizedSemImWrapper(GeneralizedSemPmWrapper wrapper) {
        this(wrapper.getSemPm());
    }

    public GeneralizedSemImWrapper(GeneralizedSemPmWrapper genSemPm, SemImWrapper imWrapper) {
        semIms.add(new GeneralizedSemIm(genSemPm.getSemPm(), imWrapper.getSemIm()));
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static GeneralizedSemImWrapper serializableInstance() {
        return new GeneralizedSemImWrapper(GeneralizedSemPmWrapper.serializableInstance());
    }

    //============================PUBLIC METHODS=========================//
    public List<GeneralizedSemIm> getSemIms() {
        return this.semIms;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (semIms == null) {
            throw new NullPointerException();
        }
    }

    public Graph getGraph() {
        return semIms.get(0).getSemPm().getGraph();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isShowErrors() {
        return showErrors;
    }

    public void setShowErrors(boolean showErrors) {
        this.showErrors = showErrors;
    }

    //======================= Private methods ====================//
    private void log(GeneralizedSemIm im) {
        TetradLogger.getInstance().log("info", "Generalized SEM IM");
        TetradLogger.getInstance().log("im", im.toString());
    }

    public Graph getSourceGraph() {
        return getGraph();
    }

    public Graph getResultGraph() {
        return getGraph();
    }

    public List<String> getVariableNames() {
        return getGraph().getNodeNames();
    }

    public List<Node> getVariables() {
        return getGraph().getNodes();
    }

}
