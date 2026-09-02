package com.hansanie.greencart.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small shared executors container. Use AppExecutors.DB for all database/notification background work.
 */
public final class AppExecutors {

    // Single-threaded executor to serialize DB operations and avoid creating many threads.
    public static final ExecutorService DB = Executors.newSingleThreadExecutor();

    private AppExecutors() { }
}

