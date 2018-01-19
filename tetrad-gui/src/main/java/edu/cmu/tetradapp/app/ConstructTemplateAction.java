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
package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.session.SessionNode;
import edu.cmu.tetradapp.model.SessionNodeWrapper;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.SessionEditorIndirectRef;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.swing.*;

/**
 * Adds a new template session subgraph to the frontmost session editor. of one
 * of three types.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
final class ConstructTemplateAction extends AbstractAction {

    /**
     * The names of the templates supported by this action.
     */
    private static final String[] TEMPLATE_NAMES = new String[]{
        "Simulate from a given graph, then search",
        "Simulate, search, then compare",
        "Load data and search",
        "Search then estimate",
        "Search, estimate, then update",
        "MIMBuild"
    };

    /**
     * The name of the template.
     */
    private String templateName;

    /**
     * The session graph.
     */
    private SessionWrapper sessionWrapper;

    /**
     * The session workbench. Needed for selection.
     */
    private SessionEditorWorkbench sessionWorkbench;

    //============================CONSTUCTORS============================//
    /**
     * Constucts an action for adding a new template to the frontmost session.
     */
    public ConstructTemplateAction(String templateName) {
        super(templateName);

        if (templateName == null) {
            throw new NullPointerException(
                    "Template filename must not be " + "null.");
        }

        this.templateName = templateName;
    }

    //==========================PUBLIC METHODS===========================//
    /**
     * @return a copy of the template names. Must be public.
     */
    public static String[] getTemplateNames() {
        String[] templateNamesCopy = new String[TEMPLATE_NAMES.length];
        System.arraycopy(TEMPLATE_NAMES, 0, templateNamesCopy, 0,
                TEMPLATE_NAMES.length);
        return templateNamesCopy;
    }

    /**
     * Performs the action of adding the specified templatew into the frontmost
     * session. It is assumed that all example sessions will be located in
     * directory "example_sessions".
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        int leftX = getLeftX();

        if (this.templateName.equals(getTemplateNames()[0])) {
            simulateDataFixedIM(leftX);
        } else if (this.templateName.equals(getTemplateNames()[1])) {
            searchFromSimulatedDataWithCompare(leftX);
        } else if (this.templateName.equals(getTemplateNames()[2])) {
            searchFromLoadedOrSimulatedData(leftX);
        } else if (this.templateName.equals(getTemplateNames()[3])) {
            estimateFromSimulatedData(leftX);
        } else if (this.templateName.equals(getTemplateNames()[4])) {
            estimateThenUpdateUsingSearchResult(leftX);
        } else if (this.templateName.equals(getTemplateNames()[5])) {
            mimbuild(leftX);
        } else {
            throw new IllegalStateException("Unrecognized template name: " + this.templateName);
        }
    }

    public void addChild(SessionEditorNode thisNode, String type) {
        String name = nextName(type);
        addNode(type, name, thisNode.getX() + 100, thisNode.getY() + 100);
        addEdge(thisNode.getName(), name);
    }

    public void addParent(SessionEditorNode thisNode, String type) {
        String name = nextName(type);
        addNode(type, name, thisNode.getX() - 50, thisNode.getY() - 50);
        addEdge(name, thisNode.getName());
    }

    //=============================PRIVATE METHODS========================//
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

        String data = nextName("Data");
        String search = nextName("Search");

        nodes.add(addNode("Data", data, leftX, 100));
        nodes.add(addNode("Search", search, 125 + leftX, 100));

        addEdge(data, search);

        selectSubgraph(nodes);
    }

    private void simulateDataFixedIM(int leftX) {
        getSessionWorkbench().deselectAll();

        List<Node> nodes = new LinkedList<>();

        String graph = nextName("Graph");
        String pm = nextName("PM");
        String im = nextName("IM");
        String data = nextName("Simulation");
        String search = nextName("Search");

        nodes.add(addNode("Graph", graph, leftX, 100));
        nodes.add(addNode("PM", pm, leftX, 200));
        nodes.add(addNode("IM", im, leftX, 300));
        nodes.add(addNode("Simulation", data, leftX, 400));
        nodes.add(addNode("Search", search, 125 + leftX, 400));

        addEdge(graph, pm);
        addEdge(pm, im);
        addEdge(im, data);
        addEdge(data, search);

        selectSubgraph(nodes);
    }

    private void searchFromSimulatedDataWithCompare(int leftX) {
        SessionEditorIndirectRef sessionEditorRef
                = DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        SessionEditorWorkbench sessionWorkbench
                = sessionEditor.getSessionWorkbench();
        sessionWorkbench.deselectAll();

        List<Node> nodes = new LinkedList<>();

        String data = nextName("Simulation");
        String search = nextName("Search");
        String compare = nextName("Compare");

        nodes.add(addNode("Simulation", data, leftX, 100));
        nodes.add(addNode("Search", search, 150 + leftX, 100));
        nodes.add(addNode("Compare", compare, 80 + leftX, 200));

        addEdge(data, search);
        addEdge(data, compare);
        addEdge(search, compare);

        selectSubgraph(nodes);
    }

    private void estimateFromSimulatedData(int leftX) {
        SessionEditorIndirectRef sessionEditorRef
                = DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        SessionEditorWorkbench sessionWorkbench
                = sessionEditor.getSessionWorkbench();
        sessionWorkbench.deselectAll();

        List<Node> nodes = new LinkedList<>();

        String data = nextName("Data");
        String search = nextName("Search");

        nodes.add(addNode("Data", data, leftX, 100));
        nodes.add(addNode("Search", search, leftX + 150, 100));

        String graph = nextName("Graph");
        nodes.add(addNode("Graph", graph, leftX + 150, 200));

        String pm = nextName("PM");
        nodes.add(addNode("PM", pm, leftX + 150, 300));

        String estimator = nextName("Estimator");
        nodes.add(addNode("Estimator", estimator, leftX, 300));

        addEdge(data, search);
        addEdge(search, graph);
        addEdge(graph, pm);
        addEdge(data, estimator);
        addEdge(data, pm);
        addEdge(pm, estimator);

        selectSubgraph(nodes);
    }

    private void updateFromSimulatedData(int leftX) {
        SessionEditorIndirectRef sessionEditorRef
                = DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        SessionEditorWorkbench sessionWorkbench
                = sessionEditor.getSessionWorkbench();
        sessionWorkbench.deselectAll();

        List<Node> nodes = new LinkedList<>();

        String data = nextName("Data");
        String search = nextName("Search");
        String estimator = nextName("Estimator");
        String updater = nextName("Updater");

        nodes.add(addNode("Data", data, leftX, 100));
        nodes.add(addNode("Search", search, 150 + leftX, 100));
        nodes.add(addNode("Estimator", estimator, 80 + leftX, 200));
        nodes.add(addNode("Updater", updater, 80 + leftX, 300));

        addEdge(data, search);
        addEdge(data, estimator);
        addEdge(search, estimator);
        addEdge(estimator, updater);

        selectSubgraph(nodes);
    }

    private void mimbuild(int leftX) {
        getSessionWorkbench().deselectAll();

        List<Node> nodes = new LinkedList<>();

        String graph = nextName("Graph");
        String pm = nextName("PM");
        String im = nextName("IM");
        String data = nextName("Simulation");
        String search = nextName("Search");
        String mimbuild = nextName("MIMBuild");

        nodes.add(addNode("Graph", graph, leftX, 100));
        nodes.add(addNode("PM", pm, leftX, 200));
        nodes.add(addNode("IM", im, leftX, 300));
        nodes.add(addNode("Simulation", data, leftX, 400));
        nodes.add(addNode("Search", search, 125 + leftX, 400));
        nodes.add(addNode("Search", mimbuild, 65 + leftX, 500));

        addEdge(graph, pm);
        addEdge(pm, im);
        addEdge(im, data);
        addEdge(data, search);
        addEdge(data, mimbuild);
        addEdge(search, mimbuild);

        selectSubgraph(nodes);
    }

    private void estimateThenUpdateUsingSearchResult(int leftX) {
        SessionEditorIndirectRef sessionEditorRef
                = DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        SessionEditorWorkbench sessionWorkbench
                = sessionEditor.getSessionWorkbench();
        sessionWorkbench.deselectAll();

        List<Node> nodes = new LinkedList<>();

        String data = nextName("Data");
        String search = nextName("Search");

        nodes.add(addNode("Data", data, leftX, 100));
        nodes.add(addNode("Search", search, leftX + 150, 100));

        String graph = nextName("Graph");
        nodes.add(addNode("Graph", graph, leftX + 150, 200));

        String pm = nextName("PM");
        nodes.add(addNode("PM", pm, leftX + 150, 300));

        String estimator = nextName("Estimator");
        nodes.add(addNode("Estimator", estimator, leftX, 300));

        String updater = nextName("Updater");
        nodes.add(addNode("Updater", updater, leftX, 400));

        addEdge(data, search);
        addEdge(search, graph);
        addEdge(graph, pm);
        addEdge(data, estimator);
        addEdge(data, pm);
        addEdge(pm, estimator);
        addEdge(estimator, updater);

        selectSubgraph(nodes);
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

            for (Object o : graph.getNodes()) {
                Node node = (Node) (o);

                if (node.getName().equals(name)) {
                    continue loop;
                }
            }

            break;
        }

        return base + i;
    }

    private SessionWrapper getSessionWrapper() {
        SessionEditorIndirectRef sessionEditorRef
                = DesktopController.getInstance().getFrontmostSessionEditor();
        SessionEditor sessionEditor = (SessionEditor) sessionEditorRef;
        SessionEditorWorkbench sessionWorkbench
                = sessionEditor.getSessionWorkbench();
        sessionWorkbench.deselectAll();
        return sessionWorkbench.getSessionWrapper();

//        if (sessionWrapper == null) {
//            this.sessionWrapper = getSessionWorkbench().getSessionWrapper();
//        }
//        return sessionWrapper;
    }

    private SessionEditorWorkbench getSessionWorkbench() {
        if (sessionWorkbench == null) {
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
        return sessionWorkbench;
    }

    private Node addNode(String nodeType, String nodeName, int centerX,
            int centerY) {
        SessionNodeWrapper node = getNewModelNode(nodeType, nodeName);
        node.setCenter(centerX, centerY);
        getSessionWrapper().addNode(node);
        return node;
    }

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

    private static SessionNodeWrapper getNewModelNode(String nextButtonType,
            String name) {
        if (nextButtonType == null) {
            throw new NullPointerException(
                    "Next button type must be a " + "non-null string.");
        }

        Class[] modelClasses = getModelClasses(nextButtonType);
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
    private static Class[] getModelClasses(String nextButtonType) {
        TetradApplicationConfig tetradConfig = TetradApplicationConfig.getInstance();
        SessionNodeConfig config = tetradConfig.getSessionNodeConfig(nextButtonType);
        if (config == null) {
            throw new NullPointerException("There is no configuration for button: " + nextButtonType);
        }

        return config.getModels();
    }
}
