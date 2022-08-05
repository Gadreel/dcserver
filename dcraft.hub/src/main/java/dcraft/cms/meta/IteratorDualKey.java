package dcraft.cms.meta;

import dcraft.db.DatabaseException;
import dcraft.filestore.CommonPath;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.struct.BaseStruct;
import dcraft.struct.ListStruct;
import dcraft.struct.RecordStruct;
import dcraft.struct.ScalarStruct;
import dcraft.util.StringUtil;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class IteratorDualKey extends BasicCustomIterator {
    @Override
    public void search(OperationOutcomeEmpty callback) throws OperatingContextException {
        List<IndexKeyInfo> queue = new ArrayList<>();

        queue.add(this.first);
        queue.add(this.second);

        List<Object> indexkeys = CustomIndexUtil.pathToIndex(index.getFieldAsString("Alias"));

        System.out.println("Search DK a");

        this.searchLevel(indexkeys, queue);

        callback.returnEmpty();
    }
}
