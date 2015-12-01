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

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.*;
import java.util.*;

/**
 * Stores information about required and forbidden edges and common causes for
 * use in algorithms.  This information can be set edge by edge or else globally
 * via temporal tiers.  When setting temporal tiers, all edges from later tiers
 * to earlier tiers are forbidden. </p> For this class, all varNames are
 * referenced by name only.  This is because the same Knowledge object is
 * intended to plug into different graphs with nodes that possibly have the same
 * names.  Thus, if the Knowledge object forbids the edge X --> Y, then it
 * forbids any edge which connects a node named "X" to a node named "Y", even if
 * the underlying nodes themselves named "X" and "Y", respectively, are not the
 * same.
 *
 * @author Donald Crimbchin
 * @author Joseph Ramsey modifications 11/00.
 * @author Shane Harwood modifications Spring/01
 * @author Ricardo Silva modification 04/03
 * @author Tyler Gibson modifications 2/07
 */
public final class Knowledge implements TetradSerializable, IKnowledge {
    static final long serialVersionUID = 23L;

    /**
     * The set of variable names this knowledge is over.
     */
    private SortedSet<String> variables = new TreeSet<String>();

    /**
     * This is a representation of the temporal tiers, a map from Strings to
     * Integers.  Each variable name is mapped to exactly one tier (an Integer).
     * To determine whether an edge v1 --> v2 is forbidden by the tiers, find
     * tierMap(v1) and tierMap(v2); if tierMap(v1) > tierMap(v2), the edge is
     * forbidden by tiers.  If either tierMap(v1) or tierMap(v2) is null, the
     * edge is not forbidden by tiers, and if tierMap(v1) <= tierMap(v2), the
     * edge is not forbidden by tiers. In this representation, in order to get
     * all the varNames of a given tier, iterate through the domain of the map
     * and observe which elements map to the Integer value of that tier.
     *
     * @serial
     */
    private Map<String, Integer> tierMap;

    /**
     * This is the set of edges explicitly required by the user
     * <p>
     * Note: Should really be called "explicitlyRequiredEdges" (can't change though).
     *
     * @serial
     */
    private Set<KnowledgeEdge> requiredEdges;


    /**
     * This is the set of all edges that are required, including edges explicitly required and
     * required by groups.
     *
     * @serial
     */
    private Set<KnowledgeEdge> allRequiredEdges = new HashSet<KnowledgeEdge>();


    /**
     * This is the set of all edges forbidden, including edges explicitly
     * forbidden, forbidden by groups as well as edges forbidden by temporal tiers.
     *
     * @serial
     * @deprecated }
     */
    private Set<KnowledgeEdge> forbiddenEdges;

    /**
     * This is the set of edges explicitly forbidden by the user.
     *
     * @serial
     */
    private Set<KnowledgeEdge> explicitlyForbiddenEdges;

    /**
     * This is the set of edges explicitly not forbidden. Only one of this and\
     * ExplicitlyForbiddenEdges can be non-null.
     */
    private Set<KnowledgeEdge> expicitlyNotForbidden;

    /**
     * This is the set of required common causes.
     *
     * @serial
     */
    private Set<KnowledgeEdge> requiredCommonCauses;

    /**
     * This is the set of forbidden common causes.
     *
     * @serial
     */
    private Set<KnowledgeEdge> forbiddenCommonCauses;

    /**
     * True iff tier 0 consists of exogenous variables, so that all edges among
     * tier 0 variables are forbidden.
     *
     * @serial
     */
    private Set<Integer> tiersForbiddenWithin = new HashSet<Integer>();


    /**
     * The knowledge groups, this contains all the grouping information.
     *
     * @serial
     */
    private List<KnowledgeGroup> knowledgeGroups = new ArrayList<KnowledgeGroup>();


    /**
     * When the graph for this knowledge is displayed, it defauls to the
     * knowledge layout if this is true.
     *
     * @serial
     */
    private boolean defaultToKnowledgeLayout = false;
    private boolean lagged = false;

    //================================CONSTRUCTORS========================//


    /**
     * Constructs a blank knowledge object.
     */
    public Knowledge() {
        clearExplicitKnowledge();
        clearTiers();
    }

