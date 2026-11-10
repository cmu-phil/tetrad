///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.data.audit;

import java.text.NumberFormat;
import java.util.*;

/**
 * Turns the determinism findings of a {@link DataAudit} into a list of variables whose removal would resolve them,
 * for a user to review. This is advice, not a decision: which member of a deterministic relationship is the
 * derived one is a fact about how the file was built, which the audit cannot know; the suggester applies a fixed
 * convention, states it in each reason, and the user chooses.
 * <p>
 * Findings considered, in the order they are processed (exact before near, then by strength):
 * <ul>
 *     <li>{@link FindingCode#DUPLICATE_COLUMNS}: the second-listed column of the pair is suggested; the two carry
 *     the same information on their overlap, so either could go;</li>
 *     <li>{@link FindingCode#DETERMINISTIC_RELATION}, {@link FindingCode#NEAR_DETERMINISM_NONLINEAR},
 *     {@link FindingCode#NEAR_DETERMINISM_LINEAR}: the determined variable, listed first in the finding, is
 *     suggested. A determined variable is typically a computed or derived column; keeping the determiners keeps the
 *     inputs;</li>
 *     <li>{@link FindingCode#NEAR_DETERMINISM_DISCRETE_CONTINUOUS}: the discrete variable is suggested, since it is
 *     close to a coarsening of the continuous one and carries less information.</li>
 * </ul>
 * Removals are chosen greedily: findings are taken in order, and a finding any of whose listed variables has already
 * been chosen for removal is treated as resolved by that removal and produces no new suggestion. For a linear
 * finding the listed variables include the small determining subset when the audit found one, so removing a member
 * of a near-collinear cluster resolves the cluster's other findings rather than removing every member. Variables
 * that would have been suggested but for an earlier removal are still returned, unrecommended, so the user can opt
 * in; the dialog shows both.
 * <p>
 * The convention picks one member of each relationship, but the choice belongs to the user, so every OTHER variable
 * named in a determinism finding is also returned, in role {@link Role#ALTERNATIVE} and never recommended: a
 * determiner, the first column of a duplicate pair, the continuous variable of a near-coarsening. Without these a
 * user who knows that the determined-looking column is the measured one has nothing to check; naming the determiners
 * in a reason string is not an option they can act on. Removing an alternative is not guaranteed to resolve the
 * finding (the remaining determiners may still determine the target), which is why these are offered rather than
 * recommended; the audit is recomputed after removal, so surviving determinism reappears in the findings table.
 * <p>
 * Constant columns are not determinism and are not included; the Data Manipulation box has a separate model for
 * them. {@link FindingCode#EXACT_LINEAR_DEPENDENCE} names no variables and cannot be acted on here.
 *
 * @author josephramsey
 */
public final class DeterminismRemovalSuggester {

    private DeterminismRemovalSuggester() {
    }

    /**
     * Which member of its finding's relationship a suggested variable is. This says what the variable is, not
     * whether its removal is recommended; a {@link #SUGGESTED} row may be unrecommended because an earlier removal
     * has likely resolved its finding.
     */
    public enum Role {

        /**
         * The member the fixed convention picks: the determined variable, the second column of a duplicate pair, or
         * the discrete near-coarsening of a continuous variable.
         */
        SUGGESTED,

        /**
         * Another variable named in the same finding: a determiner, the first column of a duplicate pair, or the
         * continuous variable of a near-coarsening. Offered so the user can drop one of these instead, and never
         * recommended.
         */
        ALTERNATIVE
    }

    /**
     * One suggested removal.
     *
     * @param variable    The variable to remove.
     * @param code        The finding it is suggested for.
     * @param role        Which member of the finding's relationship this variable is.
     * @param reason      A one-line reason naming the other variables involved and the statistic.
     * @param recommended True if removal is recommended; false if the finding is likely already resolved by an
     *                    earlier recommended removal (named in the reason), or if the variable is an
     *                    {@link Role#ALTERNATIVE}, so the variable is offered but not checked.
     */
    public record Suggestion(String variable, FindingCode code, Role role, String reason, boolean recommended) {
    }

