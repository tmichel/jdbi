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
package org.jdbi.v3.sqlobject;

import org.jdbi.v3.core.mapper.SomethingMapper;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.testing.junit5.JdbiExtension;
import org.jdbi.v3.testing.junit5.internal.TestingInitializers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

public class TestEnumMapping {

    @RegisterExtension
    public JdbiExtension h2Extension = JdbiExtension.h2().withPlugin(new SqlObjectPlugin()).withInitializer(TestingInitializers.something());

    @Test
    public void testEnums() {
        Spiffy spiffy = h2Extension.openHandle().attach(Spiffy.class);

        int bobId = spiffy.addCoolName(CoolName.BOB);
        int joeId = spiffy.addCoolName(CoolName.JOE);

        assertThat(spiffy.findById(bobId)).isSameAs(CoolName.BOB);
        assertThat(spiffy.findById(joeId)).isSameAs(CoolName.JOE);
    }

    @RegisterRowMapper(SomethingMapper.class)
    public interface Spiffy {
        @SqlUpdate("insert into something(name) values(:name)")
        @GetGeneratedKeys
        int addCoolName(@Bind("name") CoolName coolName);

        @SqlQuery("select name from something where id = :id")
        CoolName findById(@Bind("id") int id);
    }

    public enum CoolName {
        BOB, FRANK, JOE
    }
}
