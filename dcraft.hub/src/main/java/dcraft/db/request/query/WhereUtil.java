package dcraft.db.request.query;

import dcraft.util.StringUtil;

public class WhereUtil {
	
	static public void tryWhereStartsWith(WhereGroupExpression where, String field, String term) {
		String t = StringUtil.stripWhitespace(term);
		
		if (StringUtil.isNotEmpty(t))
			where.addWhere(WhereStartsWith.starts()
					.withField(field)
					.withValue(t)
			);
	}
	
	static public void tryWhereContains(WhereGroupExpression where, String field, String term) {
		String t = StringUtil.stripWhitespace(term);
		
		if (StringUtil.isNotEmpty(t))
			where.addWhere(WhereContains.contains()
					.withField(field)
					.withValue(t)
			);
	}
}
