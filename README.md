# Impala on MapR

Impala is a distributed query execution engine that runs against data stored natively in MapRFS and HBase.


# Building Impala on CentOS 6.3

## Prerequisites

### Installing prerequisite packages

    sudo yum install libevent-devel automake libtool flex bison \
        gcc-c++ openssl-devel make cmake doxygen.x86_64 python-devel \
        bzip2-devel svn libevent-devel cyrus-sasl-devel wget git \
        unzip rpm-build libicu-devel

*Note:* Impala requires Boost 1.42 or later.

### Install MapR packages

    Create a repository file:  /etc/yum.repos.d/maprtech.repo
        [maprtech]
        name=MapR Technologies
        baseurl=http://package.mapr.com/releases/v3.0.2/redhat
        enabled=1
        gpgcheck=0
    
    Create repository file: /etc/yum.repos.d/maprecosystem.repo
        [maprecosystem]
        name=MapR Technologies
        baseurl=http://package.mapr.com/releases/ecosystem/redhat
        enabled=1
        gpgcheck=0

    sudo yum install mapr-core mapr-hive

### Install Boost

    Create repository file: /etc/yum.repos.d/jur-linux.repo
        [jur-linux]
        name=Jur Linux
        baseurl=http://jur-linux.org/download/el-updates/6.3/x86_64/
        gpgcheck=0
        enabled=1

    sudo yum install  libicu-devel chrpath openmpi-devel mpich2-devel
    sudo yum install python3-devel

    wget ftp://ftp.icm.edu.pl/vol/rzm2/linux-fedora-secondary/development/rawhide/source/SRPMS/b/boost-1.53.0-6.fc19.src.rpm
    rpmbuild --rebuild boost-1.53.0-6.fc19.src.rpm

    sudo rpm -ivh ~/rpmbuild/RPMS/x86_64/*

    Make the following change to /usr/include/boost/move/core.hpp:

       class rv
          : public ::boost::move_detail::if_c
             < ::boost::move_detail::is_class_or_union<T>::value
             , T
             , ::boost::move_detail::empty
             >::type
       {
          rv();
    ---  ~rv();
    +++  ~rv() throw();
          rv(rv const&);
          void operator=(rv const&);
       } BOOST_MOVE_ATTRIBUTE_MAY_ALIAS;

*Note:* Ubuntu 12.04 (and later) requires the libevent1-dev package to work with Thrift v0.9

### Install LLVM

    wget http://llvm.org/releases/3.3/llvm-3.3.src.tar.gz
    tar xvzf llvm-3.3.src.tar.gz
    cd llvm-3.3.src/tools
    svn co http://llvm.org/svn/llvm-project/cfe/tags/RELEASE_33/final/ clang
    cd ../projects
    svn co http://llvm.org/svn/llvm-project/compiler-rt/tags/RELEASE_33/final/ compiler-rt
    cd ..
    ./configure --with-pic
    make -j4 REQUIRES_RTTI=1
    sudo make install
    
### Install the JDK

Make sure that the Oracle Java Development Kit 6 is installed (not OpenJDK), and that `JAVA_HOME` is set in your environment.

### Install Maven

    wget http://www.fightrice.com/mirrors/apache/maven/maven-3/3.0.5/binaries/apache-maven-3.0.5-bin.tar.gz
    tar xvf apache-maven-3.0.5.tar.gz && sudo mv apache-maven-3.0.5 /usr/local
   
Add the following three lines to your .bashrc:

    export M2_HOME=/usr/local/apache-maven-3.0.5
    export M2=$M2_HOME/bin  
    export PATH=$M2:$PATH 

And make sure you pick up the changes either by logging in to a fresh shell or running:

    source ~/.bashrc

Confirm by running:

    mvn -version

and you should see at least:

    Apache Maven 3.0.5...

## Building Impala

### Clone the Impala repository

    git clone https://github.com/mapr/private-impala

### Set the Impala environment
  
    cd impala
    . bin/impala-config.sh

Confirm your environment looks correct:

    env | grep "IMPALA.*VERSION"

        IMPALA_AVRO_VERSION=1.7.1-cdh4.2.0
        IMPALA_CYRUS_SASL_VERSION=2.1.23
        IMPALA_HBASE_VERSION=0.94.9-mapr
        IMPALA_SNAPPY_VERSION=1.0.5
        IMPALA_GTEST_VERSION=1.6.0
        IMPALA_GPERFTOOLS_VERSION=2.0
        IMPALA_GFLAGS_VERSION=2.0
        IMPALA_GLOG_VERSION=0.3.2
        IMPALA_HADOOP_VERSION=1.0.3-mapr-3.0.0
        IMPALA_HIVE_VERSION=0.11-mapr
        IMPALA_MONGOOSE_VERSION=3.3
        IMPALA_THRIFT_VERSION=0.9.0

### Download required third-party packages

    cd thirdparty
    ./download_thirdparty.sh

### Build Impala

    cd ${IMPALA_HOME}
    ./build_public.sh -build_thirdparty

## Wrapping up

After a successful build, there should be an `impalad` binary in `${IMPALA_HOME}/be/build/release/service`.

You can start an Impala backend by running:

    ${IMPALA_HOME}/bin/start-impalad.sh -use_statestore=false

Note that the `start-impalad.sh` script sets some environment variables that are necessary for Impala to run successfully.

To configure Impala's use of HDFS, HBase or the Hive metastore, place the relevant configuration files somewhere in the `CLASSPATH` established by `bin/set-classpath.sh`. Internally we use `fe/src/test/resources` for this purpose, you may find it convenient to do the same.

## The Impala Shell

The Impala shell is a convenient command-line interface to Impala. To run from a source repository, do the following:

    ${IMPALA_HOME}/bin/impala-shell.sh
