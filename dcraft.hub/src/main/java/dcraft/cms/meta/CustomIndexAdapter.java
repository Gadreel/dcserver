package dcraft.cms.meta;

import dcraft.db.DatabaseException;
import dcraft.db.IRequestContext;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.filevault.Vault;
import dcraft.hub.op.OperatingContextException;
import dcraft.log.Logger;
import dcraft.tenant.Site;

import java.util.ArrayList;
import java.util.List;

public class CustomIndexAdapter {
    static public CustomIndexAdapter of(IRequestContext request, String indexName) {
        CustomIndexAdapter adapter = new CustomIndexAdapter();
        adapter.request = request;
        adapter.indexName = indexName;
        return adapter;
    }

    protected IRequestContext request = null;
    protected String indexName = null;

    public IRequestContext getRequest() {
        return this.request;
    }

    public void clearCustomIndex() throws OperatingContextException {
        try {
            List<Object> indexkeys = CustomIndexUtil.pathToIndex(this.indexName);

            this.request.getInterface().kill(indexkeys.toArray());
        }
        catch (DatabaseException x) {
            Logger.error("Unable to clea custom index in db: " + x);
        }
    }

}
