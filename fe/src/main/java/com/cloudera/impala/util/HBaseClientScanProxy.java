package com.cloudera.impala.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.client.Scan;

public class HBaseClientScanProxy {

    private static final Log LOG = LogFactory.getLog(HBaseClientScanProxy.class);

    private static boolean setBatch_InitError_ = false;
    private static Method setBatch_Method_ = null;
    private static RuntimeException setBatch_InitException_ = null;

    private static boolean setMaxVersions_InitError_ = false;
    private static Method setMaxVersions_Method_ = null;
    private static RuntimeException setMaxVersions_InitException_ = null;

    private static boolean setCacheBlocks_InitError_ = false;
    private static Method setCacheBlocks_Method_ = null;
    private static RuntimeException setCacheBlocks_InitException_ = null;

    private static boolean setRaw_InitError_ = false;
    private static Method setRaw_Method_ = null;
    private static RuntimeException setRaw_InitException_ = null;

    static {
        try {
            setBatch_Method_ = Scan.class.getMethod("setBatch", int.class);
        } catch (NoSuchMethodException | SecurityException e) {
            setBatch_InitException_ = new RuntimeException("cannot find org.apache.hadoop.hbase.client.Scan.setBatch(int) ", e);
            setBatch_InitError_ = true;
        }
        try {
            setMaxVersions_Method_ = Scan.class.getMethod("setMaxVersions", int.class);
        } catch (NoSuchMethodException | SecurityException e) {
            setMaxVersions_InitException_ = new RuntimeException("cannot find org.apache.hadoop.hbase.client.Scan.setMaxVersions(int) ", e);
            setMaxVersions_InitError_ = true;
        }
        try {
            setCacheBlocks_Method_ = Scan.class.getMethod("setCacheBlocks", boolean.class);
        } catch (NoSuchMethodException | SecurityException e) {
            setCacheBlocks_InitException_ = new RuntimeException("cannot find org.apache.hadoop.hbase.client.Scan.setCacheBlocks(boolean) ", e);
            setCacheBlocks_InitError_ = true;
        }
        try {
            setRaw_Method_ = Scan.class.getMethod("setRaw", boolean.class);
        } catch (NoSuchMethodException | SecurityException e) {
            setRaw_InitException_ = new RuntimeException("cannot find org.apache.hadoop.hbase.client.Scan.setRaw(boolean) ", e);
            setRaw_InitError_ = true;
        }
    }
    public static void setBatch(Scan object, int param) {
        if (setBatch_InitError_) {
            throw setBatch_InitException_;
        }

        try {
            if (setBatch_Method_ != null) {
                LOG.debug("Call Scan::setBatch()");
                setBatch_Method_.invoke(object, param);
                return;
            }
            throw new RuntimeException("No Scan::setBatch() function found.");
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setMaxVersions(Scan object, int param) {
        if (setMaxVersions_InitError_) {
            throw setMaxVersions_InitException_;
        }

        try {
            if (setMaxVersions_Method_ != null) {
                LOG.debug("Call Scan::setMaxVersions()");
                setMaxVersions_Method_.invoke(object, param);
                return;
            }
            throw new RuntimeException("No Scan::setMaxVersions() function found.");
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setCacheBlocks(Scan object, boolean param) {
        if (setCacheBlocks_InitError_) {
            throw setCacheBlocks_InitException_;
        }

        try {
            if (setCacheBlocks_Method_ != null) {
                LOG.debug("Call Scan::setCacheBlocks()");
                setCacheBlocks_Method_.invoke(object, param);
                return;
            }
            throw new RuntimeException("No Scan::setCacheBlocks() function found.");
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setRaw(Scan object, boolean param) {
        if (setRaw_InitError_) {
            throw setRaw_InitException_;
        }

        try {
            if (setRaw_Method_ != null) {
                LOG.debug("Call Scan::setRaw()");
                setRaw_Method_.invoke(object, param);
                return;
            }
            throw new RuntimeException("No Scan::setRaw() function found.");
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}