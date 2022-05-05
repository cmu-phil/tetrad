///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;
import java.util.stream.IntStream;

/**
 * Stores clusters of variables for MimBuild, Purify, etc.
 *
 * @author Joseph Ramsey
 * @author Ricardo Silva
 */
public final class Clusters implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * This is used to store information on pure measurement models (when the
     * graph is a measurement/structural model). The information is stored
     * variable clusters, and is used by algorithm such as Purify and MIM Build
     * (R. Silva, 04/2003)
     *
     * @serial
     */
    private final Map<String, Integer> clusters;

    /**
     * Node names.
     *
     * @serial
     */
    private final Map<Integer, String> names;

    /**
     * The number of clusters represented. If there is no fixed upper bound, set
     * this to -1.
     *
     * @serial
     */
    private int numClusters = -1;

    //================================CONSTRUCTORS========================//

    /**
     * Constructs a blank knowledge object.
     */
    public Clusters() {
        this.clusters = new HashMap<>();
        this.names = new HashMap<>();
    }

    /**
     * Copy constructor.
     */
    public Clusters(Clusters clusters) {
        this.clusters = new HashMap<>(clusters.clusters);
        this.names = new HashMap<>(clusters.names);
        this.numClusters = clusters.numClusters;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static Clusters serializableInstance() {
        return new Clusters();
    }

    //===============================PUBLIC METHODS=======================//

    /**
     * Adds the given variable to the given index.  If a variable which is being
     * added is already in a index, it is moved to the new index. This
     * information is used specifically by algorithm such as Purify and MIM
     * Build. The first variation only put an Integer associated with the
     * index, i.e., the clusterings forms a partition where the integer
     * represents the index id for the corresponding variable. The second
     * variation associates a list of Integers with each observed variable. When
     * reading clustering information, one has to pay attention if the object
     * retrieved is an Integer or a list of Integers.
     *
     * @param index the index.
     * @param var   the variable (a String name). R. Silva (04/2003)
     */
    public void addToCluster(int index, String var) {
        if (isClustersBounded() && index >= getNumClusters()) {
            throw new IllegalArgumentException();
        }

        this.clusters.put(var, index);
    }

    /**
     * @return the list of edges not in any tier.
     */
    public List<String> getVarsNotInCluster(List<String> varNames) {
        List<String> notInCluster = new ArrayList<>(varNames);
        IntStream.range(0, getNumClusters()).mapToObj(this::getCluster).forEach(notInCluster::removeAll);
        return notInCluster;
    }

    /**
     * @return the number of measurement clusters for use in Purify and MIM
     * Build. R. Silva (04/2003)
     */
    public int getNumClusters() {
        if (!isClustersBounded()) {
            return numClustersStored();
        }

        return this.numClusters;
    }

    /**
     * Sets the number of clusters represented, or -1 if the number is allowed
     * to vary.
     */
    public void setNumClusters(int numClusters) {
        if (numClusters < -1) {
            throw new IllegalArgumentException();
        }

        this.numClusters = numClusters;
    }

    /**
     * @return a copy of the cluster map, which is a map from variable names to
     * integers.
     */
    public Map<String, Integer> getClusters() {
        return new HashMap<>(this.clusters);
    }

    /**
     * @param index the index of the desired index.
     * @return a copy of this index.
     */
    public List<String> getCluster(int index) {
        if (isClustersBounded() && index > getNumClusters()) {
            throw new IllegalArgumentException();
        }

        List<String> cluster = new LinkedList<>();

        for (String _varName : this.clusters.keySet()) {
            Integer _index = this.clusters.get(_varName);

            if ((_index) == index) {
                cluster.add(_varName);
            }
        }

        Collections.sort(cluster);
        return cluster;
    }

    public String getClusterName(int index) {
        if (isClustersBounded() && index > getNumClusters()) {
            throw new IllegalArgumentException();
        }

        String name = this.names.get(index);
        if (name == null) {
            name = newClusterName();
            this.names.put(index, name);
        }
        return name;
    }

    private boolean isClustersBounded() {
        return this.numClusters != -1;
    }

    public synchronized void setClusterName(int index, String name) {
        if (name == null) {
            throw new NullPointerException();
        }

        if (isClustersBounded() && index > getNumClusters()) {
            throw new IllegalArgumentException();
        }

        for (int i = 0; i < getNumClusters(); i++) {
            if (i == index) {
                continue;
            }
            String _name = this.names.get(i);
            if (name.equals(_name)) {
                throw new IllegalArgumentException(
                        "That is the name for cluster " + "#" + (i + 1) + ": " +
                                name);
            }
        }

        this.names.put(index, name);
    }

    /**
     * Removes the given variable from the clusters.
     */
    public void removeFromClusters(String var) {
        this.clusters.remove(var);
    }

    /**
     * Computes a hashcode.
     */
    public int hashCode() {
        int hash = 37;
        hash += 17 * this.clusters.hashCode() + 37;
        return hash;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof Clusters)) {
            return false;
        }

        Clusters clusters = (Clusters) o;
        return this.clusters.equals(clusters.clusters);
    }

    /**
     * @return the contents of this Knowledge object in String form.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("Clusters:");

        for (int i = 0; i < getNumClusters(); i++) {
            List<String> s = getCluster(i);

            buf.append("\n").append(i).append(":");

            for (Object value : s) {
                buf.append("\t").append(value);
            }
        }

        buf.append("\n");
        return buf.toString();
    }

    private String newClusterName() {
        Collection<String> values = this.names.values();
        int i = 0;

        while (true) {
            ++i;
            String name = "_L" + i;
            if (!values.contains(name)) {
                return name;
            }
        }
    }

    private int numClustersStored() {
        Collection<Integer> collection = this.clusters.values();
        int max = 0;

        for (Integer cluster : collection) {
            if (cluster + 1 > max) {
                max = cluster + 1;
            }
        }

        return max;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.clusters == null) {
            throw new NullPointerException();
        }
    }

    public boolean isEmpty() {
        return this.clusters.keySet().isEmpty();
    }
}




