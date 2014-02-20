// Copyright 2012 Cloudera Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.cloudera.impala.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudera.impala.analysis.ToSqlUtils;
import com.cloudera.impala.authorization.AuthorizationConfig;
import com.cloudera.impala.authorization.ImpalaInternalAdminUser;
import com.cloudera.impala.authorization.Privilege;
import com.cloudera.impala.authorization.User;
import com.cloudera.impala.catalog.DataSource;
import com.cloudera.impala.catalog.Function;
import com.cloudera.impala.common.FileSystemUtil;
import com.cloudera.impala.common.ImpalaException;
import com.cloudera.impala.common.InternalException;
import com.cloudera.impala.common.JniUtil;
import com.cloudera.impala.thrift.TCatalogObject;
import com.cloudera.impala.thrift.TDescribeTableParams;
import com.cloudera.impala.thrift.TDescribeTableResult;
import com.cloudera.impala.thrift.TExecRequest;
import com.cloudera.impala.thrift.TGetDataSrcsParams;
import com.cloudera.impala.thrift.TGetDataSrcsResult;
import com.cloudera.impala.thrift.TGetDbsParams;
import com.cloudera.impala.thrift.TGetDbsResult;
import com.cloudera.impala.thrift.TGetFunctionsParams;
import com.cloudera.impala.thrift.TGetFunctionsResult;
import com.cloudera.impala.thrift.TGetHadoopConfigRequest;
import com.cloudera.impala.thrift.TGetHadoopConfigResponse;
import com.cloudera.impala.thrift.TGetTablesParams;
import com.cloudera.impala.thrift.TGetTablesResult;
import com.cloudera.impala.thrift.TLoadDataReq;
import com.cloudera.impala.thrift.TLoadDataResp;
import com.cloudera.impala.thrift.TLogLevel;
import com.cloudera.impala.thrift.TMetadataOpRequest;
import com.cloudera.impala.thrift.TQueryCtx;
import com.cloudera.impala.thrift.TResultSet;
import com.cloudera.impala.thrift.TShowStatsParams;
import com.cloudera.impala.thrift.TTableName;
import com.cloudera.impala.thrift.TUpdateCatalogCacheRequest;
import com.cloudera.impala.util.GlogAppender;
import com.cloudera.impala.util.TSessionStateUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * JNI-callable interface onto a wrapped Frontend instance. The main point is to serialise
 * and deserialise thrift structures between C and Java.
 */
public class JniFrontend {
  private final static Logger LOG = LoggerFactory.getLogger(JniFrontend.class);
  private final static TBinaryProtocol.Factory protocolFactory_ =
      new TBinaryProtocol.Factory();
  private final Frontend frontend_;

  // Required minimum value (in milliseconds) for the HDFS config
  // 'dfs.client.file-block-storage-locations.timeout.millis'
  private static final long MIN_DFS_CLIENT_FILE_BLOCK_STORAGE_LOCATIONS_TIMEOUT_MS =
      10 * 1000;

  /**
   * Create a new instance of the Jni Frontend.
   */
  public JniFrontend(boolean lazy, String serverName, String authorizationPolicyFile,
      String sentryConfigFile, String authPolicyProviderClass, int impalaLogLevel,
      int otherLogLevel) throws InternalException {
    GlogAppender.Install(TLogLevel.values()[impalaLogLevel],
        TLogLevel.values()[otherLogLevel]);

    // Validate the authorization configuration before initializing the Frontend.
    // If there are any configuration problems Impala startup will fail.
    AuthorizationConfig authConfig = new AuthorizationConfig(serverName,
        authorizationPolicyFile, sentryConfigFile, authPolicyProviderClass);
    authConfig.validateConfig();
    if (authConfig.isEnabled()) {
      LOG.info("Authorization is 'ENABLED' using %s",
          authConfig.isFileBasedPolicy() ? " file based policy from: " +
          authConfig.getPolicyFile() : " using Sentry Policy Service.");
    } else {
      LOG.info("Authorization is 'DISABLED'.");
    }
    frontend_ = new Frontend(authConfig);
  }

  /**
   * Jni wrapper for Frontend.createExecRequest(). Accepts a serialized
   * TQueryContext; returns a serialized TQueryExecRequest.
   */
  public byte[] createExecRequest(byte[] thriftQueryContext)
      throws ImpalaException {
    TQueryCtx queryCtx = new TQueryCtx();
    JniUtil.deserializeThrift(protocolFactory_, queryCtx, thriftQueryContext);

    StringBuilder explainString = new StringBuilder();
    TExecRequest result = frontend_.createExecRequest(queryCtx, explainString);
    LOG.debug(explainString.toString());

    // TODO: avoid creating serializer for each query?
    TSerializer serializer = new TSerializer(protocolFactory_);
    try {
      return serializer.serialize(result);
    } catch (TException e) {
      throw new InternalException(e.getMessage());
    }
  }

