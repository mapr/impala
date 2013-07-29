#!/bin/bash 
##################################################################
#
# Remove IMPALA from a MAPR system
#
# This script is intended to be run in two modes:
#   From warden:
#        uninstall.sh  /opt/mapr   /opt/mapr/impala
#
#         Warden will be removing most of the files,
#         so we are mainly cleaning up the "extras".
#
#   From development environment:
#        sudo ./install.sh
#
#        After cleaning up "extra" things, remove the installed
#        files from /opt/mapr/impala.
#
#####################################################################


# get the install locations from Warden
MAPR_HOME=${1:-/opt/mapr}
IMPALA_HOME=${2:-$MAPR_HOME/impala/impala-1.1.1} 

uninstall()
{
    # verify we are root
    if  ! is_root ; then
        echo "You must be root to install impala"
        exit 1
    fi

    # remove the warden configuration files
    rm -f $MAPR_HOME/conf/conf.d/warden.impalaserver.conf
    rm -f $MAPR_HOME/conf/conf.d/warden.impalastore.conf
    rm -f $MAPR_HOME/initscripts/impalaserver
    rm -f $MAPR_HOME/initscripts/impalastore

    # remove user accessible links
    rm -f /usr/bin/impala-shell
    
    # remove the installed files
    #   (the funny stuff is to protect from IMPALA_HOME being "/")
    rm -rf $IMPALA_HOME/../impala-1.1.1

    # remove the "service" links
    rm -f /etc/init.d/mapr-impala*
}    
    

is_root() # <user name>
{
    [ `id -u 2>/dev/null` -eq 0 ]
}


stop_service() # <service name>
{
    local status=$(service $1 status)
    if [[ "$status" == *running* ]]; then
        service $1 stop
    fi
}
    

uninstall "$@"
