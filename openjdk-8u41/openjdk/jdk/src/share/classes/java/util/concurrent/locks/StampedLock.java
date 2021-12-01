/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.LockSupport;

/**
 * A capability-based lock with three modes for controlling read/write
 * access.  The state of a StampedLock consists of a version and mode.
 * Lock acquisition methods return a stamp that represents and
 * controls access with respect to a lock state; "try" versions of
 * these methods may instead return the special value zero to
 * represent failure to acquire access. Lock release and conversion
 * methods require stamps as arguments, and fail if they do not match
 * the state of the lock. The three modes are:
 *
 * <ul>
 *
 *  <li><b>Writing.</b> Method {@link #writeLock} possibly blocks
 *   waiting for exclusive access, returning a stamp that can be used
 *   in method {@link #unlockWrite} to release the lock. Untimed and
 *   timed versions of {@code tryWriteLock} are also provided. When
 *   the lock is held in write mode, no read locks may be obtained,
 *   and all optimistic read validations will fail.  </li>
 *
 *  <li><b>Reading.</b> Method {@link #readLock} possibly blocks
 *   waiting for non-exclusive access, returning a stamp that can be
 *   used in method {@link #unlockRead} to release the lock. Untimed
 *   and timed versions of {@code tryReadLock} are also provided. </li>
 *
 *  <li><b>Optimistic Reading.</b> Method {@link #tryOptimisticRead}
 *   returns a non-zero stamp only if the lock is not currently held
 *   in write mode. Method {@link #validate} returns true if the lock
 *   has not been acquired in write mode since obtaining a given
 *   stamp.  This mode can be thought of as an extremely weak version
 *   of a read-lock, that can be broken by a writer at any time.  The
 *   use of optimistic mode for short read-only code segments often
 *   reduces contention and improves throughput.  However, its use is
 *   inherently fragile.  Optimistic read sections should only read
 *   fields and hold them in local variables for later use after
 *   validation. Fields read while in optimistic mode may be wildly
 *   inconsistent, so usage applies only when you are familiar enough
 *   with data representations to check consistency and/or repeatedly
 *   invoke method {@code validate()}.  For example, such steps are
 *   typically required when first reading an object or array
 *   reference, and then accessing one of its fields, elements or
 *   methods. </li>
 *
 * </ul>
 *
 * <p>This class also supports methods that conditionally provide
 * conversions across the three modes. For example, method {@link
 * #tryConvertToWriteLock} attempts to "upgrade" a mode, returning
 * a valid write stamp if (1) already in writing mode (2) in reading
 * mode and there are no other readers or (3) in optimistic mode and
 * the lock is available. The forms of these methods are designed to
 * help reduce some of the code bloat that otherwise occurs in
 * retry-based designs.
 *
 * <p>StampedLocks are designed for use as internal utilities in the
 * development of thread-safe components. Their use relies on
 * knowledge of the internal properties of the data, objects, and
 * methods they are protecting.  They are not reentrant, so locked
 * bodies should not call other unknown methods that may try to
 * re-acquire locks (although you may pass a stamp to other methods
 * that can use or convert it).  The use of read lock modes relies on
 * the associated code sections being side-effect-free.  Unvalidated
 * optimistic read sections cannot call methods that are not known to
 * tolerate potential inconsistencies.  Stamps use finite
 * representations, and are not cryptographically secure (i.e., a
 * valid stamp may be guessable). Stamp values may recycle after (no
 * sooner than) one year of continuous operation. A stamp held without
 * use or validation for longer than this period may fail to validate
 * correctly.  StampedLocks are serializable, but always deserialize
 * into initial unlocked state, so they are not useful for remote
 * locking.
 *
 * <p>The scheduling policy of StampedLock does not consistently
 * prefer readers over writers or vice versa.  All "try" methods are
 * best-effort and do not necessarily conform to any scheduling or
 * fairness policy. A zero return from any "try" method for acquiring
 * or converting locks does not carry any information about the state
 * of the lock; a subsequent invocation may succeed.
 *
 * <p>Because it supports coordinated usage across multiple lock
 * modes, this class does not directly implement the {@link Lock} or
 * {@link ReadWriteLock} interfaces. However, a StampedLock may be
 * viewed {@link #asReadLock()}, {@link #asWriteLock()}, or {@link
 * #asReadWriteLock()} in applications requiring only the associated
 * set of functionality.
 *
 * <p><b>Sample Usage.</b> The following illustrates some usage idioms
 * in a class that maintains simple two-dimensional points. The sample
 * code illustrates some try/catch conventions even though they are
 * not strictly needed here because no exceptions can occur in their
 * bodies.<br>
 *
 *  <pre>{@code
 * class Point {
 *   private double x, y;
 *   private final StampedLock sl = new StampedLock();
 *
 *   void move(double deltaX, double deltaY) { // an exclusively locked method
 *     long stamp = sl.writeLock();
 *     try {
 *       x += deltaX;
 *       y += deltaY;
 *     } finally {
 *       sl.unlockWrite(stamp);
 *     }
 *   }
 *
 *   double distanceFromOrigin() { // A read-only method
 *     long stamp = sl.tryOptimisticRead();
 *     double currentX = x, currentY = y;
 *     if (!sl.validate(stamp)) {
 *        stamp = sl.readLock();
 *        try {
 *          currentX = x;
 *          currentY = y;
 *        } finally {
 *           sl.unlockRead(stamp);
 *        }
 *     }
 *     return Math.sqrt(currentX * currentX + currentY * currentY);
 *   }
 *
 *   void moveIfAtOrigin(double newX, double newY) { // upgrade
 *     // Could instead start with optimistic, not read mode
 *     long stamp = sl.readLock();
 *     try {
 *       while (x == 0.0 && y == 0.0) {
 *         long ws = sl.tryConvertToWriteLock(stamp);
 *         if (ws != 0L) {
 *           stamp = ws;
 *           x = newX;
 *           y = newY;
 *           break;
 *         }
 *         else {
 *           sl.unlockRead(stamp);
 *           stamp = sl.writeLock();
 *         }
 *       }
 *     } finally {
 *       sl.unlock(stamp);
 *     }
 *   }
 * }}</pre>
 *
 * @since 1.8
 * @author Doug Lea
 */
