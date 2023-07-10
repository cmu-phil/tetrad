package edu.cmu.tetrad.util;

import java.lang.management.ManagementFactory;

/**
 * Reports elapsed time in wall time, user time, and CPU time in milliseconds. User time and CPU time are for the
 * current thread. The user needs to set which type of time is reported, and it is reported this way throughout the
 * application wherever timeMillis() is called, though for particular cases separate methods are provided to get
 * particular times.
 *
 * @author josephramsey
 */
public class MillisecondTimes {
    public static Type type = Type.CPU;

    public static long wallTimeMillis() {
        return MillisecondTimes.timeMillis();
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

    public enum Type {Wall, User, CPU}
}