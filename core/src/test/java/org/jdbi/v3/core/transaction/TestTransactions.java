/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jdbi.v3.core.transaction;

import java.io.IOException;
import java.util.List;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Something;
import org.jdbi.v3.core.junit5.H2DatabaseExtension;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.core.statement.TemplateEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestTransactions {

    @RegisterExtension
    public H2DatabaseExtension h2Extension = H2DatabaseExtension.withSomething();

    int begin, commit, rollback;

    private Handle h;

    private final LocalTransactionHandler txSpy = new LocalTransactionHandler() {
        @Override
        public void begin(Handle handle) {
            begin++;
            super.begin(handle);
        }

        @Override
        public void commit(Handle handle) {
            commit++;
            super.commit(handle);
        }

        @Override
        public void rollback(Handle handle) {
            rollback++;
            super.rollback(handle);
        }
    };

    @BeforeEach
    public void setUp() {
        h2Extension.getJdbi().setTransactionHandler(txSpy);
        h = h2Extension.openHandle();
    }

    @AfterEach
    public void close() {
        h.close();
    }

    @Test
    public void testCallback() {
        String woot = h.inTransaction(x -> "Woot!");

        assertThat(woot).isEqualTo("Woot!");
    }

    @Test
    public void testRollbackOutsideTx() {
        h.execute("insert into something (id, name) values (?, ?)", 7, "Tom");
        h.rollback();
    }

    @Test
    public void testDoubleOpen() throws Exception {
        assertThat(h.getConnection().getAutoCommit()).isTrue();

        h.begin();
        h.begin();
        assertThat(h.getConnection().getAutoCommit()).isFalse();
        h.commit();
        assertThat(h.getConnection().getAutoCommit()).isTrue();
    }

    @Test
    public void testExceptionAbortsTransaction() {
        assertThatThrownBy(() ->
            h.inTransaction(handle -> {
                handle.execute("insert into something (id, name) values (?, ?)", 0, "Keith");
                throw new IOException();
            }))
            .isInstanceOf(IOException.class);

        List<Something> r = h.createQuery("select * from something").mapToBean(Something.class).list();
        assertThat(r).isEmpty();
    }

    @Test
    public void testExceptionAbortsUseTransaction() {
        assertThatThrownBy(() ->
            h.useTransaction(handle -> {
                handle.execute("insert into something (id, name) values (?, ?)", 0, "Keith");
                throw new IOException();
            }))
            .isInstanceOf(IOException.class);

        List<Something> r = h.createQuery("select * from something").mapToBean(Something.class).list();
        assertThat(r).isEmpty();
    }

    @Test
    public void testRollbackDoesntCommit() {
        assertThat(begin).isEqualTo(0);
        h.useTransaction(th -> {
            assertThat(begin).isEqualTo(1);
            assertThat(rollback).isEqualTo(0);
            th.rollback();
        });
        assertThat(rollback).isEqualTo(1);
        assertThat(commit).isEqualTo(0);
    }

    @Test
    public void testSavepoint() {
        h.begin();

        h.execute("insert into something (id, name) values (?, ?)", 1, "Tom");
        h.savepoint("first");
        h.execute("insert into something (id, name) values (?, ?)", 2, "Martin");
        assertThat(h.createQuery("select count(*) from something").mapTo(Integer.class).one())
            .isEqualTo(Integer.valueOf(2));
        h.rollbackToSavepoint("first");
        assertThat(h.createQuery("select count(*) from something").mapTo(Integer.class).one())
            .isEqualTo(Integer.valueOf(1));
        h.commit();
        assertThat(h.createQuery("select count(*) from something").mapTo(Integer.class).one())
            .isEqualTo(Integer.valueOf(1));
    }

    @Test
    public void testReleaseSavepoint() {
        h.begin();
        h.savepoint("first");
        h.execute("insert into something (id, name) values (?, ?)", 1, "Martin");

        h.release("first");

        assertThatExceptionOfType(TransactionException.class)
            .isThrownBy(() -> h.rollbackToSavepoint("first"));

        h.rollback();
    }

    @Test
    public void testThrowingRuntimeExceptionPercolatesOriginal() {
        assertThatThrownBy(() -> h.inTransaction(handle -> {
            throw new IllegalArgumentException();
        })).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testTemplateEngineThrowsError() {
        assertThatThrownBy(() -> h.setTemplateEngine(new BoomEngine()).inTransaction(h2 -> h2.execute("select 1")))
            .isOfAnyClassIn(Error.class)
            .hasMessage("boom");
        assertThat(h.isInTransaction()).isFalse();
    }

    static class BoomEngine implements TemplateEngine {

        @Override
        public String render(String template, StatementContext ctx) {
            throw new Error("boom");
        }
    }
}
