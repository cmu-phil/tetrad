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

/**
 * A collection of public static methods to help construct SessionWrappers
 * manually.
 *
 * @author Joseph Ramsey
 */
final class SessionWrappers {

    public static Node addNode(SessionWrapper sessionWrapper, String nodeType,
            String nodeName, int centerX, int centerY) {
        SessionNodeWrapper node = getNewModelNode(nodeType, nodeName);
        node.setCenter(centerX, centerY);
        sessionWrapper.addNode(node);
        return node;
    }

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

        Class[] modelClasses = getModelClasses(nextButtonType);
        SessionNode newNode =
                new SessionNode(nextButtonType, name, modelClasses);
        SessionNodeWrapper nodeWrapper = new SessionNodeWrapper(newNode);
        nodeWrapper.setButtonType(nextButtonType);
        return nodeWrapper;
    }

    /**
     * @return the model classes associated with the given button type.
     *
     * @throws NullPointerException if no classes are stored for the given
     *                              type.
     */
    private static Class[] getModelClasses(String nextButtonType) {
        TetradApplicationConfig config = TetradApplicationConfig.getInstance();
        SessionNodeConfig nodeConfig = config.getSessionNodeConfig(nextButtonType);
        if (nodeConfig == null) {
            throw new NullPointerException("There is no configuration for: " + nextButtonType);
        }

        return nodeConfig.getModels();
    }
}





