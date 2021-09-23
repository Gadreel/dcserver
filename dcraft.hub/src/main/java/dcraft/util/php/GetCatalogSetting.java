package dcraft.util.php;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.StringValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.function.AbstractFunction;
import dcraft.hub.app.ApplicationHub;
import dcraft.hub.op.OperatingContextException;
import dcraft.script.StackUtil;
import dcraft.struct.Struct;
import dcraft.task.IParentAwareWork;
import dcraft.util.StringUtil;
import dcraft.xml.XElement;

public class GetCatalogSetting extends AbstractFunction {
    @Override
    public Value call(Env env, Value []args)
    {
        if (args.length > 1) {
            String id = args[0].toString();

            XElement settings = ApplicationHub.getCatalogSettings(id);

            if (settings != null) {
                String attr = args[1].toString();

                return StringValue.create(settings.attr(attr));
            }
        }

        return NullValue.NULL;
    }
}
