/// ////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.model.SessionNodeWrapper;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.session.SessionEvent;
import edu.cmu.tetradapp.session.SessionNode;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.SessionEditorIndirectRef;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Adds a new pipeline session subgraph to the frontmost session editor.
 *
 * @author josephramsey
 */
final class ConstructTemplateAction extends AbstractAction {

    /**
     * Marker entry rendered as a menu separator rather than a pipeline item.
     */
    static final String SEPARATOR = "--separator--";

    /**
     * Prefix for entries rendered as disabled section labels rather than pipeline items.
     */
    static final String LABEL_PREFIX = "--label--";

    // Pipeline names. Each name appears once in TEMPLATE_NAMES (which fixes the menu order)
    // and once in actionPerformed (which dispatches on the name), so they are given as
    // constants to keep the two in sync.
    private static final String LOAD_DATA_AND_SEARCH = "Load data and search";
    private static final String LOAD_DATA_KNOWLEDGE_SEARCH = "Load data, add knowledge, then search";
    private static final String LOAD_DATA_SEARCH_MARKOV_CHECK = "Load data, search, then run a Markov check";
    private static final String SEARCH_THEN_ESTIMATE = "Search then estimate";
    private static final String SEARCH_ESTIMATE_UPDATE = "Search, estimate, then update";
    private static final String LATENT_CLUSTER_SEARCH = "Latent Clustering and Structure Search";
    private static final String SIMULATE_FIXED_IM_SEARCH = "Simulate from a given graph, then search";
    private static final String SIMULATE_SEARCH_COMPARE = "Simulate, search, then compare";

    /**
     * The names of the pipelines supported by this action, in menu order: real-data analysis
     * pipelines first, simulation-study pipelines below. Entries equal to SEPARATOR render as
     * menu separators; entries starting with LABEL_PREFIX render as disabled section labels.
     */
    private static final String[] TEMPLATE_NAMES = {
            LABEL_PREFIX + "Analyze a real dataset",
            LOAD_DATA_AND_SEARCH,
            LOAD_DATA_KNOWLEDGE_SEARCH,
            LOAD_DATA_SEARCH_MARKOV_CHECK,
            SEARCH_THEN_ESTIMATE,
            SEARCH_ESTIMATE_UPDATE,
            LATENT_CLUSTER_SEARCH,
            SEPARATOR,
            LABEL_PREFIX + "Simulation studies",
            SIMULATE_FIXED_IM_SEARCH,
            SIMULATE_SEARCH_COMPARE
    };

    /**
     * The name of the template.
     */
    private final String templateName;

    /**
     * The session workbench. Needed for selection.
     */
    private SessionEditorWorkbench sessionWorkbench;

    /**
     * Constucts an action for adding a new template to the frontmost session.
     *
     * @param templateName a {@link java.lang.String} object
     */
    public ConstructTemplateAction(String templateName) {
        super(templateName);

        if (templateName == null) {
            throw new NullPointerException(
                    "Template filename must not be " + "null.");
        }

        this.templateName = templateName;
    }

    /**
     * <p>getTemplateNames.</p>
     *
     * @return a copy of the template names. Must be public.
     */
    public static String[] getTemplateNames() {
        String[] templateNamesCopy = new String[ConstructTemplateAction.TEMPLATE_NAMES.length];
        System.arraycopy(ConstructTemplateAction.TEMPLATE_NAMES, 0, templateNamesCopy, 0,
                ConstructTemplateAction.TEMPLATE_NAMES.length);
        return templateNamesCopy;
    }

    private static void selectSubgraph(List<Node> nodes) {
        SessionEditorIndirectRef sessionEditorRef
                = DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        SessionEditorWorkbench sessionWorkbench
                = sessionEditor.getSessionWorkbench();

        for (Node node : nodes) {
            sessionWorkbench.selectNode(node);
        }

        Set<Edge> edges = sessionWorkbench.getGraph().getEdges();

        for (Edge edge : edges) {
            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();
            if (nodes.contains(node1) && nodes.contains(node2)) {
                sessionWorkbench.selectEdge(edge);
            }
        }

        sessionWorkbench.scrollNodesToVisible(nodes);
        sessionWorkbench.firePropertyChange("selectMove", false, true);
    }

    /**
     * Returns the next string in the sequence.
     *
     * @param base the string base of the name--for example, "Graph".
     * @return the next string in the sequence--for example, "Graph1".
     */
    private static String nextName(String base) {
        SessionEditorIndirectRef sessionEditorRef
                = DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        SessionEditorWorkbench sessionWorkbench
                = sessionEditor.getSessionWorkbench();
        SessionWrapper graph = sessionWorkbench.getSessionWrapper();

        if (base == null) {
            throw new NullPointerException("Base name must be non-null.");
        }

        int i = 0;    // Sequence 1, 2, 3, ...

        loop:
        while (true) {
            i++;
            String name = base + i;

            for (Node o : graph.getNodes()) {
                if (o.getName().equals(name)) {
                    continue loop;
                }
            }

            break;
        }

        return base + i;
    }

