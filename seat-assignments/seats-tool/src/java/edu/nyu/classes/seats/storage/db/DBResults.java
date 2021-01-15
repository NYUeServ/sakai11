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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.function.Function;

/**
 * Provide an iterator over a ResultSet.
 */
public class DBResults implements Iterable<ResultSet>, Iterator<ResultSet>, AutoCloseable {
    private final PreparedStatement originalStatement;
    private final ResultSet resultSet;
    private boolean hasRowReady;

    public DBResults(ResultSet rs, PreparedStatement originalStatement) {
        this.resultSet = rs;
        this.originalStatement = originalStatement;
    }

    @Override
    public void close() throws SQLException {
        resultSet.close();
        originalStatement.close();
    }

    @Override
    public boolean hasNext() {
        try {
            if (!hasRowReady) {
                hasRowReady = resultSet.next();
            }

            return hasRowReady;
        } catch (SQLException e) {
            try {
                this.close();
            } catch (SQLException e2) {}

            return false;
        }
    }

    @Override
    public ResultSet next() {
        if (!hasRowReady) {
            throw new NoSuchElementException("Read past end of results");
        }

        hasRowReady = false;
        return resultSet;
    }

    @Override
    public Iterator<ResultSet> iterator() {
        return this;
    }

    public Stream<ResultSet> stream() {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(this, Spliterator.ORDERED),
                                    false);
    }

    @FunctionalInterface
    public interface SQLMapper<T> {
        T apply(ResultSet r) throws SQLException;
    }

    public <T> List<T> map(SQLMapper<T> fn) throws SQLException {
        try {
            List<T> result = new ArrayList<>();

            while (this.hasNext()) {
                result.add(fn.apply(this.next()));
            }

            return result;
        } finally {
            this.close();
        }
    }

    @FunctionalInterface
    public interface SQLAction<T> {
        void apply(ResultSet r) throws SQLException;
    }

    public void each(SQLAction fn) throws SQLException {
        try {
            while (this.hasNext()) {
                fn.apply(this.next());
            }
        } finally {
            this.close();
        }
    }

    public List<String> getStringColumn(String column) throws SQLException {
        return this.map(r -> r.getString(column));
    }

    public Optional<Integer> oneInt() throws SQLException {
        try {
            Optional<Integer> result = Optional.empty();

            if (this.hasNext()) {
                result = Optional.ofNullable(this.resultSet.getInt(1));
            }

            return result;
        } finally {
            this.close();
        }
    }

    public Optional<Long> oneLong() throws SQLException {
        try {
            Optional<Long> result = Optional.empty();

            if (this.hasNext()) {
                result = Optional.ofNullable(this.resultSet.getLong(1));
            }

            return result;
        } finally {
            this.close();
        }
    }

    public Optional<String> oneString() throws SQLException {
        try {
            Optional<String> result = Optional.empty();

            if (this.hasNext()) {
                result = Optional.ofNullable(this.resultSet.getString(1));
            }

            return result;
        } finally {
            this.close();
        }
    }

    public int getCount() throws SQLException {
        return this.oneInt().get();
    }

}
