package edu.cmu.tetrad.simulation;

import java.io.PrintWriter;
import java.util.List;

/**
 * Created by Erich on 7/15/2016.
 */
public class HsimCompareRepeat {
    public static void main(final String... args) {
        final int count = 1;

        final int numVars = 20;
        final double edgesPerNode = 1.1;
        final int numCases = 200;
        final double penaltyDiscount = 1.0;

        final int resimSize = 3;
        final int repeat = 10;

        double gAdjRecallTotal = 0, gAdjPrecisionTotal = 0, gOrRecallTotal = 0, gOrPrecisionTotal = 0;
        double fAdjRecallTotal = 0, fAdjPrecisionTotal = 0, fOrRecallTotal = 0, fOrPrecisionTotal = 0;
        double hAdjRecallTotal = 0, hAdjPrecisionTotal = 0, hOrRecallTotal = 0, hOrPrecisionTotal = 0;
        double fSE_ART = 0, fSE_APT = 0, fSE_ORT = 0, fSE_OPT = 0;
        double hSE_ART = 0, hSE_APT = 0, hSE_ORT = 0, hSE_OPT = 0;

        //ints used to correct the counts, since NaN can sometimes occur and needs to be ignored
        int fOrPrecCountCorrection = 0, gOrPrecCountCorrection = 0;
        int fSE_OPT_CountCorrection = 0, hSE_OPT_CountCorrection = 0;

        for (int i = 0; i < count; i++) {
            final List<double[]> allErrors = HsimRobustCompare.run(numVars, edgesPerNode, numCases, penaltyDiscount, resimSize,
                    repeat, false);

            gAdjRecallTotal += allErrors.get(0)[1];
            gAdjPrecisionTotal += allErrors.get(0)[2];
            gOrRecallTotal += allErrors.get(0)[3];
            //need this correction in case some output graphs have no arrowheads
            if (Double.isNaN(allErrors.get(0)[4])) {
                gOrPrecCountCorrection++;
            } else {
                gOrPrecisionTotal += allErrors.get(0)[4];
            }

            fAdjRecallTotal += allErrors.get(1)[1];
            fAdjPrecisionTotal += allErrors.get(1)[2];
            fOrRecallTotal += allErrors.get(1)[3];
            //need this correction in case some output graphs have no arrowheads
            if (Double.isNaN(allErrors.get(1)[4])) {
                fOrPrecCountCorrection++;
            } else {
                fOrPrecisionTotal += allErrors.get(1)[4];
            }

            hAdjRecallTotal += allErrors.get(2)[1];
            hAdjPrecisionTotal += allErrors.get(2)[2];
            hOrRecallTotal += allErrors.get(2)[3];
            hOrPrecisionTotal += allErrors.get(2)[4];

            fSE_ART += (allErrors.get(0)[1] - allErrors.get(1)[1]) * (allErrors.get(0)[1] - allErrors.get(1)[1]);
            fSE_APT += (allErrors.get(0)[2] - allErrors.get(1)[2]) * (allErrors.get(0)[2] - allErrors.get(1)[2]);
            fSE_ORT += (allErrors.get(0)[3] - allErrors.get(1)[3]) * (allErrors.get(0)[3] - allErrors.get(1)[3]);
            if (Double.isNaN(allErrors.get(0)[4]) || Double.isNaN(allErrors.get(1)[4])) {
                fSE_OPT_CountCorrection++;
            } else {
                fSE_OPT += (allErrors.get(0)[4] - allErrors.get(1)[4]) * (allErrors.get(0)[4] - allErrors.get(1)[4]);
            }

            hSE_ART += (allErrors.get(0)[1] - allErrors.get(2)[1]) * (allErrors.get(0)[1] - allErrors.get(2)[1]);
            hSE_APT += (allErrors.get(0)[2] - allErrors.get(2)[2]) * (allErrors.get(0)[2] - allErrors.get(2)[2]);
            hSE_ORT += (allErrors.get(0)[3] - allErrors.get(2)[3]) * (allErrors.get(0)[3] - allErrors.get(2)[3]);
            if (Double.isNaN(allErrors.get(0)[4])) {
                hSE_OPT_CountCorrection++;
            } else {
                hSE_OPT += (allErrors.get(0)[4] - allErrors.get(2)[4]) * (allErrors.get(0)[4] - allErrors.get(2)[4]);
            }

        }
        final double gAdjRecall = gAdjRecallTotal / count;
        final double gAdjPrecision = gAdjPrecisionTotal / count;
        final double gOrRecall = gOrRecallTotal / count;
        final double gOrPrecision = gOrPrecisionTotal / (count - gOrPrecCountCorrection);
        final double fAdjRecall = fAdjRecallTotal / count;
        final double fAdjPrecision = fAdjPrecisionTotal / count;
        final double fOrRecall = fOrRecallTotal / count;
        final double fOrPrecision = fOrPrecisionTotal / (count - fOrPrecCountCorrection);
        final double hAdjRecall = hAdjRecallTotal / count;
        final double hAdjPrecision = hAdjPrecisionTotal / count;
        final double hOrRecall = hOrRecallTotal / count;
        final double hOrPrecision = hOrPrecisionTotal / count;

        System.out.println(" ");
        final String GE = "G errors: AR=" + gAdjRecall + " AP=" + gAdjPrecision + " OR=" + gOrRecall + " OP=" + gOrPrecision;
        final String FE = "F errors: AR=" + fAdjRecall + " AP=" + fAdjPrecision + " OR=" + fOrRecall + " OP=" + fOrPrecision;
        final String HE = "H errors: AR=" + hAdjRecall + " AP=" + hAdjPrecision + " OR=" + hOrRecall + " OP=" + hOrPrecision;
        System.out.println(GE);
        System.out.println(FE);
        System.out.println(HE);
        System.out.println(" ");

        final double fgDifAR = fAdjRecall - gAdjRecall;
        final double fgDifAP = fAdjPrecision - gAdjPrecision;
        final double fgDifOR = fOrRecall - gOrRecall;
        final double fgDifOP = fOrPrecision - gOrPrecision;

        final double hgDifAR = hAdjRecall - gAdjRecall;
        final double hgDifAP = hAdjPrecision - gAdjPrecision;
        final double hgDifOR = hOrRecall - gOrRecall;
        final double hgDifOP = hOrPrecision - gOrPrecision;

        final String FD = "FG differences: AR=" + fgDifAR + " AP=" + fgDifAP + " OR=" + fgDifOR + " OP=" + fgDifOP;
        final String HD = "HG differences: AR=" + hgDifAR + " AP=" + hgDifAP + " OR=" + hgDifOR + " OP=" + hgDifOP;
        System.out.println(FD);
        System.out.println(HD);
        System.out.println(" ");

        final double hfDifDifAR = Math.abs(fgDifAR) - Math.abs(hgDifAR);
        final double hfDifDifAP = Math.abs(fgDifAP) - Math.abs(hgDifAP);
        final double hfDifDifOR = Math.abs(fgDifOR) - Math.abs(hgDifOR);
        final double hfDifDifOP = Math.abs(fgDifOP) - Math.abs(hgDifOP);

        final String AFH = "Absolute F-H: AR=" + hfDifDifAR + " AP=" + hfDifDifAP + " OR=" + hfDifDifOR + " OP=" + hfDifDifOP;
        System.out.println(AFH);
        System.out.println(" ");

        final double fMSE_AR = fSE_ART / count;
        final double fMSE_AP = fSE_APT / count;
        final double fMSE_OR = fSE_ORT / count;
        final double fMSE_OP = fSE_OPT / (count - fSE_OPT_CountCorrection);
        final double hMSE_AR = hSE_ART / count;
        final double hMSE_AP = hSE_APT / count;
        final double hMSE_OR = hSE_ORT / count;
        final double hMSE_OP = hSE_OPT / (count - hSE_OPT_CountCorrection);

        final String FMSE = "F-MSE: AR=" + fMSE_AR + " AP=" + fMSE_AP + " OR=" + fMSE_OR + " OP=" + fMSE_OP;
        final String HMSE = "H-MSE: AR=" + hMSE_AR + " AP=" + hMSE_AP + " OR=" + hMSE_OR + " OP=" + hMSE_OP;
        System.out.println(FMSE);
        System.out.println(HMSE);

        final String nl = System.getProperty("line.separator");
        final String resultsLog = GE + nl + FE + nl + HE + nl + nl + FD + nl + HD + nl + AFH + nl + nl + FMSE + nl + HMSE;
        final String paramsLog = "count = " + count + ", numVars = " + numVars + ", edgesPerNode = " + edgesPerNode + ", numCases = " + numCases + ", resimSize = " + resimSize + ", repeat = " + repeat;

        try {
            final PrintWriter writer = new PrintWriter("HsimCR-c" + count + "-v" + numVars + "-s" + numCases + "-rs" + resimSize + "-r" + repeat + ".txt", "UTF-8");
            writer.println(paramsLog + nl + nl + resultsLog);
            writer.close();
        } catch (final Exception IOException) {
            IOException.printStackTrace();
        }

    }
}
