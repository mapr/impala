#!/bin/bash
setCLASSPATH() {
    local h=$HADOOP_HOME/lib

    # get the jars associated with impala
    CLASSPATH+=:$(getPath ${IMPALA_HOME}/lib/*.jar)

    # get jars from the other modules
    CLASSPATH+=:$(getPath $HIVE_HOME/lib/*.jar)
    CLASSPATH+=:$(getPath $HADOOP_HOME/lib/*.jar)
    CLASSPATH+=:$(getPath $HBASE_HOME/*.jar)

    # set the class path to pick up configuration files
    CLASSPATH+=:/opt/mapr/impala/impala-1.2.3/conf:$HIVE_SITE_DIR:$HADOOP_HOME/conf:$HBASE_HOME/conf:
    export CLASSPATH

    # as a last resort, pick up files which would be preferred to
    # come from other modules earlier on the class path.
    CLASSPATH+=:$(getPath ${IMPALA_HOME}/lib/fallback/*.jar)
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

