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

import java.util.concurrent.ConcurrentLinkedQueue;

import dcraft.hub.op.OperationOutcome;
import dcraft.log.Logger;
import dcraft.util.IOUtil;

public class CallbackQueue<T> {
	protected ConcurrentLinkedQueue<T> resources = new ConcurrentLinkedQueue<T>();	
	protected ConcurrentLinkedQueue<OperationOutcome<T>> callbacks = new ConcurrentLinkedQueue<OperationOutcome<T>>();
	protected volatile boolean disposed = false;
	protected QueueWatcher qwatcher = null;

	public void setWatcher(QueueWatcher watcher) {
		this.qwatcher = watcher;
	}
	
	public void pop(OperationOutcome<T> callback) {
		if (this.disposed) {
			Logger.errorTr(1, "Disposed");		
			callback.returnValue(callback.getResult());
			return;
		}
		
		T resource = this.resources.poll();
		
		if (resource != null) {
			callback.setResult(resource);
			callback.returnValue(callback.getResult());
			return;
		}
		
		this.callbacks.add(callback);
	}
	
	public void add(T resource) {
		if (this.disposed) {
			if (this.qwatcher != null)
				this.qwatcher.disposed(resource);
			
			return;
		}
		
		OperationOutcome<T> callback = this.callbacks.poll();
		
		if (callback != null) {
			callback.returnValue(resource);
			return;
		}
		
		this.resources.add(resource);
	}
	
	// tell the callbacks to go away, nothing more to do
	// and grab all the available resources 
	public void dispose() {
		this.disposed = true;
		
		OperationOutcome<T> callback = this.callbacks.poll();
		
		while (callback != null) {
			Logger.errorTr(1, "Nothing more to do");		
			callback.returnValue(callback.getResult());
			
			IOUtil.closeQuietly(callback);
			
			callback = this.callbacks.poll();
		}		
		
		if (this.qwatcher != null) {
			T res = this.resources.poll();
			
			while (res != null) {
				this.qwatcher.disposed(res);
				res = this.resources.poll();
			}		
		}
	}
	
	abstract public class QueueWatcher {
		abstract public void disposed(T res);
	}
}
