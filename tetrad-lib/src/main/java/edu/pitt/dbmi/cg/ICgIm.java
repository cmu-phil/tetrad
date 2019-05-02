/**
 * 
 */
package edu.pitt.dbmi.cg;

import java.util.List;

import edu.cmu.tetrad.data.Simulator;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;

/**
 * Apr 10, 2019 4:04:31 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public interface ICgIm extends Simulator, TetradSerializable {

    /**
     * @return the underlying Conditional Gaussian PM.
     */
	CgPm getCgPm();
	
    /**
     * @return the underlying DAG.
     */
    Graph getDag();

    /**
     * @return the number of mixed-parent discrete child nodes in the model.
     */
    int getNumMixedParentsDiscreteNodes();
	
    /**
     * @return the node corresponding to the given mixed-parent discrete child node index.
     */
    Node getMixedParentsDiscreteNode(int nodeIndex);

    /**
     * @param name the name of the node.
     * @return the node with the given name in the associated graph.
     */
    Node getNode(String name);

    /**
     * @param node the given mixed-parent discrete child node.
     * @return the index for that node, or -1 if the node is not in the
     * CgIm.
     */
    int getMixedParentsDiscreteNodeIndex(Node node);

    /**
     * @return the list of mixed-parent discrete child variable for this conditional Gaussian model.
     */
    List<Node> getMixedParentsDiscreteVariables();

    /**
     * @return the list of mixed-parent discrete child variable names for this conditional Gaussian model.
     */
    List<String> getMixedParentsDiscreteVariableNames();
}
