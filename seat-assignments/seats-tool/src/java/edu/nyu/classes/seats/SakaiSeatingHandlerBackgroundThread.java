package edu.nyu.classes.seats;

import edu.nyu.classes.seats.models.*;
import edu.nyu.classes.seats.storage.*;
import edu.nyu.classes.seats.storage.db.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.*;
import org.sakaiproject.site.api.Site;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SakaiSeatingHandlerBackgroundThread extends Thread {
    private static final Logger LOG = LoggerFactory.getLogger(SakaiSeatingHandlerBackgroundThread.class);
    private AtomicBoolean running = new AtomicBoolean(false);

    public SakaiSeatingHandlerBackgroundThread startThread() {
        this.running = new AtomicBoolean(true);
        this.setDaemon(true);
        this.start();

        return this;
    }

    public void shutdown() {
        this.running.set(false);

        try {
            this.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    public void run() {
        long lastMtimeCheck = System.currentTimeMillis();
        long findProcessedSince = 0;

        long loopCount = 0;

        // Syncing to Sakai groups needs a logged in user.
        SakaiGroupSyncTask.login();

        while (this.running.get()) {
            try {
                if (loopCount % 60 == 0) {
                    lastMtimeCheck = SakaiSeatGroupUpdatesTask.runMTimeChecks(lastMtimeCheck);
                }

                if (loopCount % 600 == 0) {
                    SakaiSeatGroupUpdatesTask.findChangedInstructionModes();
                }

                findProcessedSince = SakaiSeatGroupUpdatesTask.handleSeatGroupUpdates(findProcessedSince);

                // To be enabled at a future date.
                if (loopCount % 30 == 0) {
                    SakaiGroupSyncTask.handleSakaiGroupSync();
                }

            } catch (Exception e) {
                LOG.error("SeatingHandlerBackgroundTask main loop hit top level: " + e);
                e.printStackTrace();
            }

            try {
                loopCount += 1;
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                LOG.error("Interrupted sleep: " + e);
            }
        }
    }

    public static void setDBTimingThresholdMs(long ms) {
        SakaiSeatGroupUpdatesTask.setDBTimingThresholdMs(ms);
        SakaiGroupSyncTask.setDBTimingThresholdMs(ms);
    }
}
