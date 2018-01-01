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
package dcraft.scriptold.inst;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.schema.SchemaHub;
import dcraft.scriptold.BlockInstruction;
import dcraft.scriptold.ExecuteState;
import dcraft.scriptold.StackBlockEntry;
import dcraft.scriptold.StackEntry;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.scalar.AnyStruct;

public class ForEach extends BlockInstruction {
    @Override
    public void alignInstruction(StackEntry stack, OperationOutcomeEmpty callback) throws OperatingContextException {
    	final StackBlockEntry bstack = (StackBlockEntry)stack;
    	
    	// signal end if conditional logic fails after loop
    	if (bstack.getPosition() >= this.instructions.size()) { 
    		RecordStruct store = stack.getStore();
    	    
    		AnyStruct collection = (AnyStruct) store.getField("Collection");
    	    final String name = store.getFieldAsString("Name");
        	
			/* TODO need to fix how FOR EACH collection works - stream
			final IAsyncIterator<Struct> it = (IAsyncIterator<Struct>) collection.getValue();
        	
        	if (it != null) {
        		it.hasNext(new OperationOutcome<Boolean>() {					
					@Override
					public void callback(Boolean res) throws OperatingContextException {
						if (res) {
							it.next(new OperationOutcome<Struct>() {								
								@Override
								public void callback(Struct res2) throws OperatingContextException {
					        		bstack.addVariable(name, res2);
					        		bstack.setPosition(0);
					        		
						        	ForEach.super.alignInstruction(stack, callback);
								}
							});
						}
						else {
				        	stack.setState(ExecuteState.Done);
				    	
				        	ForEach.super.alignInstruction(stack, callback);
						}
					}
				}); 

        		return;
        	}
        	else
        	*/
	        	stack.setState(ExecuteState.Done);
    	}
    	
       	super.alignInstruction(stack, callback);
    }
    
    @Override
    public void run(StackEntry stack) throws OperatingContextException {
		if (stack.getState() == ExecuteState.Ready) {
			String name = stack.stringFromSource("Name");  
			Struct source = stack.refFromSource("In");

			AnyStruct collection = AnyStruct.of(null);
			collection.withType(SchemaHub.getTypeOrError("Any"));		// TODO shouldn't need this
			
			/* TODO need to fix how FOR EACH collection works - stream
			if (source instanceof IItemCollection)
				collection.setValue(((IItemCollection)source).getItemsAsync().iterator());
			*/
			
    		RecordStruct store = stack.getStore();
    	    
    		store.with("Collection", collection);
    	    store.with("Name", name);
    	    
    	    // tell alignment to do first iteration by passing position beyond end
        	StackBlockEntry bstack = (StackBlockEntry)stack;
        	bstack.setPosition(this.instructions.size());
		}
		
		super.run(stack);
	}
}
