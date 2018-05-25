#!/bin/bash

usage () {
  echo "Usage: $0 -i <prom id> [-p] [-c <config.json directory>] [-z]"
  echo "where"
  echo -e "\t -i <prom_id> the identifier of the prom daemon"
  echo -e "\t -p specifies whether the daemon must start as passive"
  echo -e "\t -c is the directory where the prom config.json resides"
  echo -e "\t -z keep std out open after daemon is started (for docker containers only)"
  exit 1
}


passive="" #default=can be active or passive
config=$PWD   # default config directory is working directory
id_flag=0   #make sure user passes in id
docker_deployment=false

while getopts ":i:pc:z" o; do
  case "${o}" in
    i)
      id=${OPTARG}
      id_flag=1
      ;;
    p)
      passive="-p"
      ;;
    c)
      config=${OPTARG}
      ;;
    z)
      docker_deployment=true
      echo "docker deployment"
      ;;
    *)
      usage
      ;;
  esac
done      

if [ $id_flag -eq 0 ]; then
  echo "ERROR: Required parameter <prom id> not provided."
  usage
fi

echo "config location is $config"
echo "prom id is $id"
echo "passive is $passive"

if $docker_deployment ; then
  echo "Container version detected, keeping syso open"
  #keep container running
fi
 
dir=$PWD
ps aux > $dir/PromLog$id.out
promId=`grep "prom.jar $id" $dir/PromLog$id.out | awk '{ print $2 }'`
if [ -z "${promId}" ]; then
#		echo prom dead
    echo "Starting prom $id"
    java -jar $dir/prom.jar --id $id $passive --config $config > $dir/prom$id.out & 
fi
sleep 3
ps aux > $dir/PromLog$id.out
promId=`grep "prom.jar" $dir/PromLog$id.out | awk '{ print $2 }'`
if [ -z "${promId}" ]; then
    echo "NotRunning"
else 
		echo $promId
fi
rm $dir/PromLog$id.out

if $docker_deployment ; then
  echo "Container version detected, keeping syso open"
  #keep container running
  tail -f /dev/null
fi
