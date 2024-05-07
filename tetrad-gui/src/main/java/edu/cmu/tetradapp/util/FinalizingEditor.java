package edu.cmu.tetradapp.util;

/**
 * Actions an editor needs to perform before closing. If it's OK to close the finalizeEditor method will return true;
 * otherwise, false.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface FinalizingEditor {
    /**
     * <p>finalizeEditor.</p>
     *
     * @return a boolean
     */
    boolean finalizeEditor();
}
