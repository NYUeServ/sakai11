package edu.nyu.classes.seats.storage.migrations;

import edu.nyu.classes.seats.storage.db.DBConnection;

import java.util.List;

public class V8__LTISession extends BaseMigration {

    final static String TABLE_DEFS =
        "create table seat_lti_session (session_id varchar2(255), key varchar2(255) not null, value varchar(255), type varchar(32) default 'string', mtime number not null, primary key (session_id, key));";

    public void migrate(DBConnection connection) throws Exception {
        for (String ddl : TABLE_DEFS.split(";")) {
            if (ddl.trim().isEmpty()) {
                continue;
            }

            connection.run(ddl.trim()).executeUpdate();
        }
    }
}

