#!/bin/bash

dir=`dirname $0`
# query SDN-C cluster status
clusterStatus=$( $dir/sdnc.cluster )

if [ "ACTIVE" = "$clusterStatus" ];then
  # peform health-check
  health=$( $dir/sdnc.monitor )
  
  if [ "HEALTHY" = "$health" ]; then
    echo "Cluster is ACTIVE and HEALTHY"
    exit 0
  fi
  echo "Cluster is ACTIVE and UNHEALTHY"
  exit 1

elif [ "STANDBY" = "$clusterStatus" ]; then
  # perform takeover
  echo "Cluster is STANDBY - taking over"
  takeoverResult=$( $dir/sdnc.failover )
  if [ "SUCCESS" = "$takeoverResult" ]; then
    exit 0
  fi
  echo "Cluster takeover failed"
  exit 1
fi

echo "Unknown cluster status '$clusterStatus'"
exit 1
