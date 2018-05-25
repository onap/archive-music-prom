
# Policy Driven Ownership and Management (PROM) Protocol for Active-Passive Systems 

Often we wish to deploy service replicas in an active-passive mode where there is only one active service and if that fails, one of the passives takes over as the new active. The trouble is, to implement this, the writer of the service has to worry about challenging distributed systems concepts like group membership, failure detection, leader election, split-brain problems and so on. prom addresses this issue, by providing a library that services can simply configure and deploy as a companion daemon to their service replicas, that will handle all distributed systems issues highlighted above. 

Note: prom relies on system clocks for this timeout to detect failure. In order to make sure your system is closely synchronized, consider using NTP or similar.


<a name="local-install"> 

## Setup and Usage

</a>

- The starting point for prom is that you wish to replicate a service on multiple servers/hosts/VMs (refereed to as a node) such that one of them is active and the others are passive at all times. 
- Ensure that MUSIC <a href="">https://github.com/att/music</a> is running across all these nodes as a cluster. 
-  Build prom and copy the resultant prom.jar into all the nodes along with config.json file and the startPromDaemon.sh script (sample files provided in this repository under the sampleApp folder). 
-  Modify the config.json (same at all nodes). We explain the config.json through an example: 
		
		
		{
		    "app-name":"votingAppBharath",
                    "aid":"",
                    "namespace":"",
                    "userid":"",
                    "password":"",
		    "ensure-active-0":"/home/ubuntu/votingapp/ensureVotingAppRunning.sh 0 active",
		    "ensure-active-1":"/home/ubuntu/votingapp/ensureVotingAppRunning.sh 1 active",
		    "ensure-active-2":"/home/ubuntu/votingapp/ensureVotingAppRunning.sh 2 active",
		    "ensure-active-3":"/home/ubuntu/votingapp/ensureVotingAppRunning.sh 3 active",
		    "ensure-passive-0":"/home/ubuntu/votingapp/ensureVotingAppRunning.sh 0 passive",
		    "ensure-passive-1":"/home/ubuntu/votingapp/ensureVotingAppRunning.sh 1 passive",
		    "ensure-passive-2":"/home/ubuntu/votingapp/ensureVotingAppRunning.sh 2 passive",
		    "ensure-passive-3":"/home/ubuntu/votingapp/ensureVotingAppRunning.sh 3 passive",
		    "restart-prom-0":"ssh -i /home/ubuntu/votingapp/bharath_cirrus101.pem ubuntu@135.197.240.156 /home/ubuntu/votingapp/restartPromIfDead.sh 0",
		    "restart-prom-1":"ssh -i /home/ubuntu/votingapp/bharath_cirrus101.pem ubuntu@135.197.240.158 /home/ubuntu/votingapp/restartPromIfDead.sh 1",
		    "restart-prom-2":"ssh -i /home/ubuntu/votingapp/bharath_bigsite.pem ubuntu@135.197.226.68 /home/ubuntu/votingapp/restartPromIfDead.sh 2",
		    "restart-prom-3":"ssh -i /home/ubuntu/votingapp/bharath_bigsite.pem ubuntu@135.197.226.49 /home/ubuntu/votingapp/restartPromIfDead.sh 3",
		    "prom-timeout":"50000",
		    "restart-backoff-time":"1000",
		    "core-monitor-sleep-time":"1000",
		    "no-of-retry-attempts":"3",
		    "replica-id-list":["0","1","2","3"]
		    "music-location":["localhost"]
                    "music-version":2
		} 
	The *app-name*	 is simply the name chosen for the service. 

	The *aid* is the identification provided by MUSIC during onboarding, if AAF authentication is not used.
	
	The *start-active-i* signifies that this is the site that should start in the active mode. Valid entries for this are "true" or "false". NOTE: if multiple entries have *start-active-i* set to true, any of them are viable options for the single initial active site.
	
	The *ensure-active-i* and *ensure-passive-i* scripts need to be provided for 	all the service replicas, wherein the i corresponds to each of their ids. 	The ids must start from 0 with single increments. As seen in the example, 	the command within the string will be invoked by prom to run the servce in 	either active or passive mode. These scripts should return the linux exit 	code of 0 if they run successfully. 
	
	The *restart-prom-i* scripts are used by the hal daemons running along with 	each replica to restart each other. Since the hal daemons reside on 	different nodes, they will need ssh (and associated keys) to communicate 	with each other. 
	
	The *prom-timeout* field decides the time in ms after which one of the passive proms 	will take-over as leader after the current leader stops updating MUSIC with 	its health. The *noOfRetryAttempts* is used by prom to decide how many times 	it wants to try and start the local service replica in either active or 	passive mode (by calling the ensure- scripts). The *replicaIdList* is a 	comma separated list of the replica 	ids. Finally, the *musicLocation* should 	contain the public IP of the MUSIC 	node this prom daemon wants to talk to. 	Typically this is localhost if MUSIC is co-located on the same node as the 	prom deamon and service replica. 

        There is an optional *music-connection-timeout-ms* parameter that allows the user to configure the timeout to MUSIC. After this much time has elapsed for connection to MUSIC, the daemon will try the next MUSIC instance (if one exists).
	
	The *restart-backoff-time* backs off for the set amount of time in ms if the restart script fails. If configured, this will allow the site time to recover before trying to restart again. This is an optional parameter (default to immediate retry).
	
	The *core-monitor-sleep-time* is describes the time in ms between checking if the prom site is active. Default is 0.
		
- Once the config.json has been placed on all nodes in the same location as the prom.jar and the startPromDaemon.sh, on one of the nodes (typically the one that you want as active), run the command:

		./startPromDaemon.sh -i <node id>
		
	The prom protocol will now take over and start the service replicas in active-passive mode. The prom log can be found on each node i in the logs/EELF/application.log. 
