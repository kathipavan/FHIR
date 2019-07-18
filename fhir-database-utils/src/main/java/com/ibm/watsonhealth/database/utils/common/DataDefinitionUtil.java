/**
 * (C) Copyright IBM Corp. 2019
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.watsonhealth.database.utils.common;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.ibm.watsonhealth.database.utils.api.IDatabaseTypeAdapter;
import com.ibm.watsonhealth.database.utils.model.ColumnBase;

/**
 * Handles common syntax for generating DDL
 * @author rarnold
 *
 */
public class DataDefinitionUtil {
    private static final String NAME_PATTERN_RGX = "[a-zA-Z_]\\w*$";
    private static final Pattern NAME_PATTERN = Pattern.compile(NAME_PATTERN_RGX);

    /**
     * @param tableName
     * @param indexName
     * @param indexColumns
     * @param includeColumns
     * @return
     */
    public static String createUniqueIndex(String schemaName, String tableName, String indexName, List<String> indexColumns,
            List<String> includeColumns) {
        
        StringBuilder result = new StringBuilder();
        result.append(createUniqueIndex(schemaName, tableName, indexName, indexColumns));
        result.append(" INCLUDE (");
        result.append(join(includeColumns));
        result.append(")");
        
        return result.toString();
    }

    /**
     * @param tableName
     * @param indexName
     * @param indexColumns
     * @param includeColumns
     * @return
     */
    public static String createUniqueIndex(String schemaName, String tableName, String indexName, List<String> indexColumns) {
        
        StringBuilder result = new StringBuilder();
        result.append("CREATE UNIQUE INDEX ");
        result.append(getQualifiedName(schemaName, indexName));
        result.append(" ON ");
        result.append(getQualifiedName(schemaName, tableName));
        result.append("(");
        result.append(join(indexColumns));
        result.append(")");
                
        return result.toString();
    }


    /**
     * Create the DDL for a plain old index
     * @param tableName
     * @param indexName
     * @param indexColumns
     * @return
     */
    public static String createIndex(String schemaName, String tableName, String indexName, List<String> indexColumns) {
                
        StringBuilder result = new StringBuilder();
        result.append("CREATE INDEX ");
        result.append(getQualifiedName(schemaName, indexName));
        result.append(" ON ");
        result.append(getQualifiedName(schemaName, tableName));
        result.append("(");
        result.append(join(indexColumns));
        result.append(")");
                
        return result.toString();
    }

    /**
     * Create a comma-separated list of the items in the collection
     * @param things
     * @return
     */
    public static String join(Collection<String> things) {
        return things.stream().collect(Collectors.joining(","));
    }

    /**
     * Create a comma-separated list of the given strings
     * @param things
     * @return
     */
    public static String join(String... things) {
        return join(Arrays.asList(things));
    }

    /**
     * Although the variables used to build various DDL/DML statements are
     * all sourced internally within the program, we still check that the
     * strings are safe, just to add that extra layer of protection 
     * @param name
     * @return
     */
    public static boolean isValidName(String name) {
        Matcher m = NAME_PATTERN.matcher(name);
        return m.matches() && name.length() <= 128;
    }

    /**
     * Make sure that the given name is valid for use in database statements
     * @param name
     * @return the name confirmed as valid
     * @throws IllegalArgumentException if the given name is invalid
     */
    public static String assertValidName(String name) {
        if (!isValidName(name)) {
            throw new IllegalArgumentException("Invalid SQL object name: " + name);
        }
        return name;
    }
    
    /**
     * Assert each of the given names in the list (array) is valid
     * @param names
     */
    public static void assertValidNames(String... names) {
        for (String nm: names) {
            assertValidName(nm);
        }
    }

    /**
     * Make sure this statement fragment does not contain anything which could
     * be used as part of a SQL injection attack. This is used for more complex
     * DDL construction which may require a predicate to be passed as a string
     * as part of a larger CREATE/ALTER statement. Note: none of the input
     * being dealt with here is external input...so it should be safe already.
     * @param value
     */
    public static void assertSecure(String value) {
        if (value.contains("'") || value.contains(";") || value.contains("\\") || value.contains("--")) {
            throw new IllegalArgumentException("Value not permitted in SQL statements: " + value);
        }
    }
    
    /**
     * Return the fully qualified name in the form "SCHEMA.OBJECT"
     * Validates that both schema and object names are valid
     * @param schemaName
     * @param objectName
     * @return the fully qualified name
     * @throws IllegalArgumentException if either name is not a valid database object name
     */
    public static String getQualifiedName(String schemaName, String objectName) {
        assertValidName(schemaName);
        assertValidName(objectName);
        return schemaName + "." + objectName;
    }

    /**
     * @param columns
     * @return
     */
    public static String columnSpecList(IDatabaseTypeAdapter adapter, Collection<ColumnBase> columns) {        
        // Behold the power of streams/lambdas etc. This saves a ton of code
        return columns.stream().map(c -> c.getTypeDef(adapter)).collect(Collectors.joining(","));
    }


}
