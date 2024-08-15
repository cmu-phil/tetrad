package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A list of statistics and their utility weights.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Statistics implements TetradSerializable {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The list of statistics.
     */
    private final List<Statistic> statistics = new ArrayList<>();

    /**
     * The utility weights for the statistics.
     */
    private final Map<Statistic, Double> weights = new HashMap<>();

    /**
     * <p>Constructor for Statistics.</p>
     */
    public Statistics() {
    }

    /**
     * Adds a statistic.
     *
     * @param statistic The statistic to add.
     */
    public void add(Statistic statistic) {
        this.statistics.add(statistic);
    }

    /**
     * Sets the utility weight of the statistic by the given name.
     *
     * @param abbrebiation The abbreviation set in the statistic.
     * @param weight       The utility weight for that statistic.
     */
    public void setWeight(String abbrebiation, double weight) {
        if (weight < 0 || weight > 1) throw new IllegalArgumentException("Weight must be in [0, 1]: " + weight);

        boolean set = false;

        for (Statistic stat : this.statistics) {
            if (stat.getAbbreviation().equals(abbrebiation)) {
                this.weights.put(stat, weight);
                set = true;
            }
        }

        if (!set) {
            throw new IllegalArgumentException("No statistic has been added with that abbreviation: "
                                               + abbrebiation);
        }
    }

    /**
     * Return the list of statistics.
     *
     * @return A copy of this list, in the order added.
     */
    public List<Statistic> getStatistics() {
        return new ArrayList<>(this.statistics);
    }

    /**
     * The utility weight for the statistic.
     *
     * @param statistic The statistic.
     * @return The utility weight for it.
     */
    public double getWeight(Statistic statistic) {
        return this.weights.getOrDefault(statistic, 0.0);
    }

    /**
     * The number of statistics.
     *
     * @return This number.
     */
    public int size() {
        return this.statistics.size();
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
