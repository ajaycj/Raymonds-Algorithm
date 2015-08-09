import sys
#open the file
designatedFile=open("v-data.txt","r")


count=0
line=[]
line1=[]

#Read the first line
line1=designatedFile.readline()
line1=line1[:-2]
line1=line1[1:]
line1=line1.split(',')

#read each line from the file
for line in designatedFile:
    #split the line
    
    line=line[:-2]
    line=line[1:]
    line=line.split(',')
    
	
    if len(line)==len(line1):
        for i in range(0,len(line)):
            if line1[i]<>line[i]:
                count=count+1
                #print count
		if int(line[i])-int(line1[i])<>1 or count>1:
                    print line1
                    print line
                    print 'Two Critical Sections Overlapped'
                    sys.exit()
    else:
        print 'Vector size are not matching and something went wrong'
        sys.exit()
        
    line1=line
    count=0
    
print 'No Critical Section in Overlapped'
