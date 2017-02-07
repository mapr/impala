#!/bin/bash
this=$_



#############################################################################
# Stage runtime files from the development "built" environment to stage directory
################################################################################
main() # <built_directory>  <stage_directory>
{
    setup_environment "$@"
    echo "Staging impala files"
    remove_directories
    copy_impala_files
    copy_mapr_files
    echo "export IMPALA_VERSION=$IMPALA_VERSION" > $STAGE/mapr/IMPALA_VERSION.sh
    echo "export BUILT=$BUILT"                   > $STAGE/mapr/BUILT.sh
    echo "Done"
}


##############################################################
# Setup env variables.
#   When creating RPMs on a build machine, $1 and $2 will be specified.
#   When staging on a development machine, $1 and $2 will be blank
##############################################################
setup_environment() # <built_directory>  <stage_directory>
{
BUILT=${1:-../..}                  # Where impala was recently built
STAGE=${2:-/var/tmp/mapr-impala}   # Where we are staging the runtime files
BUILD_TYPE=`echo ${TARGET_BUILD_TYPE:-release} | tr [:upper:]  [:lower:]`  # debug | release

BUILT=`(cd $BUILT; pwd)`  # get the full path
IMPALA_VERSION=$(awk '$1=="VERSION:"{print $2}' $BUILT/bin/version.info)
thisdir=${this%/*}
}



remove_directories() {

    # Special situation when staging directly from development environment.
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
    #cp $BUILT/be/build/$BUILD_TYPE/service/impalad  $STAGE/sbin
    #cp $BUILT/be/build/$BUILD_TYPE/statestore/statestored $STAGE/sbin
    #cp $BUILT/be/build/$BUILD_TYPE/catalog/catalogd $STAGE/sbin

    #cp $BUILT/be/build/$BUILD_TYPE/service/libfesupport.so  $STAGE/sbin

    cp $BUILT/be/build/$BUILD_TYPE/service/impalad  $STAGE/sbin/daemon
    link_daemons

    if [[ $BUILD_TYPE == release ]]; then
        strip $STAGE/sbin/daemon
    fi

    # copy the impala front end jar
    mkdir -p $STAGE/lib

    cp $BUILT/fe/target/dependency/*.jar $STAGE/lib

    #
    # Remove all *mapr*jar dependency jars, except sentry*mapr*jar jars.
    # All non-sentry MapR jars will be added to the classpath
    #
    find $STAGE/lib -type f -name "*mapr*jar" ! -name '*sentry*' -exec rm \{\} \;

    # copy the impala front end jar
    cp $BUILT/fe/target/impala-frontend-0.1-SNAPSHOT-mapr.jar $STAGE/lib/.

    # Stage a log directory
    mkdir -p $STAGE/logs

    # copy some UDF files
    mkdir -p $STAGE/include
    mkdir -p $STAGE/include/impala_udf
    cp $BUILT/be/src/udf/*.h     $STAGE/include/impala_udf

    cp $BUILT/be/build/$BUILD_TYPE/udf/libImpalaUdf.a $STAGE/lib

    # copy libstdc++
    cp $BUILT/toolchain/gcc-4.9.2/lib64/libstdc++.so.6.0.20 $STAGE/lib/libstdc++.so.6

    # copy libkudu_client
    cp $BUILT/toolchain/kudu-0.10.0-RC1/$BUILD_TYPE/lib64/libkudu_client* $STAGE/lib/
}


copy_mapr_files() {
    cp -r $BUILT/mapr $STAGE
}


copy_lib() {
    cp ${1%/*}/$(readlink $1) $2
}

link_daemons() {
    popd `pwd`
    cd $STAGE/sbin
    ln -s daemon impalad
    ln -s daemon catalogd
    ln -s daemon statestored
    pushd
}

main "$@"
