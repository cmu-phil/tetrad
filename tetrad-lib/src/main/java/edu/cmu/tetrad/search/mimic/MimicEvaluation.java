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

package edu.cmu.tetrad.search.mimic;

/**
 * Stores summary statistics from evaluating an estimated MIMIC graph against a true model.
 *
 * @author josephramsey
 */
public final class MimicEvaluation {

    /**
     * Number of true latents.
     */
    private final int trueLatentCount;

    /**
     * Number of estimated latents.
     */
    private final int estimatedLatentCount;

    /**
     * Number of matched latent pairs.
     */
    private final int matchedLatentCount;

    /**
     * Average similarity over matched latent pairs.
     */
    private final double averageLatentSimilarity;

    /**
     * Average input-set Jaccard similarity over matched pairs.
     */
    private final double averageInputSimilarity;

    /**
     * Average output-set Jaccard similarity over matched pairs.
     */
    private final double averageOutputSimilarity;

    /**
     * Constructs a new evaluation summary.
     *
     * @param trueLatentCount number of true latents
     * @param estimatedLatentCount number of estimated latents
     * @param matchedLatentCount number of matched latent pairs
     * @param averageLatentSimilarity average matched latent similarity
     * @param averageInputSimilarity average matched input similarity
     * @param averageOutputSimilarity average matched output similarity
     */
    public MimicEvaluation(int trueLatentCount,
                           int estimatedLatentCount,
                           int matchedLatentCount,
                           double averageLatentSimilarity,
                           double averageInputSimilarity,
                           double averageOutputSimilarity) {
        this.trueLatentCount = trueLatentCount;
        this.estimatedLatentCount = estimatedLatentCount;
        this.matchedLatentCount = matchedLatentCount;
        this.averageLatentSimilarity = averageLatentSimilarity;
        this.averageInputSimilarity = averageInputSimilarity;
        this.averageOutputSimilarity = averageOutputSimilarity;
    }

    /**
     * Retrieves the true latent count value.
     *
     * @return the number of true latents
     */
    public int getTrueLatentCount() {
        return trueLatentCount;
    }

    /**
     * Retrieves the estimated latent count value.
     *
     * @return the number of estimated latents
     */
    public int getEstimatedLatentCount() {
        return estimatedLatentCount;
    }

    /**
     * Retrieves the matched latent count value.
     *
     * @return the number of matched latent pairs
     */
    public int getMatchedLatentCount() {
        return matchedLatentCount;
    }

    /**
     * Retrieves the average latent similarity, representing the average similarity
     * between matched latent pairs.
     *
     * @return the average latent similarity value
     */
    public double getAverageLatentSimilarity() {
        return averageLatentSimilarity;
    }

    /**
     * Retrieves the average input similarity, representing the average similarity
     * between matched inputs.
     *
     * @return the average input similarity value
     */
    public double getAverageInputSimilarity() {
        return averageInputSimilarity;
    }

    /**
     * Retrieves the average output similarity, representing the average similarity
     * between matched outputs.
     *
     * @return the average output similarity value
     */
    public double getAverageOutputSimilarity() {
        return averageOutputSimilarity;
    }

    /**
     * Returns the absolute difference in latent counts.
     *
     * @return the latent count error
     */
    public int getLatentCountError() {
        return Math.abs(this.trueLatentCount - this.estimatedLatentCount);
    }

    /**
     * Returns a string representation of the MimicEvaluation object.
     * The string contains a summary of the object's fields and their values,
     * including true latent count, estimated latent count, matched latent count,
     * and the average similarities for latent, input, and output.
     *
     * @return a string representation of the MimicEvaluation object
     */
    @Override
    public String toString() {
        return "MimicEvaluation{" +
                "trueLatentCount=" + trueLatentCount +
                ", estimatedLatentCount=" + estimatedLatentCount +
                ", matchedLatentCount=" + matchedLatentCount +
                ", averageLatentSimilarity=" + averageLatentSimilarity +
                ", averageInputSimilarity=" + averageInputSimilarity +
                ", averageOutputSimilarity=" + averageOutputSimilarity +
                '}';
    }
}