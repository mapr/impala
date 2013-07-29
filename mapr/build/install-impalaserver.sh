#!/bin/bash 
##################################################################
#
# Install IMPALA SERVER ROLE onto a MAPR system.
#
# This script is intended to be run in two modes:
#   From mapr installer (rpm)
#        install-impalaserver.sh  /opt/mapr   /opt/mapr/impala
#
#         The impala installer has already copied the files in place,
#         so we are mainly installing a warden config config file
#
#####################################################################

# Where the new IMPALA files were copied
export MAPR_HOME=${1:-/opt/mapr}
export IMPALA_HOME=${2:-$MAPR_HOME/impala/impala-1.1.1} 
. $IMPALA_HOME/mapr/IMPALA_VERSION.sh


main()
{
    # must be root to run this script
    if  ! is_root ; then
        echo "You must be root to install impala"
        exit 1
    fi

    # figure out who we will run as
    local owner=$(get_mapr_owner)

    # Create the role file
    mkdir -p $MAPR_HOME/roles
    touch $MAPR_HOME/roles/impalaserver
    chown $owner $MAPR_HOME/roles/impalaserver
  
    # install the warden config scripts
    configure_file  $IMPALA_HOME/mapr/warden/warden.impalaserver.conf \
                    $MAPR_HOME/conf/conf.d/warden.impalaserver.conf
    chown $owner    $MAPR_HOME/conf/conf.d/warden.impalaserver.conf
    configure_file  $IMPALA_HOME/mapr/warden/mapr-impalaserver \
                    $MAPR_HOME/initscripts/mapr-impalaserver
    chown $owner    $MAPR_HOME/initscripts/mapr-impalaserver
    chmod a+x       $MAPR_HOME/initscripts/mapr-impalaserver

    # create a link so "service" command can be used to start/stop
    rm -f /etc/init.d/mapr-impalaserver
    ln -s $MAPR_HOME/initscripts/mapr-impalaserver  /etc/init.d
}

################### Should be part of a "common" file ###################

##############################################################
# filter a file, substituting for the following shell variables
#     $MAPR_HOME
#     $IMPALA_HOME
#     $IMPALA_VERSION
##############################################################
configure_file() { #  <source file>  <new modified file>
    sed -e "s^\$MAPR_HOME^$MAPR_HOME^g"  \
        -e "s^\$IMPALA_HOME^$IMPALA_HOME^g"  \
        -e "s^\$IMPALA_VERSION^$IMPALA_VERSION^g"  < $1 > $2
}


# "true" if user is root
is_root() 
{
    [ `id -u 2>/dev/null` -eq 0 ]
}




valid_user() # <user name>
{
    [ "`id -u $1 2>/dev/null`" != "" ]
}



get_group() # <user name>
{
    id -g -n $1 2>/dev/null
}

get_mapr_owner()
{
    # get the mapr user out of the daemon.conf file
    local conf=$MAPR_HOME/conf/daemon.conf
    local owner=$(awk -F = '$1 == "mapr.daemon.user" { print $2 }' $conf)
    local group=$(awk -F = '$1 == "mapr.daemon.group" { print $2 }' $conf)

    # if not specified or invalid, then try "root" and 'mapr'
    ([ "$owner" == ""     ] || ! valid_user $owner)  && owner=root
    ([ "$owner" == "root" ] &&   valid_user mapr )  && owner=mapr
    [ "$group" == ""     ]  && group=$(get_group $owner)
    [ "$group" == "root" ] &&  group=mapr
    echo $owner:$group
}
  

main "$@"
