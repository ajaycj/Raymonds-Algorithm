import java.util.Random; 
import java.io.*;
import java.net.*;
import com.sun.nio.sctp.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.Semaphore;

/** 
 * RandomNumberGenerator creates random numbers according to one of the following distributions:
 * Poisson, Exponential, Geometric, Pareto, ParetoBounded, Uniform or Constant 
 *  
 * The method getRandom() will return the next random value.  
 * It can use a provided stream Random r, if needed, or use the default stream. 
 *  
 * For most of the distributions a single distribution parameter is needed.  
 * USAGE: double rand = Exponential.getRandom(0.25); 
 *  
 * When the wrong number of parameters is passed, IllegalArgumentException is thrown. 
 *  
 * Contact: zxy754219@gmail.com 
 *  
 * @author alexz3 
 *  
 * 
 */ 
public enum Application{     
     
    Poisson { 
        @Override 
        public double getRandom(Random r, double lambda) { 
            double L = Math.exp(-lambda); 
            int k = 0; 
            double p = 1.0; 
            do { 
                k++; 
                p = p * r.nextDouble(); 
            } while (p > L); 

            return k - 1; 
        } 
    },  
    Exponential { 
        @Override 
        public double getRandom(Random r, double p) { 
            return -(Math.log(r.nextDouble()) / p); 
        } 
    },  
    Geometric { 
        @Override 
        public double getRandom(Random r, double geoSeed) { 
            double p = 1.0 / ((double) geoSeed); 
            return (int)(Math.ceil(Math.log(r.nextDouble())/Math.log(1.0-p))); 
        } 
    },  
    Pareto {         
        @Override 
        public double getRandom(Random r, double alpha, double xM) { 
            double v = r.nextDouble(); 
            while (v == 0){ 
                v = r.nextDouble(); 
            } 
             
            return xM / Math.pow(v, 1.0/alpha); 
        } 
    }, 
    ParetoBounded {     
        @Override 
        public double getRandom(Random r, double alpha, double L, double H) { 
            double u = r.nextDouble(); 
            while (u == 0){ 
                u = r.nextDouble(); 
            } 
             
            double x = -(u*Math.pow(H,alpha)-u*Math.pow(L,alpha)-Math.pow(H,alpha)) /  
                            (Math.pow(H*L,alpha)); 
            return Math.pow(x, -1.0/alpha); 
        } 
    },     
    Uniform { 
        @Override 
        public double getRandom(Random r, double p) { 
            return r.nextDouble() * p; 
        } 
    },  
    Constant { 
        @Override 
        public double getRandom(Random r, double N) {             
            return N; 
        } 
    }; 

    public double getRandom(double p) throws IllegalArgumentException {         
        return getRandom(defaultR, p);         
    } 
     
    public double getRandom(double a, double b) throws IllegalArgumentException {         
        return getRandom(defaultR, a, b);         
    } 
     
    public double getRandom(double a, double b, double c) throws IllegalArgumentException {        
        return getRandom(defaultR, a, b, c);         
    } 
     
    public double getRandom(Random r, double p) throws IllegalArgumentException { 
        throw new IllegalArgumentException();         
    } 
     
    public double getRandom(Random r, double a, double b) throws IllegalArgumentException{ 
        throw new IllegalArgumentException(); 
    } 

    public double getRandom(Random r, double a, double b, double c) throws IllegalArgumentException{
        throw new IllegalArgumentException(); 
    } 
         
    public static final Random defaultR = new Random(); 
	
	//Modification for Programming Assignment 2
    public static final void launch(String args){ 
        //Testing  
		
	    int nocsreq=0,csduration=0,delaybtwcs=0;
		
        //Reading information from the configuration file
		try{
			String s;
			
			//Object to read the configuration file
			FileReader readConfig=new FileReader("config.txt");							
			BufferedReader brReader=new BufferedReader(readConfig);
		
			while((s=brReader.readLine())!=null){
				String[] str=s.split("\\s+");
				//finding out who is the starting node 
				if(str[0].equals("start")){	
					//startNode=Integer.parseInt(str[1]);									
				}
				//get the total number of nodes count in the graph
				else if(str[0].equals("total")){										
					//totalNodes=Integer.parseInt(str[1]);
				}
				else if(str[0].equals("nocsreq")){										
					nocsreq=Integer.parseInt(str[1]);
				}
				else if(str[0].equals("csduration")){										
					csduration=Integer.parseInt(str[1]);
				}
				else if(str[0].equals("delaybtwcs")){										
					delaybtwcs=Integer.parseInt(str[1]);
				}
				
				//Collecting the neighbouring nodes list, port number and server name and map it to a node ID
				else{
					
				}
			}
			//close the file here after getting all the necessary information 
			brReader.close();
		}
		catch(IOException e){
			System.err.println(e.getMessage());
		}
		try{
		Thread.sleep(10000);
		}
		catch(Exception ex){
			ex.printStackTrace();
		}
        Application testStat = Exponential;
		Application testStatdelaybtwcs = Exponential;
	
		int myNodeID=Integer.parseInt(args);

        double lambda = 1.0 / csduration; 
		double lambdadelaybtwcs = 1.0 / delaybtwcs; 
		double temp=0,temp1=0;

//        System.out.println("Testing stat: " + testStat+ " with lambda: " + lambda);
        for (int i = 0; i < nocsreq; i++){ 
	        try{
				Launcher.enter_cs();
			}
			catch(Exception ex){
				ex.printStackTrace();
			}
			temp=testStat.getRandom(lambda);
			//System.out.println(temp);							
			try{
				System.out.println(myNodeID+": csduration = "+(int)Math.round(temp));	
				Thread.sleep((int)Math.round(temp));						
			}
			catch(InterruptedException e){
				e.printStackTrace();
			}
			try{
				Launcher.leave_cs(); 
			}
			catch(Exception ex){
				ex.printStackTrace();
			}
			
			temp1=testStatdelaybtwcs.getRandom(lambdadelaybtwcs);	
	
			try{
				System.out.println(myNodeID+": durationbtwcs = "+(int)Math.round(temp1));
				Thread.sleep((int)Math.round(temp1));
			}
			catch(InterruptedException e){
				e.printStackTrace();
			}
    	} 
	}
	//Modification for Programming Assignment 2
}
