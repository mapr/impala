// Copyright (c) 2012 Cloudera, Inc. All rights reserved.

package com.cloudera.impala.catalog;

import static com.cloudera.impala.thrift.ImpalaInternalServiceConstants.DEFAULT_PARTITION_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.hadoop.hive.metastore.TableType;
import org.apache.hadoop.hive.metastore.api.ColumnStatisticsData;
import org.junit.Test;

import com.cloudera.impala.analysis.FunctionName;
import com.cloudera.impala.analysis.HdfsUri;
import com.cloudera.impala.analysis.LiteralExpr;
import com.cloudera.impala.analysis.NumericLiteral;
import com.cloudera.impala.catalog.MetaStoreClientPool.MetaStoreClient;
import com.cloudera.impala.testutil.CatalogServiceTestCatalog;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class CatalogTest {
}
