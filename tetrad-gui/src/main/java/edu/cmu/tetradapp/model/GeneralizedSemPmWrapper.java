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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.sem.TemplateExpander;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.ParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author Joseph Ramsey
 */
public class GeneralizedSemPmWrapper implements KnowledgeBoxInput {

    static final long serialVersionUID = 23L;

    /**
     * @serial Can be null.
     */
    private String name;

    /**
     * The wrapped SemPm.
     *
     * @serial Cannot be null.
     */
    private final GeneralizedSemPm semPm;

    /**
     * True iff errors should be shown in teh editor.
     */
    private boolean showErrors;

    //==============================CONSTRUCTORS==========================//
    public GeneralizedSemPmWrapper(final Simulation simulation) {
        GeneralizedSemPm semPm = null;

        if (simulation == null) {
            throw new NullPointerException("The Simulation box does not contain a simulation.");
        }

        final edu.cmu.tetrad.algcomparison.simulation.Simulation _simulation = simulation.getSimulation();

        if (_simulation == null) {
            throw new NullPointerException("No data sets have been simulated.");
        }

        if (!(_simulation instanceof GeneralSemSimulation)) {
            throw new IllegalArgumentException("That was not a Generalized SEM simulation.");
        }

        final List<GeneralizedSemIm> ims = ((GeneralSemSimulation) _simulation).getIms();

        if (ims == null || ims.size() == 0) {
            throw new NullPointerException("It looks like you have not done a simulation.");
        }

        semPm = ims.get(0).getGeneralizedSemPm();

        this.semPm = semPm;
    }

