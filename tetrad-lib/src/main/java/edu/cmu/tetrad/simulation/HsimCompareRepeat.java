package edu.cmu.tetrad.simulation;

import java.io.PrintWriter;
import java.util.List;

/**
 * Created by Erich on 7/15/2016.
 */
public class HsimCompareRepeat {
    public static void main(String... args) {
        int count = 1;

        int numVars = 20;
        double edgesPerNode = 1.1;
        int numCases = 200;
        double penaltyDiscount = 1.0;

        int resimSize = 3;
        int repeat = 10;

        double gAdjRecallTotal =0, gAdjPrecisionTotal =0, gOrRecallTotal =0, gOrPrecisionTotal =0;
        double fAdjRecallTotal =0, fAdjPrecisionTotal =0, fOrRecallTotal =0, fOrPrecisionTotal =0;
        double hAdjRecallTotal =0, hAdjPrecisionTotal =0, hOrRecallTotal =0, hOrPrecisionTotal =0;
        double fSE_ART = 0, fSE_APT = 0, fSE_ORT = 0, fSE_OPT = 0;
        double hSE_ART = 0, hSE_APT = 0, hSE_ORT = 0, hSE_OPT = 0;

        //ints used to correct the counts, since NaN can sometimes occur and needs to be ignored
        int fOrPrecCountCorrection = 0, gOrPrecCountCorrection = 0;
        int fSE_OPT_CountCorrection = 0, hSE_OPT_CountCorrection = 0;

        for (int i=0; i<count; i++) {
            List<double[]> allErrors = HsimRobustCompare.run(numVars,edgesPerNode,numCases,penaltyDiscount,resimSize,
                    repeat,false);

            gAdjRecallTotal += allErrors.get(0)[1];
            gAdjPrecisionTotal += allErrors.get(0)[2];
            gOrRecallTotal += allErrors.get(0)[3];
            //need this correction in case some output graphs have no arrowheads
            if (Double.isNaN(allErrors.get(0)[4])) {
                gOrPrecCountCorrection++;
            }
            else{
                gOrPrecisionTotal += allErrors.get(0)[4];
            }

            fAdjRecallTotal += allErrors.get(1)[1];
            fAdjPrecisionTotal += allErrors.get(1)[2];
            fOrRecallTotal += allErrors.get(1)[3];
            //need this correction in case some output graphs have no arrowheads
            if (Double.isNaN(allErrors.get(1)[4])) {
                fOrPrecCountCorrection++;
            }
            else{
                fOrPrecisionTotal += allErrors.get(1)[4];
            }

            hAdjRecallTotal += allErrors.get(2)[1];
            hAdjPrecisionTotal += allErrors.get(2)[2];
            hOrRecallTotal += allErrors.get(2)[3];
            hOrPrecisionTotal += allErrors.get(2)[4];

            fSE_ART += (allErrors.get(0)[1] - allErrors.get(1)[1])*(allErrors.get(0)[1] - allErrors.get(1)[1]);
            fSE_APT += (allErrors.get(0)[2] - allErrors.get(1)[2])*(allErrors.get(0)[2] - allErrors.get(1)[2]);
            fSE_ORT += (allErrors.get(0)[3] - allErrors.get(1)[3])*(allErrors.get(0)[3] - allErrors.get(1)[3]);
            if (Double.isNaN(allErrors.get(0)[4]) || Double.isNaN(allErrors.get(1)[4])) {
                fSE_OPT_CountCorrection++;
            }
            else{
                fSE_OPT += (allErrors.get(0)[4] - allErrors.get(1)[4])*(allErrors.get(0)[4] - allErrors.get(1)[4]);
            }

            hSE_ART += (allErrors.get(0)[1] - allErrors.get(2)[1])*(allErrors.get(0)[1] - allErrors.get(2)[1]);
            hSE_APT += (allErrors.get(0)[2] - allErrors.get(2)[2])*(allErrors.get(0)[2] - allErrors.get(2)[2]);
            hSE_ORT += (allErrors.get(0)[3] - allErrors.get(2)[3])*(allErrors.get(0)[3] - allErrors.get(2)[3]);
            if (Double.isNaN(allErrors.get(0)[4])) {
                hSE_OPT_CountCorrection++;
            }
            else{
                hSE_OPT += (allErrors.get(0)[4] - allErrors.get(2)[4])*(allErrors.get(0)[4] - allErrors.get(2)[4]);
            }

        }
        double gAdjRecall = gAdjRecallTotal/count, gAdjPrecision = gAdjPrecisionTotal/count;
        double gOrRecall = gOrRecallTotal/count, gOrPrecision = gOrPrecisionTotal/(count - gOrPrecCountCorrection);
        double fAdjRecall = fAdjRecallTotal/count, fAdjPrecision = fAdjPrecisionTotal/count;
        double fOrRecall = fOrRecallTotal/count, fOrPrecision = fOrPrecisionTotal/(count - fOrPrecCountCorrection);
        double hAdjRecall = hAdjRecallTotal/count, hAdjPrecision = hAdjPrecisionTotal/count;
        double hOrRecall = hOrRecallTotal/count, hOrPrecision = hOrPrecisionTotal/count;

        System.out.println(" ");
        String GE = "G errors: AR="+gAdjRecall+" AP="+gAdjPrecision+" OR="+gOrRecall+" OP="+gOrPrecision;
        String FE = "F errors: AR="+fAdjRecall+" AP="+fAdjPrecision+" OR="+fOrRecall+" OP="+fOrPrecision;
        String HE = "H errors: AR="+hAdjRecall+" AP="+hAdjPrecision+" OR="+hOrRecall+" OP="+hOrPrecision;
        System.out.println(GE);
        System.out.println(FE);
        System.out.println(HE);
        System.out.println(" ");

        double fgDifAR = fAdjRecall-gAdjRecall, fgDifAP = fAdjPrecision-gAdjPrecision;
        double fgDifOR = fOrRecall-gOrRecall, fgDifOP = fOrPrecision-gOrPrecision;

        double hgDifAR = hAdjRecall-gAdjRecall, hgDifAP = hAdjPrecision-gAdjPrecision;
        double hgDifOR = hOrRecall-gOrRecall, hgDifOP = hOrPrecision-gOrPrecision;

        String FD = "FG differences: AR="+fgDifAR+" AP="+fgDifAP+" OR="+fgDifOR+" OP="+fgDifOP;
        String HD = "HG differences: AR="+hgDifAR+" AP="+hgDifAP+" OR="+hgDifOR+" OP="+hgDifOP;
        System.out.println(FD);
        System.out.println(HD);
        System.out.println(" ");

        double hfDifDifAR = Math.abs(fgDifAR)-Math.abs(hgDifAR), hfDifDifAP = Math.abs(fgDifAP)-Math.abs(hgDifAP);
        double hfDifDifOR = Math.abs(fgDifOR)-Math.abs(hgDifOR), hfDifDifOP = Math.abs(fgDifOP)-Math.abs(hgDifOP);

        String AFH = "Absolute F-H: AR="+hfDifDifAR+" AP="+hfDifDifAP+" OR="+hfDifDifOR+" OP="+hfDifDifOP;
        System.out.println(AFH);
        System.out.println(" ");

        double fMSE_AR=fSE_ART/count, fMSE_AP=fSE_APT/count, fMSE_OR=fSE_ORT/count, fMSE_OP=fSE_OPT/(count-fSE_OPT_CountCorrection);
        double hMSE_AR=hSE_ART/count, hMSE_AP=hSE_APT/count, hMSE_OR=hSE_ORT/count, hMSE_OP=hSE_OPT/(count-hSE_OPT_CountCorrection);

        String FMSE = "F-MSE: AR="+fMSE_AR+" AP="+fMSE_AP+" OR="+fMSE_OR+" OP="+fMSE_OP;
        String HMSE = "H-MSE: AR="+hMSE_AR+" AP="+hMSE_AP+" OR="+hMSE_OR+" OP="+hMSE_OP;
        System.out.println(FMSE);
        System.out.println(HMSE);

        String nl = System.getProperty("line.separator");
        String resultsLog = GE+nl+FE+nl+HE+nl+nl+FD+nl+HD+nl+AFH+nl+nl+FMSE+nl+HMSE;
        String paramsLog = "count = "+count+", numVars = "+numVars+", edgesPerNode = "+edgesPerNode+", numCases = "+numCases+", resimSize = "+resimSize+", repeat = "+repeat;

        try {
            PrintWriter writer = new PrintWriter("HsimCR-c"+count+"-v"+numVars+"-s"+numCases+"-rs"+resimSize+"-r"+repeat+".txt", "UTF-8");
            writer.println(paramsLog+nl+nl+resultsLog);
            writer.close();
        }
        catch(Exception IOException){
            IOException.printStackTrace();
        }

    }
}
