package dcraft.chrono.time.db;

import dcraft.db.IRequestContext;
import dcraft.db.util.ByteUtil;
import dcraft.hub.op.IVariableAware;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.TimeUtil;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

public class ChronAdapter {
	static public ChronAdapter ofNow(IRequestContext request) {
		ChronAdapter adapter = new ChronAdapter();
		adapter.request = request;
		adapter.maxwhen = TimeUtil.now();
		return adapter;
	}
	
	static public ChronAdapter of(IRequestContext request, long minconfidence, long minsignificance, ZonedDateTime maxwhen) {
		ChronAdapter adapter = new ChronAdapter();
		adapter.request = request;
		adapter.minconfidence = minconfidence;
		adapter.minsignificance= minsignificance;
		adapter.maxwhen = (maxwhen != null) ? maxwhen : TimeUtil.now();
		return adapter;
	}
	
	protected IRequestContext request = null;
	protected ZonedDateTime maxwhen = null;		// for when the data was entered, not the time of the data occurred
	protected long minconfidence = ChronConstants.CHRON_CONFIDENCE_MEDIUM;
	protected long minsignificance = 40;
	protected Map<String, ChronDatasetView> datasets = new HashMap<>();
	
	// don't call for general code...
	protected ChronAdapter() {
	}
	
	public IRequestContext getRequest() {
		return this.request;
	}
	
	public ZonedDateTime getMaxWhen() {
		return this.maxwhen;
	}
	
	public long getMinConfidence() {
		return this.minconfidence;
	}
	
	public long getMinSignificance() {
		return this.minsignificance;
	}
	
	public ChronAdapter withDataset(ChronDatasetView v) {
		this.datasets.put(v.getId(), v);
		return this;
	}
	
	/*
		This function does not check to see if data is in the date range or if data meets signficance.
		
		Does check the datasets, max data enter when, and the confidence
		
		Returns the latest version in the allowed datasets and confidence - highest confidence first priority, latest second priority
		
	
			- Critique [
				- Id (uuid)
				- Stamp (of critique)
				- Author (uuid)
				- VersionId (uuid of version endorsed - confidence relates to)
				- Confidence: fact, strong, medium, speculative, fiction
				- Notes
				- ReferenceIds (uuid to a book or other source that is a recorded entity)
			]
	
	 */
	public RecordStruct getPropertyBestVersion(IVariableAware scope, String propid) throws OperatingContextException {
		ListStruct out = this.getPropertyVersions(scope, propid);

		if (out.size() > 0) {
			RecordStruct best = out.getItemAsRecord(0);
			long bestconfidence = best.selectAsInteger("Critique.Confidence");
			
			for (int i = 1; i < out.size(); i++) {
				RecordStruct version = out.getItemAsRecord(i);
				
				long confidence = version.selectAsInteger("Critique.Confidence");
				
				if (confidence > bestconfidence) {
					bestconfidence = confidence;
					best = version;
				}
			}
			
			return best;
		}
		
		return null;
	}
	
	/*
		This function does not check to see if data is in the date range or if data meets signficance.
		
		Does check the datasets, max data enter when, and the confidence
		
		Returns the matching versions in the allowed datasets and confidence - sorted by stamp
		
	
			- Critique [
				- Id (uuid)
				- Stamp (of critique)
				- Author (uuid)
				- VersionId (uuid of version endorsed - confidence relates to)
				- Confidence: fact, strong, medium, speculative, fiction
				- Notes
				- ReferenceIds (uuid to a book or other source that is a recorded entity)
			]
	
	 */
	public ListStruct getPropertyVersions(IVariableAware scope, String propid) throws OperatingContextException {
		ListStruct out = ListStruct.list();
		
		try {
			byte[] cid = request.getInterface().nextPeerKey(ChronConstants.CHRON_GLOBAL_PROPS, propid, "Crits", null);
			
			while (cid != null) {
				Object critid = ByteUtil.extractValue(cid);
				
				RecordStruct crit = Struct.objectToRecord(request.getInterface().get(ChronConstants.CHRON_GLOBAL_PROPS, propid, "Crits", critid));
				
				// make sure the critique meets the confidence level
				long cconfidence = crit.getFieldAsInteger("Confidence");
				
				if (cconfidence >= this.minconfidence) {
					// make sure the critique is in the selected datasets
					String cds = crit.getFieldAsString("Dataset");
					
					if (this.datasets.containsKey(cds)) {
						String verid = crit.getFieldAsString("VersionId");
						
						// make sure the property version is valid
						
						RecordStruct version = this.getPropertyVersion(scope, propid, verid);
						
						if (version != null) {
							out.with(version
									.with("Critique", crit)
							);
						}
					}
				}
				
				cid = request.getInterface().nextPeerKey(ChronConstants.CHRON_GLOBAL_PROPS, propid, "Crits", critid);
			}
			
			out.sortRecords("Stamp", true);
		}
		catch (Exception x) {
			Logger.error("get property error: " + x);
		}
		
		return out;
	}
	
	public RecordStruct getPropertyVersion(IVariableAware scope, String propid, String verid) throws OperatingContextException {
		try {
			RecordStruct version = Struct.objectToRecord(request.getInterface().get(ChronConstants.CHRON_GLOBAL_PROPS, propid, "Vers", verid));
			
			// make sure the version is valid
			long op = version.getFieldAsInteger("Op");
			
			if (op == ChronConstants.CHRON_VER_OP_ADMIT) {
				// make sure the version is in the selected datasets
				String vds = version.getFieldAsString("Dataset");
				
				if (this.datasets.containsKey(vds)) {
					// make sure the value is in the max data stamp allowed
					ZonedDateTime stamp = version.getFieldAsDateTime("Stamp");
					
					if (! this.maxwhen.isBefore(stamp)) {
						return version;
					}
				}
			}
		}
		catch (Exception x) {
			Logger.error("get property version error: " + x);
		}
		
		return null;
	}
	
}
