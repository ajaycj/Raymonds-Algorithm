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


#
# Your main project class
#
PROG=Launcher

n=1

cat $CONFIG | sed -e "s/#.*//" | sed -e "/^\s*$/d" |
(
    read i
    echo $i
    while read line 
    do
        host=$( echo $line | awk '{ print $1 }' )

        #echo $host
	if [[ $host != "start" && $host != "broadcast" && $host != "nocsreq" && $host != "csduration" && $host != "delaybtwcs" ]];then
	#if [[ $host != "start" && $host != "broadcast" ]]; then
        ssh $netid@$host killall -u $netid &
        sleep 1
	fi
        n=$(( n + 1 ))
    done
   
)


echo "Cleanup complete"
