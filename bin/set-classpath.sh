#!/bin/sh
# Copyright 2012 Cloudera Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This script explicitly sets the CLASSPATH for embedded JVMs (e.g. in
# Impalad or in runquery) Because embedded JVMs do not honour
# CLASSPATH wildcard expansion, we have to add every dependency jar
# explicitly to the CLASSPATH.

CLASSPATH=\
$IMPALA_HOME/fe/target/classes:\
$IMPALA_HOME/fe/target/dependency:\
$IMPALA_HOME/fe/target/test-classes:\
${HIVE_HOME}/lib/datanucleus-api-jdo-3.2.1.jar:\
${HIVE_HOME}/lib/datanucleus-core-3.2.2.jar:\
${HIVE_HOME}/lib/datanucleus-rdbms-3.2.1.jar:\
/opt/mapr/conf

for jar in `ls ${IMPALA_HOME}/fe/target/dependency/*.jar`; do
  CLASSPATH=${CLASSPATH}:$jar
done

#for jar in `ls /opt/mapr/hadoop/hadoop-0.20.2/lib/*.jar`; do
#  CLASSPATH=${CLASSPATH}:$jar
#done
#CLASSPATH=/opt/mapr/impala/impala-1.1.1/conf:${CLASSPATH}

export CLASSPATH