    /**
     * Copy constructor.
     */
    public Knowledge(Knowledge knowledge) {
        this.tierMap = new HashMap<String, Integer>(knowledge.tierMap);
        this.allRequiredEdges = new HashSet<KnowledgeEdge>(knowledge.allRequiredEdges);
        this.requiredEdges =
                new HashSet<KnowledgeEdge>(knowledge.requiredEdges);
//        this.setForbiddenEdges(new HashSet<KnowledgeEdge>(knowledge.getForbiddenEdges()));
        this.explicitlyForbiddenEdges =
                new HashSet<KnowledgeEdge>(knowledge.explicitlyForbiddenEdges);
        this.requiredCommonCauses =
                new HashSet<KnowledgeEdge>(knowledge.requiredCommonCauses);
        this.forbiddenCommonCauses =
                new HashSet<KnowledgeEdge>(knowledge.forbiddenCommonCauses);
        this.tiersForbiddenWithin =
                new HashSet<Integer>(knowledge.tiersForbiddenWithin);
        this.defaultToKnowledgeLayout = knowledge.defaultToKnowledgeLayout;
        this.knowledgeGroups = knowledge.knowledgeGroups;
        this.lagged = knowledge.lagged;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static IKnowledge serializableInstance() {
        return new Knowledge2();
    }

    //===============================PUBLIC METHODS=======================//

    /**
     * Adds the given variable to the given tier.  If a variable is added which
     * is already in a tier, it is moved to the new tier.
     *
     * @param tier a (usually) non-negative integer. Negative integers may be
     *             specified without breaking anything.
     * @param var  the name of a variable to put in that tier.
     */
    public final void addToTier(int tier, String var) {
        if (!variables.contains(var)) {
            addVariable(var);
        }

        if (tier < 0) {
            throw new IllegalArgumentException();
        }

        if (var == null) {
            throw new NullPointerException();
        }

        for (String var2 : tierMap.keySet()) {
            int tier2 = tierMap.get(var2);

            if (tier2 < tier) {
                if (isRequired(var, var2)) {
                    throw new IllegalStateException("Edge " + var + "-->" +
                            var2 +
                            " is a required edge. Please remove that requirement " +
                            "or adjust \nthe tiers so that " + var + "-->" +
                            var2 + " will not be forbidden.");
                }
            } else if (tier < tier2) {
                if (isRequired(var2, var)) {
                    throw new IllegalStateException("Edge " + var2 + "-->" +
                            var +
                            " is a required edge. Please remove that requirement " +
                            "or adjust \nthe tiers so that " + var2 + "-->" +
                            var + " will not be forbidden.");
                }
            }
        }

        tierMap.put(var, tier);
//        this.forbiddenEdges = null;
//        generateForbiddenEdgeList();
    }

    /**
     * Puts a variable into tier i if its name is xxx:i for some xxx and some
     * i.
     *
     * @param varNames
     */
    public final void addToTiersByVarNames(List<String> varNames) {
        if (!variables.containsAll(varNames)) {
            for (String varName : varNames) {
                addVariable(varName);
            }
        }

        for (Object varName : varNames) {
            String node = (String) varName;
            int index = node.lastIndexOf(":t");

            if (index != -1) {
                String substring = node.substring(index + 2);
                addToTier(new Integer(substring), node);
            }
        }
    }


    /**
     * @return - all the knowledge groups.
     */
    public List<KnowledgeGroup> getKnowledgeGroups() {
        return Collections.unmodifiableList(this.knowledgeGroups);
    }


    /**
     * Removes the <code>KnowledgeGroup</code> at the given index.
     *
     * @param index
     */
    public void removeKnowledgeGroup(int index) {
        this.knowledgeGroups.remove(index);
        this.generateRequiredEdgeSet();
//        this.forbiddenEdges = null;
//        this.generateForbiddenEdgeList();

    }

    /**
     * Adds the given <code>KnowledgeGroup</code>.
     *
     * @param group
     */
    public void addKnowledgeGroup(KnowledgeGroup group) {
        // check against getModel groups
        for (int i = 0; i < this.knowledgeGroups.size(); i++) {
            KnowledgeGroup g = this.knowledgeGroups.get(i);
            if (group.isConflict(g)) {
                throw new IllegalArgumentException("Conflict with group at index " + (i + 1));
            }
        }
        // check against other required/forbidden edges.
        List<KnowledgeEdge> edges = group.getEdges();
        if (group.getType() == KnowledgeGroup.REQUIRED) {
            for (KnowledgeEdge edge : edges) {
                this.checkAgainstForbidden(edge);
            }
        } else if (group.getType() == KnowledgeGroup.FORBIDDEN) {
            for (KnowledgeEdge edge : edges) {
                this.checkAgainstRequired(edge);
            }
        }
        this.knowledgeGroups.add(group);
        if (!group.isEmpty()) {
            this.generateRequiredEdgeSet();
//            this.forbiddenEdges = null;
//            this.generateForbiddenEdgeList();
        }
    }


    /**
     * Replaces the <code>KnowledgeGroup</code> at the given index.
     *
     * @param index
     * @param group
     */
    public void setKnowledgeGroup(int index, KnowledgeGroup group) {
        // check against getModel groups
        for (int i = 0; i < this.knowledgeGroups.size(); i++) {
            if (i != index) {
                KnowledgeGroup g = this.knowledgeGroups.get(i);
                if (group.isConflict(g)) {
                    throw new IllegalArgumentException("Changes to the knowedge group at " + (index + 1) +
                            " conflict with the knowledge group at " + (i + 1));
                }
            }
        }
        // check against other required/forbidden edges.
        List<KnowledgeEdge> edges = group.getEdges();
        if (group.getType() == KnowledgeGroup.REQUIRED) {
            for (KnowledgeEdge edge : edges) {
                this.checkAgainstForbidden(edge);
            }
        } else if (group.getType() == KnowledgeGroup.FORBIDDEN) {
            for (KnowledgeEdge edge : edges) {
                this.checkAgainstRequired(edge);
            }
        }
        this.knowledgeGroups.set(index, group);
        this.generateRequiredEdgeSet();
//        this.forbiddenEdges = null;
//        this.generateForbiddenEdgeList();
    }

    /**
     * Clears the tier information.
     */
    private void clearTiers() {
        tierMap = new HashMap<String, Integer>();
        tiersForbiddenWithin = new HashSet<Integer>();
    }

    /**
     * @return an iterator over the forbidden common causes.
     */
    public final Iterator<KnowledgeEdge> forbiddenCommonCausesIterator() {
        return Collections.unmodifiableSet(forbiddenCommonCauses).iterator();
    }

    /**
     * Iterator over the KnowledgeEdge's representing forbidden edges.
     */
    public final Iterator<KnowledgeEdge> forbiddenEdgesIterator() {
        return getForbiddenEdges().iterator();
//        return Collections.unmodifiableSet(getForbiddenEdges()).iterator();
    }


    /**
     * Iterator over the knowledge's explicitly forbidden edges.
     */
    public final Iterator<KnowledgeEdge> explicitlyForbiddenEdgesIterator() {
        return Collections.unmodifiableSet(this.explicitlyForbiddenEdges).iterator();
    }


    /**
     * @return the list of edges not in any tier.
     */
    public final List<String> getVariablesNotInTiers() {
        List<String> notInTier = new ArrayList<String>(variables);

        for (int i = 0; i < getNumTiers(); i++) {
            List<String> tier = getTier(i);
            notInTier.removeAll(tier);
        }

        return notInTier;
    }

    /**
     * @param tier the index of the desired tier.
     * @return a copy of this tier.
     */
    public final List<String> getTier(int tier) {
        List<String> _tier = new LinkedList<String>();

        for (String o : tierMap.keySet()) {
            Integer v = tierMap.get(o);

            if (v.equals(tier) && !_tier.contains(o)) {
                _tier.add(o);
            }
        }

        Collections.sort(_tier);
        return _tier;
    }

    /**
     * @return the number of temporal tiers.
     */
    public final int getNumTiers() {
        List<Integer> s = new ArrayList<Integer>(tierMap.values());
        int max = -1;

        for (Integer value : s) {
            if (value > max) {
                max = value;
            }
        }

        return max + 1;
    }

    /**
     * Determines whether a common case is forbidden between two varNames.
     */
    public final boolean commonCauseForbidden(String var1, String var2) {
        return forbiddenCommonCauses.contains(new KnowledgeEdge(var1, var2));
    }


    /**
     * Determines whether the dge var1 --> var2 is explicitly required.
     *
     * @param var1
     * @param var2
     * @return
     */
    public final boolean edgeExplicitlyRequired(String var1, String var2) {
        return this.requiredEdges.contains(new KnowledgeEdge(var1, var2));
    }


    /**
     * Determines whether edge is explicitly required.
     *
     * @param edge
     * @return
     */
    public final boolean edgeExplicitlyRequired(KnowledgeEdge edge) {
        return this.requiredEdges.contains(edge);
    }


    /**
     * Determines whether the edge var1 --> var2 is forbidden, either explicitly, by tiers
     * or by groups.
     */
    public final boolean isForbidden(String var1, String var2) {
        if (isForbiddenByTiers(var1, var2)) {
            return true;
        } else {
            KnowledgeEdge edge = new KnowledgeEdge(var1, var2);
            return explicitlyForbiddenEdges.contains(edge);
        }
    }

    /**
     * Determines whether the edge var1 --> var2 is required either explicitly or by grups.
     */
    public final boolean isRequired(String var1, String var2) {
        KnowledgeEdge edge = new KnowledgeEdge(var1, var2);
        return this.allRequiredEdges.contains(edge);
    }


    /**
     * Determines whether the edge var1 --> var2 is required by a knowledge group.
     */
    public final boolean isRequiredByGroups(String var1, String var2) {
        KnowledgeEdge edge = new KnowledgeEdge(var1, var2);
        for (KnowledgeGroup group : this.knowledgeGroups) {
            if (group.getType() == KnowledgeGroup.REQUIRED && group.containsEdge(edge)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Determines whether the edge var1 --> var2 is forbidden by a knowledge group.
     */
    public final boolean isForbiddenByGroups(String var1, String var2) {
        KnowledgeEdge edge = new KnowledgeEdge(var1, var2);
        for (KnowledgeGroup group : this.knowledgeGroups) {
            if (group.getType() == KnowledgeGroup.FORBIDDEN && group.containsEdge(edge)) {
                return true;
            }
        }
        return false;
    }


    /**
     * @return true iff no edge between x and y is required.
     */
    public final boolean noEdgeRequired(String x, String y) {
        return !(isCommonCauseRequired(x, y) || isRequired(x, y) ||
                isRequired(y, x));
    }


    /**
     * Determines whether a common cause is required between two varNames.
     */
    private boolean isCommonCauseRequired(String var1, String var2) {
        return requiredCommonCauses.contains(new KnowledgeEdge(var1, var2));
    }

    /**
     * Determines whether the edge var1 --> var2 is forbidden by the temporal
     * tiers.
     */
    @SuppressWarnings({"SimplifiableIfStatement"})
    public final boolean isForbiddenByTiers(String var1, String var2) {
        if (var1.equals(var2)) {
            throw new IllegalArgumentException();
        }

        Integer tier1 = tierMap.get(var1);
        Integer tier2 = tierMap.get(var2);

        if ((tier1 == null) || (tier2 == null)) {
            return false;
        } else if (tier1.equals(tier2) && isTierForbiddenWithin(tier1)) {
            return true;
        } else {
            return tier1 > tier2;
        }
    }

    /**
     * @return true if there is no background knowledge recorded.
     */
    public final boolean isEmpty() {
        return this.allRequiredEdges.isEmpty() && getForbiddenEdges().isEmpty() &&
                requiredCommonCauses.isEmpty() &&
                forbiddenCommonCauses.isEmpty() && this.isGroupKnowledgeEmpty();
    }

    /**
     * Saves a knowledge file in tetrad2 format (almost--only does temporal
     * tiers currently). Format:
     * <pre>
     * /knowledge
     * addtemporal
     * 0 x1 x2
     * 1 x3 x4
     * 4 x5
     * </pre>
     */
    public static void saveKnowledge(IKnowledge knowledge, Writer out)
            throws IOException {
        StringBuilder buf = new StringBuilder();
        buf.append("/knowledge");

        buf.append("\naddtemporal");

        for (int i = 0; i < knowledge.getNumTiers(); i++) {
            String forbiddenWithin =
                    knowledge.isTierForbiddenWithin(i) ? "*" : "";

            buf.append("\n").append(i + 1).append(forbiddenWithin).append(" ");

            List<String> tier = knowledge.getTier(i);

            for (Object aTier : tier) {
                String name = (String) aTier;
                buf.append(name).append(" ");
            }
        }

        buf.append("\n\nforbiddirect");

        for (Iterator<KnowledgeEdge> i =
             knowledge.forbiddenEdgesIterator(); i.hasNext(); ) {
            KnowledgeEdge pair = i.next();
            String from = pair.getFrom();
            String to = pair.getTo();

            if (knowledge.isForbiddenByTiers(from, to)) {
                continue;
            }

            buf.append("\n").append(from).append(" ").append(to);
        }

        buf.append("\n\nrequiredirect");

        for (Iterator<KnowledgeEdge> i =
             knowledge.requiredEdgesIterator(); i.hasNext(); ) {
            KnowledgeEdge pair = i.next();
            String from = pair.getFrom();
            String to = pair.getTo();
            buf.append("\n").append(from).append(" ").append(to);
        }

        out.write(buf.toString());
        out.flush();
    }


    /**
     * Iterator over the KnowledgeEdge's representing required common causes.
     */
    public final Iterator<KnowledgeEdge> requiredCommonCausesIterator() {
        return Collections.unmodifiableSet(requiredCommonCauses).iterator();
    }

    /**
     * Iterator over the KnowledgeEdge's representing required edges.
     */
    public final Iterator<KnowledgeEdge> requiredEdgesIterator() {
        return Collections.unmodifiableSet(this.allRequiredEdges).iterator();
    }

    /**
     * Iterator over the KnowledgeEdge's explicitly required edges.
     */
    public final Iterator<KnowledgeEdge> explicitlyRequiredEdgesIterator() {
        return Collections.unmodifiableSet(this.requiredEdges).iterator();
    }


    /**
     * Marks the edge var1 --> var2 as forbid.
     */
    public final void setForbidden(String var1, String var2) {
        if (var1.equals(var2)) {
            throw new IllegalArgumentException();
        }

        if (isForbidden(var1, var2)) {
            return;
        }
        if (this.isRequiredByGroups(var1, var2)) {
            throw new IllegalStateException("The edge " + var1 + "-->" + var2 +
                    " is required by a knowledge group. Please remove that requirement first.");
        }

        if (isRequired(var1, var2)) {
            throw new IllegalStateException("The edge " + var1 + "-->" +
                    var2 + " is already required. Please first remove that requirement.");
        }

        explicitlyForbiddenEdges.add(new KnowledgeEdge(var1, var2));
//            this.forbiddenEdges = null;
        generateForbiddenEdgeList();
    }

    /**
     * Marks the edge var1 --> var2 as forbid.
     */
    public final void removeForbidden(String var1, String var2) {
        if (var1.equals(var2)) {
            throw new IllegalArgumentException();
        }

        if (isForbiddenByTiers(var1, var2)) {
            throw new IllegalStateException("The edge " + var1 + "-->" +
                    var2 + " is forbidden by tiers. Please adjust tiers first.");
        }

        explicitlyForbiddenEdges.remove(new KnowledgeEdge(var1, var2));
//            this.forbiddenEdges = null;
        generateForbiddenEdgeList();
    }


    /**
     * Marks the edge var1 --> var2 as required.
     */
    public final void setRequired(String var1, String var2) {
        if (isForbiddenByTiers(var1, var2)) {
            throw new IllegalArgumentException("The edge " + var1 +
                    " --> " + var2 +
                    " is forbidden by temporal tiers. Please adjust tiers first.");
        }
        if (this.isForbiddenByGroups(var1, var2)) {
            throw new IllegalArgumentException("The edge " + var1 + " --> " +
                    var2 + "is forbidden by a knowledge group. Please remove this requirement first");
        }

        if (isForbidden(var1, var2)) {
            throw new IllegalArgumentException("The edge " + var1 +
                    " --> " + var2 +
                    " has been forbidden explicitly. Please adjust that " +
                    "requirement first.");
        }

        KnowledgeEdge edge = new KnowledgeEdge(var1, var2);
        if (!requiredEdges.contains(edge)) {
            requiredEdges.add(edge);
            this.generateRequiredEdgeSet();
        }
    }

    /**
     * Marks the edge var1 --> var2 as not required.
     */
    public final void removeRequired(String var1, String var2) {
        if (requiredEdges.contains(new KnowledgeEdge(var1, var2))) {
            requiredEdges.remove(new KnowledgeEdge(var1, var2));
            this.generateRequiredEdgeSet();
        }
    }


    /**
     * Removes the given variable from the given tier.
     */
    public final void removeFromTiers(String var) {
        tierMap.remove(var);
//        this.forbiddenEdges = null;
//        generateForbiddenEdgeList();
    }


    public final void setTierForbiddenWithin(int tier, boolean forbidden) {
        if (forbidden) {
            Iterator<KnowledgeEdge> iterator = requiredEdgesIterator();

            while (iterator.hasNext()) {
                KnowledgeEdge edge = iterator.next();
                System.out.println("Required edge: " + edge);

                if (getTier(tier).contains(edge.getFrom())
                        && getTier(tier).contains(edge.getTo())) {
                    throw new IllegalStateException("Edge " + edge + " is required.");
                }
            }

            this.tiersForbiddenWithin.add(tier);
        } else {
            this.tiersForbiddenWithin.remove(tier);
        }

//        this.forbiddenEdges = null;
//        generateForbiddenEdgeList();
    }

    public final boolean isTierForbiddenWithin(int tier) {
        return this.tiersForbiddenWithin.contains(tier);
    }

    public final int getMaxTierForbiddenWithin() {
        int max = -1;

        for (Integer aTiersForbiddenWithin : this.tiersForbiddenWithin) {
            if (aTiersForbiddenWithin > max) {
                max = aTiersForbiddenWithin;
            }
        }

        return max;
    }

    public final void setDefaultToKnowledgeLayout(
            boolean defaultToKnowledgeLayout) {
        this.defaultToKnowledgeLayout = defaultToKnowledgeLayout;
    }

    public final boolean isDefaultToKnowledgeLayout() {
        return defaultToKnowledgeLayout;
    }


    /**
     * Removes explicit knowledge and tier information.
     */
    public final void clear() {
        clearExplicitKnowledge();
        clearTiers();
    }

    /**
     * Computes a hashcode.
     */
    public final int hashCode() {
        int hash = 37;
        hash += 17 * this.tierMap.hashCode() + 37;
        hash += 17 * this.requiredEdges.hashCode() + 37;
        hash += 17 * this.allRequiredEdges.hashCode() + 37;
//        hash += 17 * this.getForbiddenEdges().hashCode() + 37;
        hash += 17 * this.explicitlyForbiddenEdges.hashCode() + 37;
        hash += 17 * this.requiredCommonCauses.hashCode() + 37;
        hash += 17 * this.forbiddenCommonCauses.hashCode() + 37;
        hash += 17 * this.tiersForbiddenWithin.hashCode() + 37;
        hash += 17 * this.knowledgeGroups.hashCode() + 37;
        hash += 17 * Boolean.valueOf(this.defaultToKnowledgeLayout).hashCode() + 37;
        return hash;
    }

    public final boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof Knowledge)) {
            return false;
        }

        Knowledge knowledge = (Knowledge) o;

        if (!(this.tierMap.equals(knowledge.tierMap))) {
            return false;
        }
        if (!this.allRequiredEdges.equals(knowledge.allRequiredEdges)) {
            return false;
        }

        if (!(this.requiredEdges.equals(knowledge.requiredEdges))) {
            return false;
        }
//        if (!(this.getForbiddenEdges().equals(knowledge.getForbiddenEdges()))) {
//            return false;
//        }
        if (!(this.explicitlyForbiddenEdges.equals(
                knowledge.explicitlyForbiddenEdges))) {
            return false;
        }
        if (!(this.requiredCommonCauses.equals(knowledge.requiredCommonCauses))) {
            return false;
        }
        if (!(this.forbiddenCommonCauses.equals(
                knowledge.forbiddenCommonCauses))) {
            return false;
        }
        if (!(this.tiersForbiddenWithin.equals(knowledge.tiersForbiddenWithin))) {
            return false;
        }

        if (!this.knowledgeGroups.equals(knowledge.knowledgeGroups)) {
            return false;
        }

        return this.defaultToKnowledgeLayout == knowledge
                .defaultToKnowledgeLayout;
    }

    /**
     * @return the contents of this Knowledge object in String form.
     */
    public final String toString() {
        try {
            CharArrayWriter out = new CharArrayWriter();
            Knowledge.saveKnowledge(this, out);
            return out.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Could not render knowledge.");
        }
    }

    @Override
    public IKnowledge copy() {
        return new Knowledge(this);
    }

    //===========================PRIVATE METHODS==========================//

    private boolean isGroupKnowledgeEmpty() {
        for (KnowledgeGroup group : this.knowledgeGroups) {
            if (!group.isEmpty()) {
                return false;
            }
        }
        return true;
    }


    private void checkAgainstRequired(KnowledgeEdge edge) {
        if (this.requiredEdges.contains(edge)) {
            throw new IllegalArgumentException("The edge " + edge.getFrom() + " --> " + edge.getTo() +
                    " is already required. Please first remove that requirement.");
        }
    }


    private void checkAgainstForbidden(KnowledgeEdge edge) {
        if (isForbiddenByTiers(edge.getFrom(), edge.getTo())) {
            throw new IllegalArgumentException("The edge " + edge.getFrom() +
                    " --> " + edge.getTo() +
                    " is forbidden by temporal tiers. Please adjust tiers first.");
        }

        if (this.explicitlyForbiddenEdges.contains(edge)) {
            throw new IllegalArgumentException("The edge " + edge.getFrom() +
                    " --> " + edge.getTo() +
                    " has been forbidden explicitly. Please adjust that " +
                    "requirement first.");
        }
    }


    /**
     * Sets whether a common cause is forbidden along an edge connecting var1
     * and var2.
     */
    private void setCommonCauseForbidden(String var1, String var2,
                                         boolean forbidden) {
        KnowledgeEdge edge1 = new KnowledgeEdge(var1, var2);
        KnowledgeEdge edge2 = new KnowledgeEdge(var2, var1);

        if (forbidden) {
            setCommonCauseRequired(var1, var2, false);
            forbiddenCommonCauses.add(edge1);
            forbiddenCommonCauses.add(edge2);
        } else {
            forbiddenCommonCauses.remove(edge1);
            forbiddenCommonCauses.remove(edge2);
        }
    }

    /**
     * Sets whether a common cause is required along an edge connecting var1 and
     * var2.
     */
    private void setCommonCauseRequired(String var1, String var2,
                                        boolean required) {
        KnowledgeEdge edge1 = new KnowledgeEdge(var1, var2);
        KnowledgeEdge edge2 = new KnowledgeEdge(var2, var1);

        if (required) {
            setCommonCauseForbidden(var1, var2, false);
            requiredCommonCauses.add(edge1);
            requiredCommonCauses.add(edge2);
        } else {
            requiredCommonCauses.remove(edge1);
            requiredCommonCauses.remove(edge2);
        }
    }

    /**
     * Clears the explicit knowledge information (that is, all of the knowledge
     * about edges other than tiers and clusters).
     */
    private void clearExplicitKnowledge() {
        requiredEdges = new HashSet<KnowledgeEdge>();
//        setForbiddenEdges(new HashSet<KnowledgeEdge>());
        explicitlyForbiddenEdges = new HashSet<KnowledgeEdge>();
        requiredCommonCauses = new HashSet<KnowledgeEdge>();
        forbiddenCommonCauses = new HashSet<KnowledgeEdge>();
        this.knowledgeGroups = new ArrayList<KnowledgeGroup>();
        this.allRequiredEdges = new HashSet<KnowledgeEdge>();
        tierMap = new HashMap<String, Integer>();
    }


    /**
     * Generates the <code>allRequiredEdges</code> set.
     */
    private void generateRequiredEdgeSet() {
        this.allRequiredEdges.clear();
        // add edges required by knowledge groups.
        // add edges forbidden by knowledge groups.
        for (KnowledgeGroup group : this.knowledgeGroups) {
            if (group.getType() == KnowledgeGroup.REQUIRED) {
                this.allRequiredEdges.addAll(group.getEdges());
            }
        }
        this.allRequiredEdges.addAll(this.requiredEdges);
    }


    /**
     * Method generateForbiddenEdgeList
     */
    private Set<KnowledgeEdge> generateForbiddenEdgeList() {
//        getForbiddenEdges().clear();
        Set<KnowledgeEdge> forbiddenEdges = new HashSet<KnowledgeEdge>();

        // add edges forbidden by tiers.
        List<String> vars = new ArrayList<String>(tierMap.keySet());
        for (int i = 0; i < vars.size(); i++) {
            for (int j = 0; j < vars.size(); j++) {
                if (i == j) {
                    continue;
                }

                String var1 = vars.get(i);
                String var2 = vars.get(j);

                if (isForbiddenByTiers(var1, var2)) {
                    forbiddenEdges.add(new KnowledgeEdge(var1, var2));
                }
            }
        }
        // add edges forbidden by knowledge groups.
        for (KnowledgeGroup group : this.knowledgeGroups) {
            if (group.getType() == KnowledgeGroup.FORBIDDEN) {
                forbiddenEdges.addAll(group.getEdges());
            }
        }

        return forbiddenEdges;

//        getForbiddenEdges().addAll(explicitlyForbiddenEdges);
    }

    private static String readLineSkippingComments(BufferedReader in,
                                                   String commentIndicator, int[] lineNo) throws IOException {
        if (commentIndicator == null) {
            lineNo[0]++;
            return in.readLine();
        } else {
            String line;

            while ((line = in.readLine()) != null) {
                lineNo[0]++;
                if (!isCommentLine(line, commentIndicator)) {
                    return line;
                }
            }

            return null;
        }
    }

    private static boolean isCommentLine(String line, String commentIndicator) {
        return line.startsWith(commentIndicator) || "".equals(line);
    }

    private static String substitutePeriodsForSpaces(String s) {
        return s.replaceAll(" ", ".");
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

        if (requiredEdges == null) {
            throw new NullPointerException();
        }

//        if (getForbiddenEdges() == null) {
//            throw new NullPointerException();
//        }

        if (explicitlyForbiddenEdges == null) {
            throw new NullPointerException();
        }

        if (requiredCommonCauses == null) {
            throw new NullPointerException();
        }

        if (forbiddenCommonCauses == null) {
            throw new NullPointerException();
        }

        if (tierMap == null) {
            throw new NullPointerException();
        }

        if (this.knowledgeGroups == null) {
            this.knowledgeGroups = new ArrayList<KnowledgeGroup>();
        }
        // if null then build it
        if (this.allRequiredEdges == null) {
            this.allRequiredEdges = new HashSet<KnowledgeEdge>();
            this.generateRequiredEdgeSet();
        }
        // There was an issue with an old session where all the edges forbidden by tiers
        // were also forbidden explicitly. If this happens sanitize matters by
        // removing the edges forbidden explicitly that are already forbidden by tiers.
//        Iterator<KnowledgeEdge> forbidden = this.explicitlyForbiddenEdges.iterator();
//        while(forbidden.hasNext()){
//            KnowledgeEdge edge = forbidden.next();
//            if(this.isForbiddenByTiers(edge.getFrom(), edge.getTo())){
//                forbidden.remove();
//            }
//        }

    }


    public boolean isViolatedBy(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            if (!edge.isDirected()) {
                continue;
            }

            Node from = Edges.getDirectedEdgeTail(edge);
            Node to = Edges.getDirectedEdgeHead(edge);

            if (isForbidden(from.getName(), to.getName())) {
                System.out.println("Edge forbidden: " + edge);
                return true;
            }
        }

        return false;
    }

