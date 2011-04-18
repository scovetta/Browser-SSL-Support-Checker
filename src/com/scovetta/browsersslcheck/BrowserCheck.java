package com.scovetta.browsersslcheck;

import java.io.*;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import sun.util.logging.resources.logging_pt_BR;

public class BrowserCheck {
	/** Version */
	private static final double VERSION = 1.0;
	
	/** OpenSSL Executable */
	private String openSSL = null;
	
	/** Protocol List */
	private String[] protocolList = new String[] { "ssl3" };
	
	/** Default starting port */
	private int DEFAULT_STARTING_PORT = 22222;

	/** If true, extra output */
	private static boolean verbose = false;
	
	/**
	 * Starts the Browser Check application
	 * @param args command line arguments
	 */
	public static void main(String[] args) {
		log("Browser SSL Check v" + VERSION + " - Starting...");
		log("Press Ctrl-C to break.");
		
		try {
			Runtime.getRuntime().addShutdownHook(new ShutdownThread());
			logVerbose("Shutdown hook added");
		} catch (Throwable t) {
		}
			 
		BrowserCheck app = new BrowserCheck();
		
		// Check the arguments
		app.checkArguments(args);
		
		// Create a temporary certificate
		app.createCertificate();
		
		// Grab the list of possible ciphers (from `OpenSSL ciphers`)
		ArrayList<String> cipherList = app.getCipherList();

		
		
		int port = app.DEFAULT_STARTING_PORT;
		ArrayList<Thread> threadList = new ArrayList<Thread>();
		
		StringBuffer sb = new StringBuffer();
		for (String protocol : app.protocolList) {
			for (String cipher : cipherList) {
				if (BrowserCheck.isPortAvailable(port)) {
					logVerbose("Creating thread (port=" + port + ", protocol=" + protocol + ", cipher=" + cipher + ")");
					Thread t = new BackgroundExecutionThread(app.createCommandLineOpenSSLServer(app.openSSL, port, protocol, cipher)); 
					threadList.add(t);
					t.start();
					sb.append(" <script src=\"https://localhost:" + port + "/dummy.html\" onerror=\"onError('" + port + ":" + protocol + ":" + cipher + "')\" onload=\"onSuccess('" + port + ":" + protocol + ":" + cipher + "')\"></script>\n");
					++port;
				}
			}
		}
		
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("VERSION", String.valueOf(VERSION));
		map.put("IFRAMES", sb.toString());

		app.createOutputFile(map);
		log("Output file has been created.");
		while(!BrowserCheck.isPortAvailable(++port));
		
		Thread reportThread = new BackgroundExecutionThread(app.createCommandLineOpenSSLServer(app.openSSL, port, null, null));
		reportThread.start();
		threadList.add(reportThread);
		
		log("Waiting for processes to initialize. This may take a few minutes...");
		while (BrowserCheck.isPortAvailable(port)) {
			try {
				Thread.sleep(200);
			} catch(InterruptedException ex) {
				// pass
			}
			System.out.print(".");
		}
		log("Reporting Server Available at https://localhost:" + port + "/final.html");
		
		for (Thread t : threadList) {
			while(t.isAlive()) {
				// wait
			}
		}
		
	}

	private String createCommandLineOpenSSLServer(String openSSL, int port, String protocol, String cipher) {
		String s = "\"" + openSSL + "\" s_server -rand \"" + openSSL + "\" -cert etc/localhost.crt -key etc/localhost.key -accept " + port + " -WWW";
		if (protocol != null && !"".equals(protocol))
			s += " -" + protocol;
		if (cipher != null && !"".equals(cipher))
			s += " -cipher " + cipher;
		logVerbose(s);
		return s;
	}
	private void createOutputFile(HashMap<String, String> map) {
		
		StringBuffer sb = new StringBuffer();
		String line = null;
		try {
			BufferedReader r = new BufferedReader(new FileReader("etc/template.html"));
			while ( (line = r.readLine()) != null) {
				sb.append(line + "\n");
			}
		} catch(Exception ex) {
			sb = new StringBuffer();
		}
		
		String html = sb.toString();
		for (String key : map.keySet()) {
			html = html.replaceAll("\\$\\{" + key + "\\}", map.get(key));
		}
		
		try {
			BufferedWriter w = new BufferedWriter(new FileWriter("final.html"));
			w.write(html);
			w.close();
		} catch(Exception ex) {
			ex.printStackTrace();
		}
	}
	
	private void createCertificate() { 
		ArrayList<String> output = execute("\"" + this.openSSL + "\" req -x509 -nodes -rand \"" + this.openSSL + "\" -days 30 -newkey rsa:2048 -keyout etc/localhost.key -out etc/localhost.crt -subj \"/C=US/ST=New York/CN=localhost\"");
		for (String o : output) {
			logVerbose(o);
		}
	}
	
	/**
	 * Logs a message to the console.
	 * @param string Message to log
	 */
	public static void log(String string) {
		System.err.println("[LOG] " + string);
	}
	
