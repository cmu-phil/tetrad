package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.util.Parameters;

/**
 * Tags an class as having a getParaemters method.
 */
public interface HasModelParameters {
    Parameters getParameters();
}
