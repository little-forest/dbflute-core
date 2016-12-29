/*
 * Copyright 2014-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.dbflute.logic.doc.policycheck;

import java.util.List;
import java.util.Map;

import org.apache.torque.engine.database.model.Column;
import org.apache.torque.engine.database.model.ForeignKey;
import org.apache.torque.engine.database.model.Index;
import org.apache.torque.engine.database.model.Table;
import org.apache.torque.engine.database.model.Unique;
import org.dbflute.logic.doc.policycheck.DfSchemaPolicyMiscSecretary.DfSchemaPolicyIfClause;
import org.dbflute.util.Srl;

/**
 * @author jflute
 * @since 1.1.2 (2016/12/29 Thursday at higashi-ginza)
 */
public class DfSchemaPolicyTableStatement {

    // ===================================================================================
    //                                                                           Attribute
    //                                                                           =========
    protected final DfSchemaPolicyMiscSecretary _secretary = new DfSchemaPolicyMiscSecretary();

    // ===================================================================================
    //                                                                    Table Statement
    //                                                                    ================
    public void checkTableStatement(Table table, Map<String, Object> tableMap, List<String> vioList) {
        processTableStatement(table, tableMap, vioList);
    }

    protected void processTableStatement(Table table, Map<String, Object> tableMap, List<String> vioList) {
        @SuppressWarnings("unchecked")
        final List<String> statementList = (List<String>) tableMap.get("statementList");
        if (statementList != null) {
            for (String statement : statementList) {
                evaluateTableIfClause(table, statement, vioList, _secretary.extractIfClause(statement));
            }
        }
    }

    // ===================================================================================
    //                                                                            Evaluate
    //                                                                            ========
    // -----------------------------------------------------
    //                                             If Clause
    //                                             ---------
    // e.g.
    //  if tableName is suffix:_ID then bad
    //  if tableName is suffix:_HISTORY then pkDbType is bigint
    protected void evaluateTableIfClause(Table table, String statement, List<String> vioList, DfSchemaPolicyIfClause ifClause) {
        // #hope if tableName is ... and pkDbType is ... then ... by jflute (2016/12/29)
        final String ifItem = ifClause.getIfItem();
        final String ifValue = ifClause.getIfValue();
        if (ifItem.equalsIgnoreCase("tableName")) {
            if (isHitTable(table.getTableDbName(), ifValue)) {
                evaluateTableThenClause(table, statement, vioList, ifClause);
            }
        } else if (ifItem.equalsIgnoreCase("alias")) {
            if (isHitTable(table.getAlias(), ifValue)) {
                evaluateTableThenClause(table, statement, vioList, ifClause);
            }
        } else if (ifItem.equalsIgnoreCase("pkDbType")) { // e.g. if pkDbType is char
            if (table.hasPrimaryKey()) {
                final List<Column> pkList = table.getPrimaryKey();
                for (Column pk : pkList) {
                    if (isHitTable(pk.getDbType(), ifValue)) {
                        evaluateTableThenClause(table, statement, vioList, ifClause);
                    }
                }
            }
            if (isHitTable(table.getAlias(), ifValue)) {
                evaluateTableThenClause(table, statement, vioList, ifClause);
            }
        } else {
            throwSchemaPolicyCheckIllegalIfThenStatementException(statement, "Unknown if-item: " + ifItem);
        }
    }

    // -----------------------------------------------------
    //                                           Then Clause
    //                                           -----------
    protected void evaluateTableThenClause(Table table, String statement, List<String> vioList, DfSchemaPolicyIfClause ifClause) {
        final String thenClause = ifClause.getThenClause();
        if (thenClause.equalsIgnoreCase("bad")) {
            vioList.add("The table is no good: " + toTableDisp(table));
        } else if (thenClause.contains("hasCommonColumn")) {
            if (!table.hasAllCommonColumn()) {
                vioList.add("The table should have common columns: " + toTableDisp(table));
            }
        } else if (thenClause.contains(" is ")) { // e.g. dbType is integer
            evaluateTableThenItemValue(table, statement, vioList, ifClause);
        } else {
            throwSchemaPolicyCheckIllegalIfThenStatementException(statement, "Unknown then-clause: " + thenClause);
        }
    }

