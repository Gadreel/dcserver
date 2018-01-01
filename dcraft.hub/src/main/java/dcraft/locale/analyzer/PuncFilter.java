package dcraft.locale.analyzer;

import org.apache.lucene.analysis.FilteringTokenFilter;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import java.io.IOException;

public class PuncFilter extends TokenFilter {
	private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

	public PuncFilter(TokenStream in) {
		super(in);
	}

	@Override
	public boolean incrementToken() throws IOException {
		if (input.incrementToken()) {
			StringBuilder sb = new StringBuilder();

			// If no characters actually require rewriting then we
			// just return token as-is:
			for (int i = 0 ; i < termAtt.length(); i++) {
				char c = termAtt.buffer()[i];

				if (Character.isLetterOrDigit(c)) {
					sb.append(c);
				}
			}

			termAtt.setLength(0);
			termAtt.append(sb);

			return true;
		}

		return false;
	}
}
