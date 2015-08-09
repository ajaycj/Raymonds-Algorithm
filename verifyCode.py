import sys
#open the file
designatedFile=open("data.txt","r")

expect=-1
count=0

#read each line from the file
for line in designatedFile:
    #split the line
    part=line.split();
    #check if the line is not empty
    if part:
        #if the enter CS has happened assign the expected node number and keep track of the number of times cs request make by a node
        if part[0]=='ECS':
            expect=part[1]
            count=count+1
        elif part[0]=='LCS':
            #take decision based on the node number in the file and the count
            if part[1]==expect and count==1:
                count=0
                continue
            else:
                print "Two critical sections overlapped"
                sys.exit()

#if nothing went wrong we got our mutual exclusion right            
print 'No Critical Section Overlapped'
    
