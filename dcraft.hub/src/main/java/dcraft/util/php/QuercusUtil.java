package dcraft.util.php;

import com.caucho.quercus.lib.db.JdbcDriverContext;
import com.caucho.vfs.Path;
import com.caucho.vfs.ReadStream;
import dcraft.interchange.authorize.*;
import dcraft.quercus.DCQuercusEngine;

import java.io.IOException;

public class QuercusUtil {
    static protected DCQuercusEngine singleton = null;

    static public DCQuercusEngine getEngine() {
        // it doesn't matter if there is a race, not worth the overhead of locking
        if (singleton == null) {
            DCQuercusEngine engine = new DCQuercusEngine();
            engine.setIni("unicode.semantics", "true");
            engine.init();

            JdbcDriverContext ctx = engine.getQuercus().getJdbcDriverContext();

            ctx.setProtocol("mysql", "org.mariadb.jdbc.MariaDbPoolDataSource");
            ctx.setDefaultDriver("org.mariadb.jdbc.MariaDbPoolDataSource");

            // why doesn't this work?
            //engine.getQuercus().getModuleContext().addModule(TWSModule.class.getName(), TWSModule.singleton);

            engine
                    .withFunc("dc_logger_info", new DCLoggerInfo())
                    .withFunc("dc_logger_warn", new DCLoggerWarn())
                    .withFunc("dc_logger_error", new DCLoggerError())
                    .withFunc("dc_touch", new DCTouch())
                    .withFunc("dc_alert", new DCAlert())
                    .withFunc("dc_context", new GetContext())
                    .withFunc("dc_has_badge", new HasBadge())
                    .withFunc("dc_remote_post", new RemotePost())
                    .withFunc("dc_remote_get", new RemotePost())
                    .withFunc("dc_markdown", new Markdown())
                    .withFunc("dci_auth_auth_capture", new PhpAuthCaptureFunction())
                    .withFunc("dci_auth_tx_detail", new PhpTransactionDetail())
                    .withFunc("dci_auth_tx_void", new PhpVoidTransaction())
                    .withFunc("dci_auth_tx_refund", new PhpRefundTransaction())
                    .withFunc("dci_auth_tx_cancel_full", new PhpCancelFullTransaction())
                    .withFunc("dci_auth_tx_cancel_partial", new PhpCancelPartialTransaction())
                    .withFunc("dci_auth_get_settled_batches", new PhpGetSettledBatches())
                    .withFunc("dci_auth_get_transaction_list", new PhpGetTransactionList())
                    .withFunc("dc_get_catalog_settings", new GetCatalogSetting());

            singleton = engine;
        }

        return singleton;
    }

    static public ReadStream fromPath(java.nio.file.Path path) throws IOException {
        Path qpath = new com.caucho.vfs.FilePath(path.toString());

        return qpath.openRead();
    }
}
