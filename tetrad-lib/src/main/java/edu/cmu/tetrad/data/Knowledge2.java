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

import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.OrderedPair;
import edu.cmu.tetrad.util.TetradSerializable;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
public final class Knowledge2 implements TetradSerializable, IKnowledge {

    private static final long serialVersionUID = 23L;

    private static final Pattern VARNAME_PATTERN = Pattern.compile("[A-Za-z0-9:_\\-\\.]+");
    private static final Pattern SPEC_PATTERN = Pattern.compile("[A-Za-z0-9:-_,\\-\\.*]+");
    private static final Pattern COMMAN_DELIM = Pattern.compile(",");

    private boolean defaultToKnowledgeLayout;

    private final Set<String> variables;
    private final Set<OrderedPair<Set<String>>> forbiddenRulesSpecs;
    private final Set<OrderedPair<Set<String>>> requiredRulesSpecs;
    private final List<Set<String>> tierSpecs;

    // Legacy.
    private final List<KnowledgeGroup> knowledgeGroups;
    private final Map<KnowledgeGroup, OrderedPair<Set<String>>> knowledgeGroupRules;

    public Knowledge2() {
        this.variables = new HashSet<>();
        this.forbiddenRulesSpecs = new HashSet<>();
        this.requiredRulesSpecs = new HashSet<>();
        this.tierSpecs = new ArrayList<>();
        this.knowledgeGroups = new LinkedList<>();
        this.knowledgeGroupRules = new HashMap<>();
    }

    public Knowledge2(Collection<String> nodes) {
        this();

        nodes.forEach(node -> {
            if (checkVarName(node)) {
                variables.add(node);
            } else {
                throw new IllegalArgumentException(String.format("Bad variable node %s.", node));
            }
        });
    }

    public Knowledge2(Knowledge2 knowledge) {
        this.defaultToKnowledgeLayout = knowledge.defaultToKnowledgeLayout;

        this.variables = new HashSet<>(knowledge.variables);
        this.forbiddenRulesSpecs = new HashSet<>(knowledge.forbiddenRulesSpecs);
        this.requiredRulesSpecs = new HashSet<>(knowledge.requiredRulesSpecs);
        this.tierSpecs = new ArrayList<>(knowledge.tierSpecs);

        this.knowledgeGroups = knowledge.knowledgeGroups;
        this.knowledgeGroupRules = knowledge.knowledgeGroupRules;
    }

    private boolean checkVarName(String name) {
        return VARNAME_PATTERN.matcher(name).matches();
    }

    private String checkSpec(String spec) {
        Matcher matcher = SPEC_PATTERN.matcher(spec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(spec + ": Pattern names can consist of alphabetic "
                    + "characters plus :, _, -, and .. A wildcard '*' may be included to match a "
                    + "string of such characters.");
        }

        return spec.replace(".", "\\.");
    }

    private Set<String> getExtent(String spec) {
        Set<String> vars = new HashSet<>();

        if (spec.contains("*")) {
            split(spec).stream()
                    .map(e -> e.replace("*", ".*"))
                    .forEach(e -> {
                        final Pattern pattern = Pattern.compile(e);
                        variables.stream()
                                .filter(var -> pattern.matcher(var).matches())
                                .collect(Collectors.toCollection(() -> vars));
                    });
        } else {
            if (variables.contains(spec)) {
                vars.add(spec);
            }
        }

        return vars;
    }

