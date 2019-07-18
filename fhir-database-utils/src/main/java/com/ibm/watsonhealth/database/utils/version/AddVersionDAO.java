/**
 * (C) Copyright IBM Corp. 2019
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watsonhealth.database.utils.version;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.ibm.watsonhealth.database.utils.api.IDatabaseStatement;
import com.ibm.watsonhealth.database.utils.api.IDatabaseTranslator;
import com.ibm.watsonhealth.database.utils.model.InsertStatement;

/**
 * Add the {type, name, version} record to the database. Idempotent,
 * so if it already exists, it's a NOP.
 * @author rarnold
 *
 */
public class AddVersionDAO implements IDatabaseStatement {
    private final String schemaName;
    private final String type;
    private final String name;
    private final int version;
    
    public AddVersionDAO(String schemaName, String type, String name, int version) {
        this.schemaName = schemaName;
        this.type = type;
        this.name = name;
        this.version = version;
    }

    /* (non-Javadoc)
     * @see com.ibm.watsonhealth.database.utils.api.IDatabaseStatement#run(com.ibm.watsonhealth.database.utils.api.IDatabaseTranslator, java.sql.Connection)
     */
    @Override
    public void run(IDatabaseTranslator translator, Connection c) {
        
        final InsertStatement ins = InsertStatement.builder(schemaName, SchemaConstants.VERSION_HISTORY)
                .addColumn(SchemaConstants.OBJECT_TYPE)
                .addColumn(SchemaConstants.OBJECT_NAME)
                .addColumn(SchemaConstants.VERSION)
                .addColumn(SchemaConstants.APPLIED, "CURRENT TIMESTAMP")
                .build();
        
        try (PreparedStatement ps = c.prepareStatement(ins.toString())) {
            ps.setString(1, type);
            ps.setString(2, name);
            ps.setInt(3, version);
            ps.executeUpdate();
        }
        catch (SQLException x) {
            // suppress any complaints about duplicates because we want this to
            // be idempotent
            if (!translator.isDuplicate(x)) {
                throw translator.translate(x);
            }
        }
    }

}
