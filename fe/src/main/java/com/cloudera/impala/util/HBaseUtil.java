package com.cloudera.impala.util;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;

public class HBaseUtil {
    public static Configuration getHBaseConf() {
        Configuration conf = new Configuration();
        conf.set("hbase.defaults.for.version.skip", "true");
        return HBaseConfiguration.addHbaseResources(conf);
    }
}
