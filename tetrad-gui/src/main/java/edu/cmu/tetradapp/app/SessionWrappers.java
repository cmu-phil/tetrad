package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.model.SessionNodeWrapper;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.session.SessionNode;

/**
 * A collection of public static methods to help construct SessionWrappers manually.
 *
 * @author josephramsey
 */
final class SessionWrappers {

    /**
     * <p>addNode.</p>
     *
     * @param sessionWrapper a {@link edu.cmu.tetradapp.model.SessionWrapper} object
     * @param nodeType       a {@link java.lang.String} object
     * @param nodeName       a {@link java.lang.String} object
     * @param centerX        a int
     * @param centerY        a int
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public static Node addNode(SessionWrapper sessionWrapper, String nodeType,
                               String nodeName, int centerX, int centerY) {
        SessionNodeWrapper node = SessionWrappers.getNewModelNode(nodeType, nodeName);
        node.setCenter(centerX, centerY);
        sessionWrapper.addNode(node);
        return node;
    }

    /**
     * <p>addEdge.</p>
     *
     * @param sessionWrapper a {@link edu.cmu.tetradapp.model.SessionWrapper} object
     * @param nodeName1      a {@link java.lang.String} object
     * @param nodeName2      a {@link java.lang.String} object
     */
    public static void addEdge(SessionWrapper sessionWrapper, String nodeName1,
                               String nodeName2) {

        // Retrieve the nodes from the session wrapper.
        Node node1 = sessionWrapper.getNode(nodeName1);
        Node node2 = sessionWrapper.getNode(nodeName2);

        // Make sure nodes existed in the session wrapper by these names.
        if (node1 == null) {
            throw new RuntimeException(
                    "There was no node by name nodeName1 in " +
                    "the session wrapper: " + nodeName1);
        }

        if (node2 == null) {
            throw new RuntimeException(
                    "There was no node by name nodeName2 in " +
                    "the session wrapper: " + nodeName2);
        }

        // Construct an edge.
        SessionNodeWrapper nodeWrapper1 = (SessionNodeWrapper) node1;
        SessionNodeWrapper nodeWrapper2 = (SessionNodeWrapper) node2;
        Edge edge = new Edge(nodeWrapper1, nodeWrapper2, Endpoint.TAIL,
                Endpoint.ARROW);

        // Add the edge.
        sessionWrapper.addEdge(edge);
    }

    private static SessionNodeWrapper getNewModelNode(String nextButtonType,
                                                      String name) {
        if (nextButtonType == null) {
            throw new NullPointerException(
                    "Next button type must be a " + "non-null string.");
        }

        Class<?>[] modelClasses = SessionWrappers.getModelClasses(nextButtonType);
        SessionNode newNode =
                new SessionNode(nextButtonType, name, modelClasses);
        SessionNodeWrapper nodeWrapper = new SessionNodeWrapper(newNode);
        nodeWrapper.setButtonType(nextButtonType);
        return nodeWrapper;
    }

    /**
     * @return the model classes associated with the given button type.
     * @throws NullPointerException if no classes are stored for the given type.
     */
    private static Class<?>[] getModelClasses(String nextButtonType) {
        TetradApplicationConfig config = TetradApplicationConfig.getInstance();
        SessionNodeConfig nodeConfig = config.getSessionNodeConfig(nextButtonType);
        if (nodeConfig == null) {
            throw new NullPointerException("There is no configuration for: " + nextButtonType);
        }

        return nodeConfig.getModels();
    }
}





