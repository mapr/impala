#!/bin/bash

setCLASSPATH() {
    local h=$HADOOP_HOME/lib

    # Add the hadoop jars to classpath
    CLASSPATH+=:$(getPath ${HADOOP_HOME}/share/hadoop/yarn/hadoop-yarn-server-web-proxy*.jar)
    CLASSPATH+=:$(getPath ${HADOOP_HOME}/share/hadoop/yarn/hadoop-yarn-common*.jar)
    CLASSPATH+=:$(getPath ${HADOOP_HOME}/share/hadoop/yarn/hadoop-yarn-server-common*.jar)
    CLASSPATH+=:$(getPath ${HADOOP_HOME}/share/hadoop/yarn/hadoop-yarn-api*.jar)
    CLASSPATH+=:$(getPath ${HADOOP_HOME}/share/hadoop/yarn/hadoop-yarn-server-applicationhistoryservice*.jar)
    CLASSPATH+=:$(getPath ${HADOOP_HOME}/share/hadoop/yarn/hadoop-yarn-server-resourcemanager*.jar)
    CLASSPATH+=:$(getPath ${HADOOP_HOME}/share/hadoop/common/lib/hadoop-annotations*.jar)
    CLASSPATH+=:$(getPath ${HADOOP_HOME}/share/hadoop/tools/lib/hadoop-auth*.jar)
    CLASSPATH+=:$(getPath ${HADOOP_HOME}/share/hadoop/hdfs/hadoop-hdfs*.jar)
    CLASSPATH+=:$(getPath ${HADOOP_HOME}/share/hadoop/mapreduce/hadoop-mapreduce-client-core*.jar)
    CLASSPATH+=:$(getPath /opt/mapr/lib/hadoop-common*.jar)
    CLASSPATH+=:$(getPath ${HADOOP_HOME}/share/hadoop/common/*.jar)
    CLASSPATH+=:$(getPath ${HADOOP_HOME}/share/hadoop/common/lib/*.jar)
    CLASSPATH+=:$(getPath ${HADOOP_HOME}/share/hadoop/tools/lib/*.jar)
    CLASSPATH+=:$(getPath ${HADOOP_HOME}/share/hadoop/hdfs/*.jar)
    CLASSPATH+=:$(getPath ${HADOOP_HOME}/share/hadoop/mapreduce/*.jar)
    CLASSPATH+=:$(getPath /opt/mapr/lib/maprfs*.jar)
    CLASSPATH+=:$(getPath /opt/mapr/lib/zookeeper*.jar)
    CLASSPATH+=:$(getPath /opt/mapr/lib/baseutils*.jar)
    CLASSPATH+=:$(getPath /opt/mapr/lib/libprotodefs*.jar)
    CLASSPATH+=:$(getPath /opt/mapr/lib/central-logging*.jar)
    CLASSPATH+=:$(getPath ${HIVE_HOME}/lib/hive*.jar)
    CLASSPATH+=:$(getPath ${HBASE_HOME}/lib/hbase*.jar)

    # get the jars associated with impala
    CLASSPATH+=:$(getPath ${IMPALA_HOME}/lib/*.jar)

    # set the class path to pick up configuration files
    CLASSPATH+=:/opt/mapr/impala/impala-2.10.0/conf:$HIVE_SITE_DIR:$HADOOP_HOME/conf:$HBASE_HOME/conf:
    export CLASSPATH
}



##################################################################3
# create a path from a list of things - adds ":"
################################################333
getPath() {  # <list of things on path>
    local path=$1; shift
    local p;
    for p in "$@"; do
        path+=:$p
    done
    echo $path
}

#
# For hive-site.xml 
#

findHiveSite()
{
    # CASE: we have our own XML which was not generated automatically, use it
    if [ -f ${IMPALA_HOME}/conf/hive-site.xml ]   \
       && ! grep -q GENERATED ${IMPALA_HOME}/conf/hive-site.xml; then
         echo "${IMPALA_HOME}/conf"

    # CASE: valid HIVE xml, use it
    elif grep -q hive.metastore.uris $HIVE_HOME/conf/hive-site.xml; then
        echo $HIVE_HOME/conf

    # OTHERWISE, error
    else
        echo ""
    fi
}


############################################################
# Find the home directory where a package is installed
###########################################################
findHome() { # package
    local name=$1
    # The following works in bash-4 and later
    #local home=${name^^}_HOME  # name of variable.  eg. IMPALA_HOME
    local home=$(toupper $name)_HOME  # name of variable.  eg. IMPALA_HOME

    # CASE: environment variable is set, then return it's value
    if [ ! -z "${!home}" ]; then
        echo ${!home}

    # CASE: not installed, return empty
    elif [ ! -d /opt/mapr/$name ]; then
        echo

    # CASE: has a version file. use it.
    elif [ -f /opt/mapr/$name/${name}version ]; then
        local version=`cat /opt/mapr/$name/${name}version`
        echo /opt/mapr/$name/$name-$version

    # OTHERWISE, get directory of highest version installed so far
    else
        local dir
        for dir in /opt/mapr/$name/*; do
           echo >/dev/null # need something here
        done
        echo $dir
    fi
}


#################################################################
# Convert the string to upper case
#   Note: in bash-4, this is accomplished with the "^^" substitution
#################################################################
toupper() {
    echo $* | tr [:lower:]  [:upper:]
}