    private Set<String> split(String spec) {
        return Arrays.stream(COMMAN_DELIM.split(spec))
                .map(String::trim)
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toSet());
    }

    private void ensureTiers(int tier) {
        for (int i = tierSpecs.size(); i <= tier; i++) {
            tierSpecs.add(new LinkedHashSet<>());

            for (int j = 0; j < i; j++) {
                forbiddenRulesSpecs.add(new OrderedPair<>(tierSpecs.get(i), tierSpecs.get(j)));
            }
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

    private Set<OrderedPair<Set<String>>> forbiddenTierRules() {
        Set<OrderedPair<Set<String>>> rules = new HashSet<>();

        for (int i = 0; i < tierSpecs.size(); i++) {
            if (isTierForbiddenWithin(i)) {
                rules.add(new OrderedPair<>(tierSpecs.get(i), tierSpecs.get(i)));
            }
        }

        for (int i = 0; i < tierSpecs.size(); i++) {
            if (isOnlyCanCauseNextTier(i)) {
                for (int j = i + 2; j < tierSpecs.size(); j++) {
                    rules.add(new OrderedPair<>(tierSpecs.get(i), tierSpecs.get(j)));
                }
            }
        }

        for (int i = 0; i < tierSpecs.size(); i++) {
            for (int j = i + 1; j < tierSpecs.size(); j++) {
                rules.add(new OrderedPair<>(tierSpecs.get(j), tierSpecs.get(i)));
            }
        }

        return rules;
    }

    /**
     * Adds the given variable or wildcard pattern to the given tier. The tier
     * is a non-negative integer.
     *
     * @param tier
     * @param spec
     */
    @Override
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

        getExtent(spec).stream()
                .filter(e -> checkVarName(e))
                .forEach(e -> {
                    variables.add(e);
                    tierSpecs.get(tier).add(e);
                });
    }

    /**
     * Puts a variable into tier i if its name is xxx:ti for some xxx and some
     * i.
     *
     * @param varNames
     */
    @Override
    public void addToTiersByVarNames(List<String> varNames) {
        if (!variables.containsAll(varNames)) {
            varNames.forEach(e -> {
                if (checkVarName(e)) {
                    variables.add(e);
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
     * Adds a knowledge group. Legacy method, replaced by setForbidden,
     * setRequired with patterns. Needed for the interface.
     *
     * @param group
     */
    @Override
    public void addKnowledgeGroup(KnowledgeGroup group) {
        this.knowledgeGroups.add(group);

        OrderedPair<Set<String>> o = getGroupRule(group);
        knowledgeGroupRules.put(group, o);

        if (group.getType() == KnowledgeGroup.FORBIDDEN) {
            forbiddenRulesSpecs.add(o);
        } else if (group.getType() == KnowledgeGroup.REQUIRED) {
            requiredRulesSpecs.add(o);
        }
    }

    @Override
    public void addVariable(String varName) {
        if (!variables.contains(varName) && checkVarName(varName)) {
            variables.add(varName);
        }
    }

    /**
     * Removes explicit knowledge and tier information.
     */
    @Override
    public void clear() {
        this.forbiddenRulesSpecs.clear();
        this.requiredRulesSpecs.clear();
        this.tierSpecs.clear();
    }

    /**
     * Iterator over the knowledge's explicitly forbidden edges.
     *
     * @return
     */
    @Override
    public Iterator<KnowledgeEdge> explicitlyForbiddenEdgesIterator() {
        Set<OrderedPair<Set<String>>> copy = new HashSet<>(forbiddenRulesSpecs);
        copy.removeAll(forbiddenTierRules());

        knowledgeGroups.forEach(e -> copy.remove(knowledgeGroupRules.get(e)));

        Set<KnowledgeEdge> edges = new HashSet<>();

        copy.forEach(o -> {
            o.getFirst().forEach(s1 -> {
                o.getSecond().forEach(s2 -> {
                    edges.add(new KnowledgeEdge(s1, s2));
                });
            });
        });

        return edges.iterator();
    }

    /**
     * Iterator over the KnowledgeEdge's explicitly required edges.
     *
     * @return
     */
    @Override
    public Iterator<KnowledgeEdge> explicitlyRequiredEdgesIterator() {
        return requiredEdgesIterator();
    }

    /**
     * Iterator over the KnowledgeEdge's representing forbidden edges.
     *
     * @return
     */
    @Override
    public Iterator<KnowledgeEdge> forbiddenEdgesIterator() {
        Set<KnowledgeEdge> edges = new HashSet<>();

        forbiddenRulesSpecs.forEach(o -> {
            o.getFirst().forEach(s1 -> {
                o.getSecond().forEach(s2 -> {
                    if (!s1.equals(s2)) {
                        edges.add(new KnowledgeEdge(s1, s2));
                    }
                });
            });
        });

        return edges.iterator();
    }

    /**
     *
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
        return variables.stream()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     *
     * @return the list of edges not in any tier.
     */
    @Override
    public List<String> getVariablesNotInTiers() {
        Set<String> notInTier = new HashSet<>(variables);
        tierSpecs.forEach(notInTier::removeAll);

        return notInTier.stream()
                .collect(Collectors.toList());
    }

    /**
     *
     * @param tier the index of the desired tier
     * @return a copy of this tier
     */
    @Override
    public List<String> getTier(int tier) {
        ensureTiers(tier);

        return tierSpecs.get(tier).stream()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     *
     * @return the number of temporal tiers
     */
    @Override
    public int getNumTiers() {
        return tierSpecs.size();
    }

    @Override
    public boolean isDefaultToKnowledgeLayout() {
        return defaultToKnowledgeLayout;
    }

    private boolean isForbiddenByRules(String var1, String var2) {
        return forbiddenRulesSpecs.stream()
                .anyMatch(rule -> !var1.equals(var2)
                && rule.getFirst().contains(var1)
                && rule.getSecond().contains(var2));
    }

    /**
     * Determines whether the edge var1 --> var2 is forbidden.
     *
     * @param var1
     * @param var2
     * @return
     */
    @Override
    public boolean isForbidden(String var1, String var2) {
        if (isRequired(var1, var2)) {
            return false;
        }

        return isForbiddenByRules(var1, var2) || isForbiddenByTiers(var1, var2);
    }

    /**
     * Legacy.
     *
     * @param var1
     * @param var2
     * @return
     */
    @Override
    public boolean isForbiddenByGroups(String var1, String var2) {
        Set<OrderedPair<Set<String>>> s = knowledgeGroups.stream()
                .filter(e -> e.getType() == KnowledgeGroup.FORBIDDEN)
                .map(group -> getGroupRule(group))
                .collect(Collectors.toSet());

        return s.stream()
                .anyMatch(rule -> rule.getFirst().contains(var1)
                && rule.getSecond().contains(var2));
    }

    /**
     * Determines whether the edge var1 --> var2 is forbidden by the temporal
     * tiers.
     *
     * @param var1
     * @param var2
     * @return
     */
    @Override
    public boolean isForbiddenByTiers(String var1, String var2) {
        return forbiddenTierRules().stream()
                .anyMatch(rule -> rule.getFirst().contains(var1)
                && rule.getSecond().contains(var2));
    }

    /**
     * Determines whether the edge var1 --> var2 is required.
     *
     * @param var1
     * @param var2
     * @return
     */
    @Override
    public boolean isRequired(String var1, String var2) {
        return requiredRulesSpecs.stream()
                .anyMatch(rule -> !var1.equals(var2)
                && rule.getFirst().contains(var1)
                && rule.getSecond().contains(var2));
    }

    /**
     * Legacy.
     *
     * @param var1
     * @param var2
     * @return
     */
    @Override
    public boolean isRequiredByGroups(String var1, String var2) {
        Set<OrderedPair<Set<String>>> s = knowledgeGroups.stream()
                .filter(e -> e.getType() == KnowledgeGroup.REQUIRED)
                .map(group -> getGroupRule(group))
                .collect(Collectors.toSet());

        return s.stream()
                .anyMatch(rule -> rule.getFirst().contains(var1)
                && rule.getSecond().contains(var2));
    }

    /**
     * true if there is no background knowledge recorded.
     *
     * @return
     */
    @Override
    public boolean isEmpty() {
        return forbiddenRulesSpecs.isEmpty()
                && requiredRulesSpecs.isEmpty()
                && tierSpecs.isEmpty();
    }

    /**
     * Checks whether it is the case that any variable is forbidden by any other
     * variable within a given tier.
     *
     * @param tier
     * @return
     */
    @Override
    public boolean isTierForbiddenWithin(int tier) {
        ensureTiers(tier);

        Set<String> varsInTier = tierSpecs.get(tier);
        if (varsInTier.isEmpty()) {
            return false;
        }

        return forbiddenRulesSpecs.contains(new OrderedPair<>(varsInTier, varsInTier));
    }

    @Override
    public boolean isViolatedBy(Graph graph) {
        if (graph == null) {
            throw new NullPointerException("Sorry, a graph hasn't been provided.");
        }

        return graph.getEdges().stream()
                .filter(e -> e.isDirected())
                .anyMatch(edge -> {
                    Node from = Edges.getDirectedEdgeTail(edge);
                    Node to = Edges.getDirectedEdgeHead(edge);

                    return isForbidden(from.getName(), to.getName());
                });
    }

    /**
     *
     * @param x
     * @param y
     * @return true iff no edge between x and y is required
     */
    @Override
    public boolean noEdgeRequired(String x, String y) {
        return !(isRequired(x, y) || isRequired(y, x));
    }

    /**
     * Removes the given variable by name or search string from all tiers.
     *
     * @param spec
     */
    @Override
    public void removeFromTiers(String spec) {
        if (spec == null) {
            throw new NullPointerException();
        }

        spec = checkSpec(spec);
        getExtent(spec).forEach(s -> tierSpecs.forEach(tier -> tier.remove(s)));
    }

    /**
     * Removes the knowledge group at the given index.
     *
     * @param index
     */
    @Override
    public void removeKnowledgeGroup(int index) {
        OrderedPair<Set<String>> old = knowledgeGroupRules.get(knowledgeGroups.get(index));

        forbiddenRulesSpecs.remove(old);
        requiredRulesSpecs.remove(old);

        this.knowledgeGroups.remove(index);
    }

    /**
     * Removes the given variable from the list of myNodes and all rules.
     *
     * @param name
     */
    @Override
    public void removeVariable(String name) {
        if (!checkVarName(name)) {
            throw new IllegalArgumentException("Bad variable name: " + name);
        }

        variables.remove(name);

        forbiddenRulesSpecs.forEach(o -> {
            o.getFirst().remove(name);
            o.getSecond().remove(name);
        });

        requiredRulesSpecs.forEach(o -> {
            o.getFirst().remove(name);
            o.getSecond().remove(name);
        });

        tierSpecs.forEach(tier -> tier.remove(name));
    }

    /**
     * Iterator over the KnowledgeEdge's representing required edges.
     *
     * @return
     */
    @Override
    public Iterator<KnowledgeEdge> requiredEdgesIterator() {
        Set<KnowledgeEdge> edges = new HashSet<>();

        requiredRulesSpecs.forEach(o -> {
            o.getFirst().forEach(s1 -> {
                o.getSecond().forEach(s2 -> {
                    if (!s1.equals(s2)) {
                        edges.add(new KnowledgeEdge(s1, s2));
                    }
                });
            });
        });

        return edges.iterator();
    }

    /**
     * Marks the edge var1 --> var2 as forbid.
     *
     * @param var1
     * @param var2
     */
    @Override
    public void setForbidden(String var1, String var2) {
        addVariable(var1);
        addVariable(var2);

        var1 = checkSpec(var1);
        var2 = checkSpec(var2);

        Set<String> f1 = getExtent(var1);
        Set<String> f2 = getExtent(var2);

        forbiddenRulesSpecs.add(new OrderedPair<>(f1, f2));
    }

    /**
     * Marks the edge var1 --> var2 as not forbid.
     *
     * @param var1
     * @param var2
     */
    @Override
    public void removeForbidden(String var1, String var2) {
        var1 = checkSpec(var1);
        var2 = checkSpec(var2);

        Set<String> f1 = getExtent(var1);
        Set<String> f2 = getExtent(var2);

        forbiddenRulesSpecs.remove(new OrderedPair<>(f1, f2));
    }

    /**
     * Marks the edge var1 --> var2 as required.
     *
     * @param var1
     * @param var2
     */
    @Override
    public void setRequired(String var1, String var2) {
        addVariable(var1);
        addVariable(var2);

        var1 = checkSpec(var1);
        var2 = checkSpec(var2);

        Set<String> f1 = getExtent(var1);
        Set<String> f2 = getExtent(var2);

        f1.forEach(s -> {
            if (checkVarName(s)) {
                variables.add(s);
            }
        });
        f2.forEach(s -> {
            if (checkVarName(s)) {
                variables.add(s);
            }
        });

        requiredRulesSpecs.add(new OrderedPair<>(f1, f2));
    }

    /**
     * Marks the edge var1 --> var2 as not required.
     *
     * @param var1
     * @param var2
     */
    @Override
    public void removeRequired(String var1, String var2) {
        var1 = checkSpec(var1);
        var2 = checkSpec(var2);

        Set<String> f1 = getExtent(var1);
        Set<String> f2 = getExtent(var2);

        requiredRulesSpecs.remove(new OrderedPair<>(f1, f2));
    }

    /**
     * Legacy, do not use.
     *
     * @param index
     * @param group
     */
    @Override
    public void setKnowledgeGroup(int index, KnowledgeGroup group) {
        OrderedPair<Set<String>> o = getGroupRule(group);
        OrderedPair<Set<String>> old = knowledgeGroupRules.get(knowledgeGroups.get(index));

        forbiddenRulesSpecs.remove(old);
        requiredRulesSpecs.remove(old);

        if (group.getType() == KnowledgeGroup.FORBIDDEN) {
            forbiddenRulesSpecs.add(o);
        } else if (group.getType() == KnowledgeGroup.REQUIRED) {
            requiredRulesSpecs.add(o);
        }

        knowledgeGroups.set(index, group);
    }

    /**
     * Sets the variable in a given tier to the specified list.
     *
     * @param tier
     * @param vars
     */
    @Override
    public void setTier(int tier, List<String> vars) {
        ensureTiers(tier);
        Set<String> varsInTier = tierSpecs.get(tier);
        if (varsInTier != null) {
            varsInTier.clear();
        }

        vars.forEach(var -> addToTier(tier, var));
    }

    /**
     * Forbids any variable from being parent of any other variable within the
     * given tier, or cancels this forbidding.
     *
     * @param tier
     * @param forbidden
     */
    @Override
    public void setTierForbiddenWithin(int tier, boolean forbidden) {
        ensureTiers(tier);
        Set<String> varsInTier = tierSpecs.get(tier);

        if (forbidden) {
            forbiddenRulesSpecs.add(new OrderedPair<>(varsInTier, varsInTier));
        } else {
            forbiddenRulesSpecs.remove(new OrderedPair<>(varsInTier, varsInTier));
        }
    }

    /**
     *
     * @return the largest indes of a tier in which every variable is forbidden
     * by every other variable, or -1 if there is not such tier.
     */
    @Override
    public int getMaxTierForbiddenWithin() {
        for (int tier = tierSpecs.size(); tier >= 0; tier--) {
            if (isTierForbiddenWithin(tier)) {
                return tier;
            }
        }

        return -1;
    }

    @Override
    public void setDefaultToKnowledgeLayout(boolean defaultToKnowledgeLayout) {
        this.defaultToKnowledgeLayout = defaultToKnowledgeLayout;
    }

    /**
     * Makes a shallow copy.
     *
     * @return
     */
    @Override
    public IKnowledge copy() {
        return new Knowledge2(this);
    }

    /**
     * Returns the index of the tier of node if it's in a tier, otherwise -1.
     *
     * @param node
     * @return
     */
    @Override
    public int isInWhichTier(Node node) {
        for (int i = 0; i < tierSpecs.size(); i++) {
            Set<String> tier = tierSpecs.get(i);

            for (String myNode : tier) {
                if (myNode.equals(node.getName())) {
                    return i;
                }
            }
        }

        return -1;
    } // added by DMalinsky for tsFCI on 4/20/16

    @Override
    public List<KnowledgeEdge> getListOfRequiredEdges() {
        List<KnowledgeEdge> edges = new LinkedList<>();

        requiredRulesSpecs.forEach(e -> {
            e.getFirst().forEach(e1 -> {
                e.getSecond().forEach(e2 -> {
                    if (!e1.equals(e2)) {
                        edges.add(new KnowledgeEdge(e1, e2));
                    }
                });
            });
        });

        return edges;
    }

    @Override
    public List<KnowledgeEdge> getListOfExplicitlyRequiredEdges() {
        return getListOfRequiredEdges();
    }

    @Override
    public List<KnowledgeEdge> getListOfForbiddenEdges() {
        List<KnowledgeEdge> edges = new LinkedList<>();

        forbiddenRulesSpecs.forEach(e -> {
            e.getFirst().forEach(e1 -> {
                e.getSecond().forEach(e2 -> {
                    if (!e1.equals(e2)) {
                        edges.add(new KnowledgeEdge(e1, e2));
                    }
                });
            });
        });

        return edges;
    }

    @Override
    public List<KnowledgeEdge> getListOfExplicitlyForbiddenEdges() {
        Set<OrderedPair<Set<String>>> copy = new HashSet<>(forbiddenRulesSpecs);
        copy.removeAll(forbiddenTierRules());

        knowledgeGroups.forEach(e -> copy.remove(knowledgeGroupRules.get(e)));

        List<KnowledgeEdge> edges = new LinkedList<>();
        copy.forEach(e -> {
            e.getFirst().forEach(e1 -> {
                e.getSecond().forEach(e2 -> {
                    edges.add(new KnowledgeEdge(e1, e2));
                });
            });
        });

        return edges;
    }

    @Override
    public boolean isOnlyCanCauseNextTier(int tier) {
        ensureTiers(tier);

        Set<String> varsInTier = tierSpecs.get(tier);
        if (varsInTier.isEmpty()) {
            return false;
        }

        if (tier + 2 >= tierSpecs.size()) {
            return false;
        }

        // all successive tiers > tier + 2 must be forbidden
        for (int tierN = tier + 2; tierN < tierSpecs.size(); tierN++) {
            Set<String> varsInTierN = tierSpecs.get(tierN);
            OrderedPair<Set<String>> o = new OrderedPair<>(varsInTier, varsInTierN);

            if (!forbiddenRulesSpecs.contains(o)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void setOnlyCanCauseNextTier(int tier, boolean onlyCausesNext) {
        ensureTiers(tier);

        Set<String> varsInTier = tierSpecs.get(tier);

        for (int tierN = tier + 2; tierN < tierSpecs.size(); tierN++) {
            Set<String> varsInTierN = tierSpecs.get(tierN);
            if (onlyCausesNext) {
                forbiddenRulesSpecs.add(new OrderedPair<>(varsInTier, varsInTierN));
            } else {
                forbiddenRulesSpecs.remove(new OrderedPair<>(varsInTier, varsInTierN));
            }
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return
     */
    public static Knowledge2 serializableInstance() {
        return new Knowledge2();
    }

    /**
     * Computes a hashcode.
     */
    @Override
    public final int hashCode() {
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
     *
     * @param o
     * @return
     */
    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof Knowledge2)) {
            return false;
        }
        Knowledge2 that = (Knowledge2) o;

        return this.forbiddenRulesSpecs.equals(that.forbiddenRulesSpecs)
                && this.requiredRulesSpecs.equals(that.requiredRulesSpecs)
                && this.tierSpecs.equals(that.tierSpecs);
    }

    /**
     * @return the contents of this Knowledge object in String form.
     */
    @Override
    public final String toString() {
        try {
            CharArrayWriter out = new CharArrayWriter();
            DataWriter.saveKnowledge(this, out);
            return out.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Could not render knowledge.");
        }
    }

}
