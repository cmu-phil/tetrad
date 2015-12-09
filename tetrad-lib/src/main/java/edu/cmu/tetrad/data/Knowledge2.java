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
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.regex.Matcher;

/**
 * Stores information about required and forbidden edges and common causes for
 * use in algorithms.  This information can be set edge by edge or else globally
 * via temporal tiers.  When setting temporal tiers, all edges from later tiers
 * to earlier tiers are forbidden.
 * <p>
 * For this class, all variable names are
 * referenced by name only.  This is because the same Knowledge object is
 * intended to plug into different graphs with MyNodes that possibly have the same
 * names.  Thus, if the Knowledge object forbids the edge X --> Y, then it
 * forbids any edge which connects a MyNode named "X" to a MyNode named "Y", even if
 * the underlying MyNodes themselves named "X" and "Y", respectively, are not the
 * same.
 * <p>
 * In place of variable names, wildcard expressions containing the wildcard '*'
 * may be substituted. These will be matched to as many myNodes as possible.
 * The '*' wildcard matches any string of consecutive characters up until the following
 * character is encountered. Thus, "X*a" will match "X123a" and "X45a".
 *
 * @author Joseph Ramsey
 */
public final class Knowledge2 implements TetradSerializable, IKnowledge {
    static final long serialVersionUID = 23L;

    private Set<MyNode> myNodes = new HashSet<>();

    private List<OrderedPair<Set<MyNode>>> forbiddenRulesSpecs;
    private List<OrderedPair<Set<MyNode>>> requiredRulesSpecs;
    private List<Set<MyNode>> tierSpecs;

    // Legacy.
    private final List<KnowledgeGroup> knowledgeGroups = new ArrayList<>();
    private Map<KnowledgeGroup, OrderedPair<Set<MyNode>>> knowledgeGroupRules;

    private boolean defaultToKnowledgeLayout = false;

    private Map<String, MyNode> namesToVars = new HashMap<>();

    // Wraps a variable name so that it has object identity. For speed.
    public static class MyNode implements TetradSerializable {
        static final long serialVersionUID = 23L;
        private final String name;

        public MyNode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public String toString() {
            return getName();
        }

        public static MyNode serializableInstance() {
            return new MyNode("X");
        }
    }


    //================================CONSTRUCTORS========================//

    /**
     * Constructs a blank knowledge object.
     */
    public Knowledge2() {
        this.forbiddenRulesSpecs = new ArrayList<>();
        this.requiredRulesSpecs = new ArrayList<>();
        this.knowledgeGroupRules = new HashMap<>();
        this.tierSpecs = new ArrayList<>();

        this.namesToVars = new HashMap<>();
    }

    /**
     * Constructs a knowledge object for the given nodes.
     */
    public Knowledge2(Collection<String> nodes) {
        for (String MyNode : nodes) {
            if (!checkVarName(MyNode)) {
                throw new IllegalArgumentException("Bad variable node " + MyNode);
            }
        }

        for (String name : nodes) {
            addVariable(name);
        }

        this.forbiddenRulesSpecs = new ArrayList<>();
        this.requiredRulesSpecs = new ArrayList<>();
        this.knowledgeGroupRules = new HashMap<>();
        this.tierSpecs = new ArrayList<>();
    }