    /**
     * Returns the codes this suggester acts on.
     *
     * @return The codes.
     */
    public static Set<FindingCode> codes() {
        return EnumSet.of(FindingCode.DUPLICATE_COLUMNS, FindingCode.DETERMINISTIC_RELATION,
                FindingCode.NEAR_DETERMINISM_NONLINEAR, FindingCode.NEAR_DETERMINISM_LINEAR,
                FindingCode.NEAR_DETERMINISM_DISCRETE_CONTINUOUS);
    }

    /**
     * Suggests removals for the determinism findings among the given findings.
     *
     * @param findings The audit findings.
     * @return The suggestions: recommended ones first, in the order they were chosen, then the unrecommended
     * {@link Role#SUGGESTED} ones, then the {@link Role#ALTERNATIVE} ones in finding order. Each variable appears at
     * most once, in the first role it earns.
     */
    public static List<Suggestion> suggest(List<AuditFinding> findings) {
        List<AuditFinding> relevant = new ArrayList<>();
        for (AuditFinding f : findings) {
            if (codes().contains(f.getCode()) && !f.getVariables().isEmpty()) relevant.add(f);
        }

        relevant.sort(Comparator.comparingInt((AuditFinding f) -> rank(f.getCode()))
                .thenComparing(f -> -strength(f)));

        Set<String> removed = new LinkedHashSet<>();
        List<Suggestion> recommended = new ArrayList<>();
        List<Suggestion> optional = new ArrayList<>();
        Set<String> offered = new HashSet<>();

        for (AuditFinding f : relevant) {
            String candidate = candidate(f);
            if (candidate == null) continue;

            String resolvedBy = null;
            for (String v : f.getVariables()) {
                if (removed.contains(v)) {
                    resolvedBy = v;
                    break;
                }
            }

            if (resolvedBy != null) {
                if (!removed.contains(candidate) && offered.add(candidate)) {
                    optional.add(new Suggestion(candidate, f.getCode(), Role.SUGGESTED,
                            reason(f) + " Likely resolved by removing " + resolvedBy + ".", false));
                }
                continue;
            }

            if (removed.add(candidate)) {
                offered.add(candidate);
                recommended.add(new Suggestion(candidate, f.getCode(), Role.SUGGESTED, reason(f), true));
            }
        }

        // Every other variable named in a determinism finding, offered unrecommended. The greedy pass above has
        // finished, so `offered` is final and an alternative is emitted only for a variable no finding has already
        // put on the list in its own right: the recommendations are unaffected by this pass.
        List<Suggestion> alternatives = new ArrayList<>();

        for (AuditFinding f : relevant) {
            String candidate = candidate(f);
            if (candidate == null) continue;

            for (String v : f.getVariables()) {
                if (v.equals(candidate) || !offered.add(v)) continue;
                alternatives.add(new Suggestion(v, f.getCode(), Role.ALTERNATIVE,
                        alternativeReason(f, candidate), false));
            }
        }

        List<Suggestion> out = new ArrayList<>(recommended);
        for (Suggestion s : optional) if (!removed.contains(s.variable())) out.add(s);
        out.addAll(alternatives);
        return out;
    }

    private static int rank(FindingCode code) {
        return switch (code) {
            case DUPLICATE_COLUMNS -> 0;
            case DETERMINISTIC_RELATION -> 1;
            case NEAR_DETERMINISM_NONLINEAR -> 2;
            case NEAR_DETERMINISM_LINEAR -> 3;
            case NEAR_DETERMINISM_DISCRETE_CONTINUOUS -> 4;
            default -> 9;
        };
    }

    private static double strength(AuditFinding f) {
        Map<String, Double> v = f.getValues();
        for (String key : new String[]{"rSquared", "looRSquared", "etaSquared", "correlation"}) {
            Double d = v.get(key);
            if (d != null) return Math.abs(d);
        }
        return 0.0;
    }

