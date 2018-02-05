package dcraft.db.proc.call;

import dcraft.db.DatabaseAdapter;
import dcraft.db.proc.IStoredProc;
import dcraft.db.ICallContext;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationMarker;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.struct.builder.ICompositeBuilder;
import dcraft.struct.builder.ObjectBuilder;
import dcraft.util.HexUtil;

public class KeyQuery implements IStoredProc {
	@Override
	public void execute(ICallContext request, OperationOutcomeStruct callback) throws OperatingContextException {
		RecordStruct params = request.getDataAsRecord();

		ListStruct keys = params.getFieldAsList("Keys");
		boolean explode = params.getFieldAsBooleanOrFalse("Explode");
		
		byte[] basekey = null;
		
		for (Struct ss : keys.items()) 
			basekey =  ByteUtil.combineKeys(basekey, HexUtil.decodeHex(ss.toString())); 
		
		//ByteUtil.buildKey(keys.toObjectList().toArray());
		ICompositeBuilder out = new ObjectBuilder();
		
		try (OperationMarker om = OperationMarker.create()) {
			this.listChildren(request.getInterface(), out, basekey, explode);
			
			if (! om.hasErrors()) {
				callback.returnValue(out.toLocal());
				return;
			}
		}
		catch (Exception x) {
			Logger.error("KeyQueryProc: Unable to create list: " + x);
		}
		
		callback.returnEmpty();
	}
	
	public void listChildren(DatabaseAdapter adapter, ICompositeBuilder resp, byte[] basekey, boolean explode) {
		try {
			resp.startList();
			
			// null = start of list			
			byte[] subid = adapter.nextPeerKey(basekey, null);
			
			while (subid != null) {
				Object skey = ByteUtil.extractValue(subid);
				
				if (skey == null)
					break;
				
				byte[] ckey = ByteUtil.combineKeys(basekey, subid);
				
				resp.startRecord();
				
				resp.field("Key", HexUtil.bufferToHex(subid));
				resp.field("DisplayKey", skey);
				
				//System.out.println("- " + ByteUtil.extractValue(subid));
				
				if (adapter.isSet(ckey)) {
					byte[] v = adapter.get(ckey);
					resp.field("Value", HexUtil.bufferToHex(v));
					resp.field("DisplayValue", ByteUtil.extractValue(adapter.get(ckey)));
				}
				
				if (explode) {
					resp.field("Children");
					this.listChildren(adapter, resp, ckey, explode);
				}
				
				resp.endRecord();

				subid = adapter.nextPeerKey(basekey, subid);
			}
			
			/*
			<Record Id="dcDollarOItem">
				<Field Name="Key" Type="Any" Required="True" />
				<Field Name="Value" Type="Any" />
				<Field Name="Children">
					<List Type="dcDollarOItem" />
				</Field>
			</Record>
		 * 
			 */
			
			//System.out.println("Ending key list");
			
			resp.endList();
		}
		catch (Exception x) {
			Logger.error("KeyQueryProc: Unable to create list: " + x);
		}		
	}
}