    private static SessionNodeWrapper getNewModelNode(String nextButtonType,
                                                      String name) {
        if (nextButtonType == null) {
            throw new NullPointerException(
                    "Next button type must be a " + "non-null string.");
        }

        Class<?>[] modelClasses = ConstructTemplateAction.getModelClasses(nextButtonType);
        SessionNode newNode
                = new SessionNode(nextButtonType, name, modelClasses);
        SessionNodeWrapper nodeWrapper = new SessionNodeWrapper(newNode);
        nodeWrapper.setButtonType(nextButtonType);
        return nodeWrapper;
    }

    /**
     * @return the model classes associated with the given button type.
     * @throws NullPointerException if no classes are stored for the given type.
     */
    private static Class<?>[] getModelClasses(String nextButtonType) {
        TetradApplicationConfig tetradConfig = TetradApplicationConfig.getInstance();
        SessionNodeConfig config = tetradConfig.getSessionNodeConfig(nextButtonType);
        if (config == null) {
            throw new NullPointerException("There is no configuration for button: " + nextButtonType);
        }

        return config.getModels();
    }

    /**
     * This method is called when an action event is generated. It processes the event by performing different actions
     * based on the template name.
     *
     * @param e the event to be processed
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        int leftX = getLeftX();

        switch (this.templateName) {
            case LOAD_DATA_AND_SEARCH -> searchFromLoadedOrSimulatedData(leftX);
            case LOAD_DATA_KNOWLEDGE_SEARCH -> searchWithKnowledgeFromLoadedData(leftX);
            case LOAD_DATA_SEARCH_MARKOV_CHECK -> searchThenMarkovCheck(leftX);
            case SEARCH_THEN_ESTIMATE -> estimateFromSimulatedData(leftX);
            case SEARCH_ESTIMATE_UPDATE -> estimateThenUpdateUsingSearchResult(leftX);
            case LATENT_CLUSTER_SEARCH -> latentClusterThenSearch(leftX);
            case SIMULATE_FIXED_IM_SEARCH -> simulateDataFixedIM(leftX);
            case SIMULATE_SEARCH_COMPARE -> searchFromSimulatedDataWithCompare(leftX);
            default -> throw new IllegalStateException("Unrecognized pipeline name: " + this.templateName);
        }
    }

    /**
     * <p>addParent.</p>
     *
     * @param thisNode a {@link edu.cmu.tetradapp.app.SessionEditorNode} object
     * @param type     a {@link java.lang.String} object
     */
    public void addParent(SessionEditorNode thisNode, String type) {
        String name = ConstructTemplateAction.nextName(type);
        addNode(type, name, thisNode.getX() - 50, thisNode.getY() - 50);
        addEdge(name, thisNode.getName());
    }

    private int getLeftX() {
        SessionEditorIndirectRef sessionEditorRef
                = DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        SessionEditorWorkbench sessionWorkbench
                = sessionEditor.getSessionWorkbench();
        sessionWorkbench.deselectAll();

        Component[] components = sessionWorkbench.getComponents();
        int leftX = 0;

        for (Component component : components) {
            Rectangle bounds = component.getBounds();
            int rightmost = bounds.x + bounds.width;
            if (rightmost > leftX) {
                leftX = rightmost;
            }
        }

        leftX += 100;
        return leftX;
    }

    private void searchFromLoadedOrSimulatedData(int leftX) {
        SessionEditorIndirectRef sessionEditorRef
                = DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        SessionEditorWorkbench sessionWorkbench
                = sessionEditor.getSessionWorkbench();
        sessionWorkbench.deselectAll();

        List<Node> nodes = new LinkedList<>();

        String data = ConstructTemplateAction.nextName("Data");
        String search = ConstructTemplateAction.nextName("Search");

        nodes.add(addNode("Data", data, leftX, 100));
        nodes.add(addNode("Search", search, 125 + leftX, 100));

        addEdge(data, search);

        ConstructTemplateAction.selectSubgraph(nodes);
    }

