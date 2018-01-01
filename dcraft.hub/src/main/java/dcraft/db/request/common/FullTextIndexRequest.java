package dcraft.db.request.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import dcraft.db.request.DataRequest;
import dcraft.struct.CompositeStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;
import dcraft.util.stem.IndexInfo;
import dcraft.util.stem.IndexUtility;
import dcraft.util.stem.IndexInfo.StemEntry;
import dcraft.xml.XElement;
import dcraft.xml.XNode;
import dcraft.xml.XText;

public class FullTextIndexRequest extends DataRequest {
	// field,sid,copy
	protected Map<String,Map<String,FieldIndexInfo>> fields = new HashMap<String, Map<String,FieldIndexInfo>>();
			
	/**
	 * @param table name 
	 * @param id of record
	 */
	public FullTextIndexRequest(String table, String id) {
		super("dcUpdateText");
		
		this.parameters
			.with("Table", table)
			.with("Id", id);
	}

	public FieldIndexInfo index(String field) {
		return this.index(field, "1");
	}

	public FieldIndexInfo index(String field, String sid) {
		Map<String, FieldIndexInfo> sids = this.fields.get(field);
		
		if (sids == null) {
			sids = new HashMap<String, FieldIndexInfo>();
			this.fields.put(field, sids);
		}
		
		FieldIndexInfo info = sids.get(sid);
		
		if (info == null) {
			info = new FieldIndexInfo();
			sids.put(sid, info);
		}
		
		return info;
	}
	
	public void quickIndex(String field, int bonus, String content) {
		this.quickIndex(field, bonus, "1", content);
	}
	
	public void quickIndex(String field, int bonus, String sid, String content) {
		FieldIndexInfo f = this.index(field, sid);
		
		f.add(bonus, content);
	}
	
	@Override
	public CompositeStruct buildParams() {
		if (this.fields.size() > 0) {
			RecordStruct pfields = new RecordStruct();
			
			for (String field : this.fields.keySet()) {
				Map<String, FieldIndexInfo> sids = this.fields.get(field);
				
				RecordStruct psids = new RecordStruct();
				pfields.with(field, psids);
				
				for (String sid : sids.keySet()) {
					RecordStruct sects = new RecordStruct();
					psids.with(sid, sects);
					
					StringStruct org = StringStruct.ofEmpty();
					ListStruct anal = new ListStruct();
					
					sects.with("Original", org);
					sects.with("Analyzed", anal);		// TODO this has changed from M, review

					FieldIndexInfo info = sids.get(sid);
					
					for (Entry<String, StemEntry> stem : info.info.entries.entrySet()) {
						StemEntry e = stem.getValue();

						int score = e.computeScore(); 
						String poslist = StringUtil.join(e.positions.toArray(new String[0]), ",");
						
						String c = "|" + stem.getKey() + ":" + score + ":" + poslist;
						
						anal.withItem(c);
					}
					
					// this assumes that content has large "words" stripped, see Index Utility
					String otext = info.info.content.toString();
					
					org.setValue(otext);
				}
			}
			
			this.parameters.with("Fields", pfields);
		}
		
		return super.buildParams();
	}
	
	public class FieldIndexInfo {
		protected IndexInfo info = new IndexInfo();

		public void add(int score, String content) {
			IndexUtility.stemEnglishPhraseAppend(content, score, this.info);
		}

		public void add(Map<String, Integer> bonuses, XElement html) {
			if (html != null)
				this.addHtml(bonuses, html, 1);
		}
		
		protected void addHtml(Map<String, Integer> bonuses, XElement html, int scorecontext) {
			String tag = html.getName();
			
			if ((bonuses != null) && bonuses.containsKey(tag))
				scorecontext = bonuses.get(tag);
			
			for (XNode child : html.getChildren()) {
				if (child instanceof XElement)
					this.addHtml(bonuses, (XElement)child, scorecontext);
				else if (child instanceof XText)
					IndexUtility.stemEnglishPhraseAppend(StringUtil.stripWhitespace(((XText)child).getValue()), scorecontext, this.info);
			}
		}
	}
}
