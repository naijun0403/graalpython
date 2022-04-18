/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.nodes.ErrorMessages.MUST_BE_NON_NEGATIVE;
import static com.oracle.graal.python.nodes.ErrorMessages.TIMESTAMP_OUT_OF_RANGE;
import static com.oracle.graal.python.nodes.ErrorMessages.UNKNOWN_CLOCK;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.text.DateFormatSymbols;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.TimeModuleBuiltinsClinicProviders.GetClockInfoNodeClinicProviderGen;
import com.oracle.graal.python.builtins.modules.TimeModuleBuiltinsClinicProviders.StrfTimeNodeClinicProviderGen;
import com.oracle.graal.python.builtins.modules.TimeModuleBuiltinsClinicProviders.StrptimeNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.namespace.PSimpleNamespace;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.StructSequence;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.statement.ImportNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaDoubleNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(defineModule = "time")
public final class TimeModuleBuiltins extends PythonBuiltins {
    private static final int DELAY_NANOS = 10;
    private static final String CTIME_FORMAT = "%s %s %2d %02d:%02d:%02d %d";
    private static final ZoneId GMT = ZoneId.of("GMT");

    private static final HiddenKey CURRENT_ZONE_ID = new HiddenKey("currentZoneID");
    private static final HiddenKey TIME_SLEPT = new HiddenKey("timeSlept");

