/**
 * (C) Copyright IBM Corp. 2019
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watsonhealth.task.core.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import com.ibm.watsonhealth.task.api.ITaskCollector;
import com.ibm.watsonhealth.task.api.ITaskGroup;

/**
 * Manages the execution of a set of dependent task groups using the
 * {@link ExecutorService} provided.
 * @author rarnold
 *
 */
public class TaskManager implements ITaskCollector {
    private static final Logger logger = Logger.getLogger(TaskManager.class.getName());

    // Probably a thread-pool we use to execute the submitted tasks
    private final ExecutorService pool;

    // A map of all the task groups collected/created
    private Map<String, TaskGroup> taskGroupMap = new HashMap<>();

    // The list of task groups without any children, so can be started first
    private List<TaskGroup> runnableTaskGroups = new ArrayList<>();

    // used to manage waiting for completion
    private Lock lock = new ReentrantLock();
    private Condition runningCondition = lock.newCondition();
    private int currentlyRunningCount;

    // Keep track of which tasks have failed
    private List<TaskGroup> failedTaskGroups = new ArrayList<>();

    /**
     * Public constructor 
     * @param pool
     */
    public TaskManager(ExecutorService pool) {
        this.pool = pool;
    }

    /**
     * Submit the task group to the pool
     * @param tg
     */
    public void submit(TaskGroup tg) {
        lock.lock();
        this.currentlyRunningCount++;
        lock.unlock();

        // ask the task group to submit itself to our thread pool
        tg.runTask(pool);
    }

    @Override
    public Collection<ITaskGroup> getFailedTaskGroups() {
        lock.lock();
        try {
            return Collections.unmodifiableList(this.failedTaskGroups);
        }
        finally {
            lock.unlock();
        }
    }

    /* (non-Javadoc)
     * @see com.ibm.watsonhealth.task.api.ITaskCollector#makeTaskGroup(com.ibm.watsonhealth.task.api.ITaskGroup, java.lang.Runnable)
     */
    @Override
    public ITaskGroup makeTaskGroup(String taskId, Runnable r, List<ITaskGroup> children) {
        TaskGroup result = taskGroupMap.get(taskId);
        if (result == null) {
            result = new TaskGroup(taskId, this, r);
            taskGroupMap.put(taskId, result);

            if (children == null || children.isEmpty()) {
                // Keep a list of all the task groups without any children. These leaf
                // task groups are the ones we can start first.
                this.runnableTaskGroups.add(result);
            }
        }

        if (children != null) {
            result.addChildTaskGroups(children);
        }
        return result;
    }

    /* (non-Javadoc)
     * @see com.ibm.watsonhealth.task.api.ITaskCollector#start()
     */
    @Override
    public void startAndWait() {

        // Start all the tasks we know of which don't have any dependencies
        for (TaskGroup tg: this.runnableTaskGroups) {
            submit(tg);
        }

        // Block until everything is done
        waitForCompletion();
    }

    /**
     * Wait here until all the tasks are done
     */
    private void waitForCompletion() {
        // block here until all the tasks are processed
        lock.lock();
        try {
            while (this.currentlyRunningCount > 0) {
                try {
                    runningCondition.await(1000, TimeUnit.MILLISECONDS);
                }
                catch (InterruptedException x) {
                    throw new IllegalStateException(x);
                }
            }
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Callback from each task group made when it completes
     */
    public void taskComplete(TaskGroup tg) {
        logger.info("Task complete callback for taskId: " + tg.getTaskId());

        lock.lock();
        try {
            // when there's nothing left running, alert anyone waiting
            if (--this.currentlyRunningCount == 0) {
                this.runningCondition.signalAll();
            }
        }
        finally {
            lock.unlock();
        }

    }

    /**
     * Receipt of a signal from the task group that it has failed. No taskComplete
     * will be called, so we need to signal it's the end of the road
     * @param taskGroup
     */
    public void taskFailed(TaskGroup tg) {
        logger.info("Task failed callback for taskId: " + tg.getTaskId());

        lock.lock();
        try {
            // Keep a record of anything that fails
            this.failedTaskGroups.add(tg);

            // when there's nothing left running, alert anyone waiting
            if (--this.currentlyRunningCount == 0) {
                this.runningCondition.signalAll();
            }
        }
        finally {
            lock.unlock();
        }

    }
}
