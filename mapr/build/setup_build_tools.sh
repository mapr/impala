#!/bin/bash -x

OS="redhat"
IMPALA_HOME=$(cd ../../../$(dirname $0) 2>/dev/null && echo $(pwd))

###################################################################
# Setup the build machine with the tools needed to build impala
###################################################################
main()
{
   check_OS_version
   check_jdk
   check_sudo

   if [[ "$OS" == "redhat" ]]; then
       add_yum_repositories
       install_yum_packages
   else
       install_apt_packages
   fi

   build_boost
   install_maven
   build_llvm
   
   #
   # temporary hack until the CMake files
   # can be updated.
   #
   if [[ "$OS" == "ubuntu" ]]; then
       ubuntu_build_setup
   fi
}


##############################################
# Verify we have CentOS 6.1 or later
###########################################
check_OS_version() {

   #
   # Redhat
   #
   if [ -z "$(uname -a | grep -i "ubuntu")" ]; then
       if [ ! -f /etc/redhat-release ]; then
           echo "Must be a CentOS 6.1 or later operating system"
           exit 1;
       fi

       BAD_CENTOS_VERSION=0
       BAD_REDHAT_VERSION=0
       if [[ "$(cat /etc/redhat-release)" != *"CentOS release 6."[123456789]* ]]; then
           echo "CentOS 6.1 or later not detected"
           BAD_CENTOS_VERSION=1
       fi

       if [[ "$(cat /etc/redhat-release)" != *"Red Hat Enterprise Linux"*"release 6."[123456789]* ]]; then
           echo "RedHat 6.1 or later not detected"
           BAD_REDHAT_VERSION=1
       fi

       let NUMBER_OF_BADS=$BAD_CENTOS_VERSION+$BAD_REDHAT_VERSION

       if [ $NUMBER_OF_BADS -gt 1 ]; then
           echo "Must be RedHat or CentOS version 6.1 or later"
           exit 1
       fi

    #
    # Ubuntu
    #
    else
       OS="ubuntu"

       if [ "$(lsb_release -a 2> /dev/null | grep -i "release:" | awk '{print $NF}' | cut -d. -f1)" -lt 12 ]; then 
           echo "Ubuntu 12.0 or later not detected"
           exit 1
       fi
    fi
}


######################################################################
# Make sure that the Oracle Java Development Kit 6 is installed (not OpenJDK),
#   and that `JAVA_HOME` is set in your environment.
############################################################################
check_jdk() {

    # make sure $JAVA_HOME is set
    if [ "$JAVA_HOME" == "" ]; then
        echo Must set \$JAVA_HOME
        exit 1
    fi
    
    # we must run the Oracle JDK, not openjdk
    if [[ $(java -version 2>&1) == *openjdk* ]]; then
        echo Must install Oracle JDK 1.6 or 1.7
        exit 1
    fi
    
    # Want JDK version 1.6 or 1.7
    case "$(javac -version 2>&1)" in
        *1.7.*|*1.6.*)
            ;;
        *)
            echo Must install Oracle JDK 1.6 or 1.7
            exit 1
            ;;
    esac

}


###########################################################33
# Make sure we have sudo privileges
#################################################
check_sudo() {
    if [ "$(id -u)" != "0" ]; then
        echo "User $(id -u) must have sudo priveleges"
        exit 1
    fi
}



##################################################################33
# Add the thirdparty yum (and mapr) repositories
###############################################################
add_yum_repositories() {
    mkdir -p /etc/yum.repos.d
    [ -f /etc/yum.repos.d/maprtech.repo ] || \
        sudo bash -c "cat > /etc/yum.repos.d/maprtech.repo"  <<-EOF
	[maprtech]
	name=MapR Technologies
	baseurl=http://package.mapr.com/releases/v3.0.2/redhat
	enabled=1
	gpgcheck=0
EOF
    
    [ -f /etc/yum.repos.d/maprecosystem.repo ] || \
        sudo bash -c "cat > /etc/yum.repos.d/maprecosystem.repo"  <<-EOF
	[maprecosystem]
	name=MapR Technologies
	baseurl=http://package.mapr.com/releases/ecosystem/redhat
	enabled=1
	gpgcheck=0
EOF

    [ -f /etc/yum.repos.d/jur-linux.repo ] || \
        sudo bash -c "cat > /etc/yum.repos.d/jur-linux.repo" <<-EOF
	[jur-linux]
	name=Jur Linux
	baseurl=http://jur-linux.org/download/el-updates/6.3/x86_64/
	gpgcheck=0
	enabled=1
EOF
}



##############################################################33
# Install packages which come from the yum depots
##################################################################
install_yum_packages() {

    # get the normal build tools
    sudo yum -y install libevent-devel automake libtool flex bison gcc-c++ \
          openssl-devel make cmake doxygen.x86_64 python-devel bzip2-devel \
          svn cyrus-sasl-devel wget git unzip rpm-build
    sudo yum -y install  libicu-devel chrpath openmpi-devel mpich2-devel
    sudo yum -y install python3-devel

    # No longer needed - we are getting mapr as part of "thirdparty"
    #sudo yum -y install mapr-core mapr-hive
    
    # update zlib since there is a bug in zlib's early packaging
    sudo yum -y update-to zlib-1.2.3  # a bug in early zlib
}


