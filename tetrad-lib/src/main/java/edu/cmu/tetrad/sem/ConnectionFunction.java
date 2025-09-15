package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.graph.Node;

/**
 * Created by IntelliJ IDEA. User: jdramsey Date: Oct 25, 2008 Time: 11:49:32 AM To change this template use File |
 * Settings | File Templates.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface ConnectionFunction {
    /**
     * <p>getInputNodes.</p>
     *
     * @return an array of {@link edu.cmu.tetrad.graph.Node} objects
     */
    Node[] getInputNodes();

    /**
     * <p>valueAt.</p>
     *
     * @param inputValues a double
     * @return a double
     */
    double valueAt(double... inputValues);
}



