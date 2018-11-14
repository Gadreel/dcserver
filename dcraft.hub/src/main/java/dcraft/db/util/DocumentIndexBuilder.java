package dcraft.db.util;

import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.locale.IndexInfo;
import dcraft.locale.LocaleUtil;
import dcraft.struct.ListStruct;
import dcraft.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class DocumentIndexBuilder {
	static public DocumentIndexBuilder index()  {
		DocumentIndexBuilder builder = new DocumentIndexBuilder();

		try {
			builder.lang = LocaleUtil.getLocale(OperationContext.getOrThrow().getLocale()).getLanguage();
		}
		catch (OperatingContextException x) {
			// TODO
		}

		return builder;
	}

	protected List<IndexInfo> index = new ArrayList<>();
	protected StringBuilder title = null;
	protected boolean locktitle = false;
	protected StringBuilder summary = null;
	protected boolean locksummary = false;
	protected int scorelevel = 1;
	protected StringBuilder section = null;
	protected StringBuilder sectiontxt = null;
	protected List<StringBuilder> sections = new ArrayList<>();

	protected String lang = null;		// even if there is some content that is not in this language - use one language throughout the index
	protected ListStruct badges = null;		// default is Guest
	protected String sortHint = null;		// otherwise use title - this can be used in menus or search
	protected boolean denyIndex = false;

	public CharSequence getTitle() {
		return this.title;
	}

	public void setTitle(String v) {
		if (StringUtil.isEmpty(v))
			return;

		this.title = new StringBuilder(v);
		this.locktitle = true;

		this.withFragments(LocaleUtil.full(v, this.lang),10);
	}

	public String getLang() {
		return this.lang;
	}

	public CharSequence getSummary() {
		return this.summary;
	}

	public void setSummary(String v) {
		if (StringUtil.isEmpty(v))
			return;

		this.summary = new StringBuilder(v);
		this.locksummary = true;

		this.withFragments(LocaleUtil.full(v, this.lang),5);
	}

	public ListStruct getBadges() {
		return this.badges;
	}

	public void setBadges(ListStruct v) {
		this.badges = v;
	}

	public boolean isDenyIndex() {
		return this.denyIndex;
	}

	public void setDenyIndex(boolean v) {
		this.denyIndex = v;
	}

	public String getSortHint() {
		return this.sortHint;
	}

	public void setSortHint(String v) {
		this.sortHint = v;
	}

	public List<StringBuilder> getSections() {
		return this.sections;
	}

	public List<IndexInfo> getIndex() {
		return this.index;
	}

	public void startSection() {
		this.section = new StringBuilder();
		this.sectiontxt = new StringBuilder();

		this.sections.add(this.sectiontxt);
	}

	public void endSection() {
		if (this.section == null)
			return;

		this.withFragments(LocaleUtil.full(this.section.toString(), this.lang), 0);

		if (this.scorelevel > 2) {
			if (! this.locktitle) {
				this.locktitle = true;
				this.title = this.sectiontxt;
			}
		}
		else {
			if (! this.locksummary) {
				this.locksummary = true;
				this.summary = this.sectiontxt;
			}
		}

		this.section = null;
		this.sectiontxt = null;
	}

	public void adjustScore(int amt) {
		this.scorelevel += amt;
	}

	public DocumentIndexBuilder withSpecialText(CharSequence v) {
		if (this.section == null)
			this.startSection();

		this.sectiontxt.append(v);
		return this;
	}

	public DocumentIndexBuilder withText(CharSequence v) {
		if (this.section == null)
			this.startSection();

		this.section.append(v);
		this.sectiontxt.append(v);
		return this;
	}

	public DocumentIndexBuilder withChar(char v) {
		if (this.section == null)
			this.startSection();

		this.section.append(v);
		this.sectiontxt.append(v);
		return this;
	}

	public DocumentIndexBuilder withFragment(IndexInfo v) {
		this.index.add(v);

		return this;
	}

	public DocumentIndexBuilder withFragments(List<IndexInfo> list, int bonus) {
		for (IndexInfo info : list) {
			info.score = this.scorelevel + bonus;
			this.index.add(info);
		}

		return this;
	}
}
