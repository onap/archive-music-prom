#!/bin/bash

# ensure that the config.json is in the same location
# along with ensure active/passive scripts


PROM_IMG=prom
WORKING_DIR=`dirname "$(realpath $0)"`

#
# change this location if necessary
# this is the location of the config.json and the ensure active/passive scripts
#
CONFIG_SCRIPTS_DIR=$WORKING_DIR

usage () {
  echo "Usage: $0 <start/stop> <prom id> [-p]"
}


if [ "$#" -lt 2 ]; then
  usage
  exit 1
fi

PROM_ID=$2


PROM_PASSIVE="\"\""
if [[ "$#" -ge 3 && ${3//[-]} == p* ]]; then
    PROM_PASSIVE="-p"
fi





echo "Starting prom, id:'$PROM_ID'"

if [ "$1" = "start" ];
then
  docker run -d --hostname $PROM_ID \
    -e ID=$PROM_ID \
    -e PASSIVE=$PASSIVE \
    --net="host" \
    -v $CONFIG_SCRIPTS_DIR:/opt/app/prom \
    $PROM_IMG
#    --name prom \ 
elif [ "$1" = "stop" ];
then
  docker stop prom;
  sleep 5;
else
  usage
fi