    /**
     * Real-data pipeline: load data, specify background knowledge (tiers, forbidden and required
     * edges), then search subject to that knowledge. The Data box feeds the Knowledge box its
     * variable list and feeds the Search box its data; the Knowledge box constrains the search.
     */
    private void searchWithKnowledgeFromLoadedData(int leftX) {
        getSessionWorkbench().deselectAll();

        List<Node> nodes = new LinkedList<>();

        String data = ConstructTemplateAction.nextName("Data");
        String knowledge = ConstructTemplateAction.nextName("Knowledge");
        String search = ConstructTemplateAction.nextName("Search");

        nodes.add(addNode("Data", data, leftX, 100));
        nodes.add(addNode("Knowledge", knowledge, leftX, 225));
        nodes.add(addNode("Search", search, leftX + 170, 225));

        addEdge(data, knowledge);
        addEdge(data, search);
        addEdge(knowledge, search);

        ConstructTemplateAction.selectSubgraph(nodes);
    }

    /**
     * Real-data pipeline: load data, search, then check the result against the data. The Compare
     * box takes the Data and Search boxes as parents; choosing "Markov Check" in that box tests
     * whether the independencies implied by the estimated graph hold in the data.
     */
    private void searchThenMarkovCheck(int leftX) {
        getSessionWorkbench().deselectAll();

        List<Node> nodes = new LinkedList<>();

        String data = ConstructTemplateAction.nextName("Data");
        String search = ConstructTemplateAction.nextName("Search");
        String compare = ConstructTemplateAction.nextName("Compare");

        nodes.add(addNode("Data", data, leftX, 100));
        nodes.add(addNode("Search", search, 150 + leftX, 100));
        nodes.add(addNode("Compare", compare, 80 + leftX, 200));

        addEdge(data, search);
        addEdge(data, compare);
        addEdge(search, compare);

        ConstructTemplateAction.selectSubgraph(nodes);
    }

    private void latentClusterThenSearch(int leftX) {
        SessionEditorIndirectRef sessionEditorRef
                = DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        SessionEditorWorkbench sessionWorkbench
                = sessionEditor.getSessionWorkbench();
        sessionWorkbench.deselectAll();

        List<Node> nodes = new LinkedList<>();

        String data = ConstructTemplateAction.nextName("Data");
        String cluster = ConstructTemplateAction.nextName("Latent Clusters");
        String search = ConstructTemplateAction.nextName("Latent Structure");

        nodes.add(addNode("Data", data, leftX, 100));
        nodes.add(addNode("Latent_Clusters", cluster, leftX, 225));
        nodes.add(addNode("Latent_Structure", search, leftX + 170, 225));

        addEdge(data, cluster);
        addEdge(cluster, search);
        addEdge(data, search);

        ConstructTemplateAction.selectSubgraph(nodes);
    }

    private void simulateDataFixedIM(int leftX) {
        getSessionWorkbench().deselectAll();

        List<Node> nodes = new LinkedList<>();

        String graph = ConstructTemplateAction.nextName("Graph");
        String pm = ConstructTemplateAction.nextName("PM");
        String im = ConstructTemplateAction.nextName("IM");
        String data = ConstructTemplateAction.nextName("Simulation");
        String search = ConstructTemplateAction.nextName("Search");

        nodes.add(addNode("Graph", graph, leftX, 100));
        nodes.add(addNode("PM", pm, leftX, 200));
        nodes.add(addNode("IM", im, leftX, 300));
        nodes.add(addNode("Simulation", data, leftX, 400));
        nodes.add(addNode("Search", search, 125 + leftX, 400));

        addEdge(graph, pm);
        addEdge(pm, im);
        addEdge(im, data);
        addEdge(data, search);

        ConstructTemplateAction.selectSubgraph(nodes);
    }

    private void searchFromSimulatedDataWithCompare(int leftX) {
        SessionEditorIndirectRef sessionEditorRef
                = DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        SessionEditorWorkbench sessionWorkbench
                = sessionEditor.getSessionWorkbench();
        sessionWorkbench.deselectAll();

        List<Node> nodes = new LinkedList<>();

        String data = ConstructTemplateAction.nextName("Simulation");
        String search = ConstructTemplateAction.nextName("Search");
        String compare = ConstructTemplateAction.nextName("Compare");

        nodes.add(addNode("Simulation", data, leftX, 100));
        nodes.add(addNode("Search", search, 150 + leftX, 100));
        nodes.add(addNode("Compare", compare, 80 + leftX, 200));

        addEdge(data, search);
        addEdge(data, compare);
        addEdge(search, compare);

        ConstructTemplateAction.selectSubgraph(nodes);
    }

