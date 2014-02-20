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
import com.cloudera.impala.common.FileSystemUtil;
import com.cloudera.impala.common.ImpalaException;
import com.cloudera.impala.common.InternalException;
import com.cloudera.impala.common.JniUtil;
import com.cloudera.impala.thrift.TCatalogObject;
import com.cloudera.impala.thrift.TClientRequest;
import com.cloudera.impala.thrift.TDescribeTableParams;
import com.cloudera.impala.thrift.TDescribeTableResult;
import com.cloudera.impala.thrift.TExecRequest;
import com.cloudera.impala.thrift.TGetDbsParams;
import com.cloudera.impala.thrift.TGetDbsResult;
import com.cloudera.impala.thrift.TGetFunctionsParams;
import com.cloudera.impala.thrift.TGetFunctionsResult;
import com.cloudera.impala.thrift.TGetTablesParams;
import com.cloudera.impala.thrift.TGetTablesResult;
import com.cloudera.impala.thrift.TLoadDataReq;
import com.cloudera.impala.thrift.TLoadDataResp;
import com.cloudera.impala.thrift.TLogLevel;
import com.cloudera.impala.thrift.TMetadataOpRequest;
import com.cloudera.impala.thrift.TResultSet;
import com.cloudera.impala.thrift.TShowStatsParams;
import com.cloudera.impala.thrift.TTableName;
import com.cloudera.impala.thrift.TUpdateCatalogCacheRequest;
import com.cloudera.impala.util.GlogAppender;
import com.google.common.base.Preconditions;

/**
 * JNI-callable interface onto a wrapped Frontend instance. The main point is to serialise
 * and deserialise thrift structures between C and Java.
 */
public class JniFrontend {
  private final static Logger LOG = LoggerFactory.getLogger(JniFrontend.class);
  private final static TBinaryProtocol.Factory protocolFactory =
      new TBinaryProtocol.Factory();
  private final Frontend frontend;

  /**
   * Create a new instance of the Jni Frontend.
   */
  public JniFrontend(boolean lazy, String serverName, String authorizationPolicyFile,
      String policyProviderClassName, int impalaLogLevel, int otherLogLevel)
      throws InternalException {
    GlogAppender.Install(TLogLevel.values()[impalaLogLevel],
        TLogLevel.values()[otherLogLevel]);

    // Validate the authorization configuration before initializing the Frontend.
    // If there are any configuration problems Impala startup will fail.
    AuthorizationConfig authorizationConfig = new AuthorizationConfig(serverName,
        authorizationPolicyFile, policyProviderClassName);
    authorizationConfig.validateConfig();
    frontend = new Frontend(authorizationConfig);
  }

  /**
   * Jni wrapper for Frontend.createQueryExecRequest2(). Accepts a serialized
   * TClientRequest; returns a serialized TQueryExecRequest2.
   */
  public byte[] createExecRequest(byte[] thriftClientRequest)
      throws ImpalaException {
    TClientRequest request = new TClientRequest();
    JniUtil.deserializeThrift(protocolFactory, request, thriftClientRequest);

    StringBuilder explainString = new StringBuilder();
    TExecRequest result = frontend.createExecRequest(request, explainString);
    LOG.debug(explainString.toString());

    // TODO: avoid creating serializer for each query?
    TSerializer serializer = new TSerializer(protocolFactory);
    try {
      return serializer.serialize(result);
    } catch (TException e) {
      throw new InternalException(e.getMessage());
    }
  }

