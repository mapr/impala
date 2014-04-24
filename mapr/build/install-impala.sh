#!/bin/bash 
##################################################################
#
# Install IMPALA onto a MAPR system.
#
# This script is intended to be run in two modes:
#   From mapr installer (rpm)
#        install.sh  /opt/mapr   /opt/mapr/impala/impala-VERSION
#
#         The installer has already copied the files in place,
#         so we are mainly doing the post-install configuration
#
#   From development environment:
#        stage.sh;  sudo ./install.sh
#
#        By default, "stage.sh" leaves its staged files in /var/tmp/impala.
#        We copy files from the staging area to /opt/mapr/impala/impala-<version>
#        then do the post-install activation.
#
#####################################################################

# Where the new IMPALA files were copied
export MAPR_HOME=${1:-/opt/mapr}
export IMPALA_HOME=${2:-$MAPR_HOME/impala/impala-1.2.3} 


main()
{
    # must be root to run this script
    if  ! is_root ; then
        echo "You must be root to install impala"
        exit 1
    fi

    # if this is outside the RPM, then copy files from the staging area
    #  (assumes the staging area was just used by make_tarball.sh)
    if [ "$1" == "" -a -d /var/tmp/mapr-impala -a ! -d $IMPALA_HOME ]; then
        mkdir -p $IMPALA_HOME
        cp -r /var/tmp/mapr-impala/*  $IMPALA_HOME
    fi

    # Create the configuration files
    configure_warden 
    configure_impala

    # Create user accessible script
    rm -f /usr/bin/impala-shell
    ln -s $IMPALA_HOME/bin/impala-shell /usr/bin/impala-shell

    # set ownership of impala files
    local owner=$(get_mapr_owner)
    set_ownership $owner
}    


configure_impala() {
    local mapr=$IMPALA_HOME/mapr

    # get the impala version
    . $IMPALA_HOME/mapr/IMPALA_VERSION.sh

    # configure and install bin files
    mkdir -p $IMPALA_HOME/bin
    for file in impala-shell impalaserver impalastore impalacatalog; do
        configure_file $mapr/bin/$file $IMPALA_HOME/bin/$file
        chmod a+x $IMPALA_HOME/bin/$file
    done

    # keep existing conf files, adding new ones
    mkdir -p $IMPALA_HOME/conf
    for file in `cd $mapr/conf; echo *`; do
        if [ ! -f $IMPALA_HOME/conf/$file ]; then
            configure_file $mapr/conf/$file $IMPALA_HOME/conf/$file
        fi
    done
}



set_ownership() # <owner:group>
{
    # set the ownership
    chown -R "$1" $IMPALA_HOME
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
  
configure_warden()
{
    local warden=$IMPALA_HOME/mapr/warden
    local scripts=$MAPR_HOME/initscripts

    # install helper and initscript files
    configure_file   $warden/warden_helper  $scripts/warden_helper
    configure_file  $warden/mapr-impalaserver $scripts/mapr-impalaserver
    configure_file  $warden/mapr-impalastore  $scripts/mapr-impalastore

    # create /etc/init.d links so can be started with "service" command
    rm -f /etc/init.d/mapr-impalaserver
    ln -s $scripts/mapr-impalaserver /etc/init.d
    rm -f /etc/init.d/mapr-impalastore
    ln -s $scripts/mapr-impalastore  /etc/init.d

    # NOTE: warden config files are installed by install-server.sh and install-store.sh scripts

}



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
    chmod a+x $2
}

valid_user() # <user name>
{
    [ "`id -u $1 2>/dev/null`" != "" ]
}



get_group() # <user name>
{
    id -g -n $1 2>/dev/null
}

is_root() 
{
    [ `id -u 2>/dev/null` -eq 0 ]
}


main "$@"
