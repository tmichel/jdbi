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
package org.jdbi.v3.testing.junit5;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbiH2ExtensionTest {

    @RegisterExtension
    public JdbiExtension h2 = JdbiExtension.h2();

    @Test
    public void testIsAlive() {
        Integer one = h2.getJdbi().withHandle(h -> h.createQuery("select 1").mapTo(Integer.class).one());

        assertThat(one).isEqualTo(1);
    }
}
