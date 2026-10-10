/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.TMath;

import java.util.*;

/**
 * Utility class for handling operations related to blocks, such as creating block variables, canonicalizing blocks,
 * ensuring valid indices, and applying various cluster policies. This class includes methods to manipulate and process
 * blocks and their corresponding data representations within a dataset.
 */
public final class BlocksUtil {
    private BlocksUtil() {
    }

    /**
     * Pools several imputations of one data set into a single covariance matrix: the elementwise average of the
     * imputations' covariance matrices, at the imputations' (shared) sample size. This is the multiple-imputation
     * point estimate of the covariance; the between-imputation variance is not carried into it, so tests run on the
     * pooled matrix at the full sample size act as if the pooled covariance had been estimated from complete data.
     * The effective-sample-size parameter of the consuming method is the knob for compensating.
     * <p>
     * The variables of the returned matrix are the first imputation's variable objects, so a {@link BlockSpec} built
     * over the first imputation and blocks discovered from the pooled matrix refer to the same nodes.
     *
     * @param imputations the imputed data sets: the same variables, in the same order, with the same number of rows.
     * @return the averaged covariance matrix at the shared sample size.
     * @throws IllegalArgumentException if the list is empty, a member is not continuous tabular data, or the members
     *                                  differ in their variables or row counts.
     */
    public static CovarianceMatrix pooledCovariance(List<DataSet> imputations) {
        if (imputations == null || imputations.isEmpty()) {
            throw new IllegalArgumentException("No data sets to pool.");
        }

        DataSet first = imputations.getFirst();
        List<String> names = first.getVariableNames();
        int n = first.getNumRows();

        for (DataSet dataSet : imputations) {
            if (!dataSet.isContinuous()) {
                throw new IllegalArgumentException("Pooling imputations requires continuous tabular data; '"
                        + dataSet.getName() + "' is not continuous.");
            }
            if (!dataSet.getVariableNames().equals(names)) {
                throw new IllegalArgumentException("Pooling imputations requires the same variables in the same "
                        + "order in every data set; '" + dataSet.getName() + "' differs. Pooling is for several "
                        + "imputations of one data set, not for unrelated data sets.");
            }
            if (dataSet.getNumRows() != n) {
                throw new IllegalArgumentException("Pooling imputations requires the same number of rows in every "
                        + "data set; '" + dataSet.getName() + "' has " + dataSet.getNumRows() + " rows, not " + n + ".");
            }
            if (dataSet.existsMissingValue()) {
                throw new IllegalArgumentException("Data set '" + dataSet.getName() + "' still contains missing "
                        + "values; pooling expects completed imputations.");
            }
        }

        Matrix sum = new CovarianceMatrix(first).getMatrix();

        for (int k = 1; k < imputations.size(); k++) {
            Matrix cov = new CovarianceMatrix(imputations.get(k)).getMatrix();
            for (int i = 0; i < sum.getNumRows(); i++) {
                for (int j = 0; j < sum.getNumColumns(); j++) {
                    sum.set(i, j, sum.get(i, j) + cov.get(i, j));
                }
            }
        }

        double m = imputations.size();
        for (int i = 0; i < sum.getNumRows(); i++) {
            for (int j = 0; j < sum.getNumColumns(); j++) {
                sum.set(i, j, sum.get(i, j) / m);
            }
        }

        return new CovarianceMatrix(first.getVariables(), sum, n);
    }