    public GeneralizedSemPmWrapper(final Graph graph) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        if (graph instanceof SemGraph) {
            this.semPm = new GeneralizedSemPm(graph);
        } else {
            try {
                this.semPm = new GeneralizedSemPm(new SemGraph(graph));
            } catch (final Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        log(this.semPm);
    }

    public GeneralizedSemPmWrapper(final Graph graph, final GeneralizedSemPm oldPm) {
        this(graph);

        // We can keep the old expression if the node exists in the old pm and all of the node expressions
        // in the old expression are parents of the node in the new pm.
        try {
            this.semPm.setVariablesTemplate(oldPm.getVariablesTemplate());
            this.semPm.setParametersTemplate(oldPm.getParametersTemplate());
            this.semPm.setErrorsTemplate(oldPm.getErrorsTemplate());

            for (final Node node : this.semPm.getNodes()) {
                final Set<String> parents = new HashSet<>();

                for (final Node parent : this.semPm.getParents(node)) {
                    parents.add(parent.getName());
                }

                final Node _node = oldPm.getNode(node.getName());

                final Set<Node> oldReferencedNodes = oldPm.getReferencedNodes(_node);
                final Set<String> oldReferencedNames = new HashSet<>();

                for (final Node node2 : oldReferencedNodes) {
                    oldReferencedNames.add(node2.getName());
                }

//                System.out.println("\nnode = " + node);
//                System.out.println("Parents = " + parents);
//                System.out.println("Old referenced names = " + oldReferencedNames);
                final String template;

                if (this.semPm.getVariableNodes().contains(node)) {
                    template = this.semPm.getVariablesTemplate();
                } else {
                    template = this.semPm.getErrorsTemplate();
                }

                String newExpression = "";

                try {
                    newExpression = TemplateExpander.getInstance().expandTemplate(template, this.semPm, node);
                } catch (final ParseException e) {
                    //
                }

                this.semPm.setNodeExpression(node, newExpression);

                if (_node == null || !parents.equals(oldReferencedNames)) {
                    this.semPm.setNodeExpression(node, newExpression);
                    setReferencedParameters(node, this.semPm, oldPm);
                } else {
                    try {
                        this.semPm.setNodeExpression(node, oldPm.getNodeExpressionString(node));
                    } catch (final Exception e) {
                        this.semPm.setNodeExpression(node, newExpression);
                    }
                }
            }

            for (final String startsWith : oldPm.startsWithPrefixes()) {
                try {
                    this.semPm.setStartsWithParametersTemplate(startsWith, oldPm.getStartsWithParameterTemplate(startsWith));
                } catch (final ParseException e) {
                    throw new RuntimeException(e);
                }
            }

            for (final String parameter : this.semPm.getParameters()) {
                boolean found = false;

                final Set<String> prefixes = oldPm.startsWithPrefixes();

                for (final String startsWith : prefixes) {
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

        } catch (final ParseException e) {
            throw new RuntimeException("Couldn't parse expression.", e);
        }
    }

    public void setReferencedParameters(final Node node, final GeneralizedSemPm oldPm, final GeneralizedSemPm newPm) {
        final Set<String> parameters = this.semPm.getReferencedParameters(node);

        for (final String parameter : parameters) {

        }
    }

    public GeneralizedSemPmWrapper(final SemPmWrapper pmWrapper) {
        if (pmWrapper == null) {
            throw new NullPointerException();
        }

        this.semPm = new GeneralizedSemPm(pmWrapper.getSemPm());
    }

    /**
     * Creates a new BayesPm from the given workbench and uses it to construct a
     * new BayesPm.
     */
    public GeneralizedSemPmWrapper(final GraphSource graphWrapper) {
        this(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public GeneralizedSemPmWrapper(final GraphSource graphWrapper, final DataWrapper dataWrapper) {
        this(new EdgeListGraph(graphWrapper.getGraph()));
    }

    /**
     * Creates a new BayesPm from the given workbench and uses it to construct a
     * new BayesPm.
     */
    public GeneralizedSemPmWrapper(final GraphSource graphWrapper, final GeneralizedSemPmWrapper wrapper) {
        this(new EdgeListGraph(graphWrapper.getGraph()), wrapper.getSemPm());
    }

    /**
     * Creates a new BayesPm from the given workbench and uses it to construct a
     * new BayesPm.
     */
    public GeneralizedSemPmWrapper(final DagWrapper dagWrapper, final GeneralizedSemPmWrapper wrapper) {
        this(new EdgeListGraph(dagWrapper.getDag()), wrapper.getSemPm());
    }

    /**
     * Creates a new BayesPm from the given workbench and uses it to construct a
     * new BayesPm.
     */
    public GeneralizedSemPmWrapper(final SemGraphWrapper semGraphWrapper, final GeneralizedSemPmWrapper wrapper) {
        this(semGraphWrapper.getSemGraph(), wrapper.getSemPm());
    }

    public GeneralizedSemPmWrapper(final GeneralizedSemPmWrapper pmWrapper) {
        this.semPm = new GeneralizedSemPm(pmWrapper.getSemPm());
    }

    /**
     * Creates a new SemPm from the given workbench and uses it to construct a
     * new BayesPm.
     */
    public GeneralizedSemPmWrapper(final TimeLagGraphWrapper wrapper) {
        this(wrapper.getGraph());
    }

    public GeneralizedSemPmWrapper(final SemEstimatorWrapper wrapper) {
        try {
            final SemPm oldSemPm = wrapper.getSemEstimator().getEstimatedSem()
                    .getSemPm();
            this.semPm = new GeneralizedSemPm(oldSemPm);
        } catch (final Exception e) {
            throw new RuntimeException("SemPm could not be deep cloned.", e);
        }
        log(this.semPm);
    }

    public GeneralizedSemPmWrapper(final SemImWrapper wrapper) {
        final SemPm oldSemPm = wrapper.getSemIm().getSemPm();
        this.semPm = new GeneralizedSemPm(oldSemPm);
        log(this.semPm);
    }

    public GeneralizedSemPmWrapper(final MimBuildRunner wrapper) {
        final SemPm oldSemPm = wrapper.getSemPm();
        this.semPm = new GeneralizedSemPm(oldSemPm);
        log(this.semPm);
    }

    public GeneralizedSemPmWrapper(final BuildPureClustersRunner wrapper) {
        final Graph graph = wrapper.getResultGraph();
        if (graph == null) {
            throw new IllegalArgumentException("No graph to display.");
        }
        final SemPm oldSemPm = new SemPm(graph);
        this.semPm = new GeneralizedSemPm(oldSemPm);
        log(this.semPm);
    }

    public GeneralizedSemPmWrapper(final AlgorithmRunner wrapper) {
        this(new EdgeListGraph(wrapper.getGraph()));
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static GeneralizedSemPmWrapper serializableInstance() {
        return new GeneralizedSemPmWrapper(Dag.serializableInstance());
    }

    //============================PUBLIC METHODS=========================//
    public GeneralizedSemPm getSemPm() {
        return this.semPm;
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
    private void readObject(final ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.semPm == null) {
            throw new NullPointerException();
        }
    }

    public Graph getGraph() {
        return this.semPm.getGraph();
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public boolean isShowErrors() {
        return this.showErrors;
    }

    public void setShowErrors(final boolean showErrors) {
        this.showErrors = showErrors;
    }

    //======================= Private methods ====================//
    private void log(final GeneralizedSemPm pm) {
        TetradLogger.getInstance().log("info", "Generalized Structural Equation Parameter Model (Generalized SEM PM)");
        TetradLogger.getInstance().log("pm", pm.toString());
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
