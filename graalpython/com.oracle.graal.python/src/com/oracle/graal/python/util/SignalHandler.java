/**
 * Copyright (c) 2022 naijun0403
 * <p>
 * MIT License
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.oracle.graal.python.util;

/**
 * This is the signal handler interface expected in <code>Signal.handle</code>.
 *
 * @author   Sheng Liang
 * @author   Bill Shannon
 * @see      sun.misc.Signal
 * @since    1.2
 */

public interface SignalHandler {

    /**
     * The default signal handler
     */
    public static final SignalHandler SIG_DFL = new NativeSignalHandler(0);
    /**
     * Ignore the signal
     */
    public static final SignalHandler SIG_IGN = new NativeSignalHandler(1);

    /**
     * Handle the given signal
     *
     * @param sig a signal object
     */
    public void handle(Signal sig);
}