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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.sem.TemplateExpander;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.text.ParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GeneralizedSemPmWrapper implements KnowledgeBoxInput {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The wrapped SemPm.
     */
    private final GeneralizedSemPm semPm;

    /**
     * The name of the model.
     */
    private String name;

    /**
     * True iff errors should be shown in teh editor.
     */
    private boolean showErrors;

    //==============================CONSTRUCTORS==========================//

    /**
     * <p>Constructor for GeneralizedSemPmWrapper.</p>
     *
     * @param simulation a {@link edu.cmu.tetradapp.model.Simulation} object
     */
    public GeneralizedSemPmWrapper(Simulation simulation) {
        GeneralizedSemPm semPm = null;

        if (simulation == null) {
            throw new NullPointerException("The Simulation box does not contain a simulation.");
        }

        edu.cmu.tetrad.algcomparison.simulation.Simulation _simulation = simulation.getSimulation();

        if (_simulation == null) {
            throw new NullPointerException("No data sets have been simulated.");
        }

        if (!(_simulation instanceof GeneralSemSimulation)) {
            throw new IllegalArgumentException("That was not a Generalized SEM simulation.");
        }

        List<GeneralizedSemIm> ims = ((GeneralSemSimulation) _simulation).getIms();

        if (ims == null || ims.size() == 0) {
            throw new NullPointerException("It looks like you have not done a simulation.");
        }

        semPm = ims.get(0).getGeneralizedSemPm();

        this.semPm = semPm;
    }

    /**
     * <p>Constructor for GeneralizedSemPmWrapper.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public GeneralizedSemPmWrapper(Graph graph) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        if (graph instanceof SemGraph) {
            this.semPm = new GeneralizedSemPm(graph);
        } else {
            try {
                this.semPm = new GeneralizedSemPm(new SemGraph(graph));
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        log(this.semPm);
    }

    /**
     * <p>Constructor for GeneralizedSemPmWrapper.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @param oldPm a {@link edu.cmu.tetrad.sem.GeneralizedSemPm} object
     */
    public GeneralizedSemPmWrapper(Graph graph, GeneralizedSemPm oldPm) {
        this(graph);

        // We can keep the old expression if the node exists in the old pm and all of the node expressions
        // in the old expression are parents of the node in the new pm.
        try {
            this.semPm.setVariablesTemplate(oldPm.getVariablesTemplate());
            this.semPm.setParametersTemplate(oldPm.getParametersTemplate());
            this.semPm.setErrorsTemplate(oldPm.getErrorsTemplate());

            for (Node node : this.semPm.getNodes()) {
                Set<String> parents = new HashSet<>();

                for (Node parent : this.semPm.getParents(node)) {
                    parents.add(parent.getName());
                }

                Node _node = oldPm.getNode(node.getName());

                Set<Node> oldReferencedNodes = oldPm.getReferencedNodes(_node);
                Set<String> oldReferencedNames = new HashSet<>();

                for (Node node2 : oldReferencedNodes) {
                    oldReferencedNames.add(node2.getName());
                }

                String template;

                if (this.semPm.getVariableNodes().contains(node)) {
                    template = this.semPm.getVariablesTemplate();
                } else {
                    template = this.semPm.getErrorsTemplate();
                }

                String newExpression = "";

                try {
                    newExpression = TemplateExpander.getInstance().expandTemplate(template, this.semPm, node);
                } catch (ParseException e) {
                    //
                }

                this.semPm.setNodeExpression(node, newExpression);

                if (_node == null || !parents.equals(oldReferencedNames)) {
                    this.semPm.setNodeExpression(node, newExpression);
                    setReferencedParameters(node, this.semPm, oldPm);
                } else {
                    try {
                        this.semPm.setNodeExpression(node, oldPm.getNodeExpressionString(node));
                    } catch (Exception e) {
                        this.semPm.setNodeExpression(node, newExpression);
                    }
                }
            }

            for (String startsWith : oldPm.startsWithPrefixes()) {
                try {
                    this.semPm.setStartsWithParametersTemplate(startsWith, oldPm.getStartsWithParameterTemplate(startsWith));
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            }

            for (String parameter : this.semPm.getParameters()) {
                boolean found = false;

                Set<String> prefixes = oldPm.startsWithPrefixes();

                for (String startsWith : prefixes) {
                    if (parameter.startsWith(startsWith)) {
                        this.semPm.setParameterExpression(parameter, oldPm.getStartsWithParameterTemplate(startsWith));
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    this.semPm.setParameterExpression(parameter, this.semPm.getParameterExpressionString(parameter));
                }
            }

        } catch (ParseException e) {
            throw new RuntimeException("Couldn't parse expression.", e);
        }
    }

    /**
     * <p>Constructor for GeneralizedSemPmWrapper.</p>
     *
     * @param pmWrapper a {@link edu.cmu.tetradapp.model.SemPmWrapper} object
     */
    public GeneralizedSemPmWrapper(SemPmWrapper pmWrapper) {
        if (pmWrapper == null) {
            throw new NullPointerException();
        }

        this.semPm = new GeneralizedSemPm(pmWrapper.getSemPm());
    }

    /**
     * Creates a new BayesPm from the given workbench and uses it to construct a new BayesPm.
     *
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.GraphSource} object
     */
    public GeneralizedSemPmWrapper(GraphSource graphWrapper) {
        this(new EdgeListGraph(graphWrapper.getGraph()));
    }

    /**
     * <p>Constructor for GeneralizedSemPmWrapper.</p>
     *
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param dataWrapper  a {@link edu.cmu.tetradapp.model.DataWrapper} object
     */
    public GeneralizedSemPmWrapper(GraphSource graphWrapper, DataWrapper dataWrapper) {
        this(new EdgeListGraph(graphWrapper.getGraph()));
    }

    /**
     * Creates a new BayesPm from the given workbench and uses it to construct a new BayesPm.
     *
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param wrapper      a {@link edu.cmu.tetradapp.model.GeneralizedSemPmWrapper} object
     */
    public GeneralizedSemPmWrapper(GraphSource graphWrapper, GeneralizedSemPmWrapper wrapper) {
        this(new EdgeListGraph(graphWrapper.getGraph()), wrapper.getSemPm());
    }

    /**
     * Creates a new BayesPm from the given workbench and uses it to construct a new BayesPm.
     *
     * @param dagWrapper a {@link edu.cmu.tetradapp.model.DagWrapper} object
     * @param wrapper    a {@link edu.cmu.tetradapp.model.GeneralizedSemPmWrapper} object
     */
    public GeneralizedSemPmWrapper(DagWrapper dagWrapper, GeneralizedSemPmWrapper wrapper) {
        this(new EdgeListGraph(dagWrapper.getDag()), wrapper.getSemPm());
    }

    /**
     * Creates a new BayesPm from the given workbench and uses it to construct a new BayesPm.
     *
     * @param semGraphWrapper a {@link edu.cmu.tetradapp.model.SemGraphWrapper} object
     * @param wrapper         a {@link edu.cmu.tetradapp.model.GeneralizedSemPmWrapper} object
     */
    public GeneralizedSemPmWrapper(SemGraphWrapper semGraphWrapper, GeneralizedSemPmWrapper wrapper) {
        this(semGraphWrapper.getSemGraph(), wrapper.getSemPm());
    }

    /**
     * <p>Constructor for GeneralizedSemPmWrapper.</p>
     *
     * @param pmWrapper a {@link edu.cmu.tetradapp.model.GeneralizedSemPmWrapper} object
     */
    public GeneralizedSemPmWrapper(GeneralizedSemPmWrapper pmWrapper) {
        this.semPm = new GeneralizedSemPm(pmWrapper.getSemPm());
    }

    /**
     * Creates a new SemPm from the given workbench and uses it to construct a new BayesPm.
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.TimeLagGraphWrapper} object
     */
    public GeneralizedSemPmWrapper(TimeLagGraphWrapper wrapper) {
        this(wrapper.getGraph());
    }

    /**
     * <p>Constructor for GeneralizedSemPmWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemEstimatorWrapper} object
     */
    public GeneralizedSemPmWrapper(SemEstimatorWrapper wrapper) {
        try {
            SemPm oldSemPm = wrapper.getSemEstimator().getEstimatedSem()
                    .getSemPm();
            this.semPm = new GeneralizedSemPm(oldSemPm);
        } catch (Exception e) {
            throw new RuntimeException("SemPm could not be deep cloned.", e);
        }
        log(this.semPm);
    }

    /**
     * <p>Constructor for GeneralizedSemPmWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemImWrapper} object
     */
    public GeneralizedSemPmWrapper(SemImWrapper wrapper) {
        SemPm oldSemPm = wrapper.getSemIm().getSemPm();
        this.semPm = new GeneralizedSemPm(oldSemPm);
        log(this.semPm);
    }

    /**
     * <p>Constructor for GeneralizedSemPmWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.MimBuildRunner} object
     */
    public GeneralizedSemPmWrapper(MimBuildRunner wrapper) {
        SemPm oldSemPm = wrapper.getSemPm();
        this.semPm = new GeneralizedSemPm(oldSemPm);
        log(this.semPm);
    }

    /**
     * <p>Constructor for GeneralizedSemPmWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BuildPureClustersRunner} object
     */
    public GeneralizedSemPmWrapper(BuildPureClustersRunner wrapper) {
        Graph graph = wrapper.getResultGraph();
        if (graph == null) {
            throw new IllegalArgumentException("No graph to display.");
        }
        SemPm oldSemPm = new SemPm(graph);
        this.semPm = new GeneralizedSemPm(oldSemPm);
        log(this.semPm);
    }

    /**
     * <p>Constructor for GeneralizedSemPmWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.AlgorithmRunner} object
     */
    public GeneralizedSemPmWrapper(AlgorithmRunner wrapper) {
        this(new EdgeListGraph(wrapper.getGraph()));
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.GeneralizedSemPmWrapper} object
     * @see TetradSerializableUtils
     */
    public static GeneralizedSemPmWrapper serializableInstance() {
        return new GeneralizedSemPmWrapper(Dag.serializableInstance());
    }

    /**
     * <p>setReferencedParameters.</p>
     *
     * @param node  a {@link edu.cmu.tetrad.graph.Node} object
     * @param oldPm a {@link edu.cmu.tetrad.sem.GeneralizedSemPm} object
     * @param newPm a {@link edu.cmu.tetrad.sem.GeneralizedSemPm} object
     */
    public void setReferencedParameters(Node node, GeneralizedSemPm oldPm, GeneralizedSemPm newPm) {
        Set<String> parameters = this.semPm.getReferencedParameters(node);

        for (String parameter : parameters) {

        }
    }

    //============================PUBLIC METHODS=========================//

    /**
     * <p>Getter for the field <code>semPm</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.GeneralizedSemPm} object
     */
    public GeneralizedSemPm getSemPm() {
        return this.semPm;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s a {@link java.io.ObjectInputStream} object
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.semPm == null) {
            throw new NullPointerException();
        }
    }

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return this.semPm.getGraph();
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
    private void log(GeneralizedSemPm pm) {
        TetradLogger.getInstance().forceLogMessage("Generalized Structural Equation Parameter Model (Generalized SEM PM)");
        String message = pm.toString();
        TetradLogger.getInstance().forceLogMessage(message);
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