public class StampedLock implements java.io.Serializable {
    /*
     * Algorithmic notes:
     *
     * The design employs elements of Sequence locks
     * (as used in linux kernels; see Lameter's
     * http://www.lameter.com/gelato2005.pdf
     * and elsewhere; see
     * Boehm's http://www.hpl.hp.com/techreports/2012/HPL-2012-68.html)
     * and Ordered RW locks (see Shirako et al
     * http://dl.acm.org/citation.cfm?id=2312015)
     *
     * Conceptually, the primary state of the lock includes a sequence
     * number that is odd when write-locked and even otherwise.
     * However, this is offset by a reader count that is non-zero when
     * read-locked.  The read count is ignored when validating
     * "optimistic" seqlock-reader-style stamps.  Because we must use
     * a small finite number of bits (currently 7) for readers, a
     * supplementary reader overflow word is used when the number of
     * readers exceeds the count field. We do this by treating the max
     * reader count value (RBITS) as a spinlock protecting overflow
     * updates.
     *
     * Waiters use a modified form of CLH lock used in
     * AbstractQueuedSynchronizer (see its internal documentation for
     * a fuller account), where each node is tagged (field mode) as
     * either a reader or writer. Sets of waiting readers are grouped
     * (linked) under a common node (field cowait) so act as a single
     * node with respect to most CLH mechanics.  By virtue of the
     * queue structure, wait nodes need not actually carry sequence
     * numbers; we know each is greater than its predecessor.  This
     * simplifies the scheduling policy to a mainly-FIFO scheme that
     * incorporates elements of Phase-Fair locks (see Brandenburg &
     * Anderson, especially http://www.cs.unc.edu/~bbb/diss/).  In
     * particular, we use the phase-fair anti-barging rule: If an
     * incoming reader arrives while read lock is held but there is a
     * queued writer, this incoming reader is queued.  (This rule is
     * responsible for some of the complexity of method acquireRead,
     * but without it, the lock becomes highly unfair.) Method release
     * does not (and sometimes cannot) itself wake up cowaiters. This
     * is done by the primary thread, but helped by any other threads
     * with nothing better to do in methods acquireRead and
     * acquireWrite.
     *
     * These rules apply to threads actually queued. All tryLock forms
     * opportunistically try to acquire locks regardless of preference
     * rules, and so may "barge" their way in.  Randomized spinning is
     * used in the acquire methods to reduce (increasingly expensive)
     * context switching while also avoiding sustained memory
     * thrashing among many threads.  We limit spins to the head of
     * queue. A thread spin-waits up to SPINS times (where each
     * iteration decreases spin count with 50% probability) before
     * blocking. If, upon wakening it fails to obtain lock, and is
     * still (or becomes) the first waiting thread (which indicates
     * that some other thread barged and obtained lock), it escalates
     * spins (up to MAX_HEAD_SPINS) to reduce the likelihood of
     * continually losing to barging threads.
     *
     * Nearly all of these mechanics are carried out in methods
     * acquireWrite and acquireRead, that, as typical of such code,
     * sprawl out because actions and retries rely on consistent sets
     * of locally cached reads.
     *
     * As noted in Boehm's paper (above), sequence validation (mainly
     * method validate()) requires stricter ordering rules than apply
     * to normal volatile reads (of "state").  To force orderings of
     * reads before a validation and the validation itself in those
     * cases where this is not already forced, we use
     * Unsafe.loadFence.
     *
     * The memory layout keeps lock state and queue pointers together
     * (normally on the same cache line). This usually works well for
     * read-mostly loads. In most other cases, the natural tendency of
     * adaptive-spin CLH locks to reduce memory contention lessens
     * motivation to further spread out contended locations, but might
     * be subject to future improvements.
     */

    private static final long serialVersionUID = -6001602636862214147L;

    /** Number of processors, for spin control
     * 获取当前处理器的总线程数, 用于自旋控制，此处，NCPU=12，处理器为i7-8700，6核12线程
     * */
    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    /** Maximum number of retries before enqueuing on acquisition
     * 在开始排队获取锁前，重试的最大次数。此处，SPINS=64。
     * 1.如果当前处理器为多核处理器，SPINS=2^6=64
     * 2.如果当前处理器为单核处理器，SPINS=0
     * 因为现在市场上的处理器大部分为多核处理器，所以SPINS基本上取值为64
     * */
    private static final int SPINS = (NCPU > 1) ? 1 << 6 : 0;

    /** Maximum number of retries before blocking at head on acquisition
     * 在阻塞在头部获取锁前，重试的最大次数。此处，HEAD_SPINS=1024。
     * 1.如果当前处理器为多核处理器，HEAD_SPINS=2^10=1024
     * 2.如果当前处理器为单核处理器，HEAD_SPINS=0
     * 因为现在市场上的处理器大部分为多核处理器，所以HEAD_SPINS基本上取值为1024
     * */
    private static final int HEAD_SPINS = (NCPU > 1) ? 1 << 10 : 0;

    /** Maximum number of retries before re-blocking
     * 在再次阻塞前，重试的最大次数。此处，MAX_HEAD_SPINS=65536。
     * 1.如果当前处理器为多核处理器，MAX_HEAD_SPINS=2^16=65536
     * 2.如果当前处理器为单核处理器，MAX_HEAD_SPINS=0
     * 因为现在市场上的处理器大部分为多核处理器，所以MAX_HEAD_SPINS基本上取值为65536
     * */
    private static final int MAX_HEAD_SPINS = (NCPU > 1) ? 1 << 16 : 0;

    /** The period for yielding when waiting for overflow spinlock
     * 等待溢出自旋锁的让步时间，OVERFLOW_YIELD_RATE=0b00000111
     * */
    private static final int OVERFLOW_YIELD_RATE = 7; // must be power 2 - 1

    /** The number of bits to use for reader count before overflowing
     * 溢出前，用于读取器计数的位数
     * */
    private static final int LG_READERS = 7;

    // Values for lock state and stamp operations
    // 用于锁状态和戳记操作的值
    /**
     * RUNIT=0b00000001
     */
    private static final long RUNIT = 1L;

    /**
     * WBIT=1 << 7 = 128 = 0b10000000
     */
    private static final long WBIT  = 1L << LG_READERS;

    /**
     * RBITS = 0b10000000 - 1 = 127 = 0b01111111
     */
    private static final long RBITS = WBIT - 1L;

    /**
     * RFULL = 0b01111111 - 1 = 128 = 0b01111110
     */
    private static final long RFULL = RBITS - 1L;

    /**
     * ABITS = 0b01111111 | 0b10000000 = 255 = 0b11111111
     */
    private static final long ABITS = RBITS | WBIT;

    /**
     * 非（~）运算符
     * SBITS = ~0b01111111 = 0b11111111_11111111_11111111_11111111_11111111_11111111_11111111_10000000
     */
    private static final long SBITS = ~RBITS; // note overlap with ABITS

    // Initial value for lock state; avoid failure value zero
    /**
     * 锁状态的初始值；避免故障值为零，ORIGIN=256=0b00000001_00000000
     */
    private static final long ORIGIN = WBIT << 1;

    // Special value from cancelled acquire methods so caller can throw IE
    /**
     * 被取消的获取方法的特殊值，以便调用者抛出IE
     */
    private static final long INTERRUPTED = 1L;

    // Values for node status; order matters
    // 节点状态值;顺序很重要
    private static final int WAITING   = -1;
    private static final int CANCELLED =  1;

    // Modes for nodes (int not boolean to allow arithmetic)
    // 节点模式（int而不是boolean，允许算术运）
    private static final int RMODE = 0;
    private static final int WMODE = 1;

    /** Wait nodes */
    static final class WNode {
        volatile WNode prev;
        volatile WNode next;

        // 读者链接表
        volatile WNode cowait;    // list of linked readers

        // 当可能parked时非空
        volatile Thread thread;   // non-null while possibly parked

        // 节点状态，3种可能的取值：0, WAITING, 或CANCELLED
        volatile int status;      // 0, WAITING, or CANCELLED

        // 节点模式，读模式或写模式：RMODE或WMODE
        final int mode;           // RMODE or WMODE

        /**
         * 节点构造方法
         * @param m 节点模式
         * @param p 节点的前一个节点
         */
        WNode(int m, WNode p) { mode = m; prev = p; }
    }

    /** Head of CLH queue
     * 队列的头
     * */
    private transient volatile WNode whead;
    /** Tail (last) of CLH queue
     * 队列的尾
     * */
    private transient volatile WNode wtail;

    // views
    transient ReadLockView readLockView;
    transient WriteLockView writeLockView;
    transient ReadWriteLockView readWriteLockView;

