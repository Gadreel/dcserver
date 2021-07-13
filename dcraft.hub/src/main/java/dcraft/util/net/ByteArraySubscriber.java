package dcraft.util.net;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

abstract public class ByteArraySubscriber<T> implements HttpResponse.BodySubscriber<T>, HttpResponse.BodyHandler<T> {
    protected final CompletableFuture<T> result = new MinimalFuture<>();
    protected final List<ByteBuffer> received = new ArrayList();
    protected volatile Flow.Subscription subscription = null;
    protected OperationContext context = null;

    public abstract T transform(byte[] bytes) throws OperatingContextException;

    public ByteArraySubscriber() throws OperatingContextException {
        this.context = OperationContext.getOrThrow();
    }

    public ByteArraySubscriber(OperationContext ctx) {
        this.context = ctx;
    }

    public void onSubscribe(Flow.Subscription subscription) {
        if (this.subscription != null) {
            subscription.cancel();
        } else {
            this.subscription = subscription;
            subscription.request(9223372036854775807L);
        }
    }

    public void onNext(List<ByteBuffer> items) {
        assert NetUtil.hasRemaining(items);

        this.received.addAll(items);
    }

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

    public void onComplete() {
        try {
            if (this.context != null) {
                OperationContext.set(this.context);
                this.context = null;
            }

            this.result.complete(this.transform(this.join(this.received)));
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

    public CompletionStage<T> getBody() {
        return this.result;
    }

    @Override
    public HttpResponse.BodySubscriber<T> apply(HttpResponse.ResponseInfo responseInfo) {
        return this;
    }
}
