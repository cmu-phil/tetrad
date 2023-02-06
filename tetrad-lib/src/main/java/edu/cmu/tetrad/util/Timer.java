package edu.cmu.tetrad.util;

import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import static java.lang.Math.log;

/**
 * A time class that reports elapsed time in wall time, user time, and CPU time
 * in milliseconds. User time and CPU time are for the current thread.
 *
 * @author josephramsey
 */
public class Timer {
    public enum Type {Wall, User, CPU}
    public static Type type = Type.CPU;

    private long startWall;
    private long startUser;
    private long startCpu;
    private long stopWall;
    private long stopUser;
    private long stopCpu;
    NumberFormat nf = new DecimalFormat("0.000");

    /**
     * Constructor. Starts the timer.
     */
    public Timer() {
        start();
    }

    public static long wallTimeMillis() {
        return System.currentTimeMillis();
    }

    public static long userTimeMillis() {
        return ManagementFactory.getThreadMXBean().getCurrentThreadUserTime() / 1000000L;
    }

    public static long cpuTimeMillis() {
        return ManagementFactory.getThreadMXBean().getCurrentThreadUserTime() / 1000000L;
    }

    public static long timeMillis() {
        switch (type) {
            case Wall:
                return wallTimeMillis();
            case User:
                return userTimeMillis();
            case CPU:
                return cpuTimeMillis();
            default:
                throw new IllegalArgumentException("Unexpected type: " + type);
        }
    }



    /**
     * Starts the timer. (Timer time = new Timer() also starts the timer.)
     */
    public void start() {
        startWall = wallTimeMillis();
        startUser = userTimeMillis();
        startCpu = timeMillis();

        stopWall = startWall;
        stopUser = startUser;
        stopCpu = startCpu;
    }

    /**
     * Stops the timer.
     */
    public void stop() {
        stopWall = wallTimeMillis();
        stopUser = userTimeMillis();
        stopCpu = timeMillis();
    }

    /**
     * @return The elapsed wall time im in milliseconds.
     */
    public long getWallTime() {
        return stopWall - startWall;
    }

    /**
     * @return The elapsed user time in milliseconds.
     */
    public long getUserTime() {
        return stopUser - startUser;
    }

    /**
     * @return The elapsed CPU time in seconds, in milliseconds.
     */
    public long getCpuTime() {
        return stopCpu - startCpu;
    }

    /**
     * @return A formatted string for the wall time in seconds, in millisecond precision.
     */
    public String getWallTimeString() {
        return "Wall Time = " + nf.format(getWallTime() / 1000.0) + " s";
    }

    /**
     * @return A formatted string for the user time in seconds, in millisecond precision.
     */
    public String getUserTimeString() {
        return "User Time = " + nf.format(getUserTime() / 1000.0) + " s";
    }

    /**
     * @return A formatted string for the CPU time in seconds, in millisecond precision.
     */
    public String getCpuTimeString() {
        return "CPU Time = " + nf.format(getCpuTime() / 1000.0) + " s";
    }

    private static void compareTimes() {
        int numIter = 10000000;

        Timer timer = new Timer();
        timer.start();

        double sum = 0.0;

        for (int i = 0; i < numIter; i++) {
            sum += log(1000.0);
        }

        System.out.println("Sum = " + sum);

        timer.stop();

        System.out.println("Sum: Wall = " + timer.getWallTime());
        System.out.println("Sum: User = " + timer.getUserTime());
        System.out.println("Sum: CPU = " + timer.getCpuTime());

        System.out.println("Sum: " + timer.getWallTimeString());
        System.out.println("Sum: " + timer.getUserTimeString());
        System.out.println("Sum: " + timer.getCpuTimeString());
    }

    /**
     * A main method to test the timer methods.
     */
    public static void main(String... args) {
        compareTimes();
    }
}