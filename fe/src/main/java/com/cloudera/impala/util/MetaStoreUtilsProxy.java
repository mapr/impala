package com.cloudera.impala.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.cloudera.impala.catalog.HdfsTable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.metastore.api.EnvironmentContext;
import org.apache.hadoop.hive.metastore.api.Partition;
import org.apache.hadoop.hive.metastore.MetaStoreUtils;
import org.apache.hadoop.hive.metastore.Warehouse;
import org.apache.hadoop.hive.conf.HiveConf;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

// Proxy class for org.apache.hadoop.hive.metastore.MetaStoreUtils
public class MetaStoreUtilsProxy {

    private static final Log LOG = LogFactory.getLog(MetaStoreUtilsProxy.class);
    private static String hiveVersion = "1.2";

    private static boolean validateName_InitError_ = false;
    private static Method validateName_Method_ = null;
    private static RuntimeException validateName_InitException_ = null;
    private static boolean updatePartitionStatsFast_InitError_ = false;
    private static Method updatePartitionStatsFast_Method_ = null;
    private static RuntimeException updatePartitionStatsFast_InitException_ = null;
    private static Configuration conf;

    static {
        conf = new HiveConf(HdfsTable.class);

        try {
            // Try to find validateName according to Hive 1.2 api
            validateName_Method_ = MetaStoreUtils.class.getMethod("validateName", String.class);
        } catch (NoSuchMethodException | SecurityException e) {
            try {
                // If it fails we assume that Impala uses Hive 2.0 api
                // Also we memorize hive version
                hiveVersion = "2.0";
                validateName_Method_ = MetaStoreUtils.class.getMethod("validateName", String.class, Configuration.class);
            } catch (NoSuchMethodException | SecurityException ex) {
                validateName_InitException_ = new RuntimeException("cannot find org.apache.hadoop.hive.metastore.MetaStoreUtils.validateName() ", e);
                validateName_InitError_ = true;
            }
        }

        try {
            // Try to find validateName according to Hive 1.2 api
            updatePartitionStatsFast_Method_ = MetaStoreUtils.class.getMethod("updatePartitionStatsFast", Partition.class, Warehouse.class);
        } catch (NoSuchMethodException | NoSuchMethodError e) {
            try {
                // If it fails we assume that Impala uses Hive 2.0 api
                // Also we memorize hive version
                hiveVersion = "2.0";
                updatePartitionStatsFast_Method_ = MetaStoreUtils.class.getMethod("updatePartitionStatsFast", Partition.class, Warehouse.class, EnvironmentContext.class);
            } catch (NoSuchMethodException | SecurityException ex) {
                updatePartitionStatsFast_InitException_ = new RuntimeException("cannot find org.apache.hadoop.hive.metastore.MetaStoreUtils.updatePartitionStatsFast() ", e);
                updatePartitionStatsFast_InitError_ = true;
            }
        }
    }

    public static boolean validateName (String name) {
        if (validateName_InitError_) {
            throw validateName_InitException_;
        }

        try {
            if (validateName_Method_ != null) {
                LOG.debug("Call MetaStoreUtils::validateName()");
                Object result = hiveVersion == "1.2" ?
                        validateName_Method_.invoke(null, name):
                        validateName_Method_.invoke(null, name, conf);
                return ((Boolean)result).booleanValue();

            }
            throw new RuntimeException("No MetaStoreUtils::validateName() function found.");
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean updatePartitionStatsFast (Partition part, Warehouse wh) {
        if (updatePartitionStatsFast_InitError_) {
            throw updatePartitionStatsFast_InitException_;
        }

        try {
            if (updatePartitionStatsFast_Method_ != null) {
                LOG.debug("Call MetaStoreUtils::updatePartitionStatsFast()");
                Object result = hiveVersion == "1.2" ?
                    updatePartitionStatsFast_Method_.invoke(null, part, wh):
                    updatePartitionStatsFast_Method_.invoke(null, part, wh, null);
                return ((Boolean)result).booleanValue();

            }
            throw new RuntimeException("No MetaStoreUtils::updatePartitionStatsFast() function found.");
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

}