    /** Lock sequence/state
     * 锁顺序或状态
     * */
    private transient volatile long state;
    /** extra reader count when state read count saturated
     * 当读计数达到饱和状态时，额外的读计数
     * */
    private transient int readerOverflow;

    /**
     * Creates a new lock, initially in unlocked state.
     * 创建一个新锁，最初处于解锁状态。
     */
    public StampedLock() {
        state = ORIGIN;
    }

    /**
     * Exclusively acquires the lock, blocking if necessary
     * until available.
     * 排他地获取锁，必要时阻塞，直到可用
     * @return a stamp that can be used to unlock or convert mode
     * 返回一个能被用于解锁或者模式转换的戳记
     */
    public long writeLock() {
        long s, next;  // bypass acquireWrite in fully unlocked case only

        /*
        首次获取写锁时，s=state=0b00000001_00000000。ABITS=0b11111111，目的是取锁状态的第一组八位值。
        如果第一组八位值为0说明锁没有锁定，可以直接使用CAS使锁状态值第一组八位值最高1位加1，如果成功返回此时的锁状态作为此次戳记。因此推测，第一组八位值的最高1位代表锁状态是否已经加写锁。
        如果第一组八位值不为0，或者第一组八位值最高1位加1失败，则需要进入自旋锁获取流程。
        这种方式进入自旋流程，interruptible为false，且deadline=0
         */

        return ((((s = state) & ABITS) == 0L &&
                 U.compareAndSwapLong(this, STATE, s, next = s + WBIT)) ?
                next : acquireWrite(false, 0L));
    }

    /**
     * Exclusively acquires the lock if it is immediately available.
     *
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     */
    public long tryWriteLock() {
        long s, next;
        return ((((s = state) & ABITS) == 0L &&
                 U.compareAndSwapLong(this, STATE, s, next = s + WBIT)) ?
                next : 0L);
    }

    /**
     * Exclusively acquires the lock if it is available within the
     * given time and the current thread has not been interrupted.
     * Behavior under timeout and interruption matches that specified
     * for method {@link Lock#tryLock(long,TimeUnit)}.
     *
     * @param time the maximum time to wait for the lock
     * @param unit the time unit of the {@code time} argument
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    public long tryWriteLock(long time, TimeUnit unit)
        throws InterruptedException {
        long nanos = unit.toNanos(time);
        if (!Thread.interrupted()) {
            long next, deadline;
            if ((next = tryWriteLock()) != 0L)
                return next;
            if (nanos <= 0L)
                return 0L;
            if ((deadline = System.nanoTime() + nanos) == 0L)
                deadline = 1L;
            if ((next = acquireWrite(true, deadline)) != INTERRUPTED)
                return next;
        }
        throw new InterruptedException();
    }

    /**
     * Exclusively acquires the lock, blocking if necessary
     * until available or the current thread is interrupted.
     * Behavior under interruption matches that specified
     * for method {@link Lock#lockInterruptibly()}.
     *
     * @return a stamp that can be used to unlock or convert mode
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    public long writeLockInterruptibly() throws InterruptedException {
        long next;
        if (!Thread.interrupted() &&
            (next = acquireWrite(true, 0L)) != INTERRUPTED)
            return next;
        throw new InterruptedException();
    }

    /**
     * Non-exclusively acquires the lock, blocking if necessary
     * until available.
     * 非独占地获取锁，如果需要会阻塞，直到可用
     * @return a stamp that can be used to unlock or convert mode
     */
    public long readLock() {
        long s = state, next;  // bypass acquireRead on common uncontended case

        // 如果锁链表为空，且，读锁数未超过最大值（这意味着，这个时候写锁也是释放状态，写锁是否占有，由第一组八位最高1位表示），则直接加读锁，使用CAS使锁状态+1，如果成功返回戳记为此时的锁状态。
        // 如果失败，进入读锁获取流程
        return ((whead == wtail && (s & ABITS) < RFULL &&
                 U.compareAndSwapLong(this, STATE, s, next = s + RUNIT)) ?
                next : acquireRead(false, 0L));
    }

