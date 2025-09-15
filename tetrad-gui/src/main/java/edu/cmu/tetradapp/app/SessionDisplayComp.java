package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.workbench.DisplayComp;

/**
 * The appearance of a session node.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface SessionDisplayComp extends DisplayComp {

    /**
     * <p>setAcronym.</p>
     *
     * @param acronym the acronym (e.g. "PC") for the node.
     */
    void setAcronym(String acronym);

    /**
     * <p>setHasModel.</p>
     *
     * @param b whether the node has a model--i.e. whether it should be rendered in the "filled" color or not.
     */
    void setHasModel(boolean b);


}



