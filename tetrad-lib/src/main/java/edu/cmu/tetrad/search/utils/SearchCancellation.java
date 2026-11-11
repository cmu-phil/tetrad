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

package edu.cmu.tetrad.search.utils;

/**
 * Cooperative cancellation for searches that fan work out to the ForkJoin common pool.
 * <p>
 * The GUI stops a search by interrupting the one thread that called the search method (see
 * {@code WatchedProcess}). An interrupt is thread state, so a task running on a pool worker cannot see it through
 * {@code Thread.currentThread().isInterrupted()}: the worker's own flag is clear, every check in the stack below it
 * passes, and the task runs to completion. Where each task is itself a long search, that is a stop the user appears
 * to have been ignored. The submitting thread does help run tasks while it joins, so whether a stop is noticed at
 * all comes down to which thread happened to pick up the work -- the failure is intermittent, which is what makes
 * it easy to misread as a hang.
 * <p>
 * The remedy here keeps one source of truth, the flag of the thread that started the search:
 * <ul>
 *     <li>sequential phases call {@link #checkInterrupted()} at their loop heads;</li>
 *     <li>parallel tasks call {@link #checkOwner(Thread)} with the submitting thread, captured before the fan-out.
 *     Reading another live thread's flag is a plain read and needs no synchronization;</li>
 *     <li>the submitting thread wraps its terminal operation, catches {@link InterruptedInLookahead} and rethrows
 *     {@link #asInterrupted(InterruptedInLookahead)}, so interruption reaches the caller as a real
 *     {@link InterruptedException} and not as a search result.</li>
 * </ul>
 * Two things to avoid in a parallel task, both of which this class exists to replace. Do not call
 * {@code Thread.currentThread().interrupt()} in a task: on a common-pool worker that leaves the flag set on a
 * thread shared with the whole JVM, where it resurfaces as a spurious interrupt in unrelated later work. And do
 * not absorb an {@link InterruptedException} into a return value -- a refusal, a failed check, a bumped counter.
 * The search then continues, and if the absorbed result is memoized the interrupted state is read back later as a
 * genuine verdict about the data.
 *
 * @author josephramsey
 */
public final class SearchCancellation {

    private SearchCancellation() {
    }

    /**
     * Carries an {@link InterruptedException} out of a parallel task, whose lambda cannot declare a checked
     * exception. A dedicated type, rather than a bare RuntimeException, so that unwrapping cannot turn an
     * unrelated runtime failure into a cancellation.
     */
    public static final class InterruptedInLookahead extends RuntimeException {

        private static final long serialVersionUID = 23L;

        /**
         * Constructs a wrapper around the given cause.
         *
         * @param cause the interruption being carried out of the task. The {@code (Throwable)} signature is also
         *              what {@link java.util.concurrent.ForkJoinTask} looks for when it re-wraps in the same class
         *              because the throwing thread is not the joining thread.
         */
        public InterruptedInLookahead(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Throws if the calling thread has been interrupted. For sequential phases: call it at the head of any loop
     * that can run for a long time without entering code that checks for itself.
     *
     * @throws InterruptedException if the calling thread has been interrupted
     */
    public static void checkInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("interrupted");
        }
    }

    /**
     * Throws if the given thread has been interrupted. For parallel tasks: pass the thread that submitted the
     * work, captured before the fan-out, since the task's own thread does not carry the flag. Unchecked, so it can
     * be called from a lambda; the submitting thread converts it back with
     * {@link #asInterrupted(InterruptedInLookahead)}.
     *
     * @param owner the thread that submitted the parallel work
     * @throws InterruptedInLookahead if that thread has been interrupted
     */
    public static void checkOwner(Thread owner) {
        if (owner.isInterrupted()) {
            throw new InterruptedInLookahead(
                    new InterruptedException("interrupted during parallel lookahead"));
        }
    }

    /**
     * The {@link InterruptedException} at the root of a wrapper thrown by a parallel task. The cause chain is
     * walked rather than read at one level because ForkJoinPool may re-wrap in the same class when the throwing
     * thread is not the joining thread; the guard is against a self-referential or circular chain.
     *
     * @param ex the wrapper caught by the submitting thread
     * @return the interruption to rethrow
     */
    public static InterruptedException asInterrupted(InterruptedInLookahead ex) {
        Throwable t = ex;
        int guard = 0;

        while (t != null && guard++ < 100) {
            if (t instanceof InterruptedException ie) return ie;
            if (t.getCause() == t) break;
            t = t.getCause();
        }

        return new InterruptedException("interrupted during parallel lookahead");
    }
}