  public byte[] updateCatalogCache(byte[] thriftCatalogUpdate) throws ImpalaException {
    TUpdateCatalogCacheRequest req = new TUpdateCatalogCacheRequest();
    JniUtil.deserializeThrift(protocolFactory, req, thriftCatalogUpdate);
    TSerializer serializer = new TSerializer(protocolFactory);
    try {
      return serializer.serialize(frontend.updateCatalogCache(req));
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
    JniUtil.deserializeThrift(protocolFactory, request, thriftLoadTableDataParams);
    TLoadDataResp response = frontend.loadTableData(request);
    TSerializer serializer = new TSerializer(protocolFactory);
    try {
      return serializer.serialize(response);
    } catch (TException e) {
      throw new InternalException(e.getMessage());
    }
  }

  /**
   * Return an explain plan based on thriftQueryRequest, a serialized TQueryRequest.
   * This call is thread-safe.
   */
  public String getExplainPlan(byte[] thriftQueryRequest) throws ImpalaException {
    TClientRequest request = new TClientRequest();
    JniUtil.deserializeThrift(protocolFactory, request, thriftQueryRequest);
    String plan = frontend.getExplainString(request);
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
    JniUtil.deserializeThrift(protocolFactory, params, thriftGetTablesParams);
    // If the session was not set it indicates this is an internal Impala call.
    User user = params.isSetSession() ?
        new User(params.getSession().getUser()) : ImpalaInternalAdminUser.getInstance();

    Preconditions.checkState(!params.isSetSession() || user != null );
    List<String> tables = frontend.getTableNames(params.db, params.pattern, user);

    TGetTablesResult result = new TGetTablesResult();
    result.setTables(tables);

    TSerializer serializer = new TSerializer(protocolFactory);
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
    JniUtil.deserializeThrift(protocolFactory, params, thriftGetTablesParams);
    // If the session was not set it indicates this is an internal Impala call.
    User user = params.isSetSession() ?
        new User(params.getSession().getUser()) : ImpalaInternalAdminUser.getInstance();
    List<String> dbs = frontend.getDbNames(params.pattern, user);

    TGetDbsResult result = new TGetDbsResult();
    result.setDbs(dbs);

    TSerializer serializer = new TSerializer(protocolFactory);
    try {
      return serializer.serialize(result);
    } catch (TException e) {
      throw new InternalException(e.getMessage());
    }
  }

  public byte[] getStats(byte[] thriftShowStatsParams) throws ImpalaException {
    TShowStatsParams params = new TShowStatsParams();
    JniUtil.deserializeThrift(protocolFactory, params, thriftShowStatsParams);
    Preconditions.checkState(params.isSetTable_name());
    TResultSet result;
    if (params.isIs_show_col_stats()) {
      result = frontend.getColumnStats(params.getTable_name().getDb_name(),
          params.getTable_name().getTable_name());
    } else {
      result = frontend.getTableStats(params.getTable_name().getDb_name(),
          params.getTable_name().getTable_name());
    }
    TSerializer serializer = new TSerializer(protocolFactory);
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
    JniUtil.deserializeThrift(protocolFactory, params, thriftGetFunctionsParams);

    TGetFunctionsResult result = new TGetFunctionsResult();
    result.setFn_signatures(
        frontend.getFunctions(params.type, params.db, params.pattern));
    TSerializer serializer = new TSerializer(protocolFactory);
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
    JniUtil.deserializeThrift(protocolFactory, objectDescription, thriftParams);
    TSerializer serializer = new TSerializer(protocolFactory);
    return serializer.serialize(
        frontend.getCatalog().getTCatalogObject(objectDescription));
  }

  /**
   * Returns a list of the columns making up a table.
   * The argument is a serialized TDescribeTableParams object.
   * The return type is a serialised TDescribeTableResult object.
   * @see Frontend#describeTable
   */
  public byte[] describeTable(byte[] thriftDescribeTableParams) throws ImpalaException {
    TDescribeTableParams params = new TDescribeTableParams();
    JniUtil.deserializeThrift(protocolFactory, params, thriftDescribeTableParams);

    TDescribeTableResult result = frontend.describeTable(
        params.getDb(), params.getTable_name(), params.getOutput_style());

    TSerializer serializer = new TSerializer(protocolFactory);
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
    JniUtil.deserializeThrift(protocolFactory, params, thriftTableName);
    return ToSqlUtils.getCreateTableSql(frontend.getCatalog().getTable(
        params.getDb_name(), params.getTable_name(),
        ImpalaInternalAdminUser.getInstance(), Privilege.ALL));
  }

  /**
   * Executes a HiveServer2 metadata operation and returns a TResultSet
   */
  public byte[] execHiveServer2MetadataOp(byte[] metadataOpsParams)
      throws ImpalaException {
    TMetadataOpRequest params = new TMetadataOpRequest();
    JniUtil.deserializeThrift(protocolFactory, params, metadataOpsParams);
    TResultSet result = frontend.execHiveServer2MetadataOp(params);

    TSerializer serializer = new TSerializer(protocolFactory);
    try {
      return serializer.serialize(result);
    } catch (TException e) {
      throw new InternalException(e.getMessage());
    }
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

}
