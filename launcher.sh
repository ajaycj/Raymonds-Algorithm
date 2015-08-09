#!/bin/bash


# Change this to your netid
netid=pxs142130

#
# Root directory of your project
PROJDIR=$HOME

#
# This assumes your config file is named "config.txt"
# and is located in your project directory
#
CONFIG=$PROJDIR/config.txt

#
# Directory your java classes are in
#
BINDIR=$PROJDIR

#
# Your main project class
#
PROG=Launcher

START=dc01
ID=1

n=1

cat $CONFIG | sed -e "s/#.*//" | sed -e "/^\s*$/d" |
(
    read i
    echo $i
    while read line 
    do
        host=$( echo $line | awk '{ print $1 }' )

        #ssh
	
	if [[ $host != "start" && $host != "broadcast" && $host != $START || $n != $ID  ]];then
	if [[ $host != "start" && $host != "broadcast" && $host != "nocsreq" && $host != "csduration" && $host != "delaybtwcs" ]];then
	ssh $netid@$host java $PROG $n &
	fi
	fi
        n=$(( n + 1 ))
    done
   
)

sleep 10s

ssh $netid@$START java $PROG $ID
