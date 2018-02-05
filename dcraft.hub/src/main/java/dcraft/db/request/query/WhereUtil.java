package dcraft.db.request.query;

import dcraft.util.StringUtil;

public class WhereUtil {
	
	static public void tryWhereStartsWith(WhereGroupExpression where, String field, String term) {
		String t = StringUtil.stripWhitespace(term);
		
		if (StringUtil.isNotEmpty(t))
			where.withExpression(WhereStartsWith.of(field, term));
	}
	
	static public void tryWhereContains(WhereGroupExpression where, String field, String term) {
		String t = StringUtil.stripWhitespace(term);
		
		if (StringUtil.isNotEmpty(t))
			where.withExpression(WhereContains.of(field, t));
	}
}