##############################################################33
# Install packages which come from the apt-get repositories
##################################################################
install_apt_packages() {

    # get the normal build tools
    sudo apt-get install -y automake bison build-essential chrpath
    sudo apt-get install -y cmake coreutils doxygen fakeroot flex
    sudo apt-get install -y libtool make mpich2-dev openmpi-dev
    sudo apt-get install -y g++-4.4 gcc-4.4 unzip zlib1g-dev
    sudo apt-get install -y libboost1.53-all-dev libboost1.53-dev
    sudo apt-get install -y libcurl4-openssl-dev libevent1-dev
    sudo apt-get install -y libffi-dev libicu-dev libldap2-dev libsasl2-dev
    sudo apt-get install -y libssl-dev openssl-dev
    sudo apt-get install -y python3-dev python-dev python-setuptools
    sudo apt-get install -y asciidoc  t2html liblzma-dev
    sudo apt-get install -y source-highlight
}


##################################################################
# Verify we have a valid (1.53 or later) boost installed
#################################################################
valid_boost() {

    # we must have boost installed
    [ -f /usr/include/boost/version.hpp ] || return 1

    # it must have a "define BOOST_VERSION"
    local define=$(grep '#define BOOST_VERSION ' /usr/include/boost/version.hpp)
    [ -z "$define" ] && return 1;

    # the version must bo 1.49 or greater
    local version=${define##*BOOST_VERSION}
    [[ $version -ge 104900 ]]

    return 0
}
    


######################################################################3
# Build and installs Boost 1.53 if not already done
#####################################################################
build_boost() {

    # see if we already have a valid boost installation
    valid_boost && return

    if [[ "$OS" == "redhat" ]]; then 

        echo Installing Boost 1.53

        # But first, remove any previous boost
        [ -d /usr/lib/boost ] && sudo yum -y erase boost

        # fetch the boost source rpm and create binary rpms
        wget ftp://ftp.icm.edu.pl/vol/rzm2/linux-fedora-secondary/development/rawhide/source/SRPMS/b/boost-1.53.0-6.fc19.src.rpm
        rpmbuild --rebuild boost-1.53.0-6.fc19.src.rpm

        # install the binary rpms, removing old versions if present
        # (Note: the "rpm" utility does not clean up old versions very well.)
        sudo yum -y install ~/rpmbuild/RPMS/x86_64/*

    fi

    # Make the following change to /usr/include/boost/move/core.hpp:
    #  (Note: this is probably not the best way to fix the problem.)
    sudo mv /usr/include/boost/move/core.hpp /usr/include/boost/move/core.hpp.orig
    sudo sed 's/~rv();/~rv() throw();/' < /usr/include/boost/move/core.hpp.orig \
                                 > /usr/include/boost/move/core.hpp
}


############################################################3
# Build the needed version of llvm (3.3 or higher)
#########################################################3
build_llvm() {

    # done if alreadly installed
    valid_llvm && return

    echo Building and installing llvm
    return

    # Get the sources
    wget http://llvm.org/releases/3.3/llvm-3.3.src.tar.gz
    tar xvzf llvm-3.3.src.tar.gz
    cd llvm-3.3.src/tools
    svn co http://llvm.org/svn/llvm-project/cfe/tags/RELEASE_33/final/ clang
    cd ../projects
    svn co http://llvm.org/svn/llvm-project/compiler-rt/tags/RELEASE_33/final/ compiler-rt
    cd ..

    # Build it and install
    ./configure --with-pic
    make -j4 REQUIRES_RTTI=1
    sudo make install
}



############################################################3333
# Checks if a valid llvm is installed
########################################################3
valid_llvm() {
    local conf=/usr/local/include/llvm/Config/config.h
    [ -f $conf ] || return 0

    # get the major and minor release numbers
    local minor=$(grep LLVM_VERSION_MINOR $conf)
    local major=$(grep LLVM_VERSION_MAJOR $conf)
    minor=${minor##* }   # last token (space separated)
    major=${major##* }   # last token (space separated)

    # Must be 3.3 or greater
    [ $major -eq 3 -a $minor -ge 3 ]
}
    

##################################################################33
# Install maven 3.1 or later (not currently available on yum)
###################################################################3
install_maven() {

    # done if already installed
    valid_maven && return

    echo "Installing Maven 3"

    wget ftp://mirror.reverse.net/pub/apache/maven/maven-3/3.1.1/binaries/apache-maven-3.1.1-bin.tar.gz
    sudo tar -C /usr/local -xvf apache-maven-3.1.1-bin.tar.gz 

    cat <<-EOF
Add the following to your .bashrc file
    export M2_HOME=/usr/local/apache-maven-3.1.1
    export M2=$M2_HOME/bin  
    export PATH=$M2:$PATH 
EOF
}


###################################################333
# Checks if a valid maven is installed
#   (checks for default install of maven 3.1.1)
#   (TODO: look for mvn -version 3.1.1 or later)
####################################################
valid_maven() {
    [ -f /usr/local/apache-maven-3.1.1/bin/mvn ]
}


##################################################################33
# Changes so impala builds on Ubuntu
###################################################################3
ubuntu_build_setup() {

    sed -i -e "s|void ImpalaAuxpropLookup|int ImpalaAuxpropLookup|" ${IMPALA_HOME}/be/src/rpc/authentication.cc
    sed -i -e 's|-lrt|-lrt\'$'\n  -lcrypto|' ${IMPALA_HOME}/be/CMakeLists.txt
}



main "$@"