    public void setTier(int tier, List<String> vars) {
        for (String varName : vars) {
            tierMap.put(varName, tier);
        }

//        this.forbiddenEdges = null;
//        generateForbiddenEdgeList();
    }

    /**
     * Adds the given variable name to knowledge. Duplicates are ignored.
     */
    public void addVariable(String varName) {
        variables.add(varName);
    }

    public void removeVariable(String varName) {
        variables.remove(varName);
        tierMap.remove(varName);
        removeFrom(varName, requiredEdges);
        removeFrom(varName, this.allRequiredEdges);
//        removeFrom(varName, this.getForbiddenEdges());
        removeFrom(varName, this.forbiddenCommonCauses);
        removeFrom(varName, this.explicitlyForbiddenEdges);

        for (KnowledgeGroup group : knowledgeGroups) {
            group.getFromVariables().remove(varName);
            group.getToVariables().remove(varName);
        }
    }

    private void removeFrom(String varName, Set<KnowledgeEdge> set) {
        for (KnowledgeEdge edge : set) {
            if (edge.getFrom().equals(varName) || edge.getTo().equals(varName)) {
                set.remove(edge);
            }
        }
    }

    public List<String> getVariables() {
        return new ArrayList<String>(variables);
    }

    private Set<KnowledgeEdge> getForbiddenEdges() {
        return generateForbiddenEdgeList();
//        if (forbiddenEdges == null) {
//            generateForbiddenEdgeList();
//        }
//
//        return forbiddenEdges;
    }

//    private void setForbiddenEdges(Set<KnowledgeEdge> forbiddenEdge) {
//        this.forbiddenEdges = forbiddenEdge;
//    }

    public boolean isLagged() {
        return lagged;
    }

    public void setLagged(boolean lagged) {
        this.lagged = lagged;
    }
}




