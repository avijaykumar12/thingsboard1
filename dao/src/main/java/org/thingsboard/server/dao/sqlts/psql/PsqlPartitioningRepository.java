/**
 * Copyright © 2016-2019 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.dao.sqlts.psql;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.server.dao.timeseries.PsqlPartition;
import org.thingsboard.server.dao.util.PsqlDao;
import org.thingsboard.server.dao.util.SqlTsDao;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@SqlTsDao
@PsqlDao
@Repository
@Transactional
public class PsqlPartitioningRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(PsqlPartition partition) {
        entityManager.createNativeQuery(partition.getQuery())
                .executeUpdate();
    }

}