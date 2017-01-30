package com.cloudera.impala.util;

import org.apache.hadoop.hive.conf.HiveConf;

// Proxy class for org.apache.hadoop.hive.conf.HiveConf
public class HiveConfProxy {
    static {
        // Use default value from HiveConf
        METASTORE_BATCH_RETRIEVE_TABLE_PARTITION_MAX = "hive.metastore.batch.retrieve.table.partition.max";

        // We are looking for METASTORE_BATCH_RETRIEVE_TABLE_PARTITION_MAX element of enum HiveConf.ConfVars
        // And if we succeed, we will change METASTORE_BATCH_RETRIEVE_TABLE_PARTITION_MAX from defaults
        for (HiveConf.ConfVars c : HiveConf.ConfVars.values()) {
            if (c.name().equals("METASTORE_BATCH_RETRIEVE_TABLE_PARTITION_MAX")) {
                METASTORE_BATCH_RETRIEVE_TABLE_PARTITION_MAX = c.toString();
                break;
            }
        }
    }


    public static String METASTORE_BATCH_RETRIEVE_TABLE_PARTITION_MAX;
}
