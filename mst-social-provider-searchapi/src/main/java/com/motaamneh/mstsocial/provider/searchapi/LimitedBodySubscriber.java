package com.motaamneh.mstsocial.provider.searchapi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** One instance per response. Counts actual bytes, including chunked responses. */
final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final int limit;
    private final ByteArrayOutputStream bytes;
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private Flow.Subscription subscription;

    LimitedBodySubscriber(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        this.limit = limit;
        this.bytes = new ByteArrayOutputStream(Math.min(limit, 8192));
    }

    @Override
    public CompletionStage<byte[]> getBody() {
        return body;
    }

    @Override
    public synchronized void onSubscribe(Flow.Subscription incoming) {
        Objects.requireNonNull(incoming, "subscription must not be null");
        if (subscription != null || body.isDone()) {
            incoming.cancel();
            return;
        }
        subscription = incoming;
        subscription.request(1);
    }

    @Override
    public synchronized void onNext(List<ByteBuffer> buffers) {
        if (body.isDone()) {
            return;
        }
        for (ByteBuffer buffer : buffers) {
            if (buffer.remaining() > limit - bytes.size()) {
                fail(new ResponseTooLargeException());
                return;
            }
            byte[] chunk = new byte[Math.min(buffer.remaining(), 8192)];
            while (buffer.hasRemaining()) {
                int count = Math.min(buffer.remaining(), chunk.length);
                buffer.get(chunk, 0, count);
                bytes.write(chunk, 0, count);
            }
        }
        subscription.request(1);
    }

    @Override
    public synchronized void onError(Throwable error) {
        fail(error);
    }

    @Override
    public synchronized void onComplete() {
        if (!body.isDone()) {
            body.complete(bytes.toByteArray());
            bytes.reset();
        }
    }

    synchronized void cancel() {
        body.cancel(false);
        if (subscription != null) {
            subscription.cancel();
        }
        bytes.reset();
    }

    private void fail(Throwable error) {
        body.completeExceptionally(error);
        if (subscription != null) {
            subscription.cancel();
        }
        bytes.reset();
    }

    static final class ResponseTooLargeException extends IOException {
        ResponseTooLargeException() {
            super("Provider response exceeds the configured byte limit");
        }
    }
}
