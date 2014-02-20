This directory contains a set of files for installing IMPALA on a mapr
installation. 

Impala has two daemons, impalad and stateserved, both of which are located
in IMPALA_HOME/sbin.  The impalaserver and impalastore scripts, located
under IMPALA_HOME/bin, are the recommended way to start the servers.

To build impala,
   follow instructions in README.md

   ... Or,  use the scripts in the mapr/build directory
       get_source.sh           download the source
       cd mapr/build.  
       sudo ./setup_build.sh   install tools for building impala
       build.sh                build impala from the source

Normally, impala is installed from .rpm files.  
To install impala by hand after building:
    cd mapr/build
    stage.sh   
    sudo ./install-impala.sh
    sudo ./install-impalastore.sh
    sudo ./install-impalaserver.sh
    

To remove impala 
    sudo ./uninstall.sh


WARDEN integration
------------------
impala defines two warden services:
  - impalaserver, which runs on all impala nodes
  - impalastore, which runs on one node

The impala services are are controlled through 
  - initscripts/mapr-impalaserver  controls the impala server
  - initscripts/mapr-impalastore   controls the impala state store
  - initscripts/warden_helper      handles the details of starting and
                                   monitoring a generic warden daemon.
