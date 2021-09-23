package dcraft.util.php;

import com.caucho.quercus.env.*;
import com.caucho.quercus.function.AbstractFunction;
import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.struct.Struct;
import dcraft.struct.scalar.NullStruct;
import dcraft.task.IParentAwareWork;

public class ExportVarFunc extends AbstractFunction {
    protected IParentAwareWork context = null;

    public ExportVarFunc(IParentAwareWork context){
        this.context = context;
    }

    @Override
    public Value call(Env env, Value []args)
    {
        if (args.length > 1) {
            Struct var = PhpUtil.valueToStruct(env, args[0]);

            if (args[1] instanceof StringValue) {
                String in = ((StringValue)args[1]).toString();

                try {
                    StackUtil.addVariable(this.context, in, var);
                }
                catch (OperatingContextException x) {
                    System.out.println("Missing context: " + x);
                }
            }
        }

        return NullValue.NULL;
    }
}