    private static String candidate(AuditFinding f) {
        List<String> vars = f.getVariables();
        return switch (f.getCode()) {
            case DUPLICATE_COLUMNS -> vars.size() >= 2 ? vars.get(1) : null;
            case NEAR_DETERMINISM_DISCRETE_CONTINUOUS -> vars.get(0); // the discrete variable is listed first
            default -> vars.get(0); // determined variable first
        };
    }

    private static String reason(AuditFinding f) {
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setMaximumFractionDigits(3);
        List<String> vars = f.getVariables();
        Map<String, Double> v = f.getValues();
        List<String> others = vars.size() > 1 ? vars.subList(1, vars.size()) : List.of();

        return switch (f.getCode()) {
            case DUPLICATE_COLUMNS -> "Exact affine copy of " + vars.get(0) + " (r = "
                    + nf.format(v.getOrDefault("correlation", Double.NaN)) + "); one of the pair is redundant.";
            case DETERMINISTIC_RELATION -> "Determined, cell by cell, by {" + String.join(", ", others) + "}.";
            case NEAR_DETERMINISM_NONLINEAR -> "Nearly a smooth function of {" + String.join(", ", others)
                    + "} (leave-one-out R^2 = " + nf.format(v.getOrDefault("looRSquared", Double.NaN)) + ").";
            case NEAR_DETERMINISM_LINEAR -> others.isEmpty()
                    ? "Nearly a linear function of the other continuous variables (R^2 = "
                    + nf.format(v.getOrDefault("rSquared", Double.NaN)) + "; dependence is diffuse)."
                    : "Nearly a linear function of {" + String.join(", ", others) + "} (R^2 = "
                    + nf.format(v.getOrDefault("rSquared", Double.NaN)) + ").";
            case NEAR_DETERMINISM_DISCRETE_CONTINUOUS -> "Nearly a coarsening of " + vars.get(1)
                    + " (eta^2 = " + nf.format(v.getOrDefault("etaSquared", Double.NaN)) + ").";
            default -> f.getMessage();
        };
    }

    /**
     * The reason line for an {@link Role#ALTERNATIVE} row: what part this variable plays in the finding, and what
     * removing it in place of the suggested variable does. These lines do not promise that the finding is resolved,
     * because for a relation with several determiners it may not be.
     */
    private static String alternativeReason(AuditFinding f, String candidate) {
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setMaximumFractionDigits(3);
        Map<String, Double> v = f.getValues();

        return switch (f.getCode()) {
            case DUPLICATE_COLUMNS -> "The other column of the duplicate pair with " + candidate + " (r = "
                    + nf.format(v.getOrDefault("correlation", Double.NaN))
                    + "); removing either one of the two resolves the finding.";
            case DETERMINISTIC_RELATION -> "A determiner of " + candidate
                    + ", cell by cell. Remove this instead if " + candidate
                    + " is the measured column; the finding is resolved unless the remaining determiners still "
                    + "determine " + candidate + ".";
            case NEAR_DETERMINISM_NONLINEAR -> "An input to the near-smooth function for " + candidate
                    + " (leave-one-out R^2 = " + nf.format(v.getOrDefault("looRSquared", Double.NaN))
                    + "). Remove this instead if " + candidate + " is the column to keep.";
            case NEAR_DETERMINISM_LINEAR -> "A determiner in the near-linear relation for " + candidate
                    + " (R^2 = " + nf.format(v.getOrDefault("rSquared", Double.NaN))
                    + "); removing one member of a near-collinear cluster may be enough.";
            case NEAR_DETERMINISM_DISCRETE_CONTINUOUS -> "The continuous variable that " + candidate
                    + " is nearly a coarsening of (eta^2 = " + nf.format(v.getOrDefault("etaSquared", Double.NaN))
                    + "). Remove this instead to keep the discrete grouping.";
            default -> f.getMessage();
        };
    }
}
