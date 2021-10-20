package dcraft.interchange.authorize;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.function.AbstractFunction;
import dcraft.hub.op.OperatingContextException;
import dcraft.struct.RecordStruct;
import dcraft.struct.Struct;
import dcraft.util.php.PhpUtil;

import java.math.BigDecimal;

public class PhpRefundTransaction extends AbstractFunction {
    @Override
    public Value call(Env env, Value[] args) {
        if (args.length > 3) {
            String refid = args[0].toString();
            String txid = args[1].toString();
            BigDecimal amount = Struct.objectToDecimal(PhpUtil.valueToStruct(env, args[2]));
            RecordStruct txdetail = Struct.objectToRecord(PhpUtil.valueToStruct(env, args[3]));

            if (txid != null) {
                try {
                    return PhpUtil.structToValue(env, AuthUtil.refundTransactionSync(refid, txid, amount, txdetail, null));
                }
                catch (OperatingContextException x) {
                }
            }
        }

        return NullValue.NULL;
    }
}
