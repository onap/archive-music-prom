#!/usr/bin/env python2

# -*- encoding: utf-8 -*-
# -------------------------------------------------------------------------
#   Copyright (c) 2018 AT&T Intellectual Property
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#
# -------------------------------------------------------------------------
#


import sys
import getopt
import json
import requests

musicLocation =""
base_url = ""
keyspaceName = ""
tableName = "replicas"
aid = ""
namespace = ""


def parseConfig(config):
    global musicLocation, base_url, keyspaceName, aid, namespace
    config = json.load(open(config))
    musicLocations = config["music-location"]
    base_url = "http://" + musicLocations[0] + ":8080/MUSIC/rest/v2"
    keyspaceName = "prom_" + config["app-name"]
    aid = config["aid"]
    namespace = config["namespace"]

def getHeaders():
    headers = {'aid': aid, 'ns': namespace}
    return headers

def getReplica(id):
    response = requests.get(base_url+"/keyspaces/"+keyspaceName+"/tables/"+tableName+"/rows?id="+id,
                       headers=getHeaders())
    return response.json()["result"]["row 0"]

def getAllReplicas():
    response = requests.get(base_url+"/keyspaces/"+keyspaceName+"/tables/"+tableName+"/rows",
                       headers=getHeaders())
    print json.dumps(response.json()["result"], indent=2, sort_keys=True)

def acquireLock(lockref):
    response = requests.get(base_url+"/locks/acquire/"+lockref,
                       headers=getHeaders())
    return response.json()

def releaseLock(lockref):
    print "releasing lock: " + lockref
    response = requests.delete(base_url+"/locks/release/"+lockref,
                       headers=getHeaders())
    #return response.json()
    return

def getCurrentLockHolder(lockname):
    response = requests.get(base_url+"/locks/enquire/"+lockname,
                        headers=getHeaders())
    return response.json()

def releaseLocksUntil(lockname, lockref):
    """release locks until the lockref passed in is the current lock holder
     this essentially forces the lockref to become the active prom site"""
    acquire = acquireLock(lockref)
    while acquire["status"]=="FAILURE":
        if acquire["message"]=="Lockid doesn't exist":
            print "[ERROR] Lock" , lockref, "cannot be found."
            return False
        currentLockHolder = getCurrentLockHolder(lockname)
        if currentLockHolder["lock"]["lock-holder"] is not lockref:
            releaseLock(currentLockHolder["lock"]["lock-holder"])
        acquire = acquireLock(lockref)
    return True

def deleteLock(lockname):
    response = requests.delete(base_url + "/locks/delete/"+lockname,
                          headers=getHeaders())
    return response.json()


def usage():
    print "usage: promoverride -c <prom config file> -i <prom_id>"
    print " -c, --config <prom config file> OPTIONAL location of the 'config.json' file for prom." \
          " Default location is current directory"
    print " -i <prom_id> is the replica site to force to become active"
    print " -l, --list to list current prom instances"
    print "\n Config file is needed to read information about music location and keyspace information"

if __name__=="__main__":
    try:
        opts, args = getopt.getopt(sys.argv[1:], "c:i:l", ["config=", "id=", "list"])
    except getopt.GetoptError as err:
        print(err)
        usage()
        exit(1)
    # defaults here
    configFile = "config.json"
    id = None
    listInstances = False

    for opt, args in opts:
        if opt in ("-c", "--config"):
            configFile = args
        elif opt in ("-i", "--id"):
            id = args
        elif opt in ("-l", "--list"):
            listInstances = True
        else:
            assert False, "unhandled option " + str(opt)

    parseConfig(configFile)

    if listInstances:
        # todo list current instances
        getAllReplicas()
        exit(0)

    if id == None:
        print "Mandatory prom id not provided."
        usage()
        exit(1)

    replicaInfo = getReplica(id)
    print "Forcing prom site ", id, " to become active"
    if releaseLocksUntil(keyspaceName+".active.lock", replicaInfo["lockref"]) is True:
        print "prom site", id, " should now be active"
