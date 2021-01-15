package edu.nyu.classes.seats.storage.migrations;

import edu.nyu.classes.seats.storage.db.DBConnection;

import java.util.List;

public class V7__AddSystem extends BaseMigration {

    final static String TABLE_DEFS =
        "alter table nyu_t_brightspace_oauth add system varchar2(255) default 'seating-tool' not null;";

    public void migrate(DBConnection connection) throws Exception {
        for (String ddl : TABLE_DEFS.split(";")) {
            if (ddl.trim().isEmpty()) {
                continue;
            }

            connection.run(ddl.trim()).executeUpdate();
        }
    }
}

