package dcraft.util.php;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.StringValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.function.AbstractFunction;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.web.md.MarkdownUtil;
import dcraft.xml.XElement;

public class Markdown extends AbstractFunction {
    @Override
    public Value call(Env env, Value[] args) {
        try {
            if (args.length > 0) {
                String content = args[0].toString();

                // TODO consider second param for safe

                XElement root = MarkdownUtil.process(content, true);

                if (root != null) {
                    return StringValue.create(root.toPrettyString());
                }
            }
        }
        catch (OperatingContextException x) {
        }

        return NullValue.NULL;
    }
}