    protected void evaluateTableThenItemValue(Table table, String statement, List<String> vioList, DfSchemaPolicyIfClause ifClause) {
        final String thenClause = ifClause.getThenClause();
        final String thenItem = Srl.substringFirstFront(thenClause, " is ").trim();
        final String thenValue = Srl.substringFirstRear(thenClause, " is ").trim();
        if (thenItem.equalsIgnoreCase("tableName")) { // e.g. tableName is prefix:CLS_
            final String tableDbName = table.getTableDbName();
            if (!isHitExp(tableDbName, thenValue)) {
                vioList.add("The table name should be " + thenValue + " but " + tableDbName + ": " + toTableDisp(table));
            }
        } else if (thenItem.equalsIgnoreCase("alias")) { // e.g. alias is suffix:History
            if (table.hasAlias()) {
                final String alias = table.getAlias();
                if (!isHitExp(alias, thenValue)) {
                    vioList.add("The table alias should be " + thenValue + " but " + alias + ": " + toTableDisp(table));
                }
            }
        } else if (thenItem.equalsIgnoreCase("comment")) { // e.g. comment is contain:SEA
            if (table.hasAlias()) {
                final String comment = table.getComment();
                if (!isHitExp(comment, thenValue)) {
                    vioList.add("The table comment should be " + thenValue + " but " + comment + ": " + toTableDisp(table));
                }
            }
        } else if (thenItem.equalsIgnoreCase("pkDbType")) { // e.g. pkDbType is char
            if (table.hasPrimaryKey()) {
                final List<Column> pkList = table.getPrimaryKey();
                for (Column pk : pkList) {
                    final String pkDbName = pk.getDbType();
                    if (!isHitExp(pkDbName, thenValue)) {
                        vioList.add("The PK column DB type should be " + thenValue + " but " + pkDbName + ": " + toTableDisp(table));
                    }
                }
            }
        } else if (thenItem.equalsIgnoreCase("pkName")) { // e.g. pkName is prefix:PK_
            if (table.hasPrimaryKey()) {
                final Column pk = table.getPrimaryKey().get(0); // same name if compound
                final String pkName = pk.getPrimaryKeyName();
                if (!isHitExp(pkName, thenValue)) {
                    vioList.add("The PK constraint name should be " + thenValue + " but " + pkName + ": " + toTableDisp(table));
                }
            }
        } else if (thenItem.equalsIgnoreCase("fkName")) { // e.g. fkName is prefix:FK_
            for (ForeignKey fk : table.getForeignKeyList()) {
                final String fkName = fk.getName();
                if (!isHitExp(fkName, thenValue)) {
                    vioList.add("The FK constraint name should be " + thenValue + " but " + fkName + ": " + toTableDisp(table));
                }
            }
        } else if (thenItem.equalsIgnoreCase("uniqueName")) { // e.g. uniqueName is prefix:UQ_ 
            for (Unique uq : table.getUniqueList()) {
                final String uqName = uq.getName();
                if (!isHitExp(uqName, thenValue)) {
                    vioList.add("The unique constraint name should be " + thenValue + " but " + uqName + ": " + toTableDisp(table));
                }
            }
        } else if (thenItem.equalsIgnoreCase("indexName")) { // e.g. indexName is prefix:IX_ 
            for (Index ix : table.getIndexList()) {
                final String ixName = ix.getName();
                if (!isHitExp(ixName, thenValue)) {
                    vioList.add("The index name should be " + thenValue + " but " + ixName + ": " + toTableDisp(table));
                }
            }
        } else {
            throwSchemaPolicyCheckIllegalIfThenStatementException(statement, "Unknown then-item: " + thenItem);
        }
    }

    // ===================================================================================
    //                                                                        Assist Logic
    //                                                                        ============
    protected boolean isHitTable(String columnName, String hint) {
        return _secretary.isHitTable(columnName, hint);
    }

    protected boolean isHitExp(String exp, String hint) {
        return _secretary.isHitExp(exp, hint);
    }

    protected String toTableDisp(Table table) {
        return _secretary.toTableDisp(table);
    }

    // ===================================================================================
    //                                                                           Exception
    //                                                                           =========
    protected void throwSchemaPolicyCheckUnknownThemeException(String theme, String targetType) {
        _secretary.throwSchemaPolicyCheckUnknownThemeException(theme, targetType);
    }

    protected void throwSchemaPolicyCheckUnknownPropertyException(String property) {
        _secretary.throwSchemaPolicyCheckUnknownPropertyException(property);
    }

    protected void throwSchemaPolicyCheckIllegalIfThenStatementException(String statement, String additional) {
        _secretary.throwSchemaPolicyCheckIllegalIfThenStatementException(statement, additional);
    }
}