package edu.cmu.tetrad.simulation;

import java.util.Arrays;
import java.util.List;

/**
 * Created by Erich on 4/29/2016.
 */
public class HsimSchedule {
    public static void main(String[] args) {
        //parameter: set of positive integers, which are resimSize values.
        List<Integer> schedule = Arrays.asList(5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5);

        boolean verbose=false;//set this to true if you want HsimautoRun to report information
        double[] evalTotal;
        evalTotal = new double[5];
        evalTotal[0]=0;
        evalTotal[1]=0;
        evalTotal[2]=0;
        evalTotal[3]=0;
        evalTotal[4]=0;

        double[] evalIncrement;
        //evalIncrement = new double[5];

        Integer count = 1;
        for (Integer i : schedule) {
            HsimAutoRun study = new HsimAutoRun("GeMSlim.csv",',');
            study.setWrite(true);
            study.setFilenameOut("autoout"+i+"-"+count+".txt");
            evalIncrement = study.run(i);
            evalTotal[0]=evalTotal[0]+evalIncrement[0];
            evalTotal[1]=evalTotal[1]+evalIncrement[1];
            evalTotal[2]=evalTotal[2]+evalIncrement[2];
            evalTotal[3]=evalTotal[3]+evalIncrement[3];
            evalTotal[4]=evalTotal[4]+evalIncrement[4];
            count++;
        }
        evalTotal[0]=evalTotal[0] / (double) (count - 1);
        evalTotal[1]=evalTotal[1] / (double) (count - 1);
        evalTotal[2]=evalTotal[2] / (double) (count - 1);
        evalTotal[3]=evalTotal[3] / (double) (count - 1);
        evalTotal[4]=evalTotal[4] / (double) (count - 1);

        System.out.println("Average eval scores: "+evalTotal[0]+" "+evalTotal[1]+" "+evalTotal[2]+" "+evalTotal[3]+" "+ evalTotal[4]);
    }
}
