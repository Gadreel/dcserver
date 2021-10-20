package dcraft.interchange.authorize;

import com.caucho.quercus.env.DoubleValue;
import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.function.AbstractFunction;
import dcraft.hub.op.OperatingContextException;
import dcraft.util.php.PhpUtil;

public class PhpCancelFullTransaction extends AbstractFunction {
    @Override
    public Value call(Env env, Value[] args) {
        if (args.length > 1) {
            String refid = args[0].toString();
            String txid = args[1].toString();

            if (txid != null) {
                try {
                    return DoubleValue.create(AuthUtil.cancelFullTransactionSync(refid, txid, null));
                }
                catch (OperatingContextException x) {
                }
            }
        }

        return NullValue.NULL;
    }
}