  public byte[] updateCatalogCache(byte[] thriftCatalogUpdate) throws ImpalaException {
    TUpdateCatalogCacheRequest req = new TUpdateCatalogCacheRequest();
    JniUtil.deserializeThrift(protocolFactory_, req, thriftCatalogUpdate);
    TSerializer serializer = new TSerializer(protocolFactory_);
    try {
      return serializer.serialize(frontend_.updateCatalogCache(req));
    } catch (TException e) {
      throw new InternalException(e.getMessage());
    }
  }

  /**
   * Loads a table or partition with one or more data files. If the "overwrite" flag
   * in the request is true, all existing data in the table/partition will be replaced.
   * If the "overwrite" flag is false, the files will be added alongside any existing
   * data files.
   */
  public byte[] loadTableData(byte[] thriftLoadTableDataParams)
      throws ImpalaException, IOException {
    TLoadDataReq request = new TLoadDataReq();
    JniUtil.deserializeThrift(protocolFactory_, request, thriftLoadTableDataParams);
    TLoadDataResp response = frontend_.loadTableData(request);
    TSerializer serializer = new TSerializer(protocolFactory_);
    try {
      return serializer.serialize(response);
    } catch (TException e) {
      throw new InternalException(e.getMessage());
    }
  }

  /**
   * Return an explain plan based on thriftQueryContext, a serialized TQueryContext.
   * This call is thread-safe.
   */
  public String getExplainPlan(byte[] thriftQueryContext) throws ImpalaException {
    TQueryCtx queryCtx = new TQueryCtx();
    JniUtil.deserializeThrift(protocolFactory_, queryCtx, thriftQueryContext);
    String plan = frontend_.getExplainString(queryCtx);
    LOG.debug("Explain plan: " + plan);
    return plan;
  }


  /**
   * Returns a list of table names matching an optional pattern.
   * The argument is a serialized TGetTablesParams object.
   * The return type is a serialised TGetTablesResult object.
   * @see Frontend#getTableNames
   */
  public byte[] getTableNames(byte[] thriftGetTablesParams) throws ImpalaException {
    TGetTablesParams params = new TGetTablesParams();
    JniUtil.deserializeThrift(protocolFactory_, params, thriftGetTablesParams);
    // If the session was not set it indicates this is an internal Impala call.
    User user = params.isSetSession() ?
        new User(TSessionStateUtil.getEffectiveUser(params.getSession())) :
        ImpalaInternalAdminUser.getInstance();

    Preconditions.checkState(!params.isSetSession() || user != null );
    List<String> tables = frontend_.getTableNames(params.db, params.pattern, user);

    TGetTablesResult result = new TGetTablesResult();
    result.setTables(tables);

    TSerializer serializer = new TSerializer(protocolFactory_);
    try {
      return serializer.serialize(result);
    } catch (TException e) {
      throw new InternalException(e.getMessage());
    }
  }

  /**
   * Returns a list of table names matching an optional pattern.
   * The argument is a serialized TGetTablesParams object.
   * The return type is a serialised TGetTablesResult object.
   * @see Frontend#getTableNames
   */
  public byte[] getDbNames(byte[] thriftGetTablesParams) throws ImpalaException {
    TGetDbsParams params = new TGetDbsParams();
    JniUtil.deserializeThrift(protocolFactory_, params, thriftGetTablesParams);
    // If the session was not set it indicates this is an internal Impala call.
    User user = params.isSetSession() ?
        new User(TSessionStateUtil.getEffectiveUser(params.getSession())) :
        ImpalaInternalAdminUser.getInstance();
    List<String> dbs = frontend_.getDbNames(params.pattern, user);

    TGetDbsResult result = new TGetDbsResult();
    result.setDbs(dbs);

    TSerializer serializer = new TSerializer(protocolFactory_);
    try {
      return serializer.serialize(result);
    } catch (TException e) {
      throw new InternalException(e.getMessage());
    }
  }

