package edu.nyu.classes.seats;

import edu.nyu.classes.seats.models.*;
import edu.nyu.classes.seats.storage.*;
import edu.nyu.classes.seats.storage.db.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import edu.nyu.classes.seats.brightspace.BrightspaceClient;
import java.util.function.Function;

public class BrightspaceSeatingHandlerBackgroundThread extends Thread {

    interface BrightspaceClientFactory {
        BrightspaceClient invoke();
    }

    private static final Logger LOG = LoggerFactory.getLogger(BrightspaceSeatingHandlerBackgroundThread.class);
    private AtomicBoolean running = new AtomicBoolean(false);
    private BrightspaceClientFactory brightspaceClientFactory;


    public BrightspaceSeatingHandlerBackgroundThread startThread(BrightspaceClientFactory clientFactory) {
        this.brightspaceClientFactory = clientFactory;
        this.running = new AtomicBoolean(true);
        this.setDaemon(true);
        this.start();

        return this;
    }

    public void shutdown() {
        this.running.set(false);

        try {
            this.interrupt();
            this.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    public void run() {
        long findProcessedSince = 0;

        while (this.running.get()) {
            try {
                LOG.info("Scanning for Brightspace updates");

                findProcessedSince = BrightspaceSeatGroupUpdatesTask.handleSeatGroupUpdates(findProcessedSince, brightspaceClientFactory.invoke());
            } catch (Exception e) {
                LOG.error("SeatingHandlerBackgroundTask main loop hit top level: " + e);
                e.printStackTrace();
            }

            try {
                Thread.sleep(30 * 60 * 1000);
            } catch (InterruptedException e) {
                LOG.error("Interrupted sleep: " + e);
            }
        }
    }
}
