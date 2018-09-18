package dcraft.db.request.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import dcraft.db.request.DataRequest;
import dcraft.struct.CompositeStruct;
import dcraft.struct.RecordStruct;
import dcraft.util.StringUtil;
import dcraft.util.stem.IndexInfo;
import dcraft.util.stem.IndexUtility;
import dcraft.util.stem.IndexInfo.StemEntry;

import java.util.Set;

public class FullTextSearchRequest extends DataRequest {
	// table,field
	protected Map<String,SourceInfo> sources = new HashMap<String, SourceInfo>();
	
	protected List<PhraseInfo> required = new ArrayList<PhraseInfo>(); 
	protected List<PhraseInfo> allowed = new ArrayList<PhraseInfo>(); 
	protected List<PhraseInfo> prohibited = new ArrayList<PhraseInfo>(); 
	
	/**
	 */
	public FullTextSearchRequest() {
		super("dcSearchText");
	}

	public void addSource(String table, String title, String body, String... extras) {
		SourceInfo si = new SourceInfo(title, body);
		
		for (String field : extras)
			si.addExtra(field);
		
		this.sources.put(table, si);
	}

	public void addRequired(String phrase) {
		this.required.add(new PhraseInfo(phrase, false));
	}

	public void addRequired(String phrase, boolean exact) {
		this.required.add(new PhraseInfo(phrase, exact));
	}

	public void addAllowed(String phrase) {
		this.allowed.add(new PhraseInfo(phrase, false));
	}

	public void addAllowed(String phrase, boolean exact) {
		this.allowed.add(new PhraseInfo(phrase, exact));
	}

	public void addProhibited(String phrase) {
		this.prohibited.add(new PhraseInfo(phrase, false));
	}

	public void addProhibited(String phrase, boolean exact) {
		this.prohibited.add(new PhraseInfo(phrase, exact));
	}

	public void filterField(String table, String field, String sid) {
		SourceInfo si = this.sources.get(table);
		
		if (si == null)
			return;
		
		si.addFilter(field, sid);
	}
	
	@Override
	public RecordStruct buildParams() {
		if (this.sources.size() > 0) {
			RecordStruct stables = new RecordStruct();
			RecordStruct ftables = new RecordStruct();
			
			for (String table : this.sources.keySet()) {
				SourceInfo sinfo = this.sources.get(table);
				
				RecordStruct sects = new RecordStruct();
				stables.with(table, sects);

				if (StringUtil.isNotEmpty(sinfo.title))
					sects.with("Title", sinfo.title);
				
				if (StringUtil.isNotEmpty(sinfo.body))
					sects.with("Body", sinfo.body);

				if (sinfo.extras.size() > 0) {
					RecordStruct extras = new RecordStruct();
				
					sects.with("Extras", extras);
					
					for (String extra : sinfo.extras)
						extras.with(extra, 1);
				}
				
				RecordStruct filters = new RecordStruct();
				ftables.with(table, filters);
				
				for (String fld : sinfo.filter.keySet()) {					
					RecordStruct sids = new RecordStruct();
					filters.with(fld, sids);
					
					Set<String> slist = sinfo.filter.get(fld);
					
					for (String sid : slist)
						sids.with(sid, 1);					
				}
			}
			
			this.parameters.with("Sources", stables);
			this.parameters.with("AllowedSids", ftables);
		}
		
		if (this.required.size() > 0) {
			RecordStruct words = new RecordStruct();
			
			for (PhraseInfo phrase : this.required) {
				boolean eonce = true;
				
				for (Entry<String, StemEntry> stem : phrase.info.entries.entrySet()) {
					RecordStruct sects = new RecordStruct();
					sects.with("Term", 1);
					
					if (eonce && StringUtil.isNotEmpty(phrase.exact)) {
						sects.with("Exact", phrase.exact);
						eonce = false;
					}

					String term = stem.getKey();
					
					words.with(term, sects);
				}
			}
			
			this.parameters.with("RequiredWords", words);
		}
		
		if (this.allowed.size() > 0) {
			RecordStruct words = new RecordStruct();
			
			for (PhraseInfo phrase : this.allowed) {
				boolean eonce = true;
				
				for (Entry<String, StemEntry> stem : phrase.info.entries.entrySet()) {
					RecordStruct sects = new RecordStruct();
					sects.with("Term", 1);
					
					if (eonce && StringUtil.isNotEmpty(phrase.exact)) {
						sects.with("Exact", phrase.exact);
						eonce = false;
					}

					String term = stem.getKey();
					
					words.with(term, sects);
				}
			}
			
			this.parameters.with("AllowedWords", words);
		}
		
		if (this.prohibited.size() > 0) {
			RecordStruct words = new RecordStruct();
			
			for (PhraseInfo phrase : this.prohibited) {
				boolean eonce = true;
				
				for (Entry<String, StemEntry> stem : phrase.info.entries.entrySet()) {
					RecordStruct sects = new RecordStruct();
					sects.with("Term", 1);
					
					if (eonce && StringUtil.isNotEmpty(phrase.exact)) {
						sects.with("Exact", phrase.exact);
						eonce = false;
					}

					String term = stem.getKey();
					
					words.with(term, sects);
				}
			}
			
			this.parameters.with("ProhibitedWords", words);
		}
		
		return super.buildParams();
	}
	
	public class PhraseInfo {
		public String exact = null;
		public IndexInfo info = null;
		
		public PhraseInfo(String phrase, boolean exact) {
			if (exact)
				this.exact = phrase;
			
			this.info = IndexUtility.stemEnglishPhrase(phrase, 0);
		}
	}
	
	public class SourceInfo {
		protected String title = null;
		protected String body = null;
		protected Set<String> extras = new HashSet<String>();
		protected Map<String, Set<String>> filter = new HashMap<String, Set<String>>();

		public SourceInfo(String title, String body) {
			this.title = title;
			this.body = body;
		}
		
		public void addFilter(String field, String sid) {
			Set<String> f = this.filter.get(field);
			
			if (f == null) {
				f = new HashSet<String>();
				this.filter.put(field, f);
			}
			
			f.add(sid);
		}

		public void addExtra(String field) {
			this.extras.add(field);
		}
	}
}