  /**
   * Returns a list of data sources matching an optional pattern.
   * The argument is a serialized TGetDataSrcsResult object.
   * The return type is a serialised TGetDataSrcsResult object.
   * @see Frontend#getDataSrcs
   */
  public byte[] getDataSrcMetadata(byte[] thriftParams) throws ImpalaException {
    TGetDataSrcsParams params = new TGetDataSrcsParams();
    JniUtil.deserializeThrift(protocolFactory_, params, thriftParams);

    TGetDataSrcsResult result = new TGetDataSrcsResult();
    List<DataSource> dataSources = frontend_.getDataSrcs(params.pattern);
    result.setData_src_names(Lists.<String>newArrayListWithCapacity(dataSources.size()));
    result.setLocations(Lists.<String>newArrayListWithCapacity(dataSources.size()));
    result.setClass_names(Lists.<String>newArrayListWithCapacity(dataSources.size()));
    result.setApi_versions(Lists.<String>newArrayListWithCapacity(dataSources.size()));
    for (DataSource dataSource: dataSources) {
      result.addToData_src_names(dataSource.getName());
      result.addToLocations(dataSource.getLocation().toUri().getPath());
      result.addToClass_names(dataSource.getClassName());
      result.addToApi_versions(dataSource.getApiVersion());
    }
    TSerializer serializer = new TSerializer(protocolFactory_);
    try {
      return serializer.serialize(result);
    } catch (TException e) {
      throw new InternalException(e.getMessage());
    }
  }

  public byte[] getStats(byte[] thriftShowStatsParams) throws ImpalaException {
    TShowStatsParams params = new TShowStatsParams();
    JniUtil.deserializeThrift(protocolFactory_, params, thriftShowStatsParams);
    Preconditions.checkState(params.isSetTable_name());
    TResultSet result;
    if (params.isIs_show_col_stats()) {
      result = frontend_.getColumnStats(params.getTable_name().getDb_name(),
          params.getTable_name().getTable_name());
    } else {
      result = frontend_.getTableStats(params.getTable_name().getDb_name(),
          params.getTable_name().getTable_name());
    }
    TSerializer serializer = new TSerializer(protocolFactory_);
    try {
      return serializer.serialize(result);
    } catch (TException e) {
      throw new InternalException(e.getMessage());
    }
  }

  /**
   * Returns a list of function names matching an optional pattern.
   * The argument is a serialized TGetFunctionsParams object.
   * The return type is a serialised TGetFunctionsResult object.
   * @see Frontend#getTableNames
   */
  public byte[] getFunctions(byte[] thriftGetFunctionsParams) throws ImpalaException {
    TGetFunctionsParams params = new TGetFunctionsParams();
    JniUtil.deserializeThrift(protocolFactory_, params, thriftGetFunctionsParams);

    TGetFunctionsResult result = new TGetFunctionsResult();
    List<String> signatures = Lists.newArrayList();
    List<String> retTypes = Lists.newArrayList();
    List<Function> fns = frontend_.getFunctions(params.type, params.db, params.pattern);
    for (Function fn: fns) {
      signatures.add(fn.signatureString());
      retTypes.add(fn.getReturnType().toString());
    }
    result.setFn_signatures(signatures);
    result.setFn_ret_types(retTypes);
    TSerializer serializer = new TSerializer(protocolFactory_);
    try {
      return serializer.serialize(result);
    } catch (TException e) {
      throw new InternalException(e.getMessage());
    }
  }

  /**
   * Gets the thrift representation of a catalog object.
   */
  public byte[] getCatalogObject(byte[] thriftParams) throws ImpalaException,
      TException {
    TCatalogObject objectDescription = new TCatalogObject();
    JniUtil.deserializeThrift(protocolFactory_, objectDescription, thriftParams);
    TSerializer serializer = new TSerializer(protocolFactory_);
    return serializer.serialize(
        frontend_.getCatalog().getTCatalogObject(objectDescription));
  }

  /**
   * Returns a list of the columns making up a table.
   * The argument is a serialized TDescribeTableParams object.
   * The return type is a serialised TDescribeTableResult object.
   * @see Frontend#describeTable
   */
  public byte[] describeTable(byte[] thriftDescribeTableParams) throws ImpalaException {
    TDescribeTableParams params = new TDescribeTableParams();
    JniUtil.deserializeThrift(protocolFactory_, params, thriftDescribeTableParams);

    TDescribeTableResult result = frontend_.describeTable(
        params.getDb(), params.getTable_name(), params.getOutput_style());

    TSerializer serializer = new TSerializer(protocolFactory_);
    try {
      return serializer.serialize(result);
    } catch (TException e) {
      throw new InternalException(e.getMessage());
    }
  }

