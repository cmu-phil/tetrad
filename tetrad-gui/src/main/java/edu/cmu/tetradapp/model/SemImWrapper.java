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

import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.rmi.MarshalledObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author Joseph Ramsey
 */
public class SemImWrapper implements SessionModel {

    static final long serialVersionUID = 23L;
    private List<SemIm> semIms;

    /**
     * @serial Can be null.
     */
    private String name;

    private int numModels = 1;
    private int modelIndex = 0;
    private String modelSourceName = null;

    //============================CONSTRUCTORS==========================//
    public SemImWrapper(SemIm semIm) {
        setSemIm(semIm);
    }

    public SemImWrapper(SemEstimatorWrapper semEstWrapper) {
        if (semEstWrapper == null) {
            throw new NullPointerException();
        }

        SemIm oldSemIm = semEstWrapper.getSemEstimator().getEstimatedSem();

        try {
            setSemIm((SemIm) new MarshalledObject(oldSemIm).get());
        } catch (Exception e) {
            throw new RuntimeException("SemIm could not be deep cloned.", e);
        }
    }

    public SemImWrapper(Simulation simulation) {
        if (simulation == null) {
            throw new NullPointerException("The Simulation box does not contain a simulation.");
        }

        edu.cmu.tetrad.algcomparison.simulation.Simulation _simulation = simulation.getSimulation();

        if (_simulation == null) {
            throw new NullPointerException("No data sets have been simulated.");
        }

        if (_simulation instanceof LinearFisherModel) {
            throw new IllegalArgumentException("Large SEM simulations cannot be represented "
                    + "using a SEM PM or IM box, sorry.");
        }

        if (!(_simulation instanceof SemSimulation)) {
            throw new IllegalArgumentException("That was not a linear, Gaussian SEM simulation. Sorry.");
        }

        semIms = ((SemSimulation) _simulation).getSemIms();

        if (semIms == null) {
            throw new NullPointerException("It looks like you have not done a simulation.");
        }

        this.numModels = simulation.getDataModelList().size();
        this.modelIndex = 0;
        this.modelSourceName = simulation.getName();
    }

    public SemImWrapper(SemPmWrapper semPmWrapper, Parameters params) {
        if (semPmWrapper == null) {
            throw new NullPointerException("SemPmWrapper must not be null.");
        }

        setSemIm(new SemIm(semPmWrapper.getSemPms().get(semPmWrapper.getModelIndex()), params));
    }

    public SemImWrapper(SemPmWrapper semPmWrapper, SemImWrapper oldSemImWrapper,
            Parameters params) {
        if (semPmWrapper == null) {
            throw new NullPointerException("SemPmWrapper must not be null.");
        }

        if (params == null) {
            throw new NullPointerException("Parameters must not be null.");
        }

        SemPm semPm = new SemPm(semPmWrapper.getSemPm());
        SemIm oldSemIm = oldSemImWrapper.getSemIm();

        if (!params.getBoolean("retainPreviousValues", false)) {
            setSemIm(new SemIm(semPm, params));
        } else {
            setSemIm(new SemIm(semPm, oldSemIm, params));
        }
    }

    public SemImWrapper(SemUpdaterWrapper semUpdaterWrapper) {
        if (semUpdaterWrapper == null) {
            throw new NullPointerException("SemPmWrapper must not be null.");
        }

        setSemIm(semUpdaterWrapper.getSemUpdater().getUpdatedSemIm());
    }

    private void setSemIm(SemIm updatedSemIm) {
        semIms = new ArrayList<>();
        semIms.add(new SemIm(updatedSemIm));

        for (int i = 0; i < semIms.size(); i++) {
            log(i, semIms.get(i));
        }
    }

    public SemImWrapper(SemImWrapper semImWrapper) {
        if (semImWrapper == null) {
            throw new NullPointerException("SemPmWrapper must not be null.");
        }

        setSemIm(semImWrapper.getSemIm());
    }

    public SemImWrapper(PValueImproverWrapper wrapper) {
        SemIm oldSemIm = wrapper.getNewSemIm();
        setSemIm(oldSemIm);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
//        return new SemImWrapper(SemPmWrapper.serializableInstance(),
//                new SemImWrapper(SemPmWrapper.serializableInstance(),
//                        new Parameters()), new Parameters());
    }

    //===========================PUBLIC METHODS=========================//
    public SemIm getSemIm() {
        return this.semIms.get(getModelIndex());
    }

    public Graph getGraph() {
        return getSemIm().getSemPm().getGraph();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    //======================== Private methods =======================//
    private void log(int i, SemIm pm) {
        TetradLogger.getInstance().log("info", "Linear SEM IM");
        TetradLogger.getInstance().log("info", "IM # " + (i + 1));
        TetradLogger.getInstance().log("im", pm.toString());
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

    public int getNumModels() {
        return numModels;
    }

    public int getModelIndex() {
        return modelIndex;
    }

    public String getModelSourceName() {
        return modelSourceName;
    }

    public void setModelIndex(int modelIndex) {
        this.modelIndex = modelIndex;
    }
}
