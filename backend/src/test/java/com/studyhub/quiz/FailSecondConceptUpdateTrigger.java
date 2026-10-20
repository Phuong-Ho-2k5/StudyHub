package com.studyhub.quiz;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import org.h2.api.Trigger;

/** Test-only database failure after one concept update has already executed. */
public class FailSecondConceptUpdateTrigger implements Trigger {
    static final AtomicInteger updateCount = new AtomicInteger();

    @Override
    public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
        if (updateCount.incrementAndGet() == 2) {
            throw new SQLException("Simulated failure on the second concept update");
        }
    }
}