	/**
	 * Logs a message if verbose is set.
	 * @param string String to log
	 */
	public static void logVerbose(String string) {
		if (BrowserCheck.verbose) {
			log(string);
		}
	}
	
	public ArrayList<String> getCipherList() {
		ArrayList<String> cipherList = new ArrayList<String>();
		ArrayList<String> output = BrowserCheck.execute( this.openSSL + " ciphers");
		
		if (output == null) return cipherList;	// empty
		
		for (String s : output) {
			cipherList.addAll( Arrays.asList(s.split(":")));
		}
		logVerbose("Supported Ciphers: " + cipherList.toString());
		return cipherList;
	}
	
	public static ArrayList<String> execute(String cmd) {
		logVerbose("Exec: " + cmd);
		try {
			ArrayList<String> list = new ArrayList<String>();
			Process p = Runtime.getRuntime().exec(cmd);
			BufferedReader br = new BufferedReader( new InputStreamReader( p.getInputStream() ) );
			String s = null;
			while ( (s = br.readLine()) != null)
				list.add(s);
			
			try {
				p.waitFor();
			} catch(InterruptedException e) {
				return null;
			}
			br.close();
			return list;
		} catch(Throwable t) {
			t.printStackTrace();
			return null;
			
		}
	}
	
	/**
	 * Checks to see if a particular port is available
	 * @param port port number to check
	 * @return true iff the port is available (and valid)
	 */
	public static boolean isPortAvailable(int port) {
		if (port < 1 || port > 65535) {
			return false;
		}
		
		try {
			ServerSocket socket = new ServerSocket(port);
			socket.close();
			return true;
		} catch (IOException ex) {
			return false;
		}
	}
	
	/**
	 * Tries to find the OpenSSL 
	 * @return
	 */
	public boolean findOpenSSL() {
		String systemPath = System.getenv("path");
		String pathSepChar = System.getProperty("path.separator");
		String fileSepChar = System.getProperty("file.separator");
		
		String openSSLexecutable = "openssl";
		if (System.getProperty("os.name").toLowerCase().indexOf("win") >= 0)
			openSSLexecutable = "openssl.exe";
		
		String[] pathList = systemPath.split(pathSepChar);
		for ( String path : pathList ) {
			if (path.endsWith(fileSepChar)) {
				path = path.substring(0, path.length()-1);
			}
			File f;
			try {
				f = new File(path + fileSepChar + openSSLexecutable).getCanonicalFile();
				if (f.exists()) {
					this.openSSL = path + fileSepChar + openSSLexecutable;
					logVerbose("Found OpenSSL executable: " + path);
					return true;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			f = null;
		}
		return false;		
	}

	/**
	 * Checks all arguments.
	 * @param args
	 * @return
	 */
	public boolean checkArguments(String[] args) {
		if (args == null) return false;
	
		if (!findOpenSSL()) {
			System.err.println("Error: Could not find OpenSSL. Use --openssl <filename> to specify.");
			System.exit(1);
		}
		
		for (int i=0; i<args.length; i++) {
			String arg = args[i];
			if ("--help".equals(arg)) {
				usage();
				System.exit(1);
			} else if ("--verbose".equals(arg)) { 
				BrowserCheck.verbose = true;
			} else if ("--openssl".equals(arg)) {
				this.openSSL = args[++i];
			} else if ("--protocols".equals(arg)) {
				this.protocolList = args[++i].split(",");
			} else if ("--start-port".equals(arg)) {
				try {
					this.DEFAULT_STARTING_PORT = Integer.parseInt(args[++i]);
				} catch(Exception ex) {
					// pass
				}
			}
		}
		return false;
		
		
	}
	
	/**
	 * Prints usage information.
	 */
	public static void usage() {
		String s = "";
		s += "Usage: \n";
		s += "  java -jar BrowserSSLCheck.jar [OPTIONS]\n";
		s += "    --openssl <filename>	    OpenSSL executable (default: search path)\n";
		s += "    --start-port <port>       Starting TCP port for OpenSSL to listen on (default: 22222)\n";
		s += "    --protocols <p,p,p>       Use only these protocols (default: ssl3) (available: ssl2,ssl3,tls1,dtls1)\n";
		s += "    --verbose                 Extra output\n";
		System.err.println(s);
		return;
	}
}

class BackgroundExecutionThread extends Thread {
	private String cmd;
	
	public BackgroundExecutionThread(String cmd) {
		this.cmd = cmd;
	}
	
	public void run() {
		try {
			Process p = Runtime.getRuntime().exec(cmd);
			
			try {
				p.waitFor();
			} catch(InterruptedException e) {
				// pass
			}
			return;
		} catch(Throwable t) {
			return;
		}
	}
}

//The ShutdownThread is the thread we pass to the
//addShutdownHook method
class ShutdownThread extends Thread {
	public ShutdownThread() {
	}
	
	public void run() {
		BrowserCheck.execute("pskill openssl.exe");
	}
}