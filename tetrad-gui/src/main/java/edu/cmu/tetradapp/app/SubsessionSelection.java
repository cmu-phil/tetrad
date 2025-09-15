package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetradapp.model.EditorUtils;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.rmi.MarshalledObject;
import java.util.List;

/**
 * Holds a list of session nodes for cut/paste operations. Note that a deep clone of the session elements list is made
 * on creation, and once the data is retrieved, it is deleted.
 *
 * @author josephramsey
 */
final class SubsessionSelection implements Transferable {

    /**
     * The list of session nodes that constitutes the selection.
     */
    private final List sessionElements;

    /**
     * Supported dataflavors--only one.
     */
    private final DataFlavor[] dataFlavors = {
            new DataFlavor(SubsessionSelection.class, "Subsession Selection")};


    private int numPastes;

    /**
     * Constructs a new selection with the given list of session nodes.
     *
     * @param sessionElements a {@link java.util.List} object
     */
    public SubsessionSelection(List sessionElements) {
        if (sessionElements == null) {
            throw new NullPointerException(
                    "List of session elements must " + "not be null.");
        }

        for (Object sessionElement : sessionElements) {
            if (!(sessionElement instanceof GraphNode ||
                  sessionElement instanceof Edge)) {
                throw new IllegalArgumentException("Model node list contains " +
                                                   "an object that is not a GraphNode or an Edge: " +
                                                   sessionElement);
            }
        }

        try {
            this.sessionElements =
                    (List) new MarshalledObject(sessionElements).get();
        } catch (Exception e1) {
            e1.printStackTrace();
            throw new IllegalStateException("Could not clone.");
        }
    }

    /**
     * {@inheritDoc}
     */
    public Object getTransferData(DataFlavor flavor)
            throws UnsupportedFlavorException, IOException {
        if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }

        try {
            List returnList =
                    (List) new MarshalledObject(this.sessionElements).get();
            Point point = EditorUtils.getTopLeftPoint(returnList);
            point.translate(50, 50);
            this.numPastes++;
            return returnList;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return flavor.equals(getTransferDataFlavors()[0]);
    }

    /**
     * <p>getTransferDataFlavors.</p>
     *
     * @return an array of DataFlavor objects indicating the flavors the data can be provided in.  The array should be
     * ordered according to preference for providing the data (from most richly descriptive to least descriptive).
     */
    public DataFlavor[] getTransferDataFlavors() {
        return this.dataFlavors;
    }

    /**
     * <p>Getter for the field <code>numPastes</code>.</p>
     *
     * @return a int
     */
    public int getNumPastes() {
        return this.numPastes;
    }
}





