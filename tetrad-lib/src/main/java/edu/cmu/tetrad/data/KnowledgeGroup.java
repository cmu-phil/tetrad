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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * Represents a "Other Group" in Knowledge, which can be understood as: Group1 -> Group2 where there are
 * edges between all members of Group1 to Group2.
 * <p>
 * Immutable.
 *
 * @author Tyler Gibson
 */
public final class KnowledgeGroup implements TetradSerializable {
    static final long serialVersionUID = 23L;


    /**
     * The left group of variables.
     *
     * @serial - not null
     */
    private Set<String> fromGroup;


    /**
     * The right group of variables.
     *
     * @serial - not null
     */
    private Set<String> toGroup;


    /**
     * The type of group that this is (currently either required or forbidden).
     */
    private int type;


    /**
     * The types of groups (Can an enum be used instead?)
     */
    public final static int REQUIRED = 1;
    public final static int FORBIDDEN = 2;


    /**
     * Constructs a group given the type.
     *
     * @param type - the type
     */
    public KnowledgeGroup(int type, Set<String> from, Set<String> to) {
        if (type != REQUIRED && type != FORBIDDEN) {
            throw new NullPointerException("The given type needs to be either REQUIRED or FORBIDDEN");
        }
        if (from == null) {
            throw new NullPointerException("The from set must not be null");
        }
        if (to == null) {
            throw new NullPointerException("The to set must not be null");
        }
        if (intersect(from, to)) {
            throw new IllegalArgumentException("The from and to sets must not intersect");
        }
        this.fromGroup = new HashSet<>(from);
        this.toGroup = new HashSet<>(to);
        this.type = type;
    }


    /**
     * Constructs an empty instance of a knowledge group.
     */
    public KnowledgeGroup(int type) {
        if (type != REQUIRED && type != FORBIDDEN) {
            throw new NullPointerException("The given type needs to be either REQUIRED or FORBIDDEN");
        }
        this.type = type;
        this.fromGroup = Collections.emptySet();
        this.toGroup = Collections.emptySet();
    }


    //====================== Public Methods =============================//


    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static KnowledgeGroup serializableInstance() {
        return new KnowledgeGroup(REQUIRED, new HashSet<String>(0), new HashSet<String>(0));
    }


    public int getType() {
        return this.type;
    }


    /**
     * States whether this group is empty, that is there is no edges in it (Note there may be some
     * partial information though).
     */
    public boolean isEmpty() {
        return this.fromGroup.isEmpty() || this.toGroup.isEmpty();
    }


    public Set<String> getFromVariables() {
        return Collections.unmodifiableSet(this.fromGroup);
    }


    public Set<String> getToVariables() {
        return Collections.unmodifiableSet(this.toGroup);
    }


    /**
     * @return - edges.
     */
    public List<KnowledgeEdge> getEdges() {
        List<KnowledgeEdge> edges = new ArrayList<>(this.fromGroup.size() + this.toGroup.size());
        for (String from : this.fromGroup) {
            for (String to : this.toGroup) {
                edges.add(new KnowledgeEdge(from, to));
            }
        }
        return edges;
    }


    public boolean containsEdge(KnowledgeEdge edge) {
        return this.fromGroup.contains(edge.getFrom()) && this.toGroup.contains(edge.getTo());
    }


    /**
     * Computes a hashcode.
     */
    public int hashCode() {
        int hash = 37;
        hash += 17 * this.fromGroup.hashCode() + 37;
        hash += 17 * this.toGroup.hashCode() + 37;
        hash += 17 * Integer.valueOf(this.type).hashCode() + 37;
        return hash;
    }


    /**
     * States whether this group has a conflict with the given one.
     *
     * @return true iff there is a conflict.
     */
    public boolean isConflict(KnowledgeGroup group) {
        // if they have different types, then if they share an edge there is a conflict.
        if (this.type != group.type) {
            return intersect(this.fromGroup, group.fromGroup) && intersect(this.toGroup, group.toGroup);
        }
        // otherwise they either don't share edges or are the same type and no conflicts
        // exist (as at worst they will have edges in common).
        return false;
    }


    /**
     * Equals when they are the same type and have the same edges.
     */
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof KnowledgeGroup)) {
            return false;
        }

        KnowledgeGroup thatGroup = (KnowledgeGroup) o;

        return this.type == thatGroup.type && this.fromGroup.equals(thatGroup.fromGroup)
                && this.toGroup.equals(thatGroup.toGroup);

    }

    //=========================== Private Methods ==========================//


    /**
     * States whether the two have a non-empty intersection or not.
     */
    private static boolean intersect(Set<String> set1, Set<String> set2) {
        for (String var : set1) {
            if (set2.contains(var)) {
                return true;
            }
        }
        return false;
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
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.type != REQUIRED && this.type != FORBIDDEN) {
            throw new IllegalStateException("Type must be REQUIRED or FORBIDDEN");
        }

        if (fromGroup == null) {
            throw new NullPointerException();
        }

        if (toGroup == null) {
            throw new NullPointerException();
        }

    }


}



