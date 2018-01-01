/* ************************************************************************
#
#  designCraft.io
#
#  http://designcraft.io/
#
#  Copyright:
#    Copyright 2014 eTimeline, LLC. All rights reserved.
#
#  License:
#    See the license.txt file in the project's top-level directory for details.
#
#  Authors:
#    * Andy White
#
************************************************************************ */
package dcraft.util.cb;

import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.task.TaskContext;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class CountDownCallbackContextFree {
	protected AtomicInteger count = null;
	protected Consumer<Boolean> callback = null;
	protected ReentrantLock cdlock = new ReentrantLock();		// TODO try StampedLock ? or AtomicBoolean
	
	public CountDownCallbackContextFree(int count, Consumer<Boolean> callback) {
		this.count = new AtomicInteger(count);
		this.callback = callback;
	}
	
	public int countDown() {
		this.cdlock.lock();
		
		try {
			int res = this.count.decrementAndGet();
			
			if (res < 0)
				res = 0;
			
			if (res == 0) {
				if (this.callback != null)
					this.callback.accept(true);
			}
			
			return res;
		}
		finally {
			this.cdlock.unlock();
		}
	}
	
	public int increment() {
		return this.count.incrementAndGet();
	}
	
	public int increment(int amt) {
		return this.count.addAndGet(amt);
	}

	public int value() {
		return this.count.intValue();
	}
}
