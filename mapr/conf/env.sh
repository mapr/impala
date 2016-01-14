# these are configured automatically when impala is installed
export IMPALA_HOME=$IMPALA_HOME
export MAPR_HOME=$MAPR_HOME
export IMPALA_VERSION=$IMPALA_VERSION
export LIBHDFS_OPTS="-Dhadoop.login=hybrid -Dhadoop.login=hybrid_keytab -Djavax.security.auth.useSubjectCredsOnly=false"

# Get the generic mapr environment variables
[ -f ${MAPR_HOME}/conf/env.sh ] && . ${MAPR_HOME}/conf/env.sh

# This MUST point to the node running statestore
IMPALA_STATE_STORE_HOST=localhost
IMPALA_STATE_STORE_PORT=24000
CATALOG_SERVICE_HOST=localhost

#Set the Shared Memory to 128 MB
export MAPR_CLIENT_SHMEM=16384

# These impact the impala server and can be optionally changed
IMPALA_BACKEND_PORT=22000
IMPALA_LOG_DIR=${IMPALA_HOME}/logs
IMPALA_SERVER_ARGS=" \
    -log_dir=${IMPALA_LOG_DIR} \
    -state_store_port=${IMPALA_STATE_STORE_PORT} \
    -use_statestore \
    -authorized_proxy_user_config=mapr=* \
    -state_store_host=${IMPALA_STATE_STORE_HOST} \
    -catalog_service_host=${CATALOG_SERVICE_HOST} \
    -be_port=${IMPALA_BACKEND_PORT} \
    "

# These impact the state store daemon and can be optionally changed
IMPALA_STATE_STORE_ARGS=" \
    -log_dir=${IMPALA_LOG_DIR} \
    -state_store_port=${IMPALA_STATE_STORE_PORT} \
    -catalog_service_host=${CATALOG_SERVICE_HOST} \
    "

IMPALA_CATALOG_ARGS=" -log_dir=${IMPALA_LOG_DIR} \
    -state_store_port=${IMPALA_STATE_STORE_PORT} \
    -use_statestore \
    -state_store_host=${IMPALA_STATE_STORE_HOST} \
"

# for troubleshooting
ENABLE_CORE_DUMPS=false

# Impala figures these out at runtime, but they can be overridden here.
#    (Normally, they should be commented out.)
# HIVE_HOME=${MAPR_HOME}/hive/hive-*
# HBASE_HOME=${MAPR_HOME}/hbase/hbase-*
# HADOOP_HOME=${MAPR_HOME}/hadoop/hodoop-*

# No longer used ...
# LIBHDFS_OPTS=-Djava.library.path=/usr/lib/impala/lib
# MYSQL_CONNECTOR_JAR=/usr/share/java/mysql-connector-java.jar

