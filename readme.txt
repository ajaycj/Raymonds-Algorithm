Author:Prasanna Subburaj,Hari Kishore Reddy Desanur,Ajay Chinthalapalli Jayakumar.
Project Title:Implementation of Raymond's Token Based Mutual Exclusion protocol in a Distributed System (SCTP)
Date: 28th July 2015
The following are the information need to run our project

1. Configuring the config.txt
	
	a. put the total number of nodes in the graph in the first line near total
	b. enter the tree configuration line by line in the following format next
	#Machine Name #Machine Port Number #neighbours 
	c. there should be a space between all the above information entered in the file
	d. enter the start node near the start in the text file
	e. enter the number of requests each node should make near nocsreq
	f. enter the mean duration of each cs request and delay between each cs request near nocsreq, csduration, delaybtwcs respectively

2. Compiling the java file java files(SystemUnder.java,Application.java,Launcher.java) using the command
   javac #filename

3. Running the launcher shell script
	a. open the launcher.sh file
	b. change the project directory in the variable PROJDIR
	b. in the START variable enter the designated machine name
	c. in the ID variable enter the Node Id of the designated machine
	d. give permission by typing chmod +x launcher.sh
	e. type ./launcher.sh to run the launcher file
4. Running the cleanup shell script
	a. give permission to file using command chmod +x cleanup.sh
	b. type ./cleanup.sh

5. Running verification python scripts
	a. We have two python scripts to check if our program is execting correctly and is not causing any violation of CS
	b. Running the application produces two text files data.txt and v-data.txt
	c. In the verifyVector.py supply the path to the v-data.txt
	d. In the verifyCode.py supply the path to the data.txt
	e. Run the python script by typing python verificationpgmname
	f. verificationpgmname can be either verifyVector.py or verifyCode.py
