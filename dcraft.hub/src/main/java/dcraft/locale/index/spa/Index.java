package dcraft.locale.index.spa;

import dcraft.locale.IndexBase;
import dcraft.locale.IndexInfo;
import dcraft.locale.analyzer.EnglishFullAnalyzer;
import dcraft.locale.analyzer.EnglishSimpleAnalyzer;
import dcraft.locale.analyzer.SpanishFullAnalyzer;
import dcraft.locale.analyzer.SpanishSimpleAnalyzer;
import dcraft.log.Logger;
import dcraft.util.StringUtil;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class Index extends IndexBase {
	@Override
	public List<IndexInfo> full(String value) {
		List<IndexInfo> ret = new ArrayList<>();

		if (StringUtil.isEmpty(value))
			return ret;

		try (SpanishFullAnalyzer analyzer = new SpanishFullAnalyzer()) {
			try (TokenStream stream = analyzer.tokenStream(null, new StringReader(value.trim()))) {
				CharTermAttribute cattr = stream.addAttribute(CharTermAttribute.class);
				OffsetAttribute offsetAttribute = stream.addAttribute(OffsetAttribute.class);

				stream.reset();

				while (stream.incrementToken()) {
					IndexInfo info = new IndexInfo();
					info.token = cattr.toString();
					info.start = offsetAttribute.startOffset();
					info.end = offsetAttribute.endOffset();
					info.score = 1;

					ret.add(info);
				}

				stream.end();
			}
		}
		catch (Exception x) {
			Logger.error("error with full index analyzer: " + x);
		}

		return ret;
	}

	@Override
	public List<IndexInfo> simple(String value) {
		List<IndexInfo> ret = new ArrayList<>();

		if (StringUtil.isEmpty(value))
			return ret;

		try (SpanishSimpleAnalyzer analyzer = new SpanishSimpleAnalyzer()) {
			try (TokenStream stream = analyzer.tokenStream(null, new StringReader(value.trim()))) {
				CharTermAttribute cattr = stream.addAttribute(CharTermAttribute.class);
				OffsetAttribute offsetAttribute = stream.addAttribute(OffsetAttribute.class);

				stream.reset();

				while (stream.incrementToken()) {
					IndexInfo info = new IndexInfo();
					info.token = cattr.toString();
					info.start = offsetAttribute.startOffset();
					info.end = offsetAttribute.endOffset();
					info.score = 1;

					ret.add(info);
				}

				stream.end();
			}
		}
		catch (Exception x) {
			Logger.error("error with full index analyzer: " + x);
		}

		return ret;
	}
}