  /**
   * Returns a SQL DDL string for creating the specified table.
   */
  public String showCreateTable(byte[] thriftTableName)
      throws ImpalaException {
    TTableName params = new TTableName();
    JniUtil.deserializeThrift(protocolFactory_, params, thriftTableName);
    return ToSqlUtils.getCreateTableSql(frontend_.getCatalog().getTable(
        params.getDb_name(), params.getTable_name(),
        ImpalaInternalAdminUser.getInstance(), Privilege.ALL));
  }

  /**
   * Executes a HiveServer2 metadata operation and returns a TResultSet
   */
  public byte[] execHiveServer2MetadataOp(byte[] metadataOpsParams)
      throws ImpalaException {
    TMetadataOpRequest params = new TMetadataOpRequest();
    JniUtil.deserializeThrift(protocolFactory_, params, metadataOpsParams);
    TResultSet result = frontend_.execHiveServer2MetadataOp(params);

    TSerializer serializer = new TSerializer(protocolFactory_);
    try {
      return serializer.serialize(result);
    } catch (TException e) {
      throw new InternalException(e.getMessage());
    }
  }

  public void setCatalogInitialized() {
    frontend_.getCatalog().setIsReady();
  }

  // Caching this saves ~50ms per call to getHadoopConfigAsHtml
  private static final Configuration CONF = new Configuration();

  /**
   * Returns a string of all loaded Hadoop configuration parameters as a table of keys
   * and values. If asText is true, output in raw text. Otherwise, output in html.
   */
  public String getHadoopConfig(boolean asText) {
    StringBuilder output = new StringBuilder();
    if (asText) {
      output.append("Hadoop Configuration\n");
      // Write the set of files that make up the configuration
      output.append(CONF.toString());
      output.append("\n\n");
      // Write a table of key, value pairs
      for (Map.Entry<String, String> e : CONF) {
        output.append(e.getKey() + "=" + e.getValue() + "\n");
      }
      output.append("\n");
    } else {
      output.append("<h2>Hadoop Configuration</h2>");
      // Write the set of files that make up the configuration
      output.append(CONF.toString());
      output.append("\n\n");
      // Write a table of key, value pairs
      output.append("<table class='table table-bordered table-hover'>");
      output.append("<tr><th>Key</th><th>Value</th></tr>");
      for (Map.Entry<String, String> e : CONF) {
        output.append("<tr><td>" + e.getKey() + "</td><td>" + e.getValue() +
            "</td></tr>");
      }
      output.append("</table>");
    }
    return output.toString();
  }

  /**
   * Returns the corresponding config value for the given key as a serialized
   * TGetHadoopConfigResponse. If the config value is null, the 'value' field in the
   * thrift response object will not be set.
   */
  public byte[] getHadoopConfig(byte[] serializedRequest) throws ImpalaException {
    TGetHadoopConfigRequest request = new TGetHadoopConfigRequest();
    JniUtil.deserializeThrift(protocolFactory_, request, serializedRequest);
    TGetHadoopConfigResponse result = new TGetHadoopConfigResponse();
    result.setValue(CONF.get(request.getName()));
    TSerializer serializer = new TSerializer(protocolFactory_);
    try {
      return serializer.serialize(result);
    } catch (TException e) {
      throw new InternalException(e.getMessage());
    }
  }

  public class CdhVersion implements Comparable<CdhVersion> {
    private final int major;
    private final int minor;

    public CdhVersion(String versionString) throws IllegalArgumentException {
      String[] version = versionString.split("\\.");
      if (version.length != 2) {
        throw new IllegalArgumentException("Invalid version string:" + versionString);
      }
      try {
        major = Integer.parseInt(version[0]);
        minor = Integer.parseInt(version[1]);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid version string:" + versionString);
      }
    }

    public int compareTo(CdhVersion o) {
      return (this.major == o.major) ? (this.minor - o.minor) : (this.major - o.major);
    }

    @Override
    public String toString() {
      return major + "." + minor;
    }
  }

  /**
   * Returns an error string describing all configuration issues. If no config issues are
   * found, returns an empty string.
   * Short circuit read checks and block location tracking checks are run only if Impala
   * can determine that it is running on CDH.
   */
  public String checkConfiguration() {
    CdhVersion guessedCdhVersion = new CdhVersion("4.2"); //Always default to CDH 4.2
    StringBuilder output = new StringBuilder();

    // -- PSC -- output.append(checkLogFilePermission());
    // -- PSC -- output.append(checkFileSystem(CONF));

    if (guessedCdhVersion == null) {
      // Do not run any additional checks because we cannot determine the CDH version
      LOG.warn("Cannot detect CDH version. Skipping Hadoop configuration checks");
      return output.toString();
    }
    return output.toString();
  }
