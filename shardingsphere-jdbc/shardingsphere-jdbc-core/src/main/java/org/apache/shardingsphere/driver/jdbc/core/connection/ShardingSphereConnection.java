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

package org.apache.shardingsphere.driver.jdbc.core.connection;

import lombok.Getter;
import org.apache.shardingsphere.driver.jdbc.adapter.AbstractConnectionAdapter;
import org.apache.shardingsphere.driver.jdbc.core.datasource.metadata.ShardingSphereDatabaseMetaData;
import org.apache.shardingsphere.driver.jdbc.core.statement.ShardingSpherePreparedStatement;
import org.apache.shardingsphere.driver.jdbc.core.statement.ShardingSphereStatement;
import org.apache.shardingsphere.kernel.context.SchemaContexts;
import org.apache.shardingsphere.transaction.core.TransactionType;
import org.apache.shardingsphere.transaction.spi.ShardingTransactionManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * ShardingSphere Connection.
 */
@Getter
public final class ShardingSphereConnection extends AbstractConnectionAdapter {
    
    private final TransactionType transactionType;
    
    private final ShardingTransactionManager shardingTransactionManager;
    
    public ShardingSphereConnection(final Map<String, DataSource> dataSourceMap, final SchemaContexts schemaContexts, final TransactionType transactionType) {
        super(dataSourceMap, schemaContexts);
        this.transactionType = transactionType;
        shardingTransactionManager = schemaContexts.getDefaultSchemaContext().getRuntimeContext().getTransactionManagerEngine().getTransactionManager(transactionType);
    }
    
    /**
     * Whether hold transaction or not.
     *
     * @return true or false
     */
    public boolean isHoldTransaction() {
        return (TransactionType.LOCAL == transactionType && !getAutoCommit()) || (TransactionType.XA == transactionType && isInShardingTransaction());
    }
    
    @Override
    protected Connection createConnection(final String dataSourceName, final DataSource dataSource) throws SQLException {
        return isInShardingTransaction() ? shardingTransactionManager.getConnection(dataSourceName) : dataSource.getConnection();
    }
    
    private boolean isInShardingTransaction() {
        return null != shardingTransactionManager && shardingTransactionManager.isInTransaction();
    }
    
    @Override
    public DatabaseMetaData getMetaData() {
        return new ShardingSphereDatabaseMetaData(this);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql) throws SQLException {
        return new ShardingSpherePreparedStatement(this, sql);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency) throws SQLException {
        return new ShardingSpherePreparedStatement(this, sql, resultSetType, resultSetConcurrency);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
        return new ShardingSpherePreparedStatement(this, sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys) throws SQLException {
        return new ShardingSpherePreparedStatement(this, sql, autoGeneratedKeys);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes) throws SQLException {
        return new ShardingSpherePreparedStatement(this, sql, Statement.RETURN_GENERATED_KEYS);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql, final String[] columnNames) throws SQLException {
        return new ShardingSpherePreparedStatement(this, sql, Statement.RETURN_GENERATED_KEYS);
    }
    
    @Override
    public Statement createStatement() {
        return new ShardingSphereStatement(this);
    }
    
    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency) {
        return new ShardingSphereStatement(this, resultSetType, resultSetConcurrency);
    }
    
    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) {
        return new ShardingSphereStatement(this, resultSetType, resultSetConcurrency, resultSetHoldability);
    }
    
    @Override
    public void setAutoCommit(final boolean autoCommit) throws SQLException {
        if (TransactionType.LOCAL == transactionType) {
            super.setAutoCommit(autoCommit);
            return;
        }
        if (autoCommit != shardingTransactionManager.isInTransaction()) {
            return;
        }
        if (autoCommit && shardingTransactionManager.isInTransaction()) {
            shardingTransactionManager.commit();
            return;
        }
        if (!autoCommit && !shardingTransactionManager.isInTransaction()) {
            closeCachedConnections();
            shardingTransactionManager.begin();
        }
    }
    
    private void closeCachedConnections() throws SQLException {
        getForceExecuteTemplate().execute(getCachedConnections().values(), Connection::close);
        getCachedConnections().clear();
    }
    
    @Override
    public void commit() throws SQLException {
        if (TransactionType.LOCAL == transactionType) {
            super.commit();
        } else {
            shardingTransactionManager.commit();
        }
    }
    
    @Override
    public void rollback() throws SQLException {
        if (TransactionType.LOCAL == transactionType) {
            super.rollback();
        } else {
            shardingTransactionManager.rollback();
        }
    }
}
