package dcraft.interchange.authorize;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.function.AbstractFunction;
import dcraft.util.StringUtil;
import dcraft.util.php.PhpUtil;

public class PhpGetSettledBatches extends AbstractFunction {
    @Override
    public Value call(Env env, Value[] args) {
        if (args.length > 1) {
            String first = args[0].toString();
            String last = args[1].toString();

            if (StringUtil.isNotEmpty(first) && StringUtil.isNotEmpty(last)) {
                return PhpUtil.structToValue(env, AuthUtil.getSettledBatchesSync(first, last, null));
            }
        }

        return NullValue.NULL;
    }
}
