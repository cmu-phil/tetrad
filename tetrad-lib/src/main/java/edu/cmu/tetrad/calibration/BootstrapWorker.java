package edu.cmu.tetrad.calibration;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.MillisecondTimes;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.List;


//MP: Each BootstrapWorker object will run the RFCI method on one Bootstrap and append the results to the results list
class BootstrapWorker extends Thread {
    //MP: Class variables declaration
    private static final int nprocessor = FastMath.max(Runtime.getRuntime().availableProcessors() - 1, 1); // Retain one processor for the current process

    public static double alpha;
    public static int BootstrapNum = -1; // total number of bootstrap instances that must be executed
    public static DataForCalibrationRfci DFC;
    public static Graph truePag;
    public static List<Graph> BNfromBootstrap;
    public static List<BootstrapWorker> waitingList = new ArrayList<BootstrapWorker>(); //MP: List of processes that are waiting to run
    public static List<BootstrapWorker> runningList = new ArrayList<BootstrapWorker>(); //MP: List of processes that are running

    //MP: Instance variables' declaration'
    public DataSet bootstrapSample;
    public double start_time, end_time;


    public BootstrapWorker(DataSet bootstrapSample, List<Graph> BNfromBootstrap) {
        this.start_time = -1;
        this.end_time = -1;
        this.bootstrapSample = bootstrapSample;
        BootstrapWorker.BNfromBootstrap = BNfromBootstrap;
    }

    public static void addToWaitingList(BootstrapWorker worker) {
        waitingList.add(worker);
    }

    private static boolean check_resource_availability() {
        if (runningList.size() < nprocessor) {
            return true;
        } else {
            for (int i = 0; i < runningList.size(); i++) {
                if (runningList.get(i).end_time != -1) {
                    runningList.remove(i);
                    return true;
                }
            }
        }
        return false;
    }

    public static void executeThreads_and_wait() throws InterruptedException {
        for (BootstrapWorker t : waitingList) {
            while (!BootstrapWorker.check_resource_availability()) {
                //				  Pausing for some sec
                // sleeping time in ms
                long sleep_time = 3000;
                Thread.sleep(sleep_time);
            }
//MP: It can be used later for running more bootsraps and pick the ones that are finished
//			  if (BNfromBootstrap.size()>=BootstrapNum){
//				  break;
//			  }
            runningList.add(t);
            t.start();
        }
        for (BootstrapWorker t : runningList) {
            t.join();
        }
    }

    @Override
    public void run() {
        this.start_time = MillisecondTimes.timeMillis();
        Graph outGraph = DFC.learnBNRFCI(bootstrapSample, DFC.depth, truePag);
        addToList(outGraph);
        this.end_time = MillisecondTimes.timeMillis();
    }

    public synchronized void addToList(Graph outGraph) {
        BootstrapWorker.BNfromBootstrap.add(outGraph);
    }
}


