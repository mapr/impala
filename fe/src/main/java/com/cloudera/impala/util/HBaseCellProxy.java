package com.cloudera.impala.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.Cell;

public class HBaseCellProxy {
    private static final Log LOG = LogFactory.getLog(HBaseCellProxy.class);

    private static boolean getTagsLength_InitError_ = false;
    private static Method getTagsLength_Method_ = null;
    private static RuntimeException getTagsLength_InitException_ = null;

    static {
        try {
            getTagsLength_Method_ = Cell.class.getMethod("getTagsLength");
        } catch (NoSuchMethodException | SecurityException e) {
            getTagsLength_InitException_ = new RuntimeException("cannot find org.apache.hadoop.hbase.Cell.getTagsLength() ", e);
            getTagsLength_InitError_ = true;
        }
    }

    public static short getTagsLength (Cell object) {
        if (getTagsLength_InitError_) {
            throw getTagsLength_InitException_;
        }

        try {
            if (getTagsLength_Method_ != null) {
                LOG.debug("Call Cell::getTagsLength()");
                Object result = getTagsLength_Method_.invoke(object);
                if (result instanceof Number) {
                    return ((Number)result).shortValue();
                }
            }
            throw new RuntimeException("No Cell::getTagsLength() function found.");
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}