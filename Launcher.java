import java.io.*;

public class Launcher {
	public static void main(String[] args) throws Exception {
		//Read in the node ID
		String id = args[0];
		//Launch the System 
		SystemUnder.launch(id);
		//Launch teh application
		Application.launch(id);
	}
	/*
	Function which the application need to invoke to enter CS
	*/
	static void enter_cs() throws Exception {
		SystemUnder.enter_cs();
	}
	/*
	Function which the application need to invoke to leave CS
	*/
	static void leave_cs() throws Exception {
		SystemUnder.leave_cs();	
	}
}
