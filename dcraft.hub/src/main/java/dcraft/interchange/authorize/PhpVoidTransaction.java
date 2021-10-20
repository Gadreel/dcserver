package dcraft.interchange.authorize;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.function.AbstractFunction;
import dcraft.util.php.PhpUtil;

public class PhpVoidTransaction extends AbstractFunction {
    @Override
    public Value call(Env env, Value[] args) {
        if (args.length > 1) {
            String refid = args[0].toString();
            String txid = args[1].toString();

            if (txid != null) {
                return PhpUtil.structToValue(env, AuthUtil.voidTransactionSync(refid, txid, null));
            }
        }

        return NullValue.NULL;
    }
}