    /**
     * Non-exclusively acquires the lock if it is immediately available.
     *
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     */
    public long tryReadLock() {
        for (;;) {
            long s, m, next;
            if ((m = (s = state) & ABITS) == WBIT)
                return 0L;
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))
                    return next;
            }
            else if ((next = tryIncReaderOverflow(s)) != 0L)
                return next;
        }
    }

    /**
     * Non-exclusively acquires the lock if it is available within the
     * given time and the current thread has not been interrupted.
     * Behavior under timeout and interruption matches that specified
     * for method {@link Lock#tryLock(long,TimeUnit)}.
     *
     * @param time the maximum time to wait for the lock
     * @param unit the time unit of the {@code time} argument
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    public long tryReadLock(long time, TimeUnit unit)
        throws InterruptedException {
        long s, m, next, deadline;
        long nanos = unit.toNanos(time);
        if (!Thread.interrupted()) {
            if ((m = (s = state) & ABITS) != WBIT) {
                if (m < RFULL) {
                    if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))
                        return next;
                }
                else if ((next = tryIncReaderOverflow(s)) != 0L)
                    return next;
            }
            if (nanos <= 0L)
                return 0L;
            if ((deadline = System.nanoTime() + nanos) == 0L)
                deadline = 1L;
            if ((next = acquireRead(true, deadline)) != INTERRUPTED)
                return next;
        }
        throw new InterruptedException();
    }

    /**
     * Non-exclusively acquires the lock, blocking if necessary
     * until available or the current thread is interrupted.
     * Behavior under interruption matches that specified
     * for method {@link Lock#lockInterruptibly()}.
     *
     * @return a stamp that can be used to unlock or convert mode
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    public long readLockInterruptibly() throws InterruptedException {
        long next;
        if (!Thread.interrupted() &&
            (next = acquireRead(true, 0L)) != INTERRUPTED)
            return next;
        throw new InterruptedException();
    }

    /**
     * Returns a stamp that can later be validated, or zero
     * if exclusively locked.
     * 如果已经获取写锁未释放，返回0。
     * 如果未获取写锁，或者写锁已经释放，返回的是state高位，以及前8位的最高一位，代表是否加写锁。
     * @return a stamp, or zero if exclusively locked
     */
    public long tryOptimisticRead() {
        long s;

        // 如果已经获取写锁未释放，返回0
        // 如果未获取写锁，或者写锁已经释放，返回的是state高位，以及前8位的最高一位，代表是否加写锁
        return (((s = state) & WBIT) == 0L) ? (s & SBITS) : 0L;
    }

    /**
     * Returns true if the lock has not been exclusively acquired
     * since issuance of the given stamp. Always returns false if the
     * stamp is zero. Always returns true if the stamp represents a
     * currently held lock. Invoking this method with a value not
     * obtained from {@link #tryOptimisticRead} or a locking method
     * for this lock has no defined effect or result.
     *
     * @param stamp a stamp
     * @return {@code true} if the lock has not been exclusively acquired
     * since issuance of the given stamp; else false
     */
    public boolean validate(long stamp) {
        U.loadFence();

        // 此验证分几种情况
        // 1.如果别的线程已经获取写锁且未释放，tryOptimisticRead获取的一定是0，此验证必然失败
        // 2.否则，验证stamp高位（state高位，以及前8位的最高一位）是否与state的高位相等
        // 3.在读数过程中，如果有人加写锁或者释放写锁，必然会影响高位（state高位，以及前8位的最高一位），即验证必然失败
        return (stamp & SBITS) == (state & SBITS);
    }

    /**
     * If the lock state matches the given stamp, releases the
     * exclusive lock.
     *
     * 如果锁状态匹配给定的戳记，释放这个排他锁
     *
     * @param stamp a stamp returned by a write-lock operation
     * @throws IllegalMonitorStateException if the stamp does
     * not match the current state of this lock
     */
    public void unlockWrite(long stamp) {
        WNode h;

        // 如果当前状态不等于传入的戳记，或者传入的戳记的第一组8位的最高一位等于0，即不是加锁状态
        // 则抛出异常
        if (state != stamp || (stamp & WBIT) == 0L)
            throw new IllegalMonitorStateException();

        // 设置锁状态为戳记的第一组8位的最高一位加1
        state = (stamp += WBIT) == 0L ? ORIGIN : stamp;

        // 如果head不等于空且头的状态不等于0
        if ((h = whead) != null && h.status != 0)
            release(h);
    }

    /**
     * If the lock state matches the given stamp, releases the
     * non-exclusive lock.
     *
     * @param stamp a stamp returned by a read-lock operation
     * @throws IllegalMonitorStateException if the stamp does
     * not match the current state of this lock
     */
    public void unlockRead(long stamp) {
        long s, m; WNode h;
        for (;;) {
            if (((s = state) & SBITS) != (stamp & SBITS) ||
                (stamp & ABITS) == 0L || (m = s & ABITS) == 0L || m == WBIT)
                throw new IllegalMonitorStateException();
            if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    break;
                }
            }
            else if (tryDecReaderOverflow(s) != 0L)
                break;
        }
    }

    /**
     * If the lock state matches the given stamp, releases the
     * corresponding mode of the lock.
     *
     * @param stamp a stamp returned by a lock operation
     * @throws IllegalMonitorStateException if the stamp does
     * not match the current state of this lock
     */
    public void unlock(long stamp) {
        long a = stamp & ABITS, m, s; WNode h;
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((m = s & ABITS) == 0L)
                break;
            else if (m == WBIT) {
                if (a != m)
                    break;
                state = (s += WBIT) == 0L ? ORIGIN : s;
                if ((h = whead) != null && h.status != 0)
                    release(h);
                return;
            }
            else if (a == 0L || a >= WBIT)
                break;
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    return;
                }
            }
            else if (tryDecReaderOverflow(s) != 0L)
                return;
        }
        throw new IllegalMonitorStateException();
    }

    /**
     * If the lock state matches the given stamp, performs one of
     * the following actions. If the stamp represents holding a write
     * lock, returns it.  Or, if a read lock, if the write lock is
     * available, releases the read lock and returns a write stamp.
     * Or, if an optimistic read, returns a write stamp only if
     * immediately available. This method returns zero in all other
     * cases.
     *
     * @param stamp a stamp
     * @return a valid write stamp, or zero on failure
     */
    public long tryConvertToWriteLock(long stamp) {
        long a = stamp & ABITS, m, s, next;
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((m = s & ABITS) == 0L) {
                if (a != 0L)
                    break;
                if (U.compareAndSwapLong(this, STATE, s, next = s + WBIT))
                    return next;
            }
            else if (m == WBIT) {
                if (a != m)
                    break;
                return stamp;
            }
            else if (m == RUNIT && a != 0L) {
                if (U.compareAndSwapLong(this, STATE, s,
                                         next = s - RUNIT + WBIT))
                    return next;
            }
            else
                break;
        }
        return 0L;
    }

    /**
     * If the lock state matches the given stamp, performs one of
     * the following actions. If the stamp represents holding a write
     * lock, releases it and obtains a read lock.  Or, if a read lock,
     * returns it. Or, if an optimistic read, acquires a read lock and
     * returns a read stamp only if immediately available. This method
     * returns zero in all other cases.
     *
     * @param stamp a stamp
     * @return a valid read stamp, or zero on failure
     */
    public long tryConvertToReadLock(long stamp) {
        long a = stamp & ABITS, m, s, next; WNode h;
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((m = s & ABITS) == 0L) {
                if (a != 0L)
                    break;
                else if (m < RFULL) {
                    if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))
                        return next;
                }
                else if ((next = tryIncReaderOverflow(s)) != 0L)
                    return next;
            }
            else if (m == WBIT) {
                if (a != m)
                    break;
                state = next = s + (WBIT + RUNIT);
                if ((h = whead) != null && h.status != 0)
                    release(h);
                return next;
            }
            else if (a != 0L && a < WBIT)
                return stamp;
            else
                break;
        }
        return 0L;
    }

    /**
     * If the lock state matches the given stamp then, if the stamp
     * represents holding a lock, releases it and returns an
     * observation stamp.  Or, if an optimistic read, returns it if
     * validated. This method returns zero in all other cases, and so
     * may be useful as a form of "tryUnlock".
     *
     * @param stamp a stamp
     * @return a valid optimistic read stamp, or zero on failure
     */
    public long tryConvertToOptimisticRead(long stamp) {
        long a = stamp & ABITS, m, s, next; WNode h;
        U.loadFence();
        for (;;) {
            if (((s = state) & SBITS) != (stamp & SBITS))
                break;
            if ((m = s & ABITS) == 0L) {
                if (a != 0L)
                    break;
                return s;
            }
            else if (m == WBIT) {
                if (a != m)
                    break;
                state = next = (s += WBIT) == 0L ? ORIGIN : s;
                if ((h = whead) != null && h.status != 0)
                    release(h);
                return next;
            }
            else if (a == 0L || a >= WBIT)
                break;
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, next = s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    return next & SBITS;
                }
            }
            else if ((next = tryDecReaderOverflow(s)) != 0L)
                return next & SBITS;
        }
        return 0L;
    }

    /**
     * Releases the write lock if it is held, without requiring a
     * stamp value. This method may be useful for recovery after
     * errors.
     *
     * @return {@code true} if the lock was held, else false
     */
    public boolean tryUnlockWrite() {
        long s; WNode h;
        if (((s = state) & WBIT) != 0L) {
            state = (s += WBIT) == 0L ? ORIGIN : s;
            if ((h = whead) != null && h.status != 0)
                release(h);
            return true;
        }
        return false;
    }

    /**
     * Releases one hold of the read lock if it is held, without
     * requiring a stamp value. This method may be useful for recovery
     * after errors.
     *
     * @return {@code true} if the read lock was held, else false
     */
    public boolean tryUnlockRead() {
        long s, m; WNode h;
        while ((m = (s = state) & ABITS) != 0L && m < WBIT) {
            if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    return true;
                }
            }
            else if (tryDecReaderOverflow(s) != 0L)
                return true;
        }
        return false;
    }

    // status monitoring methods

    /**
     * Returns combined state-held and overflow read count for given
     * state s.
     */
    private int getReadLockCount(long s) {
        long readers;
        if ((readers = s & RBITS) >= RFULL)
            readers = RFULL + readerOverflow;
        return (int) readers;
    }

    /**
     * Returns {@code true} if the lock is currently held exclusively.
     *
     * @return {@code true} if the lock is currently held exclusively
     */
    public boolean isWriteLocked() {
        return (state & WBIT) != 0L;
    }

    /**
     * Returns {@code true} if the lock is currently held non-exclusively.
     *
     * @return {@code true} if the lock is currently held non-exclusively
     */
    public boolean isReadLocked() {
        return (state & RBITS) != 0L;
    }

    /**
     * Queries the number of read locks held for this lock. This
     * method is designed for use in monitoring system state, not for
     * synchronization control.
     * @return the number of read locks held
     */
    public int getReadLockCount() {
        return getReadLockCount(state);
    }

    /**
     * Returns a string identifying this lock, as well as its lock
     * state.  The state, in brackets, includes the String {@code
     * "Unlocked"} or the String {@code "Write-locked"} or the String
     * {@code "Read-locks:"} followed by the current number of
     * read-locks held.
     *
     * @return a string identifying this lock, as well as its lock state
     */
    public String toString() {
        long s = state;
        return super.toString() +
            ((s & ABITS) == 0L ? "[Unlocked]" :
             (s & WBIT) != 0L ? "[Write-locked]" :
             "[Read-locks:" + getReadLockCount(s) + "]");
    }

    // views

    /**
     * Returns a plain {@link Lock} view of this StampedLock in which
     * the {@link Lock#lock} method is mapped to {@link #readLock},
     * and similarly for other methods. The returned Lock does not
     * support a {@link Condition}; method {@link
     * Lock#newCondition()} throws {@code
     * UnsupportedOperationException}.
     *
     * @return the lock
     */
    public Lock asReadLock() {
        ReadLockView v;
        return ((v = readLockView) != null ? v :
                (readLockView = new ReadLockView()));
    }

    /**
     * Returns a plain {@link Lock} view of this StampedLock in which
     * the {@link Lock#lock} method is mapped to {@link #writeLock},
     * and similarly for other methods. The returned Lock does not
     * support a {@link Condition}; method {@link
     * Lock#newCondition()} throws {@code
     * UnsupportedOperationException}.
     *
     * @return the lock
     */
    public Lock asWriteLock() {
        WriteLockView v;
        return ((v = writeLockView) != null ? v :
                (writeLockView = new WriteLockView()));
    }

    /**
     * Returns a {@link ReadWriteLock} view of this StampedLock in
     * which the {@link ReadWriteLock#readLock()} method is mapped to
     * {@link #asReadLock()}, and {@link ReadWriteLock#writeLock()} to
     * {@link #asWriteLock()}.
     *
     * @return the lock
     */
    public ReadWriteLock asReadWriteLock() {
        ReadWriteLockView v;
        return ((v = readWriteLockView) != null ? v :
                (readWriteLockView = new ReadWriteLockView()));
    }

    // view classes

    final class ReadLockView implements Lock {
        public void lock() { readLock(); }
        public void lockInterruptibly() throws InterruptedException {
            readLockInterruptibly();
        }
        public boolean tryLock() { return tryReadLock() != 0L; }
        public boolean tryLock(long time, TimeUnit unit)
            throws InterruptedException {
            return tryReadLock(time, unit) != 0L;
        }
        public void unlock() { unstampedUnlockRead(); }
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    final class WriteLockView implements Lock {
        public void lock() { writeLock(); }
        public void lockInterruptibly() throws InterruptedException {
            writeLockInterruptibly();
        }
        public boolean tryLock() { return tryWriteLock() != 0L; }
        public boolean tryLock(long time, TimeUnit unit)
            throws InterruptedException {
            return tryWriteLock(time, unit) != 0L;
        }
        public void unlock() { unstampedUnlockWrite(); }
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    final class ReadWriteLockView implements ReadWriteLock {
        public Lock readLock() { return asReadLock(); }
        public Lock writeLock() { return asWriteLock(); }
    }

    // Unlock methods without stamp argument checks for view classes.
    // Needed because view-class lock methods throw away stamps.

    final void unstampedUnlockWrite() {
        WNode h; long s;
        if (((s = state) & WBIT) == 0L)
            throw new IllegalMonitorStateException();
        state = (s += WBIT) == 0L ? ORIGIN : s;
        if ((h = whead) != null && h.status != 0)
            release(h);
    }

    final void unstampedUnlockRead() {
        for (;;) {
            long s, m; WNode h;
            if ((m = (s = state) & ABITS) == 0L || m >= WBIT)
                throw new IllegalMonitorStateException();
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    break;
                }
            }
            else if (tryDecReaderOverflow(s) != 0L)
                break;
        }
    }

    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        state = ORIGIN; // reset to unlocked state
    }

    // internals

    /**
     * Tries to increment readerOverflow by first setting state
     * access bits value to RBITS, indicating hold of spinlock,
     * then updating, then releasing.
     * 尝试通过首先将状态访问位值设置为RBITS来增加readerOverflow，指示保持自旋锁，然后更新，然后释放。
     * @param s a reader overflow stamp: (s & ABITS) >= RFULL
     * @return new stamp on success, else zero
     */
    private long tryIncReaderOverflow(long s) {
        // assert (s & ABITS) >= RFULL;
        // 如果当前锁状态的第一组八位的值等于读锁最大值，就使用CAS与读锁位位或运算的结果作为新的锁状态。其结果与+1是一样的，之所以用位或而不是+1，是为了保证结果不会溢出读锁位
        // 这个CAS+1操作（这里使用位或代替了）还有一个好处，使别的线程没有办法更改读锁状态位的数据，因为+1后，(s & ABITS)必然大于RFULL了
        // 然后使读者溢出+1，锁状态还原回s，并以此状态作为戳记返回
        // 至此，读锁也能获取成功
        // 结论，当读锁状态位满之后，依然是可以获取读锁的，只是多线程同时获取时有一定概率失败
        if ((s & ABITS) == RFULL) {
            if (U.compareAndSwapLong(this, STATE, s, s | RBITS)) {
                ++readerOverflow;
                state = s;
                return s;
            }
        }

        // 如果当前锁状态的第一组八位的值不等于读锁最大值，根据让步时间进行线程让步，然后返回0，获取锁失败
        // TODO 未明白具体运算含义
        else if ((LockSupport.nextSecondarySeed() &
                  OVERFLOW_YIELD_RATE) == 0)
            Thread.yield();
        return 0L;
    }

    /**
     * Tries to decrement readerOverflow.
     *
     * @param s a reader overflow stamp: (s & ABITS) >= RFULL
     * @return new stamp on success, else zero
     */
    private long tryDecReaderOverflow(long s) {
        // assert (s & ABITS) >= RFULL;
        if ((s & ABITS) == RFULL) {
            if (U.compareAndSwapLong(this, STATE, s, s | RBITS)) {
                int r; long next;
                if ((r = readerOverflow) > 0) {
                    readerOverflow = r - 1;
                    next = s;
                }
                else
                    next = s - RUNIT;
                 state = next;
                 return next;
            }
        }
        else if ((LockSupport.nextSecondarySeed() &
                  OVERFLOW_YIELD_RATE) == 0)
            Thread.yield();
        return 0L;
    }

    /**
     * Wakes up the successor of h (normally whead). This is normally
     * just h.next, but may require traversal from wtail if next
     * pointers are lagging. This may fail to wake up an acquiring
     * thread when one or more have been cancelled, but the cancel
     * methods themselves provide extra safeguards to ensure liveness.
     */
    private void release(WNode h) {
        if (h != null) {
            WNode q; Thread w;

            // 将头节点的状态设置为0
            U.compareAndSwapInt(h, WSTATUS, WAITING, 0);

            // 如果h的下一个节点为空，或者q的状态为CANCELLED
            // 从锁链表尾部往回找，找到第一个状态为WAITING的节点，使q指向这个节点
            if ((q = h.next) == null || q.status == CANCELLED) {
                for (WNode t = wtail; t != null && t != h; t = t.prev)
                    if (t.status <= 0)
                        q = t;
            }

            // 如果h的下一个节点的状态为CANCELLED，unpark第一个WAITING节点
            // 否则unpark这个h的下一个节点
            if (q != null && (w = q.thread) != null)
                U.unpark(w);
        }
    }

    /**
     * See above for explanation.
     *
     * @param interruptible true if should check interrupts and if so
     * return INTERRUPTED
     * @param deadline if nonzero, the System.nanoTime value to timeout
     * at (and return zero)
     * @return next state, or INTERRUPTED
     */
    private long acquireWrite(boolean interruptible, long deadline) {
        WNode node = null, p;
        for (int spins = -1;;) { // spin while enqueuing
            long m, s, ns;

            // 每一次循环，首先再次判断锁状态的第一组前八位是否为0，如果为0则说明写锁是空闲状态，可以直接获取锁，设置锁状态为锁状态的第一组前八位最高位加1作为戳记返回
            // 并且，每一次循环开始，都会设置m的值为锁状态的第一组前八位的值，s的值为当前锁状态的值。且保证m和s是同一时刻状态产生的值
            if ((m = (s = state) & ABITS) == 0L) {
                if (U.compareAndSwapLong(this, STATE, s, ns = s + WBIT))
                    return ns;
            }

            // 首次循环，spins是小于0的
            // 当spins自减到0时，会直接死循环创建节点
            else if (spins < 0)
                // 如果锁状态的第一组前八位的值m等于WBIT，说明当前加了写锁未释放，如果同时wtail == whead，则证明锁链表为空。此时需要设置spins=SPINS=64
                spins = (m == WBIT && wtail == whead) ? SPINS : 0;

            // TODO 这个LockSupport.nextSecondarySeed() >= 0是什么含义暂时未明白
            else if (spins > 0) {
                if (LockSupport.nextSecondarySeed() >= 0)
                    --spins;
            }

            // p指向最后一个节点，如果最后一个节点为空，说明整个锁链表是空的，此时要创建链表。
            // 首先设置一个写模式、上一节点为空的节点，然后用CAS设置链表头whead为这个节点。如果成功则同时设置尾节点wtail为这个节点。
            // 此时，whead=wtail=新建的节点，因此头节点不会代表任何一个锁等待节点。
            else if ((p = wtail) == null) { // initialize queue
                WNode hd = new WNode(WMODE, null);
                if (U.compareAndSwapObject(this, WHEAD, null, hd))
                    wtail = hd;
            }

            // 如果node为空，则需要创建一个新的节点设置为node，同时node的上一个节点为p，而p此时指向尾节点。
            // 意为，在当前的锁链表的尾节点上，新建一个节点node
            else if (node == null)
                node = new WNode(WMODE, p);

            // 如果此次循环中，创建的node不是最后一个节点，则重新设置为最后一个节点
            // 这种情况出现的原因为，上一次循环中在链表尾部追加创建了node，但在此次循环的同时，另外的一个线程又在链表尾部追加创建了一个节点，并率先设置wtail为新的节点了。
            // 而p指向了新的尾节点，但此时node.prev仍然指向上一次循环的尾节点p，造成了不一致。
            // 此处是想更新node到新的尾节点后面
            else if (node.prev != p)
                node.prev = p;

            // 设置wtail为node，其含义是设置node为新的尾部节点。如果设置成功，会设置原尾部节点的next指向node。实现双向指针链表。至此，创建node节点成功。
            // 如果设置失败，则继续下一次循环创建节点
            else if (U.compareAndSwapObject(this, WTAIL, p, node)) {
                p.next = node;
                break;
            }
        }

        /*
        上次循环的目的是在双向链表中添加一个代表本次加锁的节点，而node则指向了这个新建的节点。p则为node的上一个节点。
         */

        for (int spins = -1;;) {
            WNode h, np, pp; int ps;

            // 每一次循环，都会使h指向链表头
            // 当p就是链表头时，由于p为node的上一个节点，因此，node代表锁等待链表中，下一个应该获取锁的节点
            if ((h = whead) == p) {

                // 首次运行spins是小于0的，设置spins为阻塞在头部获取锁前，重试的最大次数，一般是1024次
                if (spins < 0)
                    spins = HEAD_SPINS;

                // 如果spins小于在再次阻塞前重试的最大次数，spins = spins*2
                else if (spins < MAX_HEAD_SPINS)
                    spins <<= 1;


                // 循环获取锁，第一次自旋1024次，第二次2048次，以此类推
                for (int k = spins;;) { // spin at head
                    long s, ns;

                    // 再次检查是否锁已经空闲，如果锁已经空闲，则使用CAS设置锁状态为当前状态的第一组八位的最高位加1。如果成功返回新状态作为此次加锁的戳记。
                    // 并且抛弃头节点，设置node为新的头节点。且头节点的上一节点置空。
                    if (((s = state) & ABITS) == 0L) {
                        if (U.compareAndSwapLong(this, STATE, s,
                                                 ns = s + WBIT)) {
                            whead = node;
                            node.prev = null;
                            return ns;
                        }
                    }

                    // 如果LockSupport.nextSecondarySeed() >= 0，且k自减1后小于或等于0，则放弃本次头部获取锁。
                    else if (LockSupport.nextSecondarySeed() >= 0 &&
                             --k <= 0)
                        break;
                }
            }

            // 一轮头部循环后，仍然没有获得锁，此时，h指向头节点

            // 如果头节点h不为空
            else if (h != null) { // help release stale waiters
                WNode c; Thread w;

                // 循环，使c轮询头部节点h的cowait链表
                while ((c = h.cowait) != null) {
                    // 使用CAS设置h的cowait为c的cowait，并upark此时c的线程，直到h.cowait链表节点全部被unpark并且清空
                    if (U.compareAndSwapObject(h, WCOWAIT, c, c.cowait) &&
                        (w = c.thread) != null)
                        U.unpark(w);
                }
            }

            // 保证h为头部节点
            if (whead == h) {

                // 设置np为node的上一个节点
                // 如果node的上一个节点不为p，重新使p指向node的上一个节点
                // TODO 为什么会出现这种情况呢，我认为这种情况是不会存在的，因为p和node的关系之前是通过CAS设置的
                if ((np = node.prev) != p) {
                    if (np != null)
                        (p = np).next = node;   // stale
                }

                // 设置ps为p的状态，如果p.status为0，则使用CAS设置p的状态为WAITING
                else if ((ps = p.status) == 0)
                    U.compareAndSwapInt(p, WSTATUS, 0, WAITING);

                // 如果p.status的状态为CANCELLED，如果p的上一个节点不为空，则在锁链表中删除p节点
                // p的上一个节点如果为空，意味着p是头节点，头节点即使状态为CANCELLED也是不需要删除的，因为头节点没有锁等待含义。
                else if (ps == CANCELLED) {
                    if ((pp = p.prev) != null) {
                        node.prev = pp;
                        pp.next = node;
                    }
                }
                else {
                    long time; // 0 argument to park means no timeout
                    if (deadline == 0L)
                        time = 0L;

                    // 加写锁时，time=0，deadline也等于0，因此下面两行代码不会执行
                    else if ((time = deadline - System.nanoTime()) <= 0L)
                        return cancelWaiter(node, node, false);

                    // 设置当前线程的PARKBLOCKER为本锁对象
                    // 设置node的线程为当前线程
                    Thread wt = Thread.currentThread();
                    U.putObject(wt, PARKBLOCKER, this);
                    node.thread = wt;

                    // 如果p.status < 0，则代表p的状态为WAITING=-1
                    // 且p不是头节点 || 锁状态的第一组8位不等于0，说明锁不是空闲状态
                    // h是头节点，且当前节点node的上一个节点是p
                    // 则park本线程
                    if (p.status < 0 && (p != h || (state & ABITS) != 0L) &&
                        whead == h && node.prev == p)
                        U.park(false, time);  // emulate LockSupport.park

                    // 此处本线程会阻塞

                    // 待本线程被唤醒后，置空本节点的线程，置空当前线程的PARKBLOCKER
                    node.thread = null;
                    U.putObject(wt, PARKBLOCKER, null);

                    // 如果传入参数为可中断，则中断本线程，如果成功，则cancelWaiter
                    // 获取写锁的时候，传入的interruptible是false，这种情况下以下代码不会执行
                    if (interruptible && Thread.interrupted())
                        return cancelWaiter(node, node, true);
                }
            }
        }
    }

    /**
     * See above for explanation.
     *
     * @param interruptible true if should check interrupts and if so
     * return INTERRUPTED
     * @param deadline if nonzero, the System.nanoTime value to timeout
     * at (and return zero)
     * @return next state, or INTERRUPTED
     */
    private long acquireRead(boolean interruptible, long deadline) {
        WNode node = null, p;
        for (int spins = -1;;) {
            WNode h;

            // 在每一次循环，都使h指向锁链表头，p指向锁链表尾
            // 如果锁链表头等于锁链表尾，有两种情况，1.锁链表还没有创建过，此时whead=wtail=null。2.锁链表已经创建，但是没有等待节点
            if ((h = whead) == (p = wtail)) {
                for (long m, s, ns;;) {
                    // m的值为锁状态的第一组八位的值
                    // s的值为当前锁状态的值
                    // 如果锁状态的第一组八位的值小于读锁位最大值，则使用CAS为锁状态+1作为新的锁状态，如果成功就使用新状态作为戳记返回。至此，读锁获取成功
                    // 如果锁状态的第一组八位的值小于读锁位最大值不成立，判断，写锁标记位（第一组八位最高1位）为0，且尝试自增读者溢出返回值不等于0，
                    // 也可认为获取读锁成功，以尝试自增读者溢出返回值作为戳记返回
                    if ((m = (s = state) & ABITS) < RFULL ?
                        U.compareAndSwapLong(this, STATE, s, ns = s + RUNIT) :
                        (m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L))
                        return ns;

                    // 如果获取读锁没有成功，且第一组八位的值大于或等于写锁位，则说明写锁目前未释放
                    else if (m >= WBIT) {
                        if (spins > 0) {
                            if (LockSupport.nextSecondarySeed() >= 0)
                                --spins;
                        }
                        else {
                            if (spins == 0) {
                                WNode nh = whead, np = wtail;
                                // 自旋64次后，如果原先的头和尾依然对应现在的头和尾
                                // 或者，现在头和尾已经不相等了，说明锁链表不是空的。锁链表节点变更过，且现在锁链表已经不为空了
                                // 或的两个判定总结，如果链表节点没有变更过，则跳出循环。如果链表变更过，且现在锁链表不为空，也要跳出循环。
                                // 因为这个循环是为锁链表为空的情况快速获取读锁的。
                                if ((nh == h && np == p) || (h = nh) != (p = np))
                                    break;
                            }
                            spins = SPINS;
                        }
                    }
                }
            }

            // 上一个循环快速获取读锁失败后，此时p指向链表尾节点，h指向链表头节点

            // 如果锁链表的尾节点为空，说明整个锁链表是空的。
            // 所以，使用CAS为锁链表创建链表头节点和尾节点，此时头节点和尾节点属于同一个节点。
            if (p == null) { // initialize queue
                WNode hd = new WNode(WMODE, null);
                if (U.compareAndSwapObject(this, WHEAD, null, hd))
                    wtail = hd;
            }

            // 如果代表本次获取读锁的node节点为空，也需要在尾节点后面创建node节点
            // 读锁节点是使用读模式的
            else if (node == null)
                node = new WNode(RMODE, p);

            // 如果头等于尾，说明锁链表还是空的
            // 或者，尾部节点不是读模式的
            // 将node节点放置在尾节点后面，成为新的尾节点，一但此操作成功，将跳出循环，准备进行第二组自旋
            else if (h == p || p.mode != RMODE) {
                if (node.prev != p)
                    node.prev = p;
                else if (U.compareAndSwapObject(this, WTAIL, p, node)) {
                    p.next = node;
                    break;
                }
            }

            // 此时，p指向了新的尾节点，有可能p和node是同一个节点

            // 尝试使用CAS设置尾部节点的cowait指向node
            // 同时设置node的cowait指向p的cowait，意义为，将node插入到p的cowait链头部中
            // 如果不成功，说明已经有别的线程更改了p.cowait的值，设置node.cowait为null
            // 如果上一节点是读模式，会将node插入到p的cowait链头部中
            else if (!U.compareAndSwapObject(p, WCOWAIT,
                                             node.cowait = p.cowait, node))
                node.cowait = null;
            else {
                // 走到这里，说明已经成功将node插入到p的cowait链表中的最前面了

                for (;;) {
                    WNode pp, c; Thread w;

                    // 如果锁链表头不为空，且链表头的cowait不为空
                    // 就使用CAS设置头节点的cowait的第一个节点删除，并且这个被删节点为设置为c
                    // 如果成功，unpark唤醒这个刚删除的节点的线程
                    // 也就是说，每一次循环开始，都会尝试删除锁链表头部节点的cowait链表的第一个节点，并唤醒这个节点的线程
                    if ((h = whead) != null && (c = h.cowait) != null &&
                        U.compareAndSwapObject(h, WCOWAIT, c, c.cowait) &&
                        (w = c.thread) != null) // help release
                        U.unpark(w);

                    // 如果链表尾节点的上一节点是头节点，则说明锁链表中只有一个节点p
                    // 或者，链表为空
                    if (h == (pp = p.prev) || h == p || pp == null) {
                        long m, s, ns;
                        do {
                            // m为锁状态的第一组八位的值，如果第一组八位的值小于最大值，可以尝试直接获取读锁
                            if ((m = (s = state) & ABITS) < RFULL ?
                                U.compareAndSwapLong(this, STATE, s,
                                                     ns = s + RUNIT) :
                                (m < WBIT &&
                                 (ns = tryIncReaderOverflow(s)) != 0L))
                                return ns;
                        } while (m < WBIT);
                    }

                    // 重复确定h指向锁链表头节点，pp指向p的上一个节点
                    if (whead == h && p.prev == pp) {
                        long time;
                        if (pp == null || h == p || p.status > 0) {
                            node = null; // throw away
                            break;
                        }
                        if (deadline == 0L)
                            time = 0L;
                        else if ((time = deadline - System.nanoTime()) <= 0L)
                            return cancelWaiter(node, p, false);

                        Thread wt = Thread.currentThread();
                        U.putObject(wt, PARKBLOCKER, this);
                        node.thread = wt;

                        // 如果此时，锁链不只有一个节点，或者写锁未释放，且读锁位无内容，则park本线程
                        if ((h != pp || (state & ABITS) == WBIT) &&
                            whead == h && p.prev == pp)
                            U.park(false, time);

                        // 此处会阻塞，需要unpark后，才会在此处开始继续执行

                        node.thread = null;
                        U.putObject(wt, PARKBLOCKER, null);

                        // 如果是允许中断的，尝试中断
                        if (interruptible && Thread.interrupted())
                            return cancelWaiter(node, p, true);
                    }
                }
            }
        }

        // 准备进行第二组自旋，此时，p为node的上一节点

        for (int spins = -1;;) {
            WNode h, np, pp; int ps;

            // 如果p是头节点，说明锁链表中只有一个节点，就是node
            // 再次尝试让node节点快速获取读锁
            if ((h = whead) == p) {
                if (spins < 0)
                    spins = HEAD_SPINS;
                else if (spins < MAX_HEAD_SPINS)
                    spins <<= 1;
                for (int k = spins;;) { // spin at head
                    long m, s, ns;
                    if ((m = (s = state) & ABITS) < RFULL ?
                        U.compareAndSwapLong(this, STATE, s, ns = s + RUNIT) :
                        (m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L)) {
                        WNode c; Thread w;

                        // 设置链表为头节点，至此，node失去了获取锁节点的意义
                        whead = node;
                        node.prev = null;

                        // unpark在node中的cowait链表的所有节点，并清空node的cowait链表
                        while ((c = node.cowait) != null) {
                            if (U.compareAndSwapObject(node, WCOWAIT,
                                                       c, c.cowait) &&
                                (w = c.thread) != null)
                                U.unpark(w);
                        }
                        return ns;
                    }
                    else if (m >= WBIT &&
                             LockSupport.nextSecondarySeed() >= 0 && --k <= 0)
                        break;
                }
            }

            // 如果node的上一节点p不是头节点，且头节点不为空
            else if (h != null) {
                WNode c; Thread w;
                // unpark并清空锁链表头节点的cowait链表
                while ((c = h.cowait) != null) {
                    if (U.compareAndSwapObject(h, WCOWAIT, c, c.cowait) &&
                        (w = c.thread) != null)
                        U.unpark(w);
                }
            }

            // 再次确认h指向头节点
            if (whead == h) {
                if ((np = node.prev) != p) {
                    if (np != null)
                        (p = np).next = node;   // stale
                }
                else if ((ps = p.status) == 0)
                    U.compareAndSwapInt(p, WSTATUS, 0, WAITING);

                // 如果node的上一节点p已经CANCELLED，则删除p节点
                else if (ps == CANCELLED) {
                    if ((pp = p.prev) != null) {
                        node.prev = pp;
                        pp.next = node;
                    }
                }
                else {
                    long time;
                    if (deadline == 0L)
                        time = 0L;
                    else if ((time = deadline - System.nanoTime()) <= 0L)
                        return cancelWaiter(node, node, false);

                    Thread wt = Thread.currentThread();
                    U.putObject(wt, PARKBLOCKER, this);
                    node.thread = wt;
                    if (p.status < 0 &&
                        (p != h || (state & ABITS) == WBIT) &&
                        whead == h && node.prev == p)
                        U.park(false, time);

                    node.thread = null;
                    U.putObject(wt, PARKBLOCKER, null);
                    if (interruptible && Thread.interrupted())
                        return cancelWaiter(node, node, true);
                }
            }
        }
    }

    /**
     * If node non-null, forces cancel status and unsplices it from
     * queue if possible and wakes up any cowaiters (of the node, or
     * group, as applicable), and in any case helps release current
     * first waiter if lock is free. (Calling with null arguments
     * serves as a conditional form of release, which is not currently
     * needed but may be needed under possible future cancellation
     * policies). This is a variant of cancellation methods in
     * AbstractQueuedSynchronizer (see its detailed explanation in AQS
     * internal documentation).
     *
     * @param node if nonnull, the waiter
     * @param group either node or the group node is cowaiting with
     * @param interrupted if already interrupted
     * @return INTERRUPTED if interrupted or Thread.interrupted, else zero
     */
    private long cancelWaiter(WNode node, WNode group, boolean interrupted) {
        if (node != null && group != null) {
            Thread w;
            node.status = CANCELLED;
            // unsplice cancelled nodes from group
            for (WNode p = group, q; (q = p.cowait) != null;) {
                if (q.status == CANCELLED) {
                    U.compareAndSwapObject(p, WCOWAIT, q, q.cowait);
                    p = group; // restart
                }
                else
                    p = q;
            }
            if (group == node) {
                for (WNode r = group.cowait; r != null; r = r.cowait) {
                    if ((w = r.thread) != null)
                        U.unpark(w);       // wake up uncancelled co-waiters
                }
                for (WNode pred = node.prev; pred != null; ) { // unsplice
                    WNode succ, pp;        // find valid successor
                    while ((succ = node.next) == null ||
                           succ.status == CANCELLED) {
                        WNode q = null;    // find successor the slow way
                        for (WNode t = wtail; t != null && t != node; t = t.prev)
                            if (t.status != CANCELLED)
                                q = t;     // don't link if succ cancelled
                        if (succ == q ||   // ensure accurate successor
                            U.compareAndSwapObject(node, WNEXT,
                                                   succ, succ = q)) {
                            if (succ == null && node == wtail)
                                U.compareAndSwapObject(this, WTAIL, node, pred);
                            break;
                        }
                    }
                    if (pred.next == node) // unsplice pred link
                        U.compareAndSwapObject(pred, WNEXT, node, succ);
                    if (succ != null && (w = succ.thread) != null) {
                        succ.thread = null;
                        U.unpark(w);       // wake up succ to observe new pred
                    }
                    if (pred.status != CANCELLED || (pp = pred.prev) == null)
                        break;
                    node.prev = pp;        // repeat if new pred wrong/cancelled
                    U.compareAndSwapObject(pp, WNEXT, pred, succ);
                    pred = pp;
                }
            }
        }
        WNode h; // Possibly release first waiter
        while ((h = whead) != null) {
            long s; WNode q; // similar to release() but check eligibility
            if ((q = h.next) == null || q.status == CANCELLED) {
                for (WNode t = wtail; t != null && t != h; t = t.prev)
                    if (t.status <= 0)
                        q = t;
            }
            if (h == whead) {
                if (q != null && h.status == 0 &&
                    ((s = state) & ABITS) != WBIT && // waiter is eligible
                    (s == 0L || q.mode == RMODE))
                    release(h);
                break;
            }
        }
        return (interrupted || Thread.interrupted()) ? INTERRUPTED : 0L;
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe U;
    private static final long STATE;
    private static final long WHEAD;
    private static final long WTAIL;
    private static final long WNEXT;
    private static final long WSTATUS;
    private static final long WCOWAIT;
    private static final long PARKBLOCKER;

    static {
        try {
            U = sun.misc.Unsafe.getUnsafe();
            Class<?> k = StampedLock.class;
            Class<?> wk = WNode.class;
            STATE = U.objectFieldOffset
                (k.getDeclaredField("state"));
            WHEAD = U.objectFieldOffset
                (k.getDeclaredField("whead"));
            WTAIL = U.objectFieldOffset
                (k.getDeclaredField("wtail"));
            WSTATUS = U.objectFieldOffset
                (wk.getDeclaredField("status"));
            WNEXT = U.objectFieldOffset
                (wk.getDeclaredField("next"));
            WCOWAIT = U.objectFieldOffset
                (wk.getDeclaredField("cowait"));
            Class<?> tk = Thread.class;
            PARKBLOCKER = U.objectFieldOffset
                (tk.getDeclaredField("parkBlocker"));

        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
