/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.single.distsql.statement.rql;

import org.apache.shardingsphere.distsql.statement.rql.show.ShowTablesStatement;
import org.apache.shardingsphere.sql.parser.sql.common.segment.generic.DatabaseSegment;

import java.util.Optional;

/**
 * Show unloaded single table statement.
 */
public final class ShowUnloadedSingleTableStatement extends ShowTablesStatement {
    
    private final String storageUnitName;
    
    private final String schemaName;
    
    public ShowUnloadedSingleTableStatement(final String storageUnitName, final String schemaName, final DatabaseSegment database) {
        // TODO support like later
        super(null, database);
        this.storageUnitName = storageUnitName;
        this.schemaName = schemaName;
    }
    
    /**
     * Get storage unit name.
     *
     * @return storage unit name
     */
    public Optional<String> getStorageUnitName() {
        return Optional.ofNullable(storageUnitName);
    }
    
    /**
     * Get schema name.
     *
     * @return schema name
     */
    public Optional<String> getSchemaName() {
        return Optional.ofNullable(schemaName);
    }
}
