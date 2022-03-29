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

import edu.cmu.tetrad.graph.*;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.rmi.MarshalledObject;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Stores information about required and forbidden edges and common causes for
 * use in algorithm. This information can be set edge by edge or else globally
 * via temporal tiers. When setting temporal tiers, all edges from later tiers
 * to earlier tiers are forbidden.
 * <p>
 * For this class, all variable names are referenced by name only. This is
 * because the same Knowledge object is intended to plug into different graphs
 * with MyNodes that possibly have the same names. Thus, if the Knowledge object
 * forbids the edge X --> Y, then it forbids any edge which connects a MyNode
 * named "X" to a MyNode named "Y", even if the underlying MyNodes themselves
 * named "X" and "Y", respectively, are not the same.
 * <p>
 * In place of variable names, wildcard expressions containing the wildcard '*'
 * may be substituted. These will be matched to as many myNodes as possible. The
 * '*' wildcard matches any string of consecutive characters up until the
 * following character is encountered. Thus, "X*a" will match "X123a" and
 * "X45a".
 *
 * @author Joseph Ramsey
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public final class Knowledge2 implements IKnowledge {

    private static final long serialVersionUID = 23L;

    private static final Pattern VARNAME_PATTERN = Pattern.compile("[A-Za-z0-9:_\\-.]+");
    private static final Pattern SPEC_PATTERN = Pattern.compile("[A-Za-z0-9:-_,\\-.*]+");
    private static final Pattern COMMAN_DELIM = Pattern.compile(",");
    private final Set<String> variables;
    private final List<OrderedPair<Set<String>>> forbiddenRulesSpecs;
    private final List<OrderedPair<Set<String>>> requiredRulesSpecs;
    private final List<Set<String>> tierSpecs;
    // Legacy.
    private final List<KnowledgeGroup> knowledgeGroups;
    private final Map<KnowledgeGroup, OrderedPair<Set<String>>> knowledgeGroupRules;
    private boolean defaultToKnowledgeLayout;

    public Knowledge2() {
        this.variables = new HashSet<>();
        this.forbiddenRulesSpecs = new ArrayList<>();
        this.requiredRulesSpecs = new ArrayList<>();
        this.tierSpecs = new ArrayList<>();
        this.knowledgeGroups = new LinkedList<>();
        this.knowledgeGroupRules = new HashMap<>();
    }

    public Knowledge2(final Collection<String> nodes) {
        this();

        nodes.forEach(node -> {
            if (checkVarName(node)) {
                this.variables.add(node);
            } else {
                throw new IllegalArgumentException(String.format("Bad variable node %s.", node));
            }
        });
    }

    public Knowledge2(final Knowledge2 knowledge) {
        try {
            final Knowledge2 copy = new MarshalledObject<>(knowledge).get();

            this.defaultToKnowledgeLayout = copy.defaultToKnowledgeLayout;
            this.variables = copy.variables;
            this.forbiddenRulesSpecs = copy.forbiddenRulesSpecs;
            this.requiredRulesSpecs = copy.requiredRulesSpecs;
            this.tierSpecs = copy.tierSpecs;
            this.knowledgeGroups = copy.knowledgeGroups;
            this.knowledgeGroupRules = copy.knowledgeGroupRules;
        } catch (final IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static Knowledge2 serializableInstance() {
        return new Knowledge2();
    }

    private boolean checkVarName(final String name) {
        return Knowledge2.VARNAME_PATTERN.matcher(name).matches();
    }

    private String checkSpec(final String spec) {
        final Matcher matcher = Knowledge2.SPEC_PATTERN.matcher(spec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(spec + ": Cpdag names can consist of alphabetic "
                    + "characters plus :, _, -, and .. A wildcard '*' may be included to match a "
                    + "string of such characters.");
        }

        return spec.replace(".", "\\.");
    }

    private Set<String> getExtent(final String spec) {
        final Set<String> vars = new HashSet<>();

        if (spec.contains("*")) {
            split(spec).stream()
                    .map(e -> e.replace("*", ".*"))
                    .forEach(e -> {
                        final Pattern cpdag = Pattern.compile(e);
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

    private Set<String> split(final String spec) {
        return Arrays.stream(Knowledge2.COMMAN_DELIM.split(spec))
                .map(String::trim)
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toSet());
    }

    private void ensureTiers(final int tier) {
        for (int i = this.tierSpecs.size(); i <= tier; i++) {
            this.tierSpecs.add(new LinkedHashSet<>());

            for (int j = 0; j < i; j++) {
                this.forbiddenRulesSpecs.add(new OrderedPair<>(this.tierSpecs.get(i), this.tierSpecs.get(j)));
            }
        }
    }

    private OrderedPair<Set<String>> getGroupRule(final KnowledgeGroup group) {
        final Set<String> fromExtent = new HashSet<>();
        group.getFromVariables()
                .forEach(e -> fromExtent.addAll(getExtent(e)));

        final Set<String> toExtent = new HashSet<>();
        group.getToVariables()
                .forEach(e -> toExtent.addAll(getExtent(e)));

        return new OrderedPair<>(fromExtent, toExtent);
    }

    private Set<OrderedPair<Set<String>>> forbiddenTierRules() {
        final Set<OrderedPair<Set<String>>> rules = new HashSet<>();

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
     * Adds the given variable or wildcard cpdag to the given tier. The tier
     * is a non-negative integer.
     */
    @Override
    public void addToTier(final int tier, String spec) {
        if (tier < 0) {
            throw new IllegalArgumentException();
        }
        if (spec == null) {
            throw new NullPointerException();
        }

        addVariable(spec);
        spec = checkSpec(spec);
        ensureTiers(tier);

        getExtent(spec).stream()
                .filter(this::checkVarName)
                .forEach(e -> {
                    this.variables.add(e);
                    this.tierSpecs.get(tier).add(e);
                });
    }

    /**
     * Puts a variable into tier i if its name is xxx:ti for some xxx and some
     * i.
     */
    @Override
    public void addToTiersByVarNames(final List<String> varNames) {
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
            final int index = e.lastIndexOf(":t");
            if (index >= 0) {
                addToTier(Integer.parseInt(e.substring(index + 2)), e);
            }
        });
    }

    /**
     * Adds a knowledge group. Legacy method, replaced by setForbidden,
     * setRequired with cpdags. Needed for the interface.
     */
    @Override
    public void addKnowledgeGroup(final KnowledgeGroup group) {
        this.knowledgeGroups.add(group);

        final OrderedPair<Set<String>> o = getGroupRule(group);
        this.knowledgeGroupRules.put(group, o);

        if (group.getType() == KnowledgeGroup.FORBIDDEN) {
            this.forbiddenRulesSpecs.add(o);
        } else if (group.getType() == KnowledgeGroup.REQUIRED) {
            this.requiredRulesSpecs.add(o);
        }
    }

    @Override
    public void addVariable(final String varName) {
        this.variables.add(varName);
    }

    /**
     * Removes explicit knowledge and tier information.
     */
    @Override
    public void clear() {
        this.variables.clear();
        this.forbiddenRulesSpecs.clear();
        this.requiredRulesSpecs.clear();
        this.tierSpecs.clear();
    }

    /**
     * Iterator over the knowledge's explicitly forbidden edges.
     */
    @Override
    public Iterator<KnowledgeEdge> explicitlyForbiddenEdgesIterator() {
        final Set<OrderedPair<Set<String>>> copy = new HashSet<>(this.forbiddenRulesSpecs);
        copy.removeAll(forbiddenTierRules());

        this.knowledgeGroups.forEach(e -> copy.remove(this.knowledgeGroupRules.get(e)));

        final Set<KnowledgeEdge> edges = new HashSet<>();

        copy.forEach(o -> {
            for (final String s1 : o.getFirst()) {
                o.getSecond().forEach(s2 -> edges.add(new KnowledgeEdge(s1, s2)));
            }
        });

        return edges.iterator();
    }

    /**
     * Iterator over the KnowledgeEdge's explicitly required edges.
     */
    @Override
    public Iterator<KnowledgeEdge> explicitlyRequiredEdgesIterator() {
        return requiredEdgesIterator();
    }

    /**
     * Iterator over the KnowledgeEdge's representing forbidden edges.
     */
    @Override
    public Iterator<KnowledgeEdge> forbiddenEdgesIterator() {
        final Set<KnowledgeEdge> edges = new HashSet<>();

        this.forbiddenRulesSpecs.forEach(o -> o.getFirst().forEach(s1 -> o.getSecond().forEach(s2 -> {
            if (!s1.equals(s2)) {
                edges.add(new KnowledgeEdge(s1, s2));
            }
        })));

        return edges.iterator();
    }

    /**
     * @return a shallow copy of the list of group rules.
     */
    @Override
    public List<KnowledgeGroup> getKnowledgeGroups() {
        return new ArrayList<>(this.knowledgeGroups);
    }

    /**
     * Get a list of variables.
     *
     * @return a copy of the list of variable, in alphabetical order.
     */
    @Override
    public List<String> getVariables() {
        return this.variables.stream()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * @return the list of edges not in any tier.
     */
    @Override
    public List<String> getVariablesNotInTiers() {
        final List<String> notInTier = new ArrayList<>(this.variables);

        for (final Set<String> tier : this.tierSpecs) {
            notInTier.removeAll(tier);
        }

        return notInTier;
    }

    /**
     * @param tier the index of the desired tier
     * @return a copy of this tier
     */
    @Override
    public List<String> getTier(final int tier) {
        ensureTiers(tier);

        try {
            return this.tierSpecs.get(tier).stream()
                    .sorted()
                    .collect(Collectors.toList());
        } catch (final Exception e) {
            throw new RuntimeException("Unexpected knowledge configuration.", e);
        }
    }

    /**
     * @return the number of temporal tiers
     */
    @Override
    public int getNumTiers() {
        return this.tierSpecs.size();
    }

    @Override
    public boolean isDefaultToKnowledgeLayout() {
        return this.defaultToKnowledgeLayout;
    }

    @Override
    public void setDefaultToKnowledgeLayout(final boolean defaultToKnowledgeLayout) {
        this.defaultToKnowledgeLayout = defaultToKnowledgeLayout;
    }

    private boolean isForbiddenByRules(final String var1, final String var2) {
        return this.forbiddenRulesSpecs.stream()
                .anyMatch(rule -> !var1.equals(var2)
                        && rule.getFirst().contains(var1)
                        && rule.getSecond().contains(var2));
    }

    /**
     * Determines whether the edge var1 --> var2 is forbidden.
     */
    @Override
    public boolean isForbidden(final String var1, final String var2) {
        if (isRequired(var1, var2)) {
            return false;
        }

        return isForbiddenByRules(var1, var2) || isForbiddenByTiers(var1, var2);
    }

    /**
     * Legacy.
     */
    @Override
    public boolean isForbiddenByGroups(final String var1, final String var2) {
        final Set<OrderedPair<Set<String>>> s = this.knowledgeGroups.stream()
                .filter(e -> e.getType() == KnowledgeGroup.FORBIDDEN)
                .map(this::getGroupRule)
                .collect(Collectors.toSet());

        return s.stream()
                .anyMatch(rule -> rule.getFirst().contains(var1)
                        && rule.getSecond().contains(var2));
    }

    /**
     * Determines whether the edge var1 --> var2 is forbidden by the temporal
     * tiers.
     */
    @Override
    public boolean isForbiddenByTiers(final String var1, final String var2) {
        return forbiddenTierRules().stream()
                .anyMatch(rule -> rule.getFirst().contains(var1)
                        && rule.getSecond().contains(var2));
    }

    /**
     * Determines whether the edge var1 --> var2 is required.
     */
    @Override
    public boolean isRequired(final String var1, final String var2) {
        return this.requiredRulesSpecs.stream()
                .anyMatch(rule -> !var1.equals(var2)
                        && rule.getFirst().contains(var1)
                        && rule.getSecond().contains(var2));
    }

    /**
     * Legacy.
     */
    @Override
    public boolean isRequiredByGroups(final String var1, final String var2) {
        final Set<OrderedPair<Set<String>>> s = this.knowledgeGroups.stream()
                .filter(e -> e.getType() == KnowledgeGroup.REQUIRED)
                .map(this::getGroupRule)
                .collect(Collectors.toSet());

        return s.stream()
                .anyMatch(rule -> rule.getFirst().contains(var1)
                        && rule.getSecond().contains(var2));
    }

    /**
     * true if there is no background knowledge recorded.
     */
    @Override
    public boolean isEmpty() {
        return this.forbiddenRulesSpecs.isEmpty()
                && this.requiredRulesSpecs.isEmpty()
                && this.tierSpecs.isEmpty();
    }

    /**
     * Checks whether it is the case that any variable is forbidden by any other
     * variable within a given tier.
     */
    @Override
    public boolean isTierForbiddenWithin(final int tier) {
        ensureTiers(tier);

        final Set<String> varsInTier = this.tierSpecs.get(tier);
        if (varsInTier.isEmpty()) {
            return false;
        }

        return this.forbiddenRulesSpecs.contains(new OrderedPair<>(varsInTier, varsInTier));
    }

    @Override
    public boolean isViolatedBy(final Graph graph) {
        if (graph == null) {
            throw new NullPointerException("Sorry, a graph hasn't been provided.");
        }

        return graph.getEdges().stream()
                .filter(Edge::isDirected)
                .anyMatch(edge -> {
                    final Node from = Edges.getDirectedEdgeTail(edge);
                    final Node to = Edges.getDirectedEdgeHead(edge);

                    return isForbidden(from.getName(), to.getName());
                });
    }

    @Override
    public boolean noEdgeRequired(final String x, final String y) {
        return !(isRequired(x, y) || isRequired(y, x));
    }

    /**
     * Removes the given variable by name or search string from all tiers.
     */
    @Override
    public void removeFromTiers(String spec) {
        if (spec == null) {
            throw new NullPointerException();
        }

        spec = checkSpec(spec);
        getExtent(spec).forEach(s -> this.tierSpecs.forEach(tier -> tier.remove(s)));
    }

    /**
     * Removes the knowledge group at the given index.
     */
    @Override
    public void removeKnowledgeGroup(final int index) {
        final OrderedPair<Set<String>> old = this.knowledgeGroupRules.get(this.knowledgeGroups.get(index));

        this.forbiddenRulesSpecs.remove(old);
        this.requiredRulesSpecs.remove(old);

        this.knowledgeGroups.remove(index);
    }

    /**
     * Removes the given variable from the list of myNodes and all rules.
     */
    @Override
    public void removeVariable(final String name) {
        if (!checkVarName(name)) {
            throw new IllegalArgumentException("Bad variable name: " + name);
        }

        this.variables.remove(name);

        this.forbiddenRulesSpecs.forEach(o -> {
            o.getFirst().remove(name);
            o.getSecond().remove(name);
        });

        this.requiredRulesSpecs.forEach(o -> {
            o.getFirst().remove(name);
            o.getSecond().remove(name);
        });

        this.tierSpecs.forEach(tier -> tier.remove(name));
    }

    /**
     * Iterator over the KnowledgeEdge's representing required edges.
     */
    @Override
    public Iterator<KnowledgeEdge> requiredEdgesIterator() {
        final Set<KnowledgeEdge> edges = new HashSet<>();

        this.requiredRulesSpecs.forEach(o -> o.getFirst().forEach(s1 -> o.getSecond().forEach(s2 -> {
            if (!s1.equals(s2)) {
                edges.add(new KnowledgeEdge(s1, s2));
            }
        })));

        return edges.iterator();
    }

    /**
     * Marks the edge var1 --> var2 as forbid.
     */
    @Override
    public void setForbidden(String var1, String var2) {
        addVariable(var1);
        addVariable(var2);

        var1 = checkSpec(var1);
        var2 = checkSpec(var2);

        final Set<String> f1 = getExtent(var1);
        final Set<String> f2 = getExtent(var2);

        this.forbiddenRulesSpecs.add(new OrderedPair<>(f1, f2));
    }

    /**
     * Marks the edge var1 --> var2 as not forbid.
     */
    @Override
    public void removeForbidden(String var1, String var2) {
        var1 = checkSpec(var1);
        var2 = checkSpec(var2);

        final Set<String> f1 = getExtent(var1);
        final Set<String> f2 = getExtent(var2);

        this.forbiddenRulesSpecs.remove(new OrderedPair<>(f1, f2));
    }

    /**
     * Marks the edge var1 --> var2 as required.
     */
    @Override
    public void setRequired(String var1, String var2) {
        addVariable(var1);
        addVariable(var2);

        var1 = checkSpec(var1);
        var2 = checkSpec(var2);

        final Set<String> f1 = getExtent(var1);
        final Set<String> f2 = getExtent(var2);

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

        this.requiredRulesSpecs.add(new OrderedPair<>(f1, f2));
    }

    /**
     * Marks the edge var1 --> var2 as not required.
     */
    @Override
    public void removeRequired(String var1, String var2) {
        var1 = checkSpec(var1);
        var2 = checkSpec(var2);

        final Set<String> f1 = getExtent(var1);
        final Set<String> f2 = getExtent(var2);

        this.requiredRulesSpecs.remove(new OrderedPair<>(f1, f2));
    }

    /**
     * Legacy, do not use.
     */
    @Override
    public void setKnowledgeGroup(final int index, final KnowledgeGroup group) {
        final OrderedPair<Set<String>> o = getGroupRule(group);
        final OrderedPair<Set<String>> old = this.knowledgeGroupRules.get(this.knowledgeGroups.get(index));

        this.forbiddenRulesSpecs.remove(old);
        this.requiredRulesSpecs.remove(old);

        if (group.getType() == KnowledgeGroup.FORBIDDEN) {
            this.forbiddenRulesSpecs.add(o);
        } else if (group.getType() == KnowledgeGroup.REQUIRED) {
            this.requiredRulesSpecs.add(o);
        }

        this.knowledgeGroups.set(index, group);
    }

    /**
     * Sets the variable in a given tier to the specified list.
     */
    @Override
    public void setTier(final int tier, final List<String> vars) {
        ensureTiers(tier);
        final Set<String> varsInTier = this.tierSpecs.get(tier);
        if (varsInTier != null) {
            varsInTier.clear();
        }

        vars.forEach(var -> addToTier(tier, var));
    }

    /**
     * Forbids any variable from being parent of any other variable within the
     * given tier, or cancels this forbidding.
     */
    @Override
    public void setTierForbiddenWithin(final int tier, final boolean forbidden) {
        ensureTiers(tier);
        final Set<String> varsInTier = this.tierSpecs.get(tier);

        if (forbidden) {
            this.forbiddenRulesSpecs.add(new OrderedPair<>(varsInTier, varsInTier));
        } else {
            this.forbiddenRulesSpecs.remove(new OrderedPair<>(varsInTier, varsInTier));
        }
    }

    /**
     * @return the largest indes of a tier in which every variable is forbidden
     * by every other variable, or -1 if there is not such tier.
     */
    @Override
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
     */
    @Override
    public IKnowledge copy() {
        return new Knowledge2(this);
    }

    /**
     * Returns the index of the tier of node if it's in a tier, otherwise -1.
     */
    @Override
    public int isInWhichTier(final Node node) {
        for (int i = 0; i < this.tierSpecs.size(); i++) {
            final Set<String> tier = this.tierSpecs.get(i);

            for (final String myNode : tier) {
                if (myNode.equals(node.getName())) {
                    return i;
                }
            }
        }

        return -1;
    } // added by DMalinsky for tsFCI on 4/20/16

    @Override
    public List<KnowledgeEdge> getListOfRequiredEdges() {
        final Set<KnowledgeEdge> edges = new LinkedHashSet<>();

        this.requiredRulesSpecs.forEach(e -> e.getFirst().forEach(e1 -> e.getSecond().forEach(e2 -> {
            if (!e1.equals(e2)) {
                edges.add(new KnowledgeEdge(e1, e2));
            }
        })));

        return new ArrayList<>(edges);
    }

    @Override
    public List<KnowledgeEdge> getListOfExplicitlyRequiredEdges() {
        return getListOfRequiredEdges();
    }

    @Override
    public List<KnowledgeEdge> getListOfForbiddenEdges() {
        final Set<KnowledgeEdge> edges = new LinkedHashSet<>();

        this.forbiddenRulesSpecs.forEach(e -> e.getFirst().forEach(e1 -> e.getSecond().forEach(e2 -> {
            if (!e1.equals(e2)) {
                edges.add(new KnowledgeEdge(e1, e2));
            }
        })));

        return new ArrayList<>(edges);
    }

    @Override
    public List<KnowledgeEdge> getListOfExplicitlyForbiddenEdges() {
        final Set<OrderedPair<Set<String>>> copy = new HashSet<>(this.forbiddenRulesSpecs);
        copy.removeAll(forbiddenTierRules());

        this.knowledgeGroups.forEach(e -> copy.remove(this.knowledgeGroupRules.get(e)));

        final Set<KnowledgeEdge> edges = new HashSet<>();
        for (final OrderedPair<Set<String>> e : copy)
            e.getFirst().forEach(e1 -> e.getSecond().forEach(e2 -> edges.add(new KnowledgeEdge(e1, e2))));

        return new ArrayList<>(edges);
    }

    @Override
    public boolean isOnlyCanCauseNextTier(final int tier) {
        ensureTiers(tier);

        final Set<String> varsInTier = this.tierSpecs.get(tier);
        if (varsInTier.isEmpty()) {
            return false;
        }

        if (tier + 2 >= this.tierSpecs.size()) {
            return false;
        }

        // all successive tiers > tier + 2 must be forbidden
        for (int tierN = tier + 2; tierN < this.tierSpecs.size(); tierN++) {
            final Set<String> varsInTierN = this.tierSpecs.get(tierN);
            final OrderedPair<Set<String>> o = new OrderedPair<>(varsInTier, varsInTierN);

            if (!this.forbiddenRulesSpecs.contains(o)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void setOnlyCanCauseNextTier(final int tier, final boolean onlyCausesNext) {
        ensureTiers(tier);

        final Set<String> varsInTier = this.tierSpecs.get(tier);

        for (int tierN = tier + 2; tierN < this.tierSpecs.size(); tierN++) {
            final Set<String> varsInTierN = this.tierSpecs.get(tierN);
            if (onlyCausesNext) {
                this.forbiddenRulesSpecs.add(new OrderedPair<>(varsInTier, varsInTierN));
            } else {
                this.forbiddenRulesSpecs.remove(new OrderedPair<>(varsInTier, varsInTierN));
            }
        }
    }

    /**
     * Computes a hashcode.
     */
    @Override
    public int hashCode() {
        int hash = 37;
        hash += 17 * this.variables.hashCode() + 37;
        hash += 17 * this.forbiddenRulesSpecs.hashCode() + 37;
        hash += 17 * this.requiredRulesSpecs.hashCode() + 37;
        hash += 17 * this.tierSpecs.hashCode() + 37;
        return hash;
    }

    /**
     * Two Knowledge objects are equal just in case their forbidden and required
     * edges are equal, and their tiers are equal.
     */
    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof Knowledge2)) {
            return false;
        }
        final Knowledge2 that = (Knowledge2) o;

        return this.forbiddenRulesSpecs.equals(that.forbiddenRulesSpecs)
                && this.requiredRulesSpecs.equals(that.requiredRulesSpecs)
                && this.tierSpecs.equals(that.tierSpecs);
    }

    /**
     * @return the contents of this Knowledge object in String form.
     */
    @Override
    public String toString() {
        try {
            final CharArrayWriter out = new CharArrayWriter();
            DataWriter.saveKnowledge(this, out);
            return out.toString();
        } catch (final IOException e) {
            throw new IllegalStateException("Could not render knowledge.");
        }
    }

}
