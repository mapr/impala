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
import java.io.InvalidObjectException;
import java.net.URL;
import java.net.URLConnection;
import java.rmi.NoSuchObjectException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Properties;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hive.metastore.api.AlreadyExistsException;
import org.apache.hadoop.hive.metastore.api.InvalidOperationException;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.thrift.TBase;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.log4j.PropertyConfigurator;

import com.cloudera.impala.authorization.AuthorizationConfig;
import com.cloudera.impala.authorization.ImpalaInternalAdminUser;
import com.cloudera.impala.authorization.User;
import com.cloudera.impala.catalog.TableLoadingException;
import com.cloudera.impala.common.FileSystemUtil;
import com.cloudera.impala.common.ImpalaException;
import com.cloudera.impala.common.InternalException;
import com.cloudera.impala.thrift.TAlterTableParams;
import com.cloudera.impala.thrift.TCatalogUpdate;
import com.cloudera.impala.thrift.TClientRequest;
import com.cloudera.impala.thrift.TCreateDbParams;
import com.cloudera.impala.thrift.TCreateOrAlterViewParams;
import com.cloudera.impala.thrift.TCreateTableLikeParams;
import com.cloudera.impala.thrift.TCreateTableParams;
import com.cloudera.impala.thrift.TDescribeTableParams;
import com.cloudera.impala.thrift.TDescribeTableResult;
import com.cloudera.impala.thrift.TDropDbParams;
import com.cloudera.impala.thrift.TDropTableOrViewParams;
import com.cloudera.impala.thrift.TExecRequest;
import com.cloudera.impala.thrift.TGetDbsParams;
import com.cloudera.impala.thrift.TGetDbsResult;
import com.cloudera.impala.thrift.TGetTablesParams;
import com.cloudera.impala.thrift.TGetTablesResult;
import com.cloudera.impala.thrift.TLoadDataReq;
import com.cloudera.impala.thrift.TLoadDataResp;
import com.cloudera.impala.thrift.TMetadataOpRequest;
import com.cloudera.impala.thrift.TMetadataOpResponse;
import com.cloudera.impala.thrift.TResetMetadataParams;
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
      String policyProviderClassName) throws InternalException {
    InitLogging();

    // Validate the authorization configuration before initializing the Frontend.
    // If there are any configuration problems Impala startup will fail.
    AuthorizationConfig authorizationConfig = new AuthorizationConfig(serverName,
        authorizationPolicyFile, policyProviderClassName);
    authorizationConfig.validateConfig();
    frontend = new Frontend(lazy, authorizationConfig);
  }

  /**
   * Manually override Log4j root logger configuration. Any values in log4j.properties not
   * overridden (that is, anything but the root logger and the base threshhold) will
   * continue to have effect.
   */
  private static void InitLogging() {
    Properties properties = new Properties();
    properties.setProperty("log4j.appender.glog",
                           GlogAppender.class.getName());
    properties.setProperty("log4j.rootLogger", "info,glog");
    properties.setProperty("log4j.threshhold", "INFO");
    PropertyConfigurator.configure(properties);
  }

  /**
   * Deserialized a serialized form of a Thrift data structure to its object form
   */
  private <T extends TBase> void deserializeThrift(T result, byte[] thriftData)
      throws ImpalaException {
    // TODO: avoid creating deserializer for each query?
    TDeserializer deserializer = new TDeserializer(protocolFactory);

    try {
      deserializer.deserialize(result, thriftData);
    } catch (TException e) {
      throw new InternalException(e.getMessage());
    }
  }

  /**
   * Jni wrapper for Frontend.createQueryExecRequest2(). Accepts a serialized
   * TClientRequest; returns a serialized TQueryExecRequest2.
   */
  public byte[] createExecRequest(byte[] thriftClientRequest)
      throws ImpalaException {
    TClientRequest request = new TClientRequest();
    deserializeThrift(request, thriftClientRequest);

    StringBuilder explainString = new StringBuilder();
    TExecRequest result = frontend.createExecRequest(request, explainString);
    LOG.info(explainString.toString());

    // TODO: avoid creating serializer for each query?
    TSerializer serializer = new TSerializer(protocolFactory);
    try {
      return serializer.serialize(result);
    } catch (TException e) {
      throw new InternalException(e.getMessage());
    }
  }

  public void alterTable(byte[] thriftAlterTableParams)
      throws ImpalaException, MetaException, org.apache.thrift.TException,
      InvalidObjectException, ImpalaException, TableLoadingException {
    TAlterTableParams params = new TAlterTableParams();
    deserializeThrift(params, thriftAlterTableParams);
    frontend.getDdlExecutor().alterTable(params);
  }

  public void alterView(byte[] thriftAlterViewParams)
      throws ImpalaException, MetaException, org.apache.thrift.TException,
      InvalidObjectException, ImpalaException, TableLoadingException {
    TCreateOrAlterViewParams params = new TCreateOrAlterViewParams();
    deserializeThrift(params, thriftAlterViewParams);
    frontend.getDdlExecutor().alterView(params);
  }

  public void createDatabase(byte[] thriftCreateDbParams)
      throws ImpalaException, MetaException, org.apache.thrift.TException,
      AlreadyExistsException, InvalidObjectException {
    TCreateDbParams params = new TCreateDbParams();
    deserializeThrift(params, thriftCreateDbParams);
    frontend.getDdlExecutor().createDatabase(params);
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
    deserializeThrift(request, thriftLoadTableDataParams);
    TLoadDataResp response = frontend.loadTableData(request);
    TSerializer serializer = new TSerializer(protocolFactory);
    try {
      return serializer.serialize(response);
    } catch (TException e) {
      throw new InternalException(e.getMessage());
    }
  }

  public void createTable(byte[] thriftCreateTableParams)
      throws ImpalaException, MetaException, NoSuchObjectException,
      org.apache.thrift.TException, AlreadyExistsException,
      InvalidObjectException {
    TCreateTableParams params = new TCreateTableParams();
    deserializeThrift(params, thriftCreateTableParams);
    frontend.getDdlExecutor().createTable(params);
  }

  public void createView(byte[] thriftCreateViewParams)
      throws ImpalaException, MetaException, NoSuchObjectException,
      org.apache.thrift.TException, AlreadyExistsException,
      InvalidObjectException {
    TCreateOrAlterViewParams params = new TCreateOrAlterViewParams();
    deserializeThrift(params, thriftCreateViewParams);
    frontend.getDdlExecutor().createView(params);
  }

  public void createTableLike(byte[] thriftCreateTableLikeParams)
      throws ImpalaException, MetaException, NoSuchObjectException,
      org.apache.thrift.TException, AlreadyExistsException, InvalidObjectException,
      TableLoadingException {
    TCreateTableLikeParams params = new TCreateTableLikeParams();
    deserializeThrift(params, thriftCreateTableLikeParams);
    frontend.getDdlExecutor().createTableLike(params);
  }

  public void dropDatabase(byte[] thriftDropDbParams)
      throws ImpalaException, MetaException, NoSuchObjectException,
      org.apache.thrift.TException, AlreadyExistsException, InvalidOperationException,
      InvalidObjectException {
    TDropDbParams params = new TDropDbParams();
    deserializeThrift(params, thriftDropDbParams);
    frontend.getDdlExecutor().dropDatabase(params);
  }

  public void dropTableOrView(byte[] thriftDropTableObjectParams)
      throws ImpalaException, MetaException, NoSuchObjectException,
      org.apache.thrift.TException, AlreadyExistsException, InvalidOperationException,
      InvalidObjectException {
    TDropTableOrViewParams params = new TDropTableOrViewParams();
    deserializeThrift(params, thriftDropTableObjectParams);
    frontend.getDdlExecutor().dropTableOrView(params);
  }

  /**
   * Return an explain plan based on thriftQueryRequest, a serialized TQueryRequest.
   * This call is thread-safe.
   */
  public String getExplainPlan(byte[] thriftQueryRequest) throws ImpalaException {
    TClientRequest request = new TClientRequest();
    deserializeThrift(request, thriftQueryRequest);
    String plan = frontend.getExplainString(request);
    LOG.info("Explain plan: " + plan);
    return plan;
  }

  /**
   * Process any updates to the metastore required after a query executes.
   * The argument is a serialized TCatalogUpdate.
   * @see Frontend#updateMetastore
   */
  public void updateMetastore(byte[] thriftCatalogUpdate) throws ImpalaException {
    TCatalogUpdate update = new TCatalogUpdate();
    deserializeThrift(update, thriftCatalogUpdate);
    frontend.updateMetastore(update);
  }

  /**
   * Returns a list of table names matching an optional pattern.
   * The argument is a serialized TGetTablesParams object.
   * The return type is a serialised TGetTablesResult object.
   * @see Frontend#getTableNames
   */
  public byte[] getTableNames(byte[] thriftGetTablesParams) throws ImpalaException {
    TGetTablesParams params = new TGetTablesParams();
    deserializeThrift(params, thriftGetTablesParams);
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
    deserializeThrift(params, thriftGetTablesParams);
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

  /**
   * Returns a list of the columns making up a table.
   * The argument is a serialized TDescribeTableParams object.
   * The return type is a serialised TDescribeTableResult object.
   * @see Frontend#describeTable
   */
  public byte[] describeTable(byte[] thriftDescribeTableParams) throws ImpalaException {
    TDescribeTableParams params = new TDescribeTableParams();
    deserializeThrift(params, thriftDescribeTableParams);

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
   * Executes a HiveServer2 metadata operation and returns a TMetadataOpResponse
   */
  public byte[] execHiveServer2MetadataOp(byte[] metadataOpsParams)
      throws ImpalaException {
    TMetadataOpRequest params = new TMetadataOpRequest();
    deserializeThrift(params, metadataOpsParams);
    TMetadataOpResponse result = frontend.execHiveServer2MetadataOp(params);

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


    return output.toString();
  }

  public void resetMetadata(byte[] thriftResetMetadataRequest)
      throws ImpalaException {
    TResetMetadataParams request = new TResetMetadataParams();
    deserializeThrift(request, thriftResetMetadataRequest);
    frontend.execResetMetadata(request);
  }
}
