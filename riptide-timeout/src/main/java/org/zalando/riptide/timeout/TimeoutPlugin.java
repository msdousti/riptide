package org.zalando.riptide.timeout;

import com.google.gag.annotation.remark.ThisWouldBeOneLineIn;
import org.springframework.http.client.ClientHttpResponse;
import org.zalando.riptide.Plugin;
import org.zalando.riptide.RequestArguments;
import org.zalando.riptide.RequestExecution;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

import static java.util.Arrays.stream;
import static org.zalando.riptide.CancelableCompletableFuture.forwardTo;
import static org.zalando.riptide.CancelableCompletableFuture.preserveCancelability;

/**
 * @see CompletableFuture#orTimeout(long, TimeUnit)
 */
@ThisWouldBeOneLineIn(language = "Java 9", toWit = "return () -> execution.execute().orTimeout(timeout, unit)")
public final class TimeoutPlugin implements Plugin {

    private final ScheduledExecutorService scheduler;
    private final long timeout;
    private final TimeUnit unit;

    public TimeoutPlugin(final ScheduledExecutorService scheduler, final long timeout,
            final TimeUnit unit) {
        this.scheduler = scheduler;
        this.timeout = timeout;
        this.unit = unit;
    }

    @Override
    public RequestExecution prepare(final RequestArguments arguments, final RequestExecution execution) {
        return () -> {
            final CompletableFuture<ClientHttpResponse> upstream = execution.execute();
            final CompletableFuture<ClientHttpResponse> downstream = cancelUpstreamCompleteDownstream(upstream);
            final ScheduledFuture<?> scheduledTimeout = delay(timeout(downstream), cancel(upstream));
            upstream.whenComplete(cancel(scheduledTimeout));
            return downstream;
        };
    }

    private <T> Runnable cancel(final CompletableFuture<T> future) {
        return () -> future.cancel(true);
    }

    private <T> Runnable timeout(final CompletableFuture<T> future) {
        return () -> future.completeExceptionally(new TimeoutException());
    }

    private ScheduledFuture<?> delay(final Runnable... tasks) {
        return scheduler.schedule(run(tasks), timeout, unit);
    }

    private Runnable run(final Runnable... tasks) {
        return () -> stream(tasks).forEach(Runnable::run);
    }

    private <T> BiConsumer<T, Throwable> cancel(final Future<?> future) {
        return (result, throwable) -> future.cancel(true);
    }

    private static CompletableFuture<ClientHttpResponse> cancelUpstreamCompleteDownstream(
            final CompletableFuture<ClientHttpResponse> original) {

        final CompletableFuture<ClientHttpResponse> future = preserveCancelability(original);
        original.whenComplete(forwardTo(future));
        return future;
    }

}
