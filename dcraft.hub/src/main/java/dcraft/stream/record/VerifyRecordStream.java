package dcraft.stream.record;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationMarker;
import dcraft.log.Logger;
import dcraft.schema.DataType;
import dcraft.scriptold.StackEntry;
import dcraft.stream.ReturnOption;
import dcraft.struct.RecordStruct;
import dcraft.xml.XElement;

public class VerifyRecordStream extends TransformRecordStream {
	static public VerifyRecordStream forType(DataType type) {
		VerifyRecordStream hs = new VerifyRecordStream();
		hs.type = type;
		return hs;
	}
	
	protected DataType type = null;

	protected VerifyRecordStream() {
	}
	
	@Override
	public void init(StackEntry stack, XElement el) {
		/* TODO
		String algo = stack.stringFromElement(el, "Algorithm", "SHA-256");
		
		try {
			this.md = MessageDigest.getInstance(algo);
		} 
		catch (NoSuchAlgorithmException x) {
			Logger.error("Hash stream, bad algorithm:" + x);
		}
		*/
	}

	@Override
	public ReturnOption handle(RecordStruct slice) throws OperatingContextException {
		try (OperationMarker om = OperationMarker.create()) {
			RecordStruct nv = (RecordStruct) this.type.normalizeValidate(slice);
			
			if (! om.hasErrors()) {
				if (this.tabulator != null)
					this.tabulator.accept(nv);
				
				return this.consumer.handle(nv);
			}
		}
		catch (Exception x) {
			Logger.error("Error validating stream record: " + x);
		}
		
		OperationContext.getAsTaskOrThrow().kill("Invalid record in stream!");
		return ReturnOption.DONE;
	}
}
