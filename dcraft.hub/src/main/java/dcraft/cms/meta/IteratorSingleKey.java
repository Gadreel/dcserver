package dcraft.cms.meta;

import dcraft.db.DatabaseException;
import dcraft.db.fileindex.FileIndexAdapter;
import dcraft.db.proc.ExpressionResult;
import dcraft.db.util.ByteUtil;
import dcraft.filestore.CommonPath;
import dcraft.filevault.Vault;
import dcraft.hub.op.OperatingContextException;
import dcraft.hub.op.OperationContext;
import dcraft.hub.op.OperationOutcomeEmpty;
import dcraft.hub.op.OperationOutcomeStruct;
import dcraft.log.Logger;
import dcraft.struct.*;
import dcraft.struct.scalar.DateTimeStruct;
import dcraft.struct.scalar.StringStruct;
import dcraft.util.StringUtil;
import dcraft.util.TimeUtil;
import org.threeten.extra.PeriodDuration;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class IteratorSingleKey extends BasicCustomIterator {
    @Override
    public void search(OperationOutcomeEmpty callback) throws OperatingContextException {
        List<IndexKeyInfo> queue = new ArrayList<>();

        queue.add(this.first);

        List<Object> indexkeys = CustomIndexUtil.pathToIndex(index.getFieldAsString("Alias"));

        this.searchLevel(indexkeys, queue);

        callback.returnEmpty();
    }
}