    /**
     * Estimates an effective sample size for analyses run on the pooled covariance of several imputations, from the
     * disagreement among the imputations themselves. For each pair of variables, the correlation is Fisher-z
     * transformed in each imputation; its within-imputation variance is 1/(n-3) and its between-imputation variance
     * B is observed, so by Rubin's rules the pooled estimate has the precision of a complete sample of about
     * (n-3)/(1 + (1 + 1/M) B (n-3)) + 3 observations. The SMALLEST of these pairwise sizes is returned: a rank
     * test's precision is limited by its least well determined correlation, and in calibration runs on simulated
     * missing-at-random data the per-pair sizes consistently understated the imputation noise that rank tests react
     * to, so the conservative end of their distribution recovered clusters best at every missingness level tried,
     * with no cost at low missingness. With many variables the minimum leans low (it is a minimum over many noisy
     * ratios), which fails toward less power rather than toward spurious structure.
     * <p>
     * Improper imputers (which do not redraw the imputation model's parameters) understate B, so this estimate leans
     * large; it is nevertheless far closer to the truth than the full n, which treats the pooled covariance as if it
     * had been estimated from complete data.
     *
     * @param imputations the imputed data sets, as for {@link #pooledCovariance(List)}.
     * @return the estimated effective sample size, at least 10 and at most n.
     * @throws IllegalArgumentException as for {@link #pooledCovariance(List)}, and if fewer than two imputations are
     *                                  given.
     */
    public static int pooledEffectiveSampleSize(List<DataSet> imputations) {
        if (imputations == null || imputations.size() < 2) {
            throw new IllegalArgumentException("Estimating an effective sample size from imputation disagreement "
                    + "requires at least two imputations.");
        }

        int n = imputations.getFirst().getNumRows();
        int p = imputations.getFirst().getNumColumns();
        int m = imputations.size();

        if (n <= 4) {
            return Math.max(2, n);
        }

        List<Matrix> correlations = new ArrayList<>();
        for (DataSet dataSet : imputations) {
            correlations.add(new CorrelationMatrix(dataSet).getMatrix());
        }

        double w = 1.0 / (n - 3);
        List<Double> sizes = new ArrayList<>();

        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                double mean = 0;
                double[] z = new double[m];
                for (int k = 0; k < m; k++) {
                    double r = Math.max(-0.9999, Math.min(0.9999, correlations.get(k).get(i, j)));
                    z[k] = 0.5 * Math.log((1 + r) / (1 - r));
                    mean += z[k];
                }
                mean /= m;
                double b = 0;
                for (int k = 0; k < m; k++) b += (z[k] - mean) * (z[k] - mean);
                b /= (m - 1);

                double rIncrease = (1 + 1.0 / m) * b / w;
                sizes.add((n - 3) / (1 + rIncrease) + 3);
            }
        }

        double smallest = Collections.min(sizes);

        return (int) Math.max(10, Math.min(n, Math.round(smallest)));
    }

    /**
     * Checks that a pooled covariance matrix handed to a block discoverer covers the same variables, in the same
     * order, as the discoverer's representative data set, which anchors the resulting {@link BlockSpec}.
     *
     * @param pooled  the pooled covariance matrix, or null (no check).
     * @param dataSet the representative data set.
     * @throws IllegalArgumentException if the variable names differ.
     */
    static void checkPooledMatchesData(CovarianceMatrix pooled, DataSet dataSet) {
        if (pooled == null) return;
        if (!pooled.getVariableNames().equals(dataSet.getVariableNames())) {
            throw new IllegalArgumentException("The pooled covariance matrix does not cover the same variables, in "
                    + "the same order, as the representative data set.");
        }
    }

    /**
     * Creates a list of block variables based on the provided list of blocks and the dataset. If a block contains a
     * single index, the corresponding variable from the dataset is added to the result. For larger blocks, a new latent
     * variable is created and added to the result.
     *
     * @param blocks  a list of lists, where each inner list represents a block of indices
     * @param dataSet the dataset associated with the specified blocks, providing the variables
     * @return a list of Node objects representing the block variables, either existing or newly created
     */
    public static List<Node> makeBlockVariables(List<List<Integer>> blocks, DataSet dataSet) {
        int latentIndex = 1;
        List<Node> meta = new ArrayList<>();
        for (List<Integer> block : blocks) {
            if (block.size() == 1) {
                meta.add(dataSet.getVariable(block.getFirst()));
            } else {
                ContinuousVariable latent = new ContinuousVariable("L" + latentIndex++);
                latent.setNodeType(NodeType.LATENT);
                meta.add(latent);
            }
        }

        return meta;
    }

    /**
     * Canonicalizes a list of blocks by removing null or empty blocks, sorting the contents of each block, and ensuring
     * the resulting blocks are unique. The returned list maintains the order of the first occurrence of each unique
     * block.
     *
     * @param blocks a list of lists, where each inner list represents a block of indices to canonicalize
     * @return a list of canonicalized blocks that are non-empty, sorted internally, and unique in order
     */
    public static List<List<Integer>> canonicalizeBlocks(List<List<Integer>> blocks) {
        LinkedHashSet<List<Integer>> uniq = new LinkedHashSet<>();
        for (List<Integer> b : blocks) {
            if (b == null || b.isEmpty()) continue;
            List<Integer> s = new ArrayList<>(b);
            Collections.sort(s);
            uniq.add(Collections.unmodifiableList(s));
        }
        return new ArrayList<>(uniq);
    }

    /**
     * Validates the provided list of blocks to ensure that all indices within each block are non-negative, within the
     * range of columns in the given dataset, and not null. Throws an IllegalArgumentException if any of these
     * conditions are violated.
     *
     * @param blocks a list of lists, where each inner list represents a block of indices to validate
     * @param data   the dataset providing the number of columns for range validation
     */
    public static void validateBlocks(List<List<Integer>> blocks, DataSet data) {
        int p = data.getNumColumns();
        for (List<Integer> b : blocks) {
            for (Integer v : b) {
                if (v == null || v < 0 || v >= p) {
                    throw new IllegalArgumentException("Block contains out-of-range index: " + v);
                }
            }
        }
    }

    /**
     * Converts a list of block indices and a dataset into a BlockSpec object, ensuring the blocks are canonicalized and
     * generating the appropriate block variables.
     *
     * @param blocks  a list of lists, where each inner list represents a block of indices
     * @param dataSet the dataset associated with the blocks
     * @return a BlockSpec object containing the dataset, canonicalized blocks, and block variables
     */
    public static BlockSpec toSpec(List<List<Integer>> blocks, DataSet dataSet) {
        List<List<Integer>> canon = canonicalizeBlocks(blocks);
        return new BlockSpec(dataSet, canon, makeBlockVariables(canon, dataSet));
    }

    /**
     * Converts a list of blocks, ranks, and a dataset into a BlockSpec object. The blocks are canonicalized to ensure
     * uniformity, and block variables are generated based on the canonicalized blocks and dataset.
     *
     * @param blocks  a list of lists, where each inner list represents a block of indices
     * @param ranks   a list of integers representing the ranks associated with the blocks
     * @param dataSet the dataset associated with the blocks, providing the variables for block creation
     * @return a BlockSpec object containing the dataset, canonicalized blocks, block variables, and ranks
     */
    public static BlockSpec toSpec(List<List<Integer>> blocks, List<Integer> ranks, DataSet dataSet) {
        List<List<Integer>> canon = canonicalizeBlocks(blocks);
        return new BlockSpec(dataSet, canon, makeBlockVariables(canon, dataSet), ranks);
    }

    /**
     * Expand ranks -> per-latent variables named Lk-1..Lk-r.
     *
     * @param spec the BlockSpec object containing the block variables to expand
     * @return the expanded list of Node objects
     */
    public static List<Node> expandLatents(BlockSpec spec) {
        List<Node> expanded = new ArrayList<>();
        for (int i = 0; i < spec.blocks().size(); i++) {
            int r = spec.ranks().get(i);
            String baseName = spec.blockVariables().get(i).getName();
            if (spec.blocks().get(i).size() == 1 && r == 1) {
                // singleton: just pass through observed Node
                expanded.add(spec.blockVariables().get(i));
            } else {
                for (int k = 1; k <= r; k++) {
                    var L = new ContinuousVariable(baseName + "-" + k);
                    L.setNodeType(NodeType.LATENT);
                    expanded.add(L);
                }
            }
        }
        return expanded;
    }

    /**
     * Creates a list of disjoint blocks from the provided list of blocks, prioritizing larger blocks first. Each block
     * is processed to ensure no overlapping indices, and elements within processed blocks are sorted. The resulting
     * list is unmodifiable and contains unique, disjoint, and sorted blocks.
     *
     * @param blocks a list of lists, where each inner list represents a block of indices to be made disjoint
     * @return a list of disjoint blocks, where each block is a sorted and unmodifiable list of indices
     */
    public static List<List<Integer>> makeDisjointBySize(List<List<Integer>> blocks) {
        // Sort by descending size; work on copies so we donât mutate inputs
        List<ArrayList<Integer>> sorted = blocks.stream().sorted((a, b) -> Integer.compare(b.size(), a.size())).map(ArrayList::new).toList();

        BitSet used = new BitSet();
        List<List<Integer>> out = new ArrayList<>();

        for (List<Integer> block : sorted) {
            // Drop indices already used by earlier (bigger) blocks
            List<Integer> pruned = new ArrayList<>(block.size());
            for (Integer v : block) {
                if (v != null && !used.get(v)) {
                    pruned.add(v);
                }
            }
            if (!pruned.isEmpty()) {
                // Mark these indices as used and keep this pruned block
                for (int v : pruned) used.set(v);
                // Optional: sort within-block for determinism
                Collections.sort(pruned);
                out.add(Collections.unmodifiableList(pruned));
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Constructs a BlockSpec object using the provided DataSet and block definitions, ensuring that the blocks are made
     * disjoint by prioritizing larger blocks first. The resulting BlockSpec includes the dataset, the disjoint blocks,
     * and associated block variables.
     *
     * @param ds     the dataset associated with the blocks
     * @param blocks a list of lists, where each inner list represents a block of indices
     * @return a BlockSpec object containing the dataset, disjoint blocks, and block variables
     */
    public static BlockSpec makeDisjointSpec(DataSet ds, List<List<Integer>> blocks) {
        List<List<Integer>> disjoint = makeDisjointBySize(blocks);
        List<Node> blockVars = makeBlockVariables(disjoint, ds); // your existing helper
        return new BlockSpec(ds, disjoint, blockVars); // ranks default to 1s
    }

    /**
     * Applies a single-cluster policy to the provided BlockSpec. Depending on the specified policy, the method modifies
     * the blocks, ranks, and variables in the BlockSpec and returns a new BlockSpec object.
     *
     * @param blockSpec the BlockSpec object containing the current block configuration, ranks, and dataset
     * @param policy    the SingletonClusterPolicy to apply, which determines how unused columns or variables are handled
     *                  (e.g., INCLUDE, EXCLUDE, NOISE_VAR)
     * @param alpha     a double value representing a parameter used in the computation of ranks
     * @return a new BlockSpec object that reflects the changes made according to the specified policy
     */
    public static BlockSpec applySingleClusterPolicy(BlockSpec blockSpec, SingletonClusterPolicy policy, double alpha) {
        final DataSet dataSet = blockSpec.dataSet();
        final List<List<Integer>> blocks = blockSpec.blocks();

        // --- normalize ranks (may be null) ---
        final List<Integer> ranksIn = blockSpec.ranks();
        final List<Integer> ranksNorm = new ArrayList<>(blocks.size());
        if (ranksIn != null && !ranksIn.isEmpty()) {
            for (int i = 0; i < blocks.size(); i++) {
                int r = i < ranksIn.size() ? ranksIn.get(i) : 0;
                ranksNorm.add(Math.max(r, 0));
            }
        } else {
            for (int i = 0; i < blocks.size(); i++) ranksNorm.add(0);
        }

        // --- start output with current blocks/latents/ranks ---
        final List<List<Integer>> outBlocks = new ArrayList<>(blocks.size() + 8);
        for (List<Integer> b : blocks) outBlocks.add(new ArrayList<>(b));

        final List<Node> inLatents = blockSpec.blockVariables();
        final List<Node> outLatents = new ArrayList<>(blocks.size() + 8);
        final Set<String> takenNames = new HashSet<>();
        final Set<String> observedNames = new HashSet<>();
        for (Node v : dataSet.getVariables()) observedNames.add(v.getName());

        if (inLatents != null && !inLatents.isEmpty()) {
            for (Node n : inLatents) {
                outLatents.add(n);
                takenNames.add(n.getName());
            }
        } else {
            // synthesize L1, L2, ... for existing blocks if none present
            for (int i = 0; i < blocks.size(); i++) {
                String nm = ensureUnique("L" + (i + 1), takenNames, observedNames);
                Node latent = new edu.cmu.tetrad.data.ContinuousVariable(nm);
                latent.setNodeType(edu.cmu.tetrad.graph.NodeType.LATENT);
                outLatents.add(latent);
                takenNames.add(nm);
            }
        }

        final List<Integer> outRanks = new ArrayList<>(ranksNorm);

        // --- precompute correlation once ---
        final edu.cmu.tetrad.data.CorrelationMatrix corr = new edu.cmu.tetrad.data.CorrelationMatrix(dataSet);
        final org.ejml.simple.SimpleMatrix S = corr.getMatrix().getSimpleMatrix();
        final int n = dataSet.getNumRows();
        final int p = dataSet.getNumColumns();

        // compute used / unused
        final Set<Integer> used = new HashSet<>();
        for (List<Integer> b : blocks) used.addAll(b);
        final List<Integer> all = new ArrayList<>(p);
        for (int i = 0; i < p; i++) all.add(i);
        final LinkedHashSet<Integer> unused = new LinkedHashSet<>(all);
        unused.removeAll(used);

        switch (policy) {
            case INCLUDE -> {
                List<Integer> reestimatedRanks = new ArrayList<>(outRanks);

                for (int idx : unused) {
                    outBlocks.add(Collections.singletonList(idx));
                    outLatents.add(dataSet.getVariable(idx));
                    reestimatedRanks.add(0);  // singleton: no shared latent, rank must be 0
                }

                return new BlockSpec(dataSet, outBlocks, outLatents, reestimatedRanks);
            }

            case EXCLUDE -> {
                List<List<Integer>> filteredBlocks = new ArrayList<>();
                List<Node> filteredLatents = new ArrayList<>();
                List<Integer> filteredRanks = new ArrayList<>();

                for (int i = 0; i < outBlocks.size(); i++) {
                    if (outBlocks.get(i).size() > 1) {
                        filteredBlocks.add(outBlocks.get(i));
                        filteredLatents.add(outLatents.get(i));
                        filteredRanks.add(outRanks.get(i));  // preserve TSC rank
                    }
                }

                return new BlockSpec(dataSet, filteredBlocks, filteredLatents, filteredRanks);
            }

            default -> throw new IllegalArgumentException("Unknown policy: " + policy);
        }
    }

    // ---------- helpers ----------

    // Safe rank: if others empty â unconditioned fallback; singleton â 1.
    private static int estimateRankSafe(org.ejml.simple.SimpleMatrix S, int nRows, List<Integer> block, int[] others, double alpha) {
        if (block == null || block.isEmpty()) return 0; // empty â 0
        int[] blk = toIndexArray(block);

        if (others != null && others.length > 0) {
            int rank = RankTests.estimateWilksRank(S, blk, others, nRows, alpha);
            return TMath.max(0, rank);
        } else {
            int rank = RankTests.estimateWilksRank(S, blk, new int[0], nRows, alpha);
            return TMath.max(0, rank);
        }
    }

    private static int[] toIndexArray(Collection<Integer> list) {
        int[] a = new int[list.size()];
        int k = 0;
        for (int v : list) a[k++] = v;
        return a;
    }

    private static List<Integer> allMinus(Collection<Integer> minus, List<Integer> all) {
        List<Integer> out = new ArrayList<>(all.size());
        for (int x : all) if (!minus.contains(x)) out.add(x);
        return out;
    }

    private static String sanitize(String s) {
        return s == null ? "X" : s.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    private static String ensureUnique(String base, Set<String> taken, Set<String> observed) {
        String b = (base == null || base.isEmpty()) ? "L" : base;
        if (!taken.contains(b) && !observed.contains(b)) return b;
        int k = 2;
        while (taken.contains(b + "-" + k) || observed.contains(b + "-" + k)) k++;
        return b + "-" + k;
    }

    /**
     * Assigns meaningful names to latent variables in the provided BlockSpec object based on the given true clusters
     * and the specified naming mode. This helps in creating more interpretable and user-friendly block specifications.
     *
     * @param spec         the BlockSpec object containing the initial latent variable definitions
     * @param trueClusters a map where keys represent cluster names and values are lists of variable names associated
     *                     with each cluster
     * @param mode         the NamingMode specifying how the latent variables should be named
     * @return a BlockSpec object with updated latent variable names based on the true clusters and naming mode
     */
    public static BlockSpec giveGoodLatentNames(BlockSpec spec, Map<String, List<String>> trueClusters, NamingMode mode) {
        return LatentNameAssigner.giveGoodLatentNames(spec, trueClusters, mode);
    }

    /**
     * An enumeration representing different naming modes for assigning names to latent variables in the context of
     * block specifications.
     */
    public enum NamingMode {

        /**
         * Represents a naming mode where latent variables are named based on a single learning mechanism, typically
         * resulting in consolidated names.
         */
        LEARNED_SINGLE,

        /**
         * Represents a naming mode focused on scenarios where the names are expanded and adjusted for simulations,
         * often leading to more detailed or descriptive names for latent variables
         */
        SIMULATION_EXPANDED
    }
}
