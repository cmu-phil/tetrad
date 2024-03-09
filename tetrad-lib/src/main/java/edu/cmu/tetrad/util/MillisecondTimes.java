package edu.cmu.tetrad.util;

import java.lang.management.ManagementFactory;

/**
 * Reports elapsed time in wall time, user time, and CPU time in milliseconds. User time and CPU time are for the
 * current thread. The user needs to set which type of time is reported, and it is reported this way throughout the
 * application wherever timeMillis() is called, though for particular cases separate methods are provided to get
 * particular times.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class MillisecondTimes {

    /**
     * Constant <code>type</code>
     */
    public static Type type = Type.CPU;

    /**
     * Prevents instantiation.
     */
    private MillisecondTimes() {
    }

    /**
     * <p>wallTimeMillis.</p>
     *
     * @return a long
     */
    public static long wallTimeMillis() {
        return MillisecondTimes.timeMillis();
    }

    /**
     * <p>userTimeMillis.</p>
     *
     * @return a long
     */
    public static long userTimeMillis() {
        return ManagementFactory.getThreadMXBean().getCurrentThreadUserTime() / 1000000L;
    }

    /**
     * <p>cpuTimeMillis.</p>
     *
     * @return a long
     */
    public static long cpuTimeMillis() {
        return ManagementFactory.getThreadMXBean().getCurrentThreadUserTime() / 1000000L;
    }

    /**
     * <p>timeMillis.</p>
     *
     * @return a long
     */
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
     * An enum for the type of time.
     */
    public enum Type {

        /**
         * Wall time.
         */
        Wall,

        /**
         * User time.
         */
        User,

        /**
         * CPU time.
         */
        CPU
    }
}
