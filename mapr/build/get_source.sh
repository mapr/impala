REPO=git@github.com:mapr/private-impala.git
#`REPO=~/impala/private-impala
BRANCH=v1.1.1_mapr


main()
{
    # Clone the Impala repository
    rm -rf mapr-impala;  mkdir mapr-impala
    git clone $REPO  mapr-impala
    (cd mapr-impala; git checkout $BRANCH)

    # Download required third-party packages
    (cd mapr-impala/thirdparty; ./download_thirdparty.sh)
}



main "$@"
