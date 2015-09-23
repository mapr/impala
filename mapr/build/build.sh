

main()
{
    # Set the Impala environment (assumes we're in mapr/build subdirectory)
    export IMPALA_HOME=${IMPALA_HOME:-$(pwd)/../..}
    export M2_HOME=${M2_HOME:-/usr/local/apache-maven-3.1.1}
    export PATH+=:$M2_HOME/bin
    export JAVA_HOME=${JAVA_HOME:-/usr/java/latest}

    # Build Impala (including the shell)
    cd $IMPALA_HOME
    ./build_public.sh  -build_thirdparty

}



main "$@"