    private static final StructSequence.BuiltinTypeDescriptor STRUCT_TIME_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PStructTime,
                    // @formatter:off The formatter joins these lines making it less readable
            "The time value as returned by gmtime(), localtime(), and strptime(), and\n" +
                    " accepted by asctime(), mktime() and strftime().  May be considered as a\n" +
                    " sequence of 9 integers.\n\n" +
                    " Note that several fields' values are not the same as those defined by\n" +
                    " the C language standard for struct tm.  For example, the value of the\n" +
                    " field tm_year is the actual year, not year - 1900.  See individual\n" +
                    " fields' descriptions for details.",
            // @formatter:on
                    9,
                    new String[]{
                                    "tm_year", "tm_mon", "tm_mday", "tm_hour", "tm_min", "tm_sec",
                                    "tm_wday", "tm_yday", "tm_isdst", "tm_zone", "tm_gmtoff"
                    },
                    new String[]{
                                    "year, for example, 1993",
                                    "month of year, range [1, 12]",
                                    "day of month, range [1, 31]",
                                    "hours, range [0, 23]",
                                    "minutes, range [0, 59]",
                                    // compatibility note: CPython has an extra ')'
                                    "seconds, range [0, 61]",
                                    "day of week, range [0, 6], Monday is 0",
                                    "day of year, range [1, 366]",
                                    "1 if summer time is in effect, 0 if not, and -1 if unknown",
                                    "abbreviation of timezone name",
                                    "offset from UTC in seconds"
                    });

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TimeModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        // Should we read TZ env variable?
        ZoneId defaultZoneId = core.getContext().getEnv().getTimeZone();
        core.lookupBuiltinModule("time").setAttribute(CURRENT_ZONE_ID, defaultZoneId);

        TimeZone defaultTimeZone = TimeZone.getTimeZone(defaultZoneId);
        String noDaylightSavingZone = defaultTimeZone.getDisplayName(false, TimeZone.SHORT);
        String daylightSavingZone = defaultTimeZone.getDisplayName(true, TimeZone.SHORT);

        boolean hasDaylightSaving = !noDaylightSavingZone.equals(daylightSavingZone);
        if (hasDaylightSaving) {
            builtinConstants.put("tzname", core.factory().createTuple(new Object[]{noDaylightSavingZone, daylightSavingZone}));
        } else {
            builtinConstants.put("tzname", core.factory().createTuple(new Object[]{noDaylightSavingZone}));
        }

        builtinConstants.put("daylight", PInt.intValue(hasDaylightSaving));
        int rawOffsetSeconds = defaultTimeZone.getRawOffset() / -1000;
        builtinConstants.put("timezone", rawOffsetSeconds);
        builtinConstants.put("altzone", rawOffsetSeconds - 3600);
        builtinConstants.put("_STRUCT_TM_ITEMS", 11);
        StructSequence.initType(core, STRUCT_TIME_DESC);
    }

    @TruffleBoundary
    public static double timeSeconds() {
        return System.currentTimeMillis() / 1000.0;
    }

    private static final int TM_YEAR = 0; /* year */
    private static final int TM_MON = 1; /* month */
    private static final int TM_MDAY = 2; /* day of the month */
    private static final int TM_HOUR = 3; /* hours */
    private static final int TM_MIN = 4; /* minutes */
    private static final int TM_SEC = 5; /* seconds */
    private static final int TM_WDAY = 6; /* day of the week */
    private static final int TM_YDAY = 7; /* day in the year */
    private static final int TM_ISDST = 8; /* daylight saving time */

    @TruffleBoundary
    private static Object[] getTimeStruct(ZoneId zone, long seconds) {
        Object[] timeStruct = new Object[11];
        Instant instant = Instant.ofEpochSecond(seconds);
        ZonedDateTime zonedDateTime = LocalDateTime.ofInstant(instant, zone).atZone(zone);
        timeStruct[TM_YEAR] = zonedDateTime.getYear();
        timeStruct[TM_MON] = zonedDateTime.getMonth().ordinal() + 1; /* Want January == 1 */
        timeStruct[TM_MDAY] = zonedDateTime.getDayOfMonth();
        timeStruct[TM_HOUR] = zonedDateTime.getHour();
        timeStruct[TM_MIN] = zonedDateTime.getMinute();
        timeStruct[TM_SEC] = zonedDateTime.getSecond();
        timeStruct[TM_WDAY] = zonedDateTime.getDayOfWeek().getValue() - 1; /* Want Monday == 0 */
        timeStruct[TM_YDAY] = zonedDateTime.getDayOfYear(); /* Want January, 1 == 1 */
        boolean isDaylightSavings = zonedDateTime.getZone().getRules().isDaylightSavings(instant);
        timeStruct[TM_ISDST] = (isDaylightSavings) ? 1 : 0;
        timeStruct[9] = TimeZone.getTimeZone(zone.getId()).getDisplayName(isDaylightSavings, TimeZone.SHORT);
        timeStruct[10] = zonedDateTime.getOffset().getTotalSeconds();

        return timeStruct;
    }

    @TruffleBoundary
    private static int[] getIntLocalTimeStruct(ZoneId zone, long seconds) {
        int[] timeStruct = new int[9];
        Instant instant = Instant.ofEpochSecond(seconds);
        ZonedDateTime zonedDateTime = LocalDateTime.ofInstant(instant, zone).atZone(zone);
        timeStruct[TM_YEAR] = zonedDateTime.getYear();
        timeStruct[TM_MON] = zonedDateTime.getMonth().ordinal() + 1; /* Want January == 1 */
        timeStruct[TM_MDAY] = zonedDateTime.getDayOfMonth();
        timeStruct[TM_HOUR] = zonedDateTime.getHour();
        timeStruct[TM_MIN] = zonedDateTime.getMinute();
        timeStruct[TM_SEC] = zonedDateTime.getSecond();
        timeStruct[TM_WDAY] = zonedDateTime.getDayOfWeek().getValue() - 1; /* Want Monday == 0 */
        timeStruct[TM_YDAY] = zonedDateTime.getDayOfYear(); /* Want January, 1 == 1 */
        timeStruct[TM_ISDST] = (zonedDateTime.getZone().getRules().isDaylightSavings(instant)) ? 1 : 0;

        return timeStruct;
    }

    protected abstract static class ToLongTime extends PNodeWithContext {

        private static final long MIN_TIME = Instant.MIN.getEpochSecond();
        private static final long MAX_TIME = Instant.MAX.getEpochSecond();

        @Child PRaiseNode raiseNode = PRaiseNode.create();

        public abstract long execute(VirtualFrame frame, Object secs);

        @Specialization
        long doNone(@SuppressWarnings("unused") PNone none) {
            return (long) timeSeconds();
        }

        @Specialization
        long doLong(long t,
                        @Shared("e") @Cached ConditionProfile err) {
            check(t, err);
            return t;
        }

        @Specialization
        long doDouble(double t,
                        @Shared("e") @Cached ConditionProfile err) {
            check(t, err);
            return (long) t;
        }

        @Specialization(guards = "!isPNone(obj)")
        long doObject(VirtualFrame frame, Object obj,
                        @Cached CastToJavaDoubleNode castToDouble,
                        @Cached PyLongAsLongNode asLongNode,
                        @Shared("e") @Cached ConditionProfile err) {
            long t;
            try {
                t = (long) castToDouble.execute(obj);
            } catch (CannotCastException e) {
                t = asLongNode.execute(frame, obj);
            }
            check(t, err);
            return t;
        }

        protected static boolean isValidTime(double t) {
            return t >= MIN_TIME && t <= MAX_TIME;
        }

        private void check(double time, ConditionProfile err) {
            if (err.profile(!isValidTime(time))) {
                throw raiseNode.raise(OverflowError, TIMESTAMP_OUT_OF_RANGE);
            }
        }
    }

    // time.gmtime([seconds])
    @Builtin(name = "gmtime", maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class PythonGMTimeNode extends PythonBuiltinNode {

        @Specialization
        public PTuple gmtime(VirtualFrame frame, Object seconds,
                        @Cached ToLongTime toLongTime) {
            return factory().createStructSeq(STRUCT_TIME_DESC, getTimeStruct(GMT, toLongTime.execute(frame, seconds)));
        }
    }

    // time.localtime([seconds])
    @Builtin(name = "localtime", maxNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class PythonLocalTimeNode extends PythonBinaryBuiltinNode {

        @Specialization
        public PTuple localtime(VirtualFrame frame, PythonModule module, Object seconds,
                        @Cached ReadAttributeFromDynamicObjectNode readZoneId,
                        @Cached ToLongTime toLongTime) {
            ZoneId zoneId = (ZoneId) readZoneId.execute(module, CURRENT_ZONE_ID);
            return factory().createStructSeq(STRUCT_TIME_DESC, getTimeStruct(zoneId, toLongTime.execute(frame, seconds)));
        }
    }

    // time.time()
    @Builtin(name = "time")
    @GenerateNodeFactory
    public abstract static class PythonTimeNode extends PythonBuiltinNode {

        /**
         * The logic is borrowed from Jython.
         *
         * @return current system millisecond time in second
         */

        @Specialization
        public double time() {
            return timeSeconds();
        }
    }

    // time.time_ns()
    @Builtin(name = "time_ns", doc = "Similar to time() but returns time as an integer number of nanoseconds since the epoch.")
    @GenerateNodeFactory
    public abstract static class PythonTimeNsNode extends PythonBuiltinNode {

        /**
         * The maximum date, which are systems able to handle is 2262 04 11. This corresponds to the
         * 64 bit long.
         */
        @Specialization
        public long time() {
            return timeNanoSeconds();
        }

        @TruffleBoundary
        private static long timeNanoSeconds() {
            Instant now = Instant.now();
            // From java we are not able to obtain the nano seconds resolution. It depends on the
            // jdk
            // JKD 1.8 the resolution is usually miliseconds (for example 1576081173486000000)
            // From JDK 9 including JDK 11 the resolution is usually microseconds (for example
            // 1576082578022393000)
            // To obtain really nanosecond resulution we have to fake the nanoseconds
            return now.getEpochSecond() * 1000_000_000L + now.getNano();
        }
    }

    // time.monotonic()
    @Builtin(name = "monotonic")
    @GenerateNodeFactory
    public abstract static class PythonMonotonicNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public double time() {
            return System.nanoTime() / 1000_000_000D;
        }
    }

    // time.monotonic_ns()
    @Builtin(name = "monotonic_ns", maxNumOfPositionalArgs = 1, doc = "Similar to monotonic(), but return time as nanoseconds.")
    @GenerateNodeFactory
    public abstract static class PythonMonotonicNsNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static long time(@SuppressWarnings("unused") Object dummy) {
            return System.nanoTime();
        }
    }

    @Builtin(name = "perf_counter")
    @GenerateNodeFactory
    public abstract static class PythonPerfCounterNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public double counter() {
            return (System.nanoTime() - PythonContext.get(this).getPerfCounterStart()) / 1000_000_000.0;
        }
    }

    @Builtin(name = "perf_counter_ns")
    @GenerateNodeFactory
    public abstract static class PythonPerfCounterNsNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public long counter() {
            return System.nanoTime() - PythonContext.get(this).getPerfCounterStart();
        }
    }

    @Builtin(name = "process_time", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ProcessTimeNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object getProcesTime(PythonModule self) {
            return (System.nanoTime() - PythonContext.get(this).getPerfCounterStart() - timeSlept(self)) / 1000_000_000.0;
        }
    }

    @Builtin(name = "process_time_ns", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ProcessTimeNsNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object getProcesNsTime(PythonModule self) {
            return (System.nanoTime() - PythonContext.get(this).getPerfCounterStart() - timeSlept(self));
        }
    }

    private static long timeSlept(PythonModule self) {
        try {
            return DynamicObjectLibrary.getUncached().getLongOrDefault(self, TIME_SLEPT, 0L);
        } catch (UnexpectedResultException ex) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @Builtin(name = "thread_time")
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ThreadTimeNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object getProcesTime() {
            try {
                Class<?> debug = Class.forName("android.os.Debug");
                return !ImageInfo.inImageCode() ? (long) debug.getDeclaredMethod("threadCpuTimeNanos").invoke(null) / 1000_000_000.0 : 0;
            } catch (NoClassDefFoundError | ClassNotFoundException | NoSuchMethodException | InvocationTargetException |
                     IllegalAccessException ignored) {
                return 0;
            }
        }
    }

    @Builtin(name = "thread_time_ns")
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ThreadTimeNsNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object getProcesNsTime() {
            try {
                Class<?> debug = Class.forName("android.os.Debug");
                return !ImageInfo.inImageCode() ? debug.getDeclaredMethod("threadCpuTimeNanos").invoke(null) : 0;
            } catch (NoClassDefFoundError | ClassNotFoundException | NoSuchMethodException | InvocationTargetException |
                     IllegalAccessException ignored) {
                return 0;
            }
        }
    }

    @Builtin(name = "sleep", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class SleepNode extends PythonBuiltinNode {
        // see: https://github.com/python/cpython/blob/master/Modules/timemodule.c#L1741

        protected abstract Object execute(VirtualFrame frame, PythonModule self, double seconds);

        @Specialization(guards = "isPositive(seconds)", limit = "1")
        Object sleep(PythonModule self, long seconds,
                        @Cached GilNode gil,
                        @CachedLibrary("self") DynamicObjectLibrary dylib) {
            long t = nanoTime();
            long deadline = (long) timeSeconds() + seconds;
            gil.release(true);
            try {
                doSleep(seconds, deadline);
            } finally {
                gil.acquire();
                dylib.put(self, TIME_SLEPT, nanoTime() - t + timeSlept(self));
            }
            PythonContext.triggerAsyncActions(this);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isPositive(seconds)")
        Object err(PythonModule self, long seconds) {
            throw raise(ValueError, MUST_BE_NON_NEGATIVE, "sleep length");
        }

        @Specialization(guards = "isPositive(seconds)", limit = "1")
        Object sleep(PythonModule self, double seconds,
                        @Cached GilNode gil,
                        @CachedLibrary("self") DynamicObjectLibrary dylib) {
            long t = nanoTime();
            double deadline = timeSeconds() + seconds;
            gil.release(true);
            try {
                doSleep(seconds, deadline);
            } finally {
                gil.acquire();
                dylib.put(self, TIME_SLEPT, nanoTime() - t + timeSlept(self));
            }
            PythonContext.triggerAsyncActions(this);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isPositive(seconds)")
        Object err(PythonModule self, double seconds) {
            throw raise(ValueError, MUST_BE_NON_NEGATIVE, "sleep length");
        }

        @Specialization(guards = "!isInteger(secondsObj)")
        Object sleepObj(VirtualFrame frame, PythonModule self, Object secondsObj,
                        @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached SleepNode recursive) {
            return recursive.execute(frame, self, asDoubleNode.execute(frame, secondsObj));
        }

        protected static boolean isPositive(double t) {
            return t >= 0;
        }

        @TruffleBoundary
        private static void doSleep(long seconds, long deadline) {
            long secs = seconds;
            do {
                try {
                    Thread.sleep(secs * 1000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
                secs = deadline - (long) timeSeconds();
            } while (secs >= 0);
        }

        @TruffleBoundary
        private static void doSleep(double seconds, double deadline) {
            double secs = seconds;
            do {
                double milliseconds = secs * 1000;
                long millis = Math.round(Math.floor(milliseconds));
                int nanos = ((Long) Math.round((milliseconds - millis) * 1000)).intValue();
                nanos = (millis == 0 && nanos == 0) ? DELAY_NANOS : nanos;
                try {
                    Thread.sleep(millis, nanos);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
                secs = deadline - timeSeconds();
            } while (secs >= 0);
        }

        @TruffleBoundary
        public static long nanoTime() {
            return System.nanoTime();
        }

        public static SleepNode create() {
            return TimeModuleBuiltinsFactory.SleepNodeFactory.create(null);
        }
    }

    // time.strftime(format[, t])
    @Builtin(name = "strftime", minNumOfPositionalArgs = 2, declaresExplicitSelf = true, parameterNames = {"$self", "format", "time"})
    @ArgumentClinic(name = "format", conversion = ArgumentClinic.ClinicConversion.String)
    @GenerateNodeFactory
    public abstract static class StrfTimeNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StrfTimeNodeClinicProviderGen.INSTANCE;
        }

        private static String format(String format, int arg1) {
            return PythonUtils.format(format, arg1);
        }

        private static String dateFormat(int[] date) {
            return PythonUtils.format("%02d/%02d/", date[TM_MON], date[TM_MDAY]) + truncYear(date[TM_YEAR]);
        }

        private static String timeFormat(int[] date) {
            return PythonUtils.format("%02d:%02d:%02d", date[TM_HOUR], date[TM_MIN], date[TM_SEC]);
        }

        protected static int[] checkStructtime(VirtualFrame frame, PTuple time,
                        SequenceStorageNodes.GetInternalObjectArrayNode getInternalObjectArrayNode,
                        SequenceStorageNodes.LenNode lenNode,
                        PyNumberAsSizeNode asSizeNode,
                        PRaiseNode raise) {
            Object[] otime = getInternalObjectArrayNode.execute(time.getSequenceStorage());
            if (lenNode.execute(time.getSequenceStorage()) != 9) {
                throw raise.raise(TypeError, ErrorMessages.S_ILLEGAL_TIME_TUPLE_ARG, "asctime()");
            }
            int[] date = new int[9];
            for (int i = 0; i < 9; i++) {
                date[i] = asSizeNode.executeExact(frame, otime[i]);
            }

            // This is specific to java
            if (date[TM_YEAR] < Year.MIN_VALUE || date[TM_YEAR] > Year.MAX_VALUE) {
                throw raise.raise(OverflowError, "year out of range");
            }

            if (date[TM_MON] == 0) {
                date[TM_MON] = 1;
            } else if (date[TM_MON] < 0 || date[TM_MON] > 12) {
                throw raise.raise(ValueError, "month out of range");
            }

            if (date[TM_MDAY] == 0) {
                date[TM_MDAY] = 1;
            } else if (date[TM_MDAY] < 0 || date[TM_MDAY] > 31) {
                throw raise.raise(ValueError, "day of month out of range");
            }

            if (date[TM_HOUR] < 0 || date[TM_HOUR] > 23) {
                throw raise.raise(ValueError, "hour out of range");
            }

            if (date[TM_MIN] < 0 || date[TM_MIN] > 59) {
                throw raise.raise(ValueError, "minute out of range");
            }

            if (date[TM_SEC] < 0 || date[TM_SEC] > 61) {
                throw raise.raise(ValueError, "seconds out of range");
            }

            if (date[TM_WDAY] == -1) {
                date[TM_WDAY] = 6;
            } else if (date[TM_WDAY] < 0) {
                throw raise.raise(ValueError, "day of week out of range");
            } else if (date[TM_WDAY] > 6) {
                date[TM_WDAY] = date[TM_WDAY] % 7;
            }

            if (date[TM_YDAY] == 0) {
                date[TM_YDAY] = 1;
            } else if (date[TM_YDAY] < 0 || date[TM_YDAY] > 366) {
                throw raise.raise(ValueError, "day of year out of range");
            }

            if (date[TM_ISDST] < -1) {
                date[TM_ISDST] = -1;
            } else if (date[TM_ISDST] > 1) {
                date[TM_ISDST] = 1;
            }
            return date;
        }

        protected static DateFormatSymbols datesyms = new DateFormatSymbols();

        @TruffleBoundary
        private static String getDayShortName(int day) {
            assert day < 7;
            String[] names = datesyms.getShortWeekdays();
            // "":0, "Sun", .., "Sat":7
            return names[day == 6 ? 1 : day + 2];
        }

        @TruffleBoundary
        private static String getDayLongName(int day) {
            assert day < 7;
            String[] names = datesyms.getWeekdays();
            // "":0, "Sunday", .., "Saturday":7
            return names[day == 6 ? 1 : day + 2];
        }

        @TruffleBoundary
        private static String getMonthShortName(int month) {
            assert month > 0;
            String[] names = datesyms.getShortMonths();
            // "Jan":0, .., "Dec":11, "":12
            return names[month - 1];
        }

        @TruffleBoundary
        private static String getMonthLongName(int month) {
            assert month > 0;
            String[] names = datesyms.getMonths();
            // "January":0, .., "December":11, "":12
            return names[month - 1];
        }

        @TruffleBoundary
        private static String truncYear(int year) {
            String yearstr = format("%04d", year);
            return yearstr.substring(yearstr.length() - 2);
        }

        private static GregorianCalendar getCalendar(int[] time) {
            Month month = Month.of(time[1]); // GregorianCalendar expect months that starts from 0
            return new GregorianCalendar(time[0], month.ordinal(), time[2], time[3], time[4], time[5]);
        }

        // This taken from JPython + some switches were corrected to provide the
        // same result as CPython
        @TruffleBoundary
        private static String format(String format, int[] date) {
            String s = "";
            int lastc = 0;
            int j;
            String[] syms;
            GregorianCalendar cal = null;
            while (lastc < format.length()) {
                int i = format.indexOf("%", lastc);
                if (i < 0) {
                    // the end of the format string
                    s = s + format.substring(lastc);
                    break;
                }
                if (i == format.length() - 1) {
                    // there's a bare % at the end of the string. Python lets
                    // this go by just sticking a % at the end of the result
                    // string
                    s = s + "%";
                    break;
                }
                s = s + format.substring(lastc, i);
                i++;
                switch (format.charAt(i)) {
                    case 'a':
                        // abbrev weekday
                        j = date[TM_WDAY];
                        s = s + getDayShortName(j);
                        break;
                    case 'A':
                        // full weekday
                        j = date[TM_WDAY];
                        s = s + getDayLongName(j);
                        break;
                    case 'b':
                        // abbrev month
                        j = date[TM_MON];
                        s = s + getMonthShortName(j);
                        break;
                    case 'B':
                        // full month
                        j = date[TM_MON];
                        s = s + getMonthLongName(j);
                        break;
                    case 'c':
                        s = s + CTimeNode.format(date);
                        break;
                    case 'd':
                        // day of month (01-31)
                        s = s + format("%02d", date[TM_MDAY]);
                        break;
                    case 'H':
                        // hour (00-23)
                        s = s + format("%02d", date[TM_HOUR]);
                        break;
                    case 'I':
                        // hour (01-12)
                        j = date[TM_HOUR] % 12;
                        if (j == 0) {
                            j = 12;  // midnight or noon
                        }
                        s = s + format("%02d", j);
                        break;
                    case 'j':
                        // day of year (001-366)
                        s = s + format("%03d", date[TM_YDAY]);
                        break;
                    case 'm':
                        // month (01-12)
                        s = s + format("%02d", date[TM_MON]);
                        break;
                    case 'M':
                        // minute (00-59)
                        s = s + format("%02d", date[TM_MIN]);
                        break;
                    case 'p':
                        // AM/PM
                        j = date[TM_HOUR];
                        syms = datesyms.getAmPmStrings();
                        if (0 <= j && j < 12) {
                            s = s + syms[0];
                        } else if (12 <= j && j < 24) {
                            s = s + syms[1];
                        }
                        break;
                    case 'S':
                        // seconds (00-61)
                        s = s + format("%02d", date[TM_SEC]);
                        break;
                    case 'U':
                        // week of year (sunday is first day) (00-53). all days in
                        // new year preceding first sunday are considered to be in
                        // week 0

                        // TODO this is not correct, CPython counts the week of year
                        // from day of year item [8]
                        if (cal == null) {
                            cal = getCalendar(date);
                        }

                        cal.setFirstDayOfWeek(Calendar.SUNDAY);
                        cal.setMinimalDaysInFirstWeek(7);
                        j = cal.get(Calendar.WEEK_OF_YEAR);
                        if (cal.get(Calendar.MONTH) == Calendar.JANUARY && j >= 52) {
                            j = 0;
                        }
                        s = s + format("%02d", j);
                        break;
                    case 'w':
                        // weekday as decimal (0=Sunday-6)
                        j = (date[TM_WDAY] + 1) % 7;
                        s = s + j;
                        break;
                    case 'W':
                        // week of year (monday is first day) (00-53). all days in
                        // new year preceding first sunday are considered to be in
                        // week 0

                        // TODO this is not correct, CPython counts the week of year
                        // from day of year item [8]

                        if (cal == null) {
                            cal = getCalendar(date);
                        }
                        cal.setFirstDayOfWeek(Calendar.MONDAY);
                        cal.setMinimalDaysInFirstWeek(7);
                        j = cal.get(Calendar.WEEK_OF_YEAR);

                        if (cal.get(Calendar.MONTH) == Calendar.JANUARY && j >= 52) {
                            j = 0;
                        }
                        s = s + format("%02d", j);
                        break;
                    case 'x':
                        // TBD: A note about %x and %X. Python's time.strftime()
                        // by default uses the "C" locale, which is changed by
                        // using the setlocale() function. In Java, the default
                        // locale is set by user.language and user.region
                        // properties and is "en_US" by default, at least around
                        // here! Locale "en_US" differs from locale "C" in the way
                        // it represents dates and times. Eventually we might want
                        // to craft a "C" locale for Java and set Jython to use
                        // this by default, but that's too much work right now.
                        //
                        // For now, we hard code %x and %X to return values
                        // formatted in the "C" locale, i.e. the default way
                        // CPython does it. E.g.:
                        // %x == mm/dd/yy
                        // %X == HH:mm:SS
                        //
                        s = s + dateFormat(date);
                        break;
                    case 'X':
                        // See comment for %x above
                        s = s + timeFormat(date);
                        break;
                    case 'Y':
                        // year w/ century
                        s = s + date[TM_YEAR];
                        break;
                    case 'y':
                        // year w/o century (00-99)
                        s = s + truncYear(date[TM_YEAR]);
                        break;
                    case 'Z':
                        // timezone name
                        if (cal == null) {
                            cal = getCalendar(date);
                        }
                        // If items[8] == 1, we're in daylight savings time.
                        // -1 means the information was not available; treat this as if not in dst.
                        s = s + cal.getTimeZone().getDisplayName(date[TM_ISDST] > 0, 0);
                        break;
                    case '%':
                        // %
                        s = s + "%";
                        break;
                    default:
                        // TBD: should this raise a ValueError?
                        s = s + "%" + format.charAt(i);
                        i++;
                        break;
                }
                lastc = i + 1;
            }
            return s;
        }

        @Specialization
        public String formatTime(PythonModule module, String format, @SuppressWarnings("unused") PNone time,
                        @Cached ReadAttributeFromDynamicObjectNode readZoneId) {
            if (format.indexOf(0) > -1) {
                throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.EMBEDDED_NULL_CHARACTER);
            }
            ZoneId zoneId = (ZoneId) readZoneId.execute(module, CURRENT_ZONE_ID);
            return format(format, getIntLocalTimeStruct(zoneId, (long) timeSeconds()));
        }

        @Specialization
        public String formatTime(VirtualFrame frame, @SuppressWarnings("unused") PythonModule module, String format, PTuple time,
                        @Cached SequenceStorageNodes.GetInternalObjectArrayNode getArray,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            if (format.indexOf(0) > -1) {
                throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.EMBEDDED_NULL_CHARACTER);
            }
            int[] date = checkStructtime(frame, time, getArray, lenNode, asSizeNode, getRaiseNode());
            return format(format, date);
        }

        @Specialization
        @SuppressWarnings("unused")
        public String formatTime(PythonModule module, String format, Object time) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.TUPLE_OR_STRUCT_TIME_ARG_REQUIRED);
        }
    }

    @Builtin(name = "mktime", minNumOfPositionalArgs = 2, declaresExplicitSelf = true, doc = "mktime(tuple) -> floating point number\n\n" +
                    "Convert a time tuple in local time to seconds since the Epoch.\n" +
                    "Note that mktime(gmtime(0)) will not generally return zero for most\n" +
                    "time zones; instead the returned value will either be equal to that\n" +
                    "of the timezone or altzone attributes on the time module.")
    @GenerateNodeFactory
    abstract static class MkTimeNode extends PythonBinaryBuiltinNode {
        private static final int ELEMENT_COUNT = 9;

        @Specialization
        @ExplodeLoop
        double mktime(VirtualFrame frame, PythonModule module, PTuple tuple,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached GetObjectArrayNode getObjectArrayNode,
                        @Cached ReadAttributeFromDynamicObjectNode readZoneId) {
            Object[] items = getObjectArrayNode.execute(tuple);
            if (items.length != ELEMENT_COUNT) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.FUNC_TAKES_EXACTLY_D_ARGS, ELEMENT_COUNT, items.length);
            }
            int[] integers = new int[ELEMENT_COUNT];
            for (int i = 0; i < ELEMENT_COUNT; i++) {
                integers[i] = asSizeNode.executeExact(frame, items[i]);
            }
            ZoneId zoneId = (ZoneId) readZoneId.execute(module, CURRENT_ZONE_ID);
            return op(zoneId, integers);
        }

        @TruffleBoundary
        private static long op(ZoneId timeZone, int[] integers) {
            LocalDateTime localtime = LocalDateTime.of(integers[0], integers[1], integers[2], integers[3], integers[4], integers[5]);
            return localtime.toEpochSecond(timeZone.getRules().getOffset(localtime));
        }
    }

    // time.ctime([secs])
    @Builtin(name = "ctime", maxNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class CTimeNode extends PythonBinaryBuiltinNode {

        @Specialization
        public static String localtime(VirtualFrame frame, PythonModule module, Object seconds,
                        @Cached ReadAttributeFromDynamicObjectNode readZoneId,
                        @Cached ToLongTime toLongTime) {
            ZoneId zoneId = (ZoneId) readZoneId.execute(module, CURRENT_ZONE_ID);
            return format(getIntLocalTimeStruct(zoneId, toLongTime.execute(frame, seconds)));
        }

        protected static String format(int[] tm) {
            return ASCTimeNode.format(CTIME_FORMAT, tm);
        }
    }

    // time.asctime([t])
    @Builtin(name = "asctime", maxNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class ASCTimeNode extends PythonBinaryBuiltinNode {

        static final String[] WDAY_NAME = new String[]{
                        "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"
        };
        static final String[] MON_NAME = new String[]{"",
                        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        };

        @Specialization
        public static String localtime(PythonModule module, @SuppressWarnings("unused") PNone time,
                        @Cached ReadAttributeFromDynamicObjectNode readZoneId) {
            ZoneId zoneId = (ZoneId) readZoneId.execute(module, CURRENT_ZONE_ID);
            return format(getIntLocalTimeStruct(zoneId, (long) timeSeconds()));
        }

        @Specialization
        public String localtime(VirtualFrame frame, @SuppressWarnings("unused") PythonModule module, PTuple time,
                        @Cached SequenceStorageNodes.GetInternalObjectArrayNode getArray,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            return format(StrfTimeNode.checkStructtime(frame, time, getArray, lenNode, asSizeNode, getRaiseNode()));
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object localtime(Object module, Object time) {
            throw raise(TypeError, ErrorMessages.TUPLE_OR_STRUCT_TIME_ARG_REQUIRED);
        }

        protected static String format(int[] tm) {
            return format(CTIME_FORMAT, tm);
        }

        @TruffleBoundary
        protected static String format(String format, int[] tm) {
            assert tm[TM_WDAY] >= 0;
            assert tm[TM_MON] > 0;
            String day = WDAY_NAME[tm[TM_WDAY]];
            String month = MON_NAME[tm[TM_MON]];
            return PythonUtils.format(format, day, month, tm[TM_MDAY], tm[TM_HOUR], tm[TM_MIN], tm[TM_SEC], tm[TM_YEAR]);
        }
    }

    // time.get_clock_info(name)
    @Builtin(name = "get_clock_info", parameterNames = {"name"}, doc = "get_clock_info(name: str) -> dict\n" +
                    "\n" +
                    "Get information of the specified clock.")
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.String)
    @GenerateNodeFactory
    public abstract static class GetClockInfoNode extends PythonUnaryClinicBuiltinNode {
        public static final String TIME_IMPL_MONOTONIC = "monotonic";
        public static final String TIME_IMPL_PERF_COUNTER = "perf_counter";
        public static final String TIME_IMPL_PROCESS_TIME = "process_time";
        public static final String TIME_IMPL_THREAD_TIME = "thread_time";
        public static final String TIME_IMPL_TIME = "time";

        // cpython gives resolution 1e-9 in some cases, but jdks System.nanoTime() does not
        // guarantee that
        public static final double TIME_RESOLUTION = 1e-6;

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return GetClockInfoNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        public Object getClockInfo(String name,
                        @CachedLibrary(limit = "1") DynamicObjectLibrary dyLib) {
            final boolean adjustable;
            final boolean monotonic;

            switch (name) {
                case TIME_IMPL_MONOTONIC:
                case TIME_IMPL_PERF_COUNTER:
                case TIME_IMPL_THREAD_TIME:
                case TIME_IMPL_PROCESS_TIME:
                    adjustable = false;
                    monotonic = true;
                    break;
                case TIME_IMPL_TIME:
                    adjustable = true;
                    monotonic = false;
                    break;
                default:
                    throw raise(PythonBuiltinClassType.ValueError, UNKNOWN_CLOCK);
            }

            final PSimpleNamespace ns = factory().createSimpleNamespace();
            dyLib.put(ns, "adjustable", adjustable);
            dyLib.put(ns, "implementation", name);
            dyLib.put(ns, "monotonic", monotonic);
            dyLib.put(ns, "resolution", TIME_RESOLUTION);
            return ns;
        }
    }

    // time.strptime(string[, format])
    @Builtin(name = "strptime", parameterNames = {"data_string", "format"}, doc = "strftime(format[, tuple]) -> string\n" +
                    "\n" +
                    "Convert a time tuple to a string according to a format specification.\n" +
                    "See the library reference manual for formatting codes. When the time tuple\n" +
                    "is not present, current time as returned by localtime() is used.\n" +
                    "\n")
    @ArgumentClinic(name = "data_string", conversion = ArgumentClinic.ClinicConversion.String)
    @ArgumentClinic(name = "format", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"%a %b %d %H:%M:%S %Y\"", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class StrptimeNode extends PythonBinaryClinicBuiltinNode {
        public static final String MOD_STRPTIME = "_strptime";
        public static final String FUNC_STRPTIME_TIME = "_strptime_time";

        static ImportNode.ImportExpression createImportStrptime() {
            return ImportNode.createAsExpression(MOD_STRPTIME);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StrptimeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        public Object strptime(VirtualFrame frame, String dataString, String format,
                        @Cached("createImportStrptime()") ImportNode.ImportExpression importNode,
                        @Cached PyObjectCallMethodObjArgs callNode) {
            final Object module = importNode.execute(frame);
            return callNode.execute(frame, module, FUNC_STRPTIME_TIME, dataString, format);
        }
    }
}
