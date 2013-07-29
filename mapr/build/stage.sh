#!/bin/bash  -x
this=$_

main() # <staging directory>
{
    setup_environment "$@"
    echo "Staging impala files"
    remove_directories
    copy_impala_files
    copy_mapr_files
    copy_boost_files
    echo "export IMPALA_VERSION=$IMPALA_VERSION" > $STAGE/mapr/IMPALA_VERSION.sh
    echo "export BUILT=$BUILT"                   > $STAGE/mapr/BUILT.sh
    echo "Done"
}


setup_environment() # <staging directory>
{
thisdir=${this%/*}
BUILT=${1:-../..}         # Where impala was recently built
BUILT=`(cd $BUILT; pwd)`  # get the full path
IMPALA_VERSION=$(awk '$1=="VERSION:"{print $2}' $BUILT/bin/version.info)
STAGE=${2:-/var/tmp/mapr-impala}   # Where we are staging the runtime files
}


remove_directories() {

    # Special check when staging directly from development environment.
    #    This should not happen during normal RPM creation on a build machine.
    if [ "$STAGE" == /var/tmp/mapr-impala ]; then
        rm -rf /var/tmp/mapr-impala
    fi

    # check if the STAGE directory exists before looking inside of it
    if [ ! -d $STAGE ]; then
        echo Staging directory $STAGE not found, creating it now
        mkdir -p $STAGE
    fi

    # the STAGE directory should be empty. Otherwise, something is fishy.
    if [ ! -z "$(ls $STAGE)" ]; then
        echo "The staging directory already has contents. "
        echo "  We could blow away the contents, but we will remain "
        echo "  cautious and ask it be cleaned up by hand. "
        echo "The contents of $STAGE are:"
        echo `ls -a $STAGE`
        exit 1
    fi
}



copy_impala_files() {
    mkdir -p $STAGE

    # copy run time directories
    cp -r $BUILT/llvm-ir $STAGE
    cp -r $BUILT/www     $STAGE
    cp -r $BUILT/shell/build/impala-shell-$IMPALA_VERSION $STAGE/shell

    # copy binary files
    mkdir $STAGE/sbin
    cp $BUILT/be/build/release/service/impalad  $STAGE/sbin
    #cp $BUILT/be/build/release/service/libfesupport.so  $STAGE/sbin
    cp $BUILT/be/build/release/statestore/statestored $STAGE/sbin

    # copy the impala front end jar
    mkdir -p $STAGE/lib
    cp $BUILT/fe/target/impala-frontend-*-mapr.jar $STAGE/lib/impala-front-end.jar
    # copy the front end dependencies
    local dependencies="
               commons-cli commons-dbcp commons-lang
               java-cup guava libfb303 libthrift log4j 
               postgresql parquet
               sentry-binding-hive sentry-core sentry-provider-file
               shiro-core
               slf4j-api slf4j-log4j12"

    local d
    for d in $dependencies; do
        cp $BUILT/fe/target/dependency/${d}*.jar $STAGE/lib
    done

    # copy some "fall back" dependencies.
    #   These are only used if other packages (eg. hbase) aren't installed
    dependencies="hbase"
    mkdir -p $STAGE/lib/fallback
    for d in $dependencies; do
        cp $BUILT/fe/target/dependency/${d}*.jar $STAGE/lib/fallback
    done

    # Stage a log directory
    mkdir -p $STAGE/logs
}


copy_mapr_files() {
    cp -r $BUILT/mapr $STAGE
}

copy_boost_files() {
    local l=/usr/lib64
    copy_lib $l/libboost_thread-mt.so $STAGE/lib
    copy_lib $l/libboost_system-mt.so $STAGE/lib
    copy_lib $l/libboost_filesystem-mt.so $STAGE/lib
    copy_lib $l/libboost_date_time-mt.so $STAGE/lib
    copy_lib $l/libboost_regex-mt.so $STAGE/lib

    # specific fix for libboost_threat-mt.so, which is actually a linker script that pulls in two other files
    BOOST_THREAD_FIND_RESULTS=`find $STAGE/lib -n "libboost_thread-mt*"`
    echo "Looking to see if we got libboost_thread.mt.so"
    echo "$BOOST_THREAD_FIND_RESULTS"
    if [[ -z "$BOOST_THREAD_FIND_RESULTS" ]]; then
        echo "libboost_thread.mt.so was not found the first time"
        echo "trying to copy it now..."
        cp -vf $l/libboost_thread-mt.so.1.* $STAGE/lib/.
    fi
}

copy_lib() {
    cp ${1%/*}/$(readlink $1) $2
}


main "$@"
