package dcraft.web.ui.inst.feed;

import dcraft.hub.op.OperatingContextException;

import dcraft.cms.feed.core.FeedAdapter;
import dcraft.struct.RecordStruct;
import dcraft.script.inst.doc.Base;
import dcraft.xml.XElement;

public class FeedParams extends Base {
	static public FeedParams tag() {
		FeedParams el = new FeedParams();
		el.setName("dcm.FeedParams");
		return el;
	}
	
	//protected RecordStruct feeddata = null;
	protected FeedAdapter adapter = null;
	
	@Override
	public XElement newNode() {
		return FeedParams.tag();
	}
	
	public void setFeedData(RecordStruct v) throws OperatingContextException {
		// TODO review
		//this.adapter = FeedAdapter.from(v);
	}
	
	public void setFeedData(FeedAdapter v) {
		this.adapter = v;
	}
	
	public FeedParams() {
		this.exclude = true;
	}
	
	// TODO something with this
	public String getParam(String name) throws OperatingContextException {
		/*
		if (name.startsWith("Field|")) {
			String[] pieces = name.split("\\|");
			
			String v = this.adapter.getField(pieces[1]);
			
			v = (v == null) ? "" : v;
			
			if (pieces.length > 2) {
				String fmt = pieces[2];
				
				if ("dtfmt".equals(fmt)) {
					if (pieces.length > 3) {
						ZonedDateTime dtv = Struct.objectToDateTime(v);
						
						//System.out.println("zone: " + OperationContext.get().getWorkingChronologyDefinition());
						
						String pattern = pieces[3].replaceAll("\\\\s", " ");
						DateTimeFormatter dtfmt = DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.of(OperationContext.getOrThrow().getChronology()));
						
						v = dtfmt.format(dtv);
					}
				}
			}
			
			return v;
		}
		
		if (name.startsWith("Part|")) {
			String v= this.adapter.getPart(name.substring(5), null);		// TODO someday support preview
			return (v == null) ? "" : v;
		}
		
		if (name.equals("Path")) {
			String v= this.adapter.getPath();
			return (v == null) ? "" : v;
		}
		
		if (name.equals("Tags")) {
			String v= this.adapter.getTags();
			return (v == null) ? "" : v;
		}
		*/
		
		return null;
	}
}
