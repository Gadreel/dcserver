package dcraft.example.service;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.service.BaseService;
import dcraft.service.ServiceRequest;
import dcraft.stream.*;
import dcraft.stream.record.RecordDumpStream;
import dcraft.stream.record.SyncRecordStreamSupplier;
import dcraft.stream.record.TransformRecordStream;
import dcraft.struct.RecordStruct;
import dcraft.task.Task;
import dcraft.task.TaskContext;
import dcraft.task.TaskHub;
import dcraft.task.TaskObserver;
import dcraft.util.chars.PigLatin;

/*
 * 
 */
public class ChefDataStreamService extends BaseService {
	@Override
	public boolean handle(ServiceRequest request, OperationOutcomeStruct listoutcome) throws OperatingContextException {
		String chef = request.getData().toString();

		if ("Lookup".equals(request.getOp())) {
			StreamFragment sd = request.getResponseStream();
			
			IStreamUp ss = new SyncRecordStreamSupplier() {
				protected int curr = 0;
				
				@Override
				public RecordStruct get() {
					if (this.curr == 0) {
						this.curr ++;
						
						return RecordStruct.record()
							.with("Dish", "Mac and Cheese")
					     	.with("Judge", "Janet")
					     	.with("Rating", 7.0);
					}
					
					if (this.curr == 1) {
						this.curr ++;
						
						return RecordStruct.record()
							.with("Dish", "Cheesy Potatoes")
					     	.with("Judge", "Ernie")
					     	.with("Rating", 4.5);
					}
				
					return null;
				}
			};
			
			StreamWork strm = StreamWork.of(ss)
					.with(sd);
			
			Task task = Task
					.ofSubtask("Chef ratings streamer for: " + chef, "RateStream")
					.withWork(strm);
			
			TaskHub.submit(task, new TaskObserver() {
				@Override
				public void callback(TaskContext subtask) {
					// indicate that we are done
					listoutcome.returnEmpty();
				}
			});		
	
			// true means we are able to process the request (even if the data was not found)
			return true;
		}

		if ("Store".equals(request.getOp())) {
			StreamFragment ss = request.getRequestStream();
			
			IStreamDown<RecordStruct> sd = new RecordDumpStream();
			
			StreamWork strm = StreamWork.of(ss)
					.with(sd);
			
			Task task = Task
					.ofSubtask("Chef ratings streamer for: " + chef, "RateStream")
					.withWork(strm);
			
			TaskHub.submit(task, new TaskObserver() {
				@Override
				public void callback(TaskContext subtask) {
					// indicate that we are done
					listoutcome.returnEmpty();
				}
			});		
	
			// true means we are able to process the request (even if the data was not found)
			return true;
		}

		if ("Transform".equals(request.getOp())) {
			StreamFragment ss = request.getRequestStream();
			StreamFragment sd = request.getResponseStream();
			
			RatingTransformer ts = new RatingTransformer();
			
			StreamWork strm = StreamWork.of(ss)
					.with(ts)
					.with(sd);
			
			Task task = Task
					.ofSubtask("Chef ratings streamer for: " + chef, "RateStream")
					.withWork(strm);
			
			TaskHub.submit(task, new TaskObserver() {
				@Override
				public void callback(TaskContext subtask) {
					// indicate that we are done
					listoutcome.returnEmpty();
				}
			});		
	
			// true means we are able to process the request (even if the data was not found)
			return true;
		}
		
		return false;
	}
	
	public class RatingTransformer extends TransformRecordStream {
		@Override
		public ReturnOption handle(RecordStruct slice) throws OperatingContextException {
			if (slice != null)
				slice.with("Dish", PigLatin.translate(slice.getFieldAsString("Dish")));
			
			return this.consumer.handle(slice);
		}
	}
}