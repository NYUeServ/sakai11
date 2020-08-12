/**********************************************************************************
 *
 * Copyright (c) 2019 The Sakai Foundation
 *
 * Original developers:
 *
 *   New York University
 *   Payten Giles
 *   Mark Triggs
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package edu.nyu.classes.seats.storage.db;

import java.io.Reader;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Calendar;
import java.util.Date;

/**
 * Wrap a PreparedStatement, providing nicer parameter handling and transaction commit/rollback checks.
 */
public class DBPreparedStatement {

    private final DBConnection dbConnection;
    private final PreparedStatement preparedStatement;
    private int paramCount;

    public DBPreparedStatement(PreparedStatement preparedStatement, DBConnection dbc) {
        this.dbConnection = dbc;
        this.preparedStatement = preparedStatement;
        this.paramCount = 1;
    }

    public DBPreparedStatement param(String parameter) throws SQLException {
        try {
            preparedStatement.setString(paramCount(), parameter);
            return this;
        } catch (SQLException e) {
            cleanup();
            throw e;
        }
    }

    public DBPreparedStatement nullParam() throws SQLException {
        try {
            preparedStatement.setNull(paramCount(), java.sql.Types.VARCHAR);
            return this;
        } catch (SQLException e) {
            cleanup();
            throw e;
        }
    }

    public DBPreparedStatement param(Date parameter, Calendar cal) throws SQLException {
        try {
            preparedStatement.setTimestamp(paramCount(), new java.sql.Timestamp(parameter.getTime()), cal);
            return this;
        } catch (SQLException e) {
            cleanup();
            throw e;
        }
    }

    public DBPreparedStatement param(Long parameter) throws SQLException {
        try {
            preparedStatement.setLong(paramCount(), parameter);
            return this;
        } catch (SQLException e) {
            cleanup();
            throw e;
        }
    }

    public DBPreparedStatement param(Integer parameter) throws SQLException {
        try {
            preparedStatement.setInt(paramCount(), parameter);
            return this;
        } catch (SQLException e) {
            cleanup();
            throw e;
        }
    }

    public DBPreparedStatement param(Reader reader) throws SQLException {
        try {
            preparedStatement.setClob(paramCount(), reader);
            return this;
        } catch (SQLException e) {
            cleanup();
            throw e;
        }
    }

    public DBPreparedStatement param(Reader reader, long length) throws SQLException {
        try {
            int param = paramCount();
            try {
                preparedStatement.setCharacterStream(param, reader, length);
            } catch (java.lang.AbstractMethodError e) {
                // Older JDBC versions don't support the method with a long
                // argument.  Use an int instead.
                preparedStatement.setCharacterStream(param, reader, (int)length);
            }

            return this;
        } catch (SQLException e) {
            cleanup();
            throw e;
        }
    }

    public DBPreparedStatement stringParams(Collection<String> strings) throws SQLException {
        for (String s : strings) {
            if (s == null) {
                this.nullParam();
            } else {
                this.param(s);
            }
        }

        return this;
    }

    public int executeUpdate() throws SQLException {
        try {
            dbConnection.markAsDirty();
            return preparedStatement.executeUpdate();
        } finally {
            cleanup();
        }
    }

    public DBResults executeQuery() throws SQLException {
        return new DBResults(preparedStatement.executeQuery(),
                preparedStatement);
    }

    private void cleanup() throws SQLException {
        preparedStatement.close();
    }

    private int paramCount() {
        return this.paramCount++;
    }

}
