package it;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.BooleanSupplier;

import org.jboss.logging.Logger;

import io.strimzi.test.container.WaitException;

public class Utils {
    private static final Logger LOGGER = Logger.getLogger(Utils.class);

    /**
     * Poll the given {@code ready} function every {@code pollIntervalMs} milliseconds until it returns true,
     * or throw a WaitException if it doesn't returns true within {@code timeoutMs} milliseconds.
     * @return The remaining time left until timeout occurs
     * (helpful if you have several calls which need to share a common timeout),
     *
     * @param description waiting for `description`
     * @param pollIntervalMs poll interval in milliseconds
     * @param timeoutMs timeout in milliseconds
     * @param ready lambda predicate
     */
    static long waitFor(String description, long pollIntervalMs, long timeoutMs, BooleanSupplier ready) {
        LOGGER.debug("Waiting for " + description);
        long deadline = System.currentTimeMillis() + timeoutMs;
        String exceptionMessage = null;
        int exceptionCount = 0;
        StringWriter stackTraceError = new StringWriter();

        while (true) {
            boolean result;
            try {
                result = ready.getAsBoolean();
            } catch (Exception e) {
                exceptionMessage = e.getMessage();
                if (++exceptionCount == 1 && exceptionMessage != null) {
                    // Log the first exception as soon as it occurs
                    LOGGER.error("Exception waiting for " + description + " " + exceptionMessage);
                    // log the stacktrace
                    e.printStackTrace(new PrintWriter(stackTraceError));
                }
                result = false;
            }
            long timeLeft = deadline - System.currentTimeMillis();
            if (result) {
                return timeLeft;
            }
            if (timeLeft <= 0) {
                if (exceptionCount > 1) {
                    LOGGER.error("Exception waiting for " + description + " " + exceptionMessage);
                    if (!stackTraceError.toString().isEmpty()) {
                        // printing handled stacktrace
                        LOGGER.error(stackTraceError.toString());
                    }
                }
                WaitException waitException = new WaitException("Timeout after " + timeoutMs + " ms waiting for " + description);
                waitException.addSuppressed(waitException);
                throw waitException;
            }
            long sleepTime = Math.min(pollIntervalMs, timeLeft);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.info(description + " not satisfied, will try again in " + sleepTime + "ms (" + timeLeft + " ms till timeout)");
            }
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                return deadline - System.currentTimeMillis();
            }
        }
    }
}
