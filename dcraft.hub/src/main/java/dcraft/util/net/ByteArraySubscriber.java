package dcraft.util.net;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.log.Logger;

import java.io.*;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.zip.GZIPInputStream;

abstract public class ByteArraySubscriber<T> implements HttpResponse.BodySubscriber<T>, HttpResponse.BodyHandler<T> {
    protected final CompletableFuture<T> result = new MinimalFuture<>();
    protected final List<ByteBuffer> received = new ArrayList();
    protected volatile Flow.Subscription subscription = null;
    protected OperationContext context = null;
    protected HttpResponse.ResponseInfo responseInfo = null;

    public abstract T transform(byte[] bytes) throws OperatingContextException;

    public ByteArraySubscriber() throws OperatingContextException {
        this.context = OperationContext.getOrThrow();
    }

    public ByteArraySubscriber(OperationContext ctx) {
        this.context = ctx;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        if (this.subscription != null) {
            subscription.cancel();
        } else {
            this.subscription = subscription;
            subscription.request(9223372036854775807L);
        }
    }

    @Override
    public void onNext(List<ByteBuffer> items) {
        assert NetUtil.hasRemaining(items);

        this.received.addAll(items);
    }

    @Override
    public void onError(Throwable throwable) {
        this.received.clear();
        this.result.completeExceptionally(throwable);
    }

    private static byte[] join(List<ByteBuffer> bytes) {
        int size = NetUtil.remaining(bytes, 2147483647);
        byte[] res = new byte[size];
        int from = 0;

        int l;
        for(Iterator var4 = bytes.iterator(); var4.hasNext(); from += l) {
            ByteBuffer b = (ByteBuffer)var4.next();
            l = b.remaining();
            b.get(res, from, l);
        }

        return res;
    }

    @Override
    public void onComplete() {
        try {
            if (this.context != null) {
                OperationContext.set(this.context);
                this.context = null;
            }

            byte[] raw = this.join(this.received);

            String encoding = this.responseInfo.headers().firstValue("Content-Encoding").orElse("");

            if (encoding.equals("gzip")) {
                System.out.println("gzip compressed");

                try (InputStream is = new GZIPInputStream(new ByteArrayInputStream(raw)); ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                    is.transferTo(os);
                    raw = os.toByteArray();
                }
                catch (IOException x) {
                    Logger.error("Failed to read gzip response");
                }
            }

            this.result.complete(this.transform(raw));
            this.received.clear();
        }
        catch (IllegalArgumentException x) {
            this.result.completeExceptionally(x);
        }
        catch (OperatingContextException x) {
            System.out.println("missing context in ByteArraySubscriber: " + x);
            this.result.completeExceptionally(x);
        }
    }

    @Override
    public CompletionStage<T> getBody() {
        return this.result;
    }

    @Override
    public HttpResponse.BodySubscriber<T> apply(HttpResponse.ResponseInfo responseInfo) {
        this.responseInfo = responseInfo;

        return this;
    }
}