    private void estimateFromSimulatedData(int leftX) {
        SessionEditorIndirectRef sessionEditorRef
                = DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        SessionEditorWorkbench sessionWorkbench
                = sessionEditor.getSessionWorkbench();
        sessionWorkbench.deselectAll();

        List<Node> nodes = new LinkedList<>();

        String data = ConstructTemplateAction.nextName("Data");
        String search = ConstructTemplateAction.nextName("Search");

        nodes.add(addNode("Data", data, leftX, 100));
        nodes.add(addNode("Search", search, leftX + 150, 100));

        String graph = ConstructTemplateAction.nextName("Graph");
        nodes.add(addNode("Graph", graph, leftX + 150, 200));

        String pm = ConstructTemplateAction.nextName("PM");
        nodes.add(addNode("PM", pm, leftX + 150, 300));

        String estimator = ConstructTemplateAction.nextName("Estimator");
        nodes.add(addNode("Estimator", estimator, leftX, 300));

        addEdge(data, search);
        addEdge(search, graph);
        addEdge(graph, pm);
        addEdge(data, estimator);
        addEdge(data, pm);
        addEdge(pm, estimator);

        ConstructTemplateAction.selectSubgraph(nodes);
    }

    private void estimateThenUpdateUsingSearchResult(int leftX) {
        SessionEditorIndirectRef sessionEditorRef
                = DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        SessionEditorWorkbench sessionWorkbench
                = sessionEditor.getSessionWorkbench();
        sessionWorkbench.deselectAll();

        List<Node> nodes = new LinkedList<>();

        String data = ConstructTemplateAction.nextName("Data");
        String search = ConstructTemplateAction.nextName("Search");

        nodes.add(addNode("Data", data, leftX, 100));
        nodes.add(addNode("Search", search, leftX + 150, 100));

        String graph = ConstructTemplateAction.nextName("Graph");
        nodes.add(addNode("Graph", graph, leftX + 150, 200));

        String pm = ConstructTemplateAction.nextName("PM");
        nodes.add(addNode("PM", pm, leftX + 150, 300));

        String estimator = ConstructTemplateAction.nextName("Estimator");
        nodes.add(addNode("Estimator", estimator, leftX, 300));

        String updater = ConstructTemplateAction.nextName("Updater");
        nodes.add(addNode("Updater", updater, leftX, 400));

        addEdge(data, search);
        addEdge(search, graph);
        addEdge(graph, pm);
        addEdge(data, estimator);
        addEdge(data, pm);
        addEdge(pm, estimator);
        addEdge(estimator, updater);

        ConstructTemplateAction.selectSubgraph(nodes);
    }

    private SessionWrapper getSessionWrapper() {
        SessionEditorIndirectRef sessionEditorRef
                = DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        SessionEditorWorkbench sessionWorkbench
                = sessionEditor.getSessionWorkbench();
        sessionWorkbench.deselectAll();
        return sessionWorkbench.getSessionWrapper();
    }

    private SessionEditorWorkbench getSessionWorkbench() {
        if (this.sessionWorkbench == null) {
            SessionEditorIndirectRef sessionEditorRef
                    = DesktopController.getInstance().getFrontmostSessionEditor();
            SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;

            if (sessionEditor == null) {
                DesktopController.getInstance().newSessionEditor();
                sessionEditorRef
                        = DesktopController.getInstance().getFrontmostSessionEditor();
                sessionEditor = (SessionEditor) sessionEditorRef;
            }

            this.sessionWorkbench = sessionEditor.getSessionWorkbench();
        }
        return this.sessionWorkbench;
    }

    private Node addNode(String nodeType, String nodeName, int centerX,
                         int centerY) {
        SessionNodeWrapper node = ConstructTemplateAction.getNewModelNode(nodeType, nodeName);
        node.setCenter(centerX, centerY);
        getSessionWrapper().addNode(node);
        return node;
    }

    /**
     * <p>addEdge.</p>
     *
     * @param nodeName1 a {@link java.lang.String} object
     * @param nodeName2 a {@link java.lang.String} object
     */
    public void addEdge(String nodeName1, String nodeName2) {

        // Retrieve the nodes from the session wrapper.
        Node node1 = getSessionWrapper().getNode(nodeName1);
        Node node2 = getSessionWrapper().getNode(nodeName2);

        // Make sure nodes existed in the session wrapper by these names.
        if (node1 == null) {
            throw new RuntimeException(
                    "There was no node by name nodeName1 in "
                    + "the session wrapper: " + nodeName1);
        }

        if (node2 == null) {
            throw new RuntimeException(
                    "There was no node by name nodeName2 in "
                    + "the session wrapper: " + nodeName2);
        }

        // Construct an edge.
        SessionNodeWrapper nodeWrapper1 = (SessionNodeWrapper) node1;
        SessionNodeWrapper nodeWrapper2 = (SessionNodeWrapper) node2;
        Edge edge = new Edge(nodeWrapper1, nodeWrapper2, Endpoint.TAIL,
                Endpoint.ARROW);

        // Add the edge.
        getSessionWrapper().addEdge(edge);
        getSessionWorkbench().revalidate();
        getSessionWorkbench().repaint();
    }
}