    /**
     * Makes a shallow copy.
     */
    private Knowledge2(Knowledge2 knowledge) {
        this.namesToVars = new HashMap<>(knowledge.namesToVars);
        this.myNodes = new HashSet<>(knowledge.myNodes);

        this.forbiddenRulesSpecs = new ArrayList<>(knowledge.forbiddenRulesSpecs);
        this.requiredRulesSpecs = new ArrayList<>(knowledge.requiredRulesSpecs);
        this.knowledgeGroupRules = new HashMap<>();
        this.tierSpecs = new ArrayList<>(knowledge.tierSpecs);

        this.defaultToKnowledgeLayout = knowledge.defaultToKnowledgeLayout;

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static Knowledge2 serializableInstance() {
        return new Knowledge2();
    }

    //===============================PUBLIC METHODS=======================//

    private MyNode getVar(String var1) {
        return namesToVars.get(var1);
    }


    /**
     * Adds the given variable or wildcard pattern to the given tier. The tier
     * is a non-negative integer.
     */
    public final void addToTier(int tier, String spec) {
        addVariable(spec);

        if (tier < 0) {
            throw new IllegalArgumentException();
        }

        if (spec == null) {
            throw new NullPointerException();
        }

        spec = checkSpec(spec);
        final Set<MyNode> vars = getExtent(spec);

        ensureTiers(tier);

        for (MyNode s : vars) {
            if (checkVarName(s.getName())) {
                addVariable(s.getName());
                tierSpecs.get(tier).add(s);
            }
        }
    }

    /**
     * Puts a variable into tier i if its name is xxx:ti for some xxx and some i.
     */
    public final void addToTiersByVarNames(List<String> myNodes) {
        if (!this.myNodes.containsAll(myNodes)) {
            for (String variable : myNodes) {
                if (!checkVarName(variable)) {
                    throw new IllegalArgumentException("Bad variable name: " + variable);
                }
                addVariable(variable);
            }
        }

        for (Object variable : myNodes) {
            String MyNode = (String) variable;
            int index = MyNode.lastIndexOf(":t");

            if (index != -1) {
                String substring = MyNode.substring(index + 2);
                addToTier(new Integer(substring), MyNode);
            }
        }
    }


    /**
     * @return a shallow copy of the list of group rules.
     */
    public List<KnowledgeGroup> getKnowledgeGroups() {
        return new ArrayList<>(this.knowledgeGroups);
    }


    /**
     * Removes the knowledge group at the given index.
     */
    public void removeKnowledgeGroup(int index) {
        OrderedPair<Set<MyNode>> old = knowledgeGroupRules.get(knowledgeGroups.get(index));

        forbiddenRulesSpecs.remove(old);
        requiredRulesSpecs.remove(old);

        this.knowledgeGroups.remove(index);
    }

    /**
     * Adds a knowledge group. Legacy method, replaced by setForbidden, setRequired with patterns.
     * Needed for the interface.
     */
    public void addKnowledgeGroup(KnowledgeGroup group) {
        this.knowledgeGroups.add(group);

        OrderedPair<Set<MyNode>> o = getGroupRule(group);
        knowledgeGroupRules.put(group, o);

        if (group.getType() == KnowledgeGroup.FORBIDDEN) {
            forbiddenRulesSpecs.add(o);
        } else if (group.getType() == KnowledgeGroup.REQUIRED) {
            requiredRulesSpecs.add(o);
        }
    }

    /**
     * Legacy, do not use.
     */
    public void setKnowledgeGroup(int index, KnowledgeGroup group) {
        OrderedPair<Set<MyNode>> o = getGroupRule(group);
        OrderedPair<Set<MyNode>> old = knowledgeGroupRules.get(knowledgeGroups.get(index));

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
     * Iterator over the KnowledgeEdge's representing forbidden edges.
     */
    public final Iterator<KnowledgeEdge> forbiddenEdgesIterator() {
        Set<KnowledgeEdge> edges = new HashSet<>();

        for (OrderedPair<Set<MyNode>> o : forbiddenRulesSpecs) {
            final Set<MyNode> first = o.getFirst();
            for (MyNode s1 : first) {
                final Set<MyNode> second = o.getSecond();
                for (MyNode s2 : second) {
                    if (!s1.equals(s2)) {
                        edges.add(new KnowledgeEdge(s1.getName(), s2.getName()));
                    }
                }
            }
        }

        return edges.iterator();
    }


    /**
     * Iterator over the knowledge's explicitly forbidden edges.
     */
    public final Iterator<KnowledgeEdge> explicitlyForbiddenEdgesIterator() {
        Set<OrderedPair<Set<MyNode>>> copy = new HashSet<>(forbiddenRulesSpecs);
        copy.removeAll(forbiddenTierRules());

        for (KnowledgeGroup group : knowledgeGroups) {
            copy.remove(knowledgeGroupRules.get(group));
        }

        Set<KnowledgeEdge> edges = new HashSet<>();

        for (OrderedPair<Set<MyNode>> o : copy) {
            final Set<MyNode> first = o.getFirst();
            for (MyNode s1 : first) {
                final Set<MyNode> second = o.getSecond();
                for (MyNode s2 : second) {
                    edges.add(new KnowledgeEdge(s1.getName(), s2.getName()));
                }
            }
        }

        return edges.iterator();
    }

    /**
     * @return the list of edges not in any tier.
     */
    public final List<String> getVariablesNotInTiers() {
        List<MyNode> notInTier = new ArrayList<>(myNodes);

        for (Set<MyNode> tier : tierSpecs) {
            if (tier == null) tier = new HashSet<>();
            notInTier.removeAll(tier);
        }

        List<String> names = new ArrayList<>();
        for (MyNode MyNode : notInTier) names.add(MyNode.getName());

        return names;
    }

    /**
     * @param tier the index of the desired tier.
     * @return a copy of this tier.
     */
    public final List<String> getTier(int tier) {
        ensureTiers(tier);

        List<String> names = new ArrayList<>();
        for (MyNode MyNode : tierSpecs.get(tier)) names.add(MyNode.getName());

        return names;
    }

    /**
     * @return the number of temporal tiers.
     */
    public final int getNumTiers() {
        return tierSpecs.size();
    }

    /**
     * Determines whether the edge var1 --> var2 is forbidden.
     */
    public final boolean isForbidden(String var1, String var2) {
        for (OrderedPair<Set<MyNode>> rule : forbiddenRulesSpecs) {
            if (rule.getFirst().contains(getVar(var1))) {
                if (rule.getSecond().contains(getVar(var2))) {
                    if (!var1.equals(var2)) {
                        return true;
                    }
                }
            }
        }

        if (isForbiddenByTiers(var1, var2)) return false;

        return false;
    }

    /**
     * Determines whether the edge var1 --> var2 is required..
     */
    public final boolean isRequired(String var1, String var2) {
        for (OrderedPair<Set<MyNode>> rule : requiredRulesSpecs) {
            if (rule.getFirst().contains(getVar(var1))) {
                if (rule.getSecond().contains(getVar(var2))) {
                    if (!var1.equals(var2)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }


    /**
     * Legacy.
     */
    public final boolean isRequiredByGroups(String var1, String var2) {
        Set<OrderedPair<Set<MyNode>>> s = new HashSet<>();

        for (KnowledgeGroup group : knowledgeGroups) {
            if (group.getType() == KnowledgeGroup.REQUIRED) {
                OrderedPair<Set<MyNode>> o = getGroupRule(group);
                s.add(o);
            }
        }

        for (OrderedPair<Set<MyNode>> rule : s) {
            if (rule.getFirst().contains(getVar(var1))) {
                if (rule.getSecond().contains(getVar(var2))) {
                    return true;
                }
            }
        }

        return false;
    }


    /**
     * Legacy.
     */
    public final boolean isForbiddenByGroups(String var1, String var2) {
        Set<OrderedPair<Set<MyNode>>> s = new HashSet<>();

        for (KnowledgeGroup group : knowledgeGroups) {
            if (group.getType() == KnowledgeGroup.FORBIDDEN) {
                OrderedPair<Set<MyNode>> o = getGroupRule(group);
                s.add(o);
            }
        }

        for (OrderedPair<Set<MyNode>> rule : s) {
            if (rule.getFirst().contains(namesToVars.get(var1))) {
                if (rule.getSecond().contains(namesToVars.get(var2))) {
                    return true;
                }
            }
        }

        return false;
    }


    /**
     * @return true iff no edge between x and y is required.
     */
    public final boolean noEdgeRequired(String x, String y) {
        return !(isRequired(x, y) || isRequired(y, x));
    }

    /**
     * Determines whether the edge var1 --> var2 is forbidden by the temporal
     * tiers.
     */
    public final boolean isForbiddenByTiers(String var1, String var2) {
        for (OrderedPair<Set<MyNode>> rule : forbiddenTierRules()) {
            if (rule.getFirst().contains(getVar(var1))) {
                if (rule.getSecond().contains(getVar(var2))) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * @return true if there is no background knowledge recorded.
     */
    public final boolean isEmpty() {
        return forbiddenRulesSpecs.isEmpty() && requiredRulesSpecs.isEmpty() && tierSpecs.isEmpty();
    }

    private void saveKnowledge(Writer out)
            throws IOException {
        StringBuilder buf = new StringBuilder();
        buf.append("/knowledge");

        buf.append("\naddtemporal\n");

        for (int i = 0; i < tierSpecs.size(); i++) {
            String forbiddenWithin = isTierForbiddenWithin(i) ? "*" : "";

            buf.append("\n").append(i).append(forbiddenWithin).append(" ");

            List<String> tier = getTier(i);

            for (Object aTier : tier) {
                String name = (String) aTier;
                buf.append(name).append(" ");
            }
        }

        buf.append("\n");

        buf.append("\nforbiddirect\n\n");

        Set<OrderedPair<Set<MyNode>>> copy = new HashSet<>(forbiddenRulesSpecs);
        copy.removeAll(forbiddenTierRules());

        for (OrderedPair<Set<MyNode>> o : copy) {
            Set<MyNode> first = o.getFirst();
            Set<MyNode> second = o.getSecond();

            for (MyNode s : first) {
                buf.append(s).append(" ");
            }

            buf.append("==> ");

            for (MyNode s : second) {
                buf.append(s).append(" ");
            }

            buf.append("\n");
        }

        buf.append("requiredirect\n\n");

        for (OrderedPair<Set<MyNode>> o : requiredRulesSpecs) {
            Set<MyNode> first = o.getFirst();
            Set<MyNode> second = o.getSecond();

            for (MyNode s : first) {
                buf.append(s).append(" ");
            }

            buf.append("==> ");

            for (MyNode s : second) {
                buf.append(s).append(" ");
            }

            buf.append("\n");
        }

        out.write(buf.toString());
        out.flush();
    }

    /**
     * Iterator over the KnowledgeEdge's representing required edges.
     */
    public final Iterator<KnowledgeEdge> requiredEdgesIterator() {
        Set<KnowledgeEdge> edges = new HashSet<>();

        for (OrderedPair<Set<MyNode>> o : requiredRulesSpecs) {
            final Set<MyNode> first = o.getFirst();
            for (MyNode s1 : first) {
                final Set<MyNode> second = o.getSecond();
                for (MyNode s2 : second) {
                    if (!s1.equals(s2)) {
                        edges.add(new KnowledgeEdge(s1.getName(), s2.getName()));
                    }
                }
            }
        }

        return edges.iterator();
    }

    /**
     * Iterator over the KnowledgeEdge's explicitly required edges.
     */
    public final Iterator<KnowledgeEdge> explicitlyRequiredEdgesIterator() {
        return requiredEdgesIterator();
    }


    /**
     * Marks the edge var1 --> var2 as forbid.
     */
    public final void setForbidden(String spec1, String spec2) {
        addVariable(spec1);
        addVariable(spec2);

        spec1 = checkSpec(spec1);
        spec2 = checkSpec(spec2);

        Set<MyNode> f1 = getExtent(spec1);
        Set<MyNode> f2 = getExtent(spec2);

        OrderedPair<Set<MyNode>> o = new OrderedPair<>(f1, f2);

        forbiddenRulesSpecs.add(o);
    }

    /**
     * Marks the edge var1 --> var2 as not forbid.
     */
    @Override
    public final void removeForbidden(String spec1, String spec2) {
        spec1 = checkSpec(spec1);
        spec2 = checkSpec(spec2);

        Set<MyNode> f1 = getExtent(spec1);
        Set<MyNode> f2 = getExtent(spec2);

        OrderedPair<Set<MyNode>> o = new OrderedPair<>(f1, f2);

        forbiddenRulesSpecs.remove(o);
    }

    /**
     * Marks the edge var1 --> var2 as required.
     */
    public final void setRequired(String spec1, String spec2) {
        addVariable(spec1);
        addVariable(spec2);

        spec1 = checkSpec(spec1);
        spec2 = checkSpec(spec2);

        Set<MyNode> f1 = getExtent(spec1);
        Set<MyNode> f2 = getExtent(spec2);

        for (MyNode s : f1) {
            if (checkVarName(s.getName())) {
                addVariable(s.getName());
            }
        }

        for (MyNode s : f2) {
            if (checkVarName(s.getName())) {
                addVariable(s.getName());
            }
        }

        OrderedPair<Set<MyNode>> o = new OrderedPair<>(f1, f2);

        requiredRulesSpecs.add(o);
    }

    /**
     * Marks the edge var1 --> var2 as not required.
     */
    public final void removeRequired(String spec1, String spec2) {
        spec1 = checkSpec(spec1);
        spec2 = checkSpec(spec2);

        Set<MyNode> f1 = getExtent(spec1);
        Set<MyNode> f2 = getExtent(spec2);

        OrderedPair<Set<MyNode>> o = new OrderedPair<>(f1, f2);

        requiredRulesSpecs.remove(o);
    }

    /**
     * Removes the given variable from all tiers.
     */
    public final void removeFromTiers(String spec) {
        for (Set<MyNode> tier : tierSpecs) {
            tier.remove(getVar(spec));
        }
    }

    /**
     * Forbids any variable from being parent of any other variable within the given
     * tier, or cancels this forbidding.
     */
    public final void setTierForbiddenWithin(int tier, boolean forbidden) {
        ensureTiers(tier);
        Set<MyNode> _tier = tierSpecs.get(tier);
        OrderedPair<Set<MyNode>> o = new OrderedPair<>(_tier, _tier);

        if (forbidden) {
            forbiddenRulesSpecs.add(o);
        } else {
            forbiddenRulesSpecs.remove(o);
        }
    }

    /**
     * Checks whether it is the case that any variable is forbidden by any other variable
     * within a given tier.
     */
    public final boolean isTierForbiddenWithin(int tier) {
        ensureTiers(tier);

        if (tierSpecs.get(tier).isEmpty()) return false;

        Set<MyNode> _tier = tierSpecs.get(tier);
        OrderedPair<Set<MyNode>> o = new OrderedPair<>(_tier, _tier);

        return forbiddenRulesSpecs.contains(o);

    }

    private void ensureTiers(int tier) {
        for (int i = tierSpecs.size(); i <= tier; i++) {
            tierSpecs.add(new HashSet<MyNode>());

            for (int j = 0; j < i; j++) {
                forbiddenRulesSpecs.add(new OrderedPair<>(tierSpecs.get(i), tierSpecs.get(j)));
            }
        }
    }

    /**
     * @return the largest indes of a tier in which every variable is forbidden by every
     * other variable, or -1 if there is not such tier.
     */
    public final int getMaxTierForbiddenWithin() {
        for (int tier = tierSpecs.size(); tier >= 0; tier--) {
            if (isTierForbiddenWithin(tier)) {
                return tier;
            }
        }

        return -1;
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
        this.forbiddenRulesSpecs = new ArrayList<>();
        this.requiredRulesSpecs = new ArrayList<>();
        this.tierSpecs = new ArrayList<>();
    }

    /**
     * Computes a hashcode.
     */
    public final int hashCode() {
        int hash = 37;
        hash += 17 * this.myNodes.hashCode() + 37;
        hash += 17 * this.forbiddenRulesSpecs.hashCode() + 37;
        hash += 17 * this.requiredRulesSpecs.hashCode() + 37;
        hash += 17 * this.tierSpecs.hashCode() + 37;
        return hash;
    }

    /**
     * Two Knowledge objects are equal just in case their forbidden and required edges
     * are equal, and their tiers are equal.
     */
    public final boolean equals(Object o) {
        if (!(o instanceof Knowledge2)) return false;
        Knowledge2 that = (Knowledge2) o;

        return this.forbiddenRulesSpecs.equals(that.forbiddenRulesSpecs)
                && this.requiredRulesSpecs.equals(that.requiredRulesSpecs)
                && this.tierSpecs.equals(that.tierSpecs);
    }

    /**
     * @return the contents of this Knowledge object in String form.
     */
    public final String toString() {
        try {
            CharArrayWriter out = new CharArrayWriter();
            saveKnowledge(out);
            return out.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Could not render knowledge.");
        }
    }

    /**
     * Does a subclass specific copy. For this class does a deep copy.
     */
    @Override
    public IKnowledge copy() {
        return new Knowledge2(this);
    }

    public boolean isViolatedBy(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            if (!edge.isDirected()) {
                continue;
            }

            Node from = Edges.getDirectedEdgeTail(edge);
            Node to = Edges.getDirectedEdgeHead(edge);

            if (isForbidden(from.getName(), to.getName())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Sets the variable in a given tier to the specified list.
     */
    public void setTier(int tier, List<String> vars) {
        ensureTiers(tier);
        Set<MyNode> _tier = tierSpecs.get(tier);
        if (_tier != null) _tier.clear();

        for (String var : vars) {
            addToTier(tier, var);
        }
    }

    /**
     * Adds the given variable name to knowledge. Duplicates are ignored.
     */
    public void addVariable(String varName) {
        if (!namesToVars.containsKey(varName) && checkVarName(varName)) {
            MyNode e = new MyNode(varName);
            myNodes.add(e);
            namesToVars.put(varName, e);
        }
    }

    /**
     * Removes the given variable from the list of myNodes and all rules.
     */
    public void removeVariable(String name) {
        if (!checkVarName(name)) {
            throw new IllegalArgumentException("Bad variable name: " + name);
        }

        MyNode MyNode = getVar(name);

        myNodes.remove(MyNode);

        for (OrderedPair<Set<MyNode>> o : forbiddenRulesSpecs) {
            o.getFirst().remove(MyNode);
            o.getSecond().remove(MyNode);
        }
        for (OrderedPair<Set<MyNode>> o : requiredRulesSpecs) {
            o.getFirst().remove(MyNode);
            o.getSecond().remove(MyNode);
        }
        for (Set<MyNode> tier : tierSpecs) {
            tier.remove(MyNode);
        }
    }

    /**
     * @return a copy of the list of variable, in alphabetical order.
     */
    public List<String> getVariables() {
        List<String> names = new ArrayList<>();
        for (MyNode MyNode : myNodes) names.add(MyNode.getName());
        return new ArrayList<>(names);
    }

    //=====================================PRIVATE METHODS============================//

    private OrderedPair<Set<MyNode>> getGroupRule(KnowledgeGroup group) {
        Set<String> from = group.getFromVariables();
        Set<String> to = group.getToVariables();
        Set<MyNode> fromExtent = new HashSet<>();
        Set<MyNode> toExtent = new HashSet<>();
        for (String s : from) {
            fromExtent.addAll(getExtent(s));
        }
        for (String s : to) {
            toExtent.addAll(getExtent(s));
        }
        return new OrderedPair<>(fromExtent, toExtent);
    }

    private boolean checkVarName(String name) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[A-Za-z0-9:_\\-\\.]+");
        Matcher matcher = pattern.matcher(name);
        return matcher.matches();
    }

    private String checkSpec(String spec) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[A-Za-z0-9:-_,\\-\\.*]+");
        Matcher matcher = pattern.matcher(spec);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(spec + ": Pattern names can consist of alphabetic " +
                    "characters plus :, _, -, and .. A wildcard '*' may be included to match a " +
                    "string of such characters.");
        }

        spec = spec.replace(".", "\\.");

        return spec;
    }

    private Set<MyNode> getExtent(String spec) {
        Set<String> split = split(spec);
        Set<MyNode> matches = new HashSet<>();

        for (String _spec : split) {
            _spec = _spec.replace("*", ".*");

            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(_spec);

            for (MyNode var : myNodes) {
                Matcher matcher = pattern.matcher(var.getName());
                if (matcher.matches()) {
                    matches.add(var);
                }
            }
        }

        return matches;
    }

    private Set<String> split(String spec) {
        String[] tokens = spec.split(",");

        Set<String> _tokens = new HashSet<>();

        for (String _token : tokens) {
            if (!_token.trim().equals("")) {
                _tokens.add(_token);
            }
        }

        return _tokens;
    }

    private Set<OrderedPair<Set<MyNode>>> forbiddenTierRules() {
        Set<OrderedPair<Set<MyNode>>> rules = new HashSet<>();

        for (int i = 0; i < tierSpecs.size(); i++) {
            if (isTierForbiddenWithin(i)) {
                rules.add(new OrderedPair<>(tierSpecs.get(i), tierSpecs.get(i)));
            }
        }

        for (int i = 0; i < tierSpecs.size(); i++) {
            for (int j = i + 1; j < tierSpecs.size(); j++) {
                rules.add(new OrderedPair<>(tierSpecs.get(j), tierSpecs.get(i)));
            }
        }

        return rules;
    }
}




