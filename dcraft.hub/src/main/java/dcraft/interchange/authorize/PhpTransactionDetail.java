package dcraft.interchange.authorize;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.function.AbstractFunction;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.php.PhpUtil;

public class PhpTransactionDetail extends AbstractFunction {
    @Override
    public Value call(Env env, Value[] args) {
        if (args.length > 0) {
            String txid = args[0].toString();

            if (txid != null) {
                return PhpUtil.structToValue(env, AuthUtil.getTransactionDetailSync(txid, null));
            }
        }

        return NullValue.NULL;
    }
}
