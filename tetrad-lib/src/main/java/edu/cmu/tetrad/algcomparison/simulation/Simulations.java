package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * A list of simulations to be compared.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Simulations implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The simulations.
     */
    private final List<Simulation> simulations = new ArrayList<>();

    /**
     * <p>Constructor for Simulations.</p>
     */
    public Simulations() {
    }

    /**
     * Adds an simulation.
     *
     * @param simulation The simulation to add.
     */
    public void add(Simulation simulation) {
        this.simulations.add(simulation);
    }

    /**
     * Returns the list of simulations.
     *
     * @return A copy of the list of simulations that have been added, in that order.
     */
    public List<Simulation> getSimulations() {
        return new ArrayList<>(this.simulations);
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }
}
