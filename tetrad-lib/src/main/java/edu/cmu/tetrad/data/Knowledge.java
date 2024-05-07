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

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.Serial;
import java.rmi.MarshalledObject;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Stores information about required and forbidden edges and common causes for use in algorithm. This information can be
 * set edge by edge or else globally via temporal tiers. When setting temporal tiers, all edges from later tiers to
 * earlier tiers are forbidden.
 * <p>
 * For this class, all variable names are referenced by name only. This is because the same Knowledge object is intended
 * to plug into different graphs with MyNodes that possibly have the same names. Thus, if the Knowledge object forbids
 * the edge X --&gt; Y, then it forbids any edge which connects a MyNode named "X" to a MyNode named "Y", even if the
 * underlying MyNodes themselves named "X" and "Y", respectively, are not the same.
 * <p>
 * In place of variable names, wildcard expressions containing the wildcard '*' may be substituted. These will be
 * matched to as many myNodes as possible. The '*' wildcard matches any string of consecutive characters up until the
 * following character is encountered. Thus, "X*a" will match "X123a" and "X45a".
 *
 * @author josephramsey
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public final class Knowledge implements TetradSerializable {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The comma delimiter.
     */
    private static final Pattern COMMAN_DELIM = Pattern.compile(",");

    /**
     * The variable names.
     */
    private final Set<String> variables;

    /**
     * This needs to be a list for backward compatibility. Need to check when adding a new spec whether it's already in
     * the list.
     */
    private final List<OrderedPair<Set<String>>> forbiddenRulesSpecs;

    /**
     * This needs to be a list for backward compatibility. Need to check when adding a new spec whether it's already in
     * the list.
     */
    private final List<OrderedPair<Set<String>>> requiredRulesSpecs;

    /**
     * The tier specs.
     */
    private final List<Set<String>> tierSpecs;

    /**
     * The knowledge groups.
     */
    private final List<KnowledgeGroup> knowledgeGroups;

    /**
     * The knowledge group rules.
     */
    private final Map<KnowledgeGroup, OrderedPair<Set<String>>> knowledgeGroupRules;

    /**
     * The default to knowledge layout.
     */
    private boolean defaultToKnowledgeLayout;

    /**
     * <p>Constructor for Knowledge.</p>
     */
    public Knowledge() {
        this.variables = new HashSet<>();
        this.forbiddenRulesSpecs = new ArrayList<>();
        this.requiredRulesSpecs = new ArrayList<>();
        this.tierSpecs = new ArrayList<>();
        this.knowledgeGroups = new LinkedList<>();
        this.knowledgeGroupRules = new HashMap<>();
    }

    /**
     * <p>Constructor for Knowledge.</p>
     *
     * @param nodes a {@link java.util.Collection} object
     */
    public Knowledge(Collection<String> nodes) {
        this();

        nodes.forEach(node -> {
            if (checkVarName(node)) {
                this.variables.add(node);
            } else {
                throw new IllegalArgumentException(String.format("Bad variable node %s.", node));
            }
        });
    }

    /**
     * <p>Constructor for Knowledge.</p>
     *
     * @param knowledge a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public Knowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new IllegalArgumentException("Knowledge is null.");
        }

        try {
            Knowledge copy = new MarshalledObject<>(knowledge).get();

            this.defaultToKnowledgeLayout = copy.defaultToKnowledgeLayout;
            this.variables = copy.variables;
            this.forbiddenRulesSpecs = copy.forbiddenRulesSpecs;
            this.requiredRulesSpecs = copy.requiredRulesSpecs;
            this.tierSpecs = copy.tierSpecs;
            this.knowledgeGroups = copy.knowledgeGroups;
            this.knowledgeGroupRules = copy.knowledgeGroupRules;
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public static Knowledge serializableInstance() {
        return new Knowledge();
    }

    // Checking this spec can cause the drag and drop feature to fail.
    private boolean checkVarName(String name) {
        return true;
//        return Knowledge.VARNAME_PATTERN.matcher(name).matches();
    }

    // Checking this spec can cause the drag and drop feature to fail.
    private String checkSpec(String spec) {
//        Matcher matcher = Knowledge.SPEC_PATTERN.matcher(spec);
//        if (!matcher.matches()) {
//            throw new IllegalArgumentException(spec + ": Cpdag names can consist of alphabetic "
//                    + "characters plus :, _, -, and .. A wildcard '*' may be included to match a "
//                    + "string of such characters.");
//        }

        return spec;//.replace(".", "\\.");
    }

    private Set<String> getExtent(String spec) {
        Set<String> vars = new HashSet<>();

        if (spec.contains("*")) {
            split(spec).stream()
                    .map(e -> e.replace("*", ".*"))
                    .forEach(e -> {
                        Pattern cpdag = Pattern.compile(e);
                        this.variables.stream()
                                .filter(var -> cpdag.matcher(var).matches())
                                .collect(Collectors.toCollection(() -> vars));
                    });
        } else {
            if (this.variables.contains(spec)) {
                vars.add(spec);
            }
        }

        return vars;
    }

    private Set<String> split(String spec) {
        return Arrays.stream(Knowledge.COMMAN_DELIM.split(spec))
                .map(String::trim)
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toSet());
    }

    private void ensureTiers(int tier) {
        for (int i = this.tierSpecs.size(); i <= tier; i++) {
            this.tierSpecs.add(new HashSet<>());
        }
    }

    private OrderedPair<Set<String>> getGroupRule(KnowledgeGroup group) {
        Set<String> fromExtent = new HashSet<>();
        group.getFromVariables()
                .forEach(e -> fromExtent.addAll(getExtent(e)));

        Set<String> toExtent = new HashSet<>();
        group.getToVariables()
                .forEach(e -> toExtent.addAll(getExtent(e)));

        return new OrderedPair<>(fromExtent, toExtent);
    }

    private List<OrderedPair<Set<String>>> forbiddenTierRules() {
        List<OrderedPair<Set<String>>> rules = new ArrayList<>();

        for (int i = 0; i < this.tierSpecs.size(); i++) {
            if (isTierForbiddenWithin(i)) {
                rules.add(new OrderedPair<>(this.tierSpecs.get(i), this.tierSpecs.get(i)));
            }
        }

        for (int i = 0; i < this.tierSpecs.size(); i++) {
            if (isOnlyCanCauseNextTier(i)) {
                for (int j = i + 2; j < this.tierSpecs.size(); j++) {
                    rules.add(new OrderedPair<>(this.tierSpecs.get(i), this.tierSpecs.get(j)));
                }
            }
        }

        for (int i = 0; i < this.tierSpecs.size(); i++) {
            for (int j = i + 1; j < this.tierSpecs.size(); j++) {
                rules.add(new OrderedPair<>(this.tierSpecs.get(j), this.tierSpecs.get(i)));
            }
        }

        return rules;
    }

    /**
     * Adds the given variable or wildcard cpdag to the given tier. The tier is a non-negative integer.
     *
     * @param tier a int
     * @param spec a {@link java.lang.String} object
     */
    public void addToTier(int tier, String spec) {
        if (tier < 0) {
            throw new IllegalArgumentException();
        }

        if (spec == null) {
            throw new NullPointerException();
        }

        addVariable(spec);
        spec = checkSpec(spec);
        ensureTiers(tier);

        Set<String> extent = getExtent(spec);

        for (Set<String> tierSpec : tierSpecs) {
            for (String var : extent) {
                tierSpec.remove(var);
            }
        }

        for (String var : extent) {
            tierSpecs.get(tier).add(var);
        }
    }

    /**
     * Puts a variable into tier i if its name is xxx:ti for some xxx and some i.
     *
     * @param varNames a {@link java.util.List} object
     */
    public void addToTiersByVarNames(List<String> varNames) {
        if (!this.variables.containsAll(varNames)) {
            varNames.forEach(e -> {
                if (checkVarName(e)) {
                    this.variables.add(e);
                } else {
                    throw new IllegalArgumentException(String.format("Bad variable node %s.", e));
                }
            });
        }

        varNames.forEach(e -> {
            int index = e.lastIndexOf(":t");
            if (index >= 0) {
                addToTier(Integer.parseInt(e.substring(index + 2)), e);
            }
        });
    }

    /**
     * Adds a knowledge group. Legacy method, replaced by setForbidden, setRequired with cpdags. Needed for the
     * interface.
     *
     * @param group a {@link edu.cmu.tetrad.data.KnowledgeGroup} object
     */
    public void addKnowledgeGroup(KnowledgeGroup group) {
        if (group == null) throw new NullPointerException("Knowledge group is null.");

        this.knowledgeGroups.add(group);

        OrderedPair<Set<String>> o = getGroupRule(group);
        this.knowledgeGroupRules.put(group, o);

        if (group.getType() == KnowledgeGroup.FORBIDDEN) {
            if (!forbiddenRulesSpecs.contains(o)) {
                this.forbiddenRulesSpecs.add(o);
            }
        } else if (group.getType() == KnowledgeGroup.REQUIRED) {
            if (!requiredRulesSpecs.contains(o)) {
                this.requiredRulesSpecs.add(o);
            }
        }
    }

    /**
     * <p>addVariable.</p>
     *
     * @param varName a {@link java.lang.String} object
     */
    public void addVariable(String varName) {
        this.variables.add(varName);
    }

    /**
     * Removes explicit knowledge and tier information.
     */
    public void clear() {
        this.variables.clear();
        this.forbiddenRulesSpecs.clear();
        this.requiredRulesSpecs.clear();
        this.tierSpecs.clear();
    }

    /**
     * Iterator over the KnowledgeEdge's representing forbidden edges.
     *
     * @return a {@link java.util.Iterator} object
     */
    public Iterator<KnowledgeEdge> forbiddenEdgesIterator() {
        List<KnowledgeEdge> forbiddenEdges = getListOfForbiddenEdges();
        return forbiddenEdges.iterator();
    }

    /**
     * <p>Getter for the field <code>knowledgeGroups</code>.</p>
     *
     * @return a shallow copy of the list of group rules.
     */
    public List<KnowledgeGroup> getKnowledgeGroups() {
        return new ArrayList<>(this.knowledgeGroups);
    }

    /**
     * Get a list of variables.
     *
     * @return a copy of the list of variable, in alphabetical order.
     */
    public List<String> getVariables() {
        return this.variables.stream()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * <p>getVariablesNotInTiers.</p>
     *
     * @return the list of edges not in any tier.
     */
    public List<String> getVariablesNotInTiers() {
        List<String> notInTier = new ArrayList<>(this.variables);

        for (Set<String> tier : this.tierSpecs) {
            notInTier.removeAll(tier);
        }

        return notInTier;
    }

    /**
     * <p>getTier.</p>
     *
     * @param tier the index of the desired tier
     * @return a copy of this tier
     */
    public List<String> getTier(int tier) {
        ensureTiers(tier);

        try {
            List<String> list = new ArrayList<>(tierSpecs.get(tier));
            Collections.sort(list);
            return list;
        } catch (Exception e) {
            throw new RuntimeException("Expecting tiered knowledge", e);
        }
    }

    /**
     * <p>getNumTiers.</p>
     *
     * @return the number of temporal tiers
     */
    public int getNumTiers() {
        return this.tierSpecs.size();
    }

    /**
     * <p>isDefaultToKnowledgeLayout.</p>
     *
     * @return a boolean
     */
    public boolean isDefaultToKnowledgeLayout() {
        return this.defaultToKnowledgeLayout;
    }

    /**
     * <p>Setter for the field <code>defaultToKnowledgeLayout</code>.</p>
     *
     * @param defaultToKnowledgeLayout a boolean
     */
    public void setDefaultToKnowledgeLayout(boolean defaultToKnowledgeLayout) {
        this.defaultToKnowledgeLayout = defaultToKnowledgeLayout;
    }

    private boolean isForbiddenByRules(String var1, String var2) {
        for (OrderedPair<Set<String>> o : this.forbiddenRulesSpecs) {
            if (o.getFirst().contains(var1) && o.getSecond().contains(var2)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines whether the edge var1 --&gt; var2 is forbidden.
     *
     * @param var1 a {@link java.lang.String} object
     * @param var2 a {@link java.lang.String} object
     * @return a boolean
     */
    public boolean isForbidden(String var1, String var2) {
        return isForbiddenByRules(var1, var2) || isForbiddenByTiers(var1, var2);
    }

    /**
     * Legacy.
     *
     * @param var1 a {@link java.lang.String} object
     * @param var2 a {@link java.lang.String} object
     * @return a boolean
     */
    public boolean isForbiddenByGroups(String var1, String var2) {
        for (KnowledgeGroup group : this.knowledgeGroups) {
            if (group.getType() == KnowledgeGroup.FORBIDDEN) {
                OrderedPair<Set<String>> o = this.knowledgeGroupRules.get(group);
                if (o.getFirst().contains(var1) && o.getSecond().contains(var2)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Determines whether the edge var1 --&gt; var2 is forbidden by the temporal tiers.
     *
     * @param var1 a {@link java.lang.String} object
     * @param var2 a {@link java.lang.String} object
     * @return a boolean
     */
    public boolean isForbiddenByTiers(String var1, String var2) {
        for (int i = tierSpecs.size() - 1; i >= 0; i--) {
            for (int j = i - 1; j >= 0; j--) {
                if (tierSpecs.get(i).contains(var1) && tierSpecs.get(j).contains(var2)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Determines whether the edge var1 --&gt; var2 is required.
     *
     * @param var1 a {@link java.lang.String} object
     * @param var2 a {@link java.lang.String} object
     * @return a boolean
     */
    public boolean isRequired(String var1, String var2) {
        for (OrderedPair<Set<String>> o : this.requiredRulesSpecs) {
            if (o.getFirst().contains(var1) && o.getSecond().contains(var2)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Legacy.
     *
     * @param var1 a {@link java.lang.String} object
     * @param var2 a {@link java.lang.String} object
     * @return a boolean
     */
    public boolean isRequiredByGroups(String var1, String var2) {
        for (KnowledgeGroup group : this.knowledgeGroups) {
            if (group.getType() == KnowledgeGroup.REQUIRED) {
                OrderedPair<Set<String>> o = this.knowledgeGroupRules.get(group);
                if (o.getFirst().contains(var1) && o.getSecond().contains(var2)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * true if there is no background knowledge recorded.
     *
     * @return a boolean
     */
    public boolean isEmpty() {
        return this.forbiddenRulesSpecs.isEmpty()
               && this.requiredRulesSpecs.isEmpty()
               && this.tierSpecs.isEmpty();
    }

    /**
     * Checks whether it is the case that any variable is forbidden by any other variable within a given tier.
     *
     * @param tier a int
     * @return a boolean
     */
    public boolean isTierForbiddenWithin(int tier) {
        ensureTiers(tier);

        Set<String> varsInTier = this.tierSpecs.get(tier);
        if (varsInTier.isEmpty()) {
            return false;
        }

        return this.forbiddenRulesSpecs.contains(new OrderedPair<>(varsInTier, varsInTier));
    }

    /**
     * <p>isViolatedBy.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a boolean
     */
    public boolean isViolatedBy(Graph graph) {
        if (graph == null) {
            throw new NullPointerException("Sorry, a graph hasn't been provided.");
        }

        return graph.getEdges().stream()
                .filter(Edge::isDirected)
                .anyMatch(edge -> {
                    Node from = Edges.getDirectedEdgeTail(edge);
                    Node to = Edges.getDirectedEdgeHead(edge);

                    return isForbidden(from.getName(), to.getName());
                });
    }

    /**
     * <p>noEdgeRequired.</p>
     *
     * @param x a {@link java.lang.String} object
     * @param y a {@link java.lang.String} object
     * @return a boolean
     */
    public boolean noEdgeRequired(String x, String y) {
        return !(isRequired(x, y) || isRequired(y, x));
    }

    /**
     * Removes the given variable by name or search string from all tiers.
     *
     * @param spec a {@link java.lang.String} object
     */
    public void removeFromTiers(String spec) {
        if (spec == null) {
            throw new NullPointerException();
        }

        spec = checkSpec(spec);
        Set<String> extent = getExtent(spec);

        for (Set<String> tier : this.tierSpecs) {
            for (String s : extent) {
                tier.remove(s);
            }
        }
    }

    /**
     * Removes the knowledge group at the given index.
     *
     * @param index a int
     */
    public void removeKnowledgeGroup(int index) {
        OrderedPair<Set<String>> old = this.knowledgeGroupRules.get(this.knowledgeGroups.get(index));

        this.forbiddenRulesSpecs.remove(old);
        this.requiredRulesSpecs.remove(old);

        this.knowledgeGroups.remove(index);
    }

    /**
     * Iterator over the KnowledgeEdge's representing required edges.
     *
     * @return a {@link java.util.Iterator} object
     */
    public Iterator<KnowledgeEdge> requiredEdgesIterator() {
        Set<KnowledgeEdge> edges = new HashSet<>();

        this.requiredRulesSpecs.forEach(o -> o.getFirst().forEach(s1 -> o.getSecond().forEach(s2 -> {
            if (!s1.equals(s2)) {
                edges.add(new KnowledgeEdge(s1, s2));
            }
        })));

        return edges.iterator();
    }

    /**
     * Marks the edge var1 --&gt; var2 as forbid.
     *
     * @param var1 a {@link java.lang.String} object
     * @param var2 a {@link java.lang.String} object
     */
    public void setForbidden(String var1, String var2) {
        if (isForbidden(var1, var2)) return;

        addVariable(var1);
        addVariable(var2);

        var1 = checkSpec(var1);
        var2 = checkSpec(var2);

        Set<String> f1 = getExtent(var1);
        Set<String> f2 = getExtent(var2);

        OrderedPair<Set<String>> o = new OrderedPair<>(f1, f2);

        if (!forbiddenRulesSpecs.contains(o)) {
            if (!forbiddenRulesSpecs.contains(o)) {
                this.forbiddenRulesSpecs.add(o);
            }
        }
    }

    /**
     * Marks the edge var1 --&gt; var2 as not forbid.
     *
     * @param var1 a {@link java.lang.String} object
     * @param var2 a {@link java.lang.String} object
     */
    public void removeForbidden(String var1, String var2) {
        var1 = checkSpec(var1);
        var2 = checkSpec(var2);

        Set<String> f1 = getExtent(var1);
        Set<String> f2 = getExtent(var2);

        this.forbiddenRulesSpecs.remove(new OrderedPair<>(f1, f2));
    }

    /**
     * Marks the edge var1 --&gt; var2 as required.
     *
     * @param var1 a {@link java.lang.String} object
     * @param var2 a {@link java.lang.String} object
     */
    public void setRequired(String var1, String var2) {
        if (isRequired(var1, var1)) return;

        addVariable(var1);
        addVariable(var2);

        var1 = checkSpec(var1);
        var2 = checkSpec(var2);

        Set<String> f1 = getExtent(var1);
        Set<String> f2 = getExtent(var2);

        f1.forEach(s -> {
            if (checkVarName(s)) {
                this.variables.add(s);
            }
        });
        f2.forEach(s -> {
            if (checkVarName(s)) {
                this.variables.add(s);
            }
        });

        OrderedPair<Set<String>> o = new OrderedPair<>(f1, f2);

        if (!requiredRulesSpecs.contains(o)) {
            this.requiredRulesSpecs.add(o);
        }
    }

    /**
     * Marks the edge var1 --&gt; var2 as not required.
     *
     * @param var1 a {@link java.lang.String} object
     * @param var2 a {@link java.lang.String} object
     */
    public void removeRequired(String var1, String var2) {
        var1 = checkSpec(var1);
        var2 = checkSpec(var2);

        Set<String> f1 = getExtent(var1);
        Set<String> f2 = getExtent(var2);

        this.requiredRulesSpecs.remove(new OrderedPair<>(f1, f2));
    }

    /**
     * Legacy, do not use.
     *
     * @param index a int
     * @param group a {@link edu.cmu.tetrad.data.KnowledgeGroup} object
     */
    public void setKnowledgeGroup(int index, KnowledgeGroup group) {
        OrderedPair<Set<String>> o = getGroupRule(group);
        OrderedPair<Set<String>> old = this.knowledgeGroupRules.get(this.knowledgeGroups.get(index));

        this.forbiddenRulesSpecs.remove(old);
        this.requiredRulesSpecs.remove(old);

        knowledgeGroupRules.put(group, o);

        if (group.getType() == KnowledgeGroup.FORBIDDEN) {
            if (!forbiddenRulesSpecs.contains(o)) {
                this.forbiddenRulesSpecs.add(o);
            }
        } else if (group.getType() == KnowledgeGroup.REQUIRED) {
            if (!requiredRulesSpecs.contains(o)) {
                this.requiredRulesSpecs.add(o);
            }
        }

        this.knowledgeGroups.set(index, group);
    }

    /**
     * Sets the variable in a given tier to the specified list.
     *
     * @param tier a int
     * @param vars a {@link java.util.List} object
     */
    public void setTier(int tier, List<String> vars) {
        ensureTiers(tier);
        Set<String> varsInTier = this.tierSpecs.get(tier);
        if (varsInTier != null) {
            varsInTier.clear();
        }

        vars.forEach(var -> addToTier(tier, var));
    }

    /**
     * Forbids any variable from being parent of any other variable within the given tier, or cancels this forbidding.
     *
     * @param tier      a int
     * @param forbidden a boolean
     */
    public void setTierForbiddenWithin(int tier, boolean forbidden) {
        ensureTiers(tier);
        Set<String> varsInTier = this.tierSpecs.get(tier);

        OrderedPair<Set<String>> o = new OrderedPair<>(varsInTier, varsInTier);

        if (forbidden) {
            if (!forbiddenRulesSpecs.contains(o)) {
                this.forbiddenRulesSpecs.add(o);
            }
        } else {
            this.forbiddenRulesSpecs.remove(o);
        }
    }

    /**
     * <p>getMaxTierForbiddenWithin.</p>
     *
     * @return the largest indes of a tier in which every variable is forbidden by every other variable, or -1 if there
     * is not such tier.
     */
    public int getMaxTierForbiddenWithin() {
        for (int tier = this.tierSpecs.size(); tier >= 0; tier--) {
            if (isTierForbiddenWithin(tier)) {
                return tier;
            }
        }

        return -1;
    }

    /**
     * Makes a shallow copy.
     *
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public Knowledge copy() {
        return new Knowledge(this);
    }

    /**
     * Returns the index of the tier of node if it's in a tier, otherwise -1.
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return a int
     */
    public int isInWhichTier(Node node) {
        for (int i = 0; i < this.tierSpecs.size(); i++) {
            Set<String> tier = this.tierSpecs.get(i);

            for (String myNode : tier) {
                if (myNode.equals(node.getName())) {
                    return i;
                }
            }
        }

        return -1;
    } // added by DMalinsky for tsFCI on 4/20/16

    /**
     * <p>getListOfRequiredEdges.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<KnowledgeEdge> getListOfRequiredEdges() {
        Set<KnowledgeEdge> edges = new HashSet<>();

        this.requiredRulesSpecs.forEach(e -> e.getFirst().forEach(e1 -> e.getSecond().forEach(e2 -> {
            if (!e1.equals(e2)) {
                edges.add(new KnowledgeEdge(e1, e2));
            }
        })));

        return new ArrayList<>(edges);
    }

    /**
     * <p>getListOfExplicitlyRequiredEdges.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<KnowledgeEdge> getListOfExplicitlyRequiredEdges() {
        return getListOfRequiredEdges();
    }

    /**
     * <p>getListOfForbiddenEdges.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<KnowledgeEdge> getListOfForbiddenEdges() {
        Set<KnowledgeEdge> edges = new HashSet<>();

        for (int i = 0; i < tierSpecs.size(); i++) {
            if (isTierForbiddenWithin(i)) {
                Set<String> tier = tierSpecs.get(i);
                for (String x : tier) {
                    for (String y : tier) {
                        if (!x.equals(y)) {
                            edges.add(new KnowledgeEdge(x, y));
                        }
                    }
                }
            }
        }

        for (int i = this.tierSpecs.size() - 1; i >= 0; i--) {

            // Make sure this iterates from i - 1 to 0 or else all directed edges will be
            // forbidden within tiers!
            for (int j = i - 1; j >= 0; j--) {
                Set<String> tieri = this.tierSpecs.get(i);
                Set<String> tierj = this.tierSpecs.get(j);

                for (String x : tieri) {
                    for (String y : tierj) {
                        edges.add(new KnowledgeEdge(x, y));
                    }
                }
            }
        }

        this.forbiddenRulesSpecs.forEach(o -> o.getFirst().forEach(s1 -> o.getSecond().forEach(s2 -> {
            if (!s1.equals(s2)) {
                edges.add(new KnowledgeEdge(s1, s2));
            }
        })));

        return new ArrayList<>(edges);
    }

    /**
     * <p>getListOfExplicitlyForbiddenEdges.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<KnowledgeEdge> getListOfExplicitlyForbiddenEdges() {
        Set<OrderedPair<Set<String>>> copy = new HashSet<>(this.forbiddenRulesSpecs);
        forbiddenTierRules().forEach(copy::remove);

        this.knowledgeGroups.forEach(e -> copy.remove(this.knowledgeGroupRules.get(e)));

        Set<KnowledgeEdge> edges = new HashSet<>();
        for (OrderedPair<Set<String>> e : copy)
            e.getFirst().forEach(e1 -> e.getSecond().forEach(e2 -> edges.add(new KnowledgeEdge(e1, e2))));

        return new ArrayList<>(edges);
    }

    /**
     * <p>isOnlyCanCauseNextTier.</p>
     *
     * @param tier a int
     * @return a boolean
     */
    public boolean isOnlyCanCauseNextTier(int tier) {
        ensureTiers(tier);

        Set<String> varsInTier = this.tierSpecs.get(tier);
        if (varsInTier.isEmpty()) {
            return false;
        }

        if (tier + 2 >= this.tierSpecs.size()) {
            return false;
        }

        // all successive tiers > tier + 2 must be forbidden
        for (int tierN = tier + 2; tierN < this.tierSpecs.size(); tierN++) {
            Set<String> varsInTierN = this.tierSpecs.get(tierN);
            OrderedPair<Set<String>> o = new OrderedPair<>(varsInTier, varsInTierN);

            if (!this.forbiddenRulesSpecs.contains(o)) {
                return false;
            }
        }

        return true;
    }

    /**
     * <p>setOnlyCanCauseNextTier.</p>
     *
     * @param tier           a int
     * @param onlyCausesNext a boolean
     */
    public void setOnlyCanCauseNextTier(int tier, boolean onlyCausesNext) {
        ensureTiers(tier);

        Set<String> varsInTier = this.tierSpecs.get(tier);

        for (int tierN = tier + 2; tierN < this.tierSpecs.size(); tierN++) {
            Set<String> varsInTierN = this.tierSpecs.get(tierN);
            OrderedPair<Set<String>> o = new OrderedPair<>(varsInTier, varsInTierN);

            if (onlyCausesNext) {
                this.forbiddenRulesSpecs.add(o);
            } else {
                this.forbiddenRulesSpecs.remove(o);
            }
        }
    }

    /**
     * Computes a hashcode.
     *
     * @return a int
     */
    public int hashCode() {
        int hash = 37;
        hash += 17 * this.variables.hashCode() + 37;
        hash += 17 * this.forbiddenRulesSpecs.hashCode() + 37;
        hash += 17 * this.requiredRulesSpecs.hashCode() + 37;
        hash += 17 * this.tierSpecs.hashCode() + 37;
        return hash;
    }

    /**
     * Compares this Knowledge object with the specified object for equality.
     *
     * @param o the object to compare this Knowledge with
     * @return true if the specified object is equal to this Knowledge, false otherwise
     */
    public boolean equals(Object o) {
        if (!(o instanceof Knowledge that)) {
            return false;
        }

        return this.forbiddenRulesSpecs.equals(that.forbiddenRulesSpecs)
               && this.requiredRulesSpecs.equals(that.requiredRulesSpecs)
               && this.tierSpecs.equals(that.tierSpecs);
    }

    /**
     * <p>toString.</p>
     *
     * @return the contents of this Knowledge object in String form.
     */
    public String toString() {
        try {
            CharArrayWriter out = new CharArrayWriter();
            DataWriter.saveKnowledge(this, out);
            return out.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Could not render knowledge.");
        }
    }

}
