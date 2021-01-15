package edu.nyu.classes.seats.storage.migrations;

import edu.nyu.classes.seats.storage.db.DBConnection;

import java.util.List;

public class V6__Brightspace_Oauth extends BaseMigration {

    final static String TABLE_DEFS =
        "create table nyu_t_brightspace_oauth (       " +
        "    client_id varchar2(255) primary key,     " +
        "    refresh_token varchar2(2048) not null   " +
        ");                                           ";

    public void migrate(DBConnection connection) throws Exception {
        for (String ddl : TABLE_DEFS.split(";")) {
            if (ddl.trim().isEmpty()) {
                continue;
            }

            connection.run(ddl.trim()).executeUpdate();
        }
    }
}

