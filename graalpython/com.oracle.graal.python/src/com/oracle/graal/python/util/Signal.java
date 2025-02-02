/*
 * Copyright (c) 1998, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.graal.python.util;

import java.util.Hashtable;

/**
 * This class provides ANSI/ISO C signal support. A Java program can register
 * signal handlers for the current process. There are two restrictions:
 * <ul>
 * <li>
 * Java code cannot register a handler for signals that are already used
 * by the Java VM implementation. The <code>Signal.handle</code>
 * function raises an <code>IllegalArgumentException</code> if such an attempt
 * is made.
 * <li>
 * When <code>Signal.handle</code> is called, the VM internally registers a
 * special C signal handler. There is no way to force the Java signal handler
 * to run synchronously before the C signal handler returns. Instead, when the
 * VM receives a signal, the special C signal handler creates a new thread
 * (at priority <code>Thread.MAX_PRIORITY</code>) to
 * run the registered Java signal handler. The C signal handler immediately
 * returns. Note that because the Java signal handler runs in a newly created
 * thread, it may not actually be executed until some time after the C signal
 * handler returns.
 * </ul>
 * <p>
 * Signal objects are created based on their names. For example:
 * <blockquote><pre>
 * new Signal("INT");
 * </blockquote></pre>
 * constructs a signal object corresponding to <code>SIGINT</code>, which is
 * typically produced when the user presses <code>Ctrl-C</code> at the command line.
 * The <code>Signal</code> constructor throws <code>IllegalArgumentException</code>
 * when it is passed an unknown signal.
 * <p>
 * This is an example of how Java code handles <code>SIGINT</code>:
 * <blockquote><pre>
 * SignalHandler handler = new SignalHandler () {
 *     public void handle(Signal sig) {
 *       ... // handle SIGINT
 *     }
 * };
 * Signal.handle(new Signal("INT"), handler);
 * </blockquote></pre>
 *
 * @author   Sheng Liang
 * @author   Bill Shannon
 * @see      sun.misc.SignalHandler
 * @since    1.2
 */
public final class Signal {
    private static Hashtable<Signal,SignalHandler> handlers = new Hashtable<>(4);
    private static Hashtable<Integer,Signal> signals = new Hashtable<>(4);

    private int number;
    private String name;

    /* Returns the signal number */
    public int getNumber() {
        return number;
    }

    /**
     * Returns the signal name.
     *
     * @return the name of the signal.
     * @see sun.misc.Signal#Signal(String name)
     */
    public String getName() {
        return name;
    }

    /**
     * Compares the equality of two <code>Signal</code> objects.
     *
     * @param other the object to compare with.
     * @return whether two <code>Signal</code> objects are equal.
     */
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || !(other instanceof Signal)) {
            return false;
        }
        Signal other1 = (Signal)other;
        return name.equals(other1.name) && (number == other1.number);
    }

    /**
     * Returns a hashcode for this Signal.
     *
     * @return  a hash code value for this object.
     */
    public int hashCode() {
        return number;
    }

    /**
     * Returns a string representation of this signal. For example, "SIGINT"
     * for an object constructed using <code>new Signal ("INT")</code>.
     *
     * @return a string representation of the signal
     */
    public String toString() {
        return "SIG" + name;
    }

    /**
     * Constructs a signal from its name.
     *
     * @param name the name of the signal.
     * @exception IllegalArgumentException unknown signal
     * @see sun.misc.Signal#getName()
     */
    public Signal(String name) {
        number = findSignal(name);
        this.name = name;
        if (number < 0) {
            throw new IllegalArgumentException("Unknown signal: " + name);
        }
    }

    /**
     * Raises a signal in the current process.
     *
     * @param sig a signal
     */
    public static void raise(Signal sig) throws IllegalArgumentException {
        if (handlers.get(sig) == null) {
            throw new IllegalArgumentException("Unhandled signal: " + sig);
        }
    }

    /* Called by the VM to execute Java signal handlers. */
    private static void dispatch(final int number) {
        final Signal sig = signals.get(number);
        final SignalHandler handler = handlers.get(sig);

        Runnable runnable = new Runnable () {
            public void run() {
                // Don't bother to reset the priority. Signal handler will
                // run at maximum priority inherited from the VM signal
                // dispatch thread.
                // Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
                handler.handle(sig);
            }
        };
        if (handler != null) {
            new Thread(runnable, sig + " handler").start();
        }
    }

    private int findSignal(String name) {
        if (name.equals("ABRT")) {
            return 22;
        } else if (name.equals("FPE")) {
            return 8;
        } else if (name.equals("ILL")) {
            return 4;
        } else if (name.equals("INT")) {
            return 2;
        } else if (name.equals("SEGV")) {
            return 11;
        } else if (name.equals("TERM")) {
            return 15;
        } else {
            return 0;
        }
    }
}
