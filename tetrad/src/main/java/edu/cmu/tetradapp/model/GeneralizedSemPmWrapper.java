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

import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.sem.TemplateExpander;
import edu.cmu.tetrad.session.SessionModel;
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
public class GeneralizedSemPmWrapper implements SessionModel, GraphSource, KnowledgeBoxInput {
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

    private GeneralizedSemPmWrapper(Graph graph) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        if (graph instanceof SemGraph) {
            this.semPm = new GeneralizedSemPm(graph);
        } else {
            try {
                this.semPm = new GeneralizedSemPm(new SemGraph(graph));
            }
            catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        log(semPm);
    }

    private GeneralizedSemPmWrapper(Graph graph, GeneralizedSemPm oldPm) {
        this(graph);

        // We can keep the old expression if the node exists in the old pm and all of the node expressions
        // in the old expression are parents of the node in the new pm.
        try {
            this.semPm.setVariablesTemplate(oldPm.getVariablesTemplate());
            this.semPm.setParametersTemplate(oldPm.getParametersTemplate());
            this.semPm.setErrorsTemplate(oldPm.getErrorsTemplate());

            for (Node node : semPm.getNodes()) {
                Set<String> parents = new HashSet<String>();

                for (Node parent : semPm.getParents(node)) {
                    parents.add(parent.getName());
                }

                Node _node = oldPm.getNode(node.getName());

                Set<Node> oldReferencedNodes = oldPm.getReferencedNodes(_node);
                Set<String> oldReferencedNames = new HashSet<String>();

                for (Node node2 : oldReferencedNodes) {
                    oldReferencedNames.add(node2.getName());
                }

//                System.out.println("\nnode = " + node);
//                System.out.println("Parents = " + parents);
//                System.out.println("Old referenced names = " + oldReferencedNames);

                String template;

                if (semPm.getVariableNodes().contains(node)) {
                    template = semPm.getVariablesTemplate();
                } else {
                    template = semPm.getErrorsTemplate();
                }

                String newExpression = "";

                try {
                    newExpression = TemplateExpander.getInstance().expandTemplate(template, semPm, node);
                } catch (ParseException e) {
                    //
                }


                this.semPm.setNodeExpression(node, newExpression);


                if (_node == null || !parents.equals(oldReferencedNames)) {
                    this.semPm.setNodeExpression(node, newExpression);
                    setReferencedParameters(node, semPm, oldPm);
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
                    semPm.setStartsWithParametersTemplate(startsWith, oldPm.getStartsWithParameterTemplate(startsWith));
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            }

            for (String parameter : semPm.getParameters()) {
                boolean found = false;

                Set<String> prefixes = oldPm.startsWithPrefixes();

                for (String startsWith : prefixes) {
                    if (parameter.startsWith(startsWith)) {
                        semPm.setParameterExpression(parameter, oldPm.getStartsWithParameterTemplate(startsWith));
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    semPm.setParameterExpression(parameter, semPm.getParameterExpressionString(parameter));
                }
            }

        } catch (ParseException e) {
            throw new RuntimeException("Couldn't parse expression.", e);
        }
    }

    private void setReferencedParameters(Node node, GeneralizedSemPm oldPm, GeneralizedSemPm newPm) {
        Set<String> parameters = semPm.getReferencedParameters(node);

        for (String parameter : parameters) {
            
        }
    }

    public GeneralizedSemPmWrapper(SemPmWrapper pmWrapper) {
        if (pmWrapper == null) {
            throw new NullPointerException();
        }

        this.semPm = new GeneralizedSemPm(pmWrapper.getSemPm());
    }

    /**
     * Creates a new BayesPm from the given workbench and uses it to construct a
     * new BayesPm.
     */
    public GeneralizedSemPmWrapper(GraphWrapper graphWrapper) {
        this(new EdgeListGraph(graphWrapper.getGraph()));
    }

    /**
     * Creates a new BayesPm from the given workbench and uses it to construct a
     * new BayesPm.
     */
    public GeneralizedSemPmWrapper(DagWrapper dagWrapper) {
        this(new EdgeListGraph(dagWrapper.getDag()));
    }

    /**
     * Creates a new BayesPm from the given workbench and uses it to construct a
     * new BayesPm.
     */
    public GeneralizedSemPmWrapper(SemGraphWrapper semGraphWrapper) {
        this(semGraphWrapper.getSemGraph());
    }

    /**
     * Creates a new BayesPm from the given workbench and uses it to construct a
     * new BayesPm.
     */
    public GeneralizedSemPmWrapper(GraphWrapper graphWrapper, GeneralizedSemPmWrapper wrapper) {
        this(new EdgeListGraph(graphWrapper.getGraph()), wrapper.getSemPm());
    }

    /**
     * Creates a new BayesPm from the given workbench and uses it to construct a
     * new BayesPm.
     */
    public GeneralizedSemPmWrapper(DagWrapper dagWrapper, GeneralizedSemPmWrapper wrapper) {
        this(new EdgeListGraph(dagWrapper.getDag()), wrapper.getSemPm());
    }

    /**
     * Creates a new BayesPm from the given workbench and uses it to construct a
     * new BayesPm.
     */
    public GeneralizedSemPmWrapper(SemGraphWrapper semGraphWrapper, GeneralizedSemPmWrapper wrapper) {
        this(semGraphWrapper.getSemGraph(), wrapper.getSemPm());
    }

    public GeneralizedSemPmWrapper(GeneralizedSemPmWrapper pmWrapper) {
        semPm = new GeneralizedSemPm(pmWrapper.getSemPm());
    }


    /**
     * Creates a new SemPm from the given workbench and uses it to construct a
     * new BayesPm.
     */
    public GeneralizedSemPmWrapper(TimeLagGraphWrapper wrapper) {
        this(wrapper.getGraph());
    }


    public GeneralizedSemPmWrapper(SemEstimatorWrapper wrapper) {
        try {
            SemPm oldSemPm = wrapper.getSemEstimator().getEstimatedSem()
                    .getSemPm();
            this.semPm = new GeneralizedSemPm(oldSemPm);
        }
        catch (Exception e) {
            throw new RuntimeException("SemPm could not be deep cloned.", e);
        }
        log(semPm);
    }

    public GeneralizedSemPmWrapper(SemImWrapper wrapper) {
        SemPm oldSemPm = wrapper.getSemIm().getSemPm();
        this.semPm = new GeneralizedSemPm(oldSemPm);
        log(semPm);
    }

    public GeneralizedSemPmWrapper(MimBuildRunner wrapper) {
        SemPm oldSemPm = wrapper.getSemPm();
        this.semPm = new GeneralizedSemPm(oldSemPm);
        log(semPm);
    }

    public GeneralizedSemPmWrapper(BuildPureClustersRunner wrapper) {
        Graph graph = wrapper.getResultGraph();
        if (graph == null) throw new IllegalArgumentException("No graph to display.");
        SemPm oldSemPm = new SemPm(graph);
        this.semPm = new GeneralizedSemPm(oldSemPm);
        log(semPm);
    }

    public GeneralizedSemPmWrapper(AlgorithmRunner wrapper) {
        this(new EdgeListGraph(wrapper.getResultGraph()));
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
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
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (semPm == null) {
            throw new NullPointerException();
        }
    }

    public Graph getGraph() {
        return semPm.getGraph();
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

    private void log(GeneralizedSemPm pm) {
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


