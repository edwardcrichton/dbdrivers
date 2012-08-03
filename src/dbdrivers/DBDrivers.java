/*
* dbdrivers
* Original author: https://github.com/edwardcrichton
* Licensed under the MIT license
*/

package dbdrivers;

import java.sql.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.net.*;

/**
*   Produces java.sql.Connection objects.
*/

public final class DBDrivers
{
	final ArrayList<Object[]> drivers;
	
	public static final int DRIVER_TITLE=0,DRIVER_CLASS=1,DRIVER_TEMPLATES=2,DRIVER_STATUS=3,DRIVER_VERSION=4;
	private static final int DRIVER_BOUNDS=5;
	public static final String DRIVER_STATUS_UNDEFINED="undefined",DRIVER_STATUS_AVAILABLE="available",DRIVER_STATUS_UNAVAILABLE="unavailable",DRIVER_STATUS_INCOMPLETE="incomplete or inconsistent installation";
	
	boolean debug=false;
	
	public DBDrivers() throws IOException
	{
		drivers=new ArrayList<Object[]>(35);
		loadDriverList(findDriverList());
		detectDrivers();
	}

	/**
	 * Prints some trace out to standard out.
	 * @param debug
	 */
	public void setDebug(final boolean debug){this.debug=debug;}

	/**
	*    Returns a connection for the driver <b>driverTitle</b> (which can be the vendor name or the class path) given that the necessary <b>properties</b> have
	*    been set. Properties can include database instance name, host, port, user and password.
	*/

	public java.sql.Connection getConnection(String driverTitle, Properties properties) throws DriverNotFoundException, SQLException
	{
		return getConnection(driverTitle, 0.0f, properties);	
	}

	/**
	*    Returns a connection for the driver <b>driverTitle</b> (which can be the vendor name or the class path) given that the necessary <b>properties</b> have
	*    been set. Properties can include database instance name, host, port, user and password.
	*    This includes a driver version. If a driver version >= the version wanted is not found
	*    this returns null.
	*/

	public java.sql.Connection getConnection(String driverTitle, float driverVersion, Properties properties) throws DriverNotFoundException, SQLException
	{
		boolean matchFound=false;
		boolean versionMatchFound=false;
		
		ArrayList<Object[]> urlSets=new ArrayList<Object[]>(3);
		
		final int EXACT=0,STARTS=1;
		
		for(int m=EXACT; m<=STARTS;m++)
		{
			for(int i=0;i<drivers.size();i++)
			{
				Object[] driverInfo=(Object[]) drivers.get(i);
				
				if(driverInfo[DRIVER_STATUS].equals(DRIVER_STATUS_AVAILABLE))
				{
					if(
						(((String) driverInfo[DRIVER_TITLE]).toLowerCase().equals(driverTitle.toLowerCase())==false && m==EXACT) ||
						(((String) driverInfo[DRIVER_TITLE]).toLowerCase().startsWith(driverTitle.toLowerCase())==false && m==STARTS) ||
						((String) driverInfo[DRIVER_CLASS]).equals(driverTitle)
					  )
					{
						continue;
					}
					matchFound=true;
					float version=0.0f;
					try
					{
						version=Float.parseFloat((String) driverInfo[DRIVER_VERSION]);
						if(version<driverVersion)
						{
							continue;
						}
					}
					catch(NumberFormatException nfe)
					{
						continue;
					}
					versionMatchFound=true;
					
					if(debug)
					{
						System.out.println(driverInfo[DRIVER_TITLE]+" "+driverInfo[DRIVER_CLASS]+" "+driverInfo[DRIVER_TEMPLATES]+" "+driverInfo[DRIVER_STATUS]+" "+driverInfo[DRIVER_VERSION]);
					}
					String[][] parameters=getParameters((String) driverInfo[DRIVER_TITLE]);
					
					String[][] parameterValues=new String[parameters.length][2];
					for(int p=0;p<parameters.length;p++)
					{
						if(debug)
						{
							System.out.print("["+parameters[p][0]+("true".equals(parameters[p][1])?"(optional)":"(mandatory)")+"] >");
						}
						
						String line=(String) properties.get((String) parameters[p][0]);
						if(line==null){line="";}else{line=line.trim();}
						if(debug)
						{
							System.out.println(line);
						}
						
						parameterValues[p][0]=parameters[p][0];
						parameterValues[p][1]=line;
					}
					
					String[] urls=getURLs((String) driverInfo[DRIVER_TITLE],parameterValues);
					urlSets.add(new Object[]{driverInfo[DRIVER_TITLE],driverInfo[DRIVER_VERSION],urls});
				}
			}
		}
		
		if(urlSets.size()==0)
		{
			if(!matchFound) {throw new DriverNotFoundException("Did not find a driver for: "+driverTitle+" . Check your classpath. ");}
			if(!versionMatchFound) {throw new DriverNotFoundException("Did not find a driver for: "+driverTitle+" version >= "+driverVersion+" . Check your classpath. ");}
		}
		
		SQLException sqlex=null;
		
		for(int i=0;i<urlSets.size();i++)
		{
			Object[] urlSet=(Object[]) urlSets.get(i);
			String localDriverTitle=(String) urlSet[0];
			String localDriverVersion=(String) urlSet[1];
			String[] urls=(String[]) urlSet[2];
			
			float localVersion=0.0f;
			try
			{
				localVersion=Float.parseFloat(localDriverVersion);
			}
			catch(NumberFormatException nfe)
			{
			}
			
			for(int u=0;u<urls.length;u++)
			{
				try
				{
					if(debug)
					{
						System.out.println(urls[u]);
					}
					java.sql.Connection connection=getConnectionWithURL(localDriverTitle,localVersion,urls[u],properties);
					if(connection!=null)
					{
						return connection;
					}
				}
				catch(SQLException sqlex2)
				{
					if(debug)
					{
						System.out.println(sqlex2);
					}
					sqlex=sqlex2;
				}
			}
		}
		if(sqlex!=null){throw sqlex;}
		
		throw new DriverNotFoundException("Could not create an instance of "+driverTitle+" >= "+driverVersion+" . Maybe the driver libraries are incomplete, inconsistent or conflicting. Check that all needed libraries are in the classpath and that they correspond to the current java version "+System.getProperty("java.version"));
	}

	public java.sql.Connection getConnectionWithURL(String driverTitle, float driverVersion, String url, Properties properties) throws SQLException
	{
		Object[] driverInfo=null;
		synchronized(drivers)
		{
			for(int i=0;i<drivers.size();i++)
			{
				Object[] localDriverInfo=(Object[]) drivers.get(i);
				if(localDriverInfo[DRIVER_TITLE].equals(driverTitle))
				{
					try
					{
						float version=Float.parseFloat((String) localDriverInfo[DRIVER_VERSION]);
						if(version==driverVersion)
						{
							driverInfo=localDriverInfo;
							break;
						}
					}
					catch(NumberFormatException nfe)
					{
					}
				}
			}
		}
		if(driverInfo==null){return null;}
		
		try
		{
			Class driverClass=Class.forName((String) driverInfo[DRIVER_CLASS]);
			driverInfo[DRIVER_STATUS]=DRIVER_STATUS_AVAILABLE;
			java.sql.Driver driver=(java.sql.Driver) driverClass.newInstance();
			driverInfo[DRIVER_VERSION]=driver.getMajorVersion()+"."+driver.getMinorVersion();
		}
		catch(Exception ex)
		{
			driverInfo[DRIVER_STATUS]=DRIVER_STATUS_UNAVAILABLE;
			return null;
		}
		try
		{
			return DriverManager.getConnection(url,properties);
		}
		catch(NoClassDefFoundError ncdfe)
		{
			driverInfo[DRIVER_STATUS]=DRIVER_STATUS_INCOMPLETE;
			return null;
		}
		catch(UnsupportedClassVersionError ucve)
		{
			System.err.println("WARNING: Unsupported class version for: "+(String) driverInfo[DRIVER_CLASS]+" . Consider using a driver compatible with this version of java "+System.getProperty("java.version"));
			driverInfo[DRIVER_STATUS]=DRIVER_STATUS_UNAVAILABLE;
			return null;
		}
		catch(SQLException sqlex)
		{
			throw sqlex;
		}
		catch(Exception ex)
		{
			System.err.println("WARNING: Could not connect to database: "+driverTitle+" : "+ex);
			return null;
		}
		
	}

	public String[] getURLs(String driverTitle,String[][] parameterValues)
	{
		Object[] driverInfo=null;
		synchronized(drivers)
		{
			for(int i=0;i<drivers.size();i++)
			{
				Object[] localDriverInfo=(Object[]) drivers.get(i);
				if(localDriverInfo[DRIVER_TITLE].equals(driverTitle))
				{
					driverInfo=localDriverInfo;
					break;
				}
			}
		}
		if(driverInfo==null){return null;}
		
		final ArrayList templates=(ArrayList) driverInfo[DRIVER_TEMPLATES];
		
		int max=-1;
		
		ArrayList candidates=new ArrayList(templates.size());
		ArrayList candidateReplaced=new ArrayList(templates.size());
		
		for(int i=0;i<templates.size();i++)
		{
			final String template=(String) templates.get(i);
			final String[] url=new String[]{null};
			int replaced=replaceParametersInTemplate(template,parameterValues,url);
			if(replaced>max)
			{
				max=replaced;
				candidates.add(url[0]);
				candidateReplaced.add(new Integer(replaced));
			}
		}
		
		ArrayList results=new ArrayList(candidates.size());
		
		for(int r=0;r<candidateReplaced.size();r++)
		{
			Integer I=(Integer) candidateReplaced.get(r);
			if(I.intValue()==max)
			{
				results.add(candidates.get(r));
			}
		}
		
		final String[] urls=(String[]) results.toArray(new String[results.size()]);
		return urls;
	}

	public ArrayList getDriverList(){return new ArrayList(drivers);}

	public String[] getAvailableDriverNames()
	{
		int count=0;
		for(int i=0;i<drivers.size();i++)
		{
			Object[] driverInfo=(Object[]) drivers.get(i);
			
			if(driverInfo[DRIVER_STATUS].equals(DRIVER_STATUS_AVAILABLE))
			{
				count++;
			}
		}
		
		String[] names=new String[count];
		
		count=0;
		for(int i=0;i<drivers.size();i++)
		{
			Object[] driverInfo=(Object[]) drivers.get(i);
			
			if(driverInfo[DRIVER_STATUS].equals(DRIVER_STATUS_AVAILABLE))
			{
				names[count++]=(String) driverInfo[DRIVER_TITLE];
			}
		}
		
		return names;
	}

	public String[][] getParameters(String driverTitle)
	{
		Object[] driverInfo=null;
		synchronized(drivers)
		{
			for(int i=0;i<drivers.size();i++)
			{
				Object[] localDriverInfo=(Object[]) drivers.get(i);
				if(localDriverInfo[DRIVER_TITLE].equals(driverTitle))
				{
					driverInfo=localDriverInfo;
					break;
				}
			}
		}
		if(driverInfo==null){return null;}
		
		final ArrayList templates=(ArrayList) driverInfo[DRIVER_TEMPLATES];
		
		final ArrayList parameters=new ArrayList(3);
		
		final HashMap parameterOccurances=new HashMap(3);
		
		for(int i=0;i<templates.size();i++)
		{
			final String template=(String) templates.get(i);
			final String[] localParameters=getParametersFromTemplate(template);
			for(int lp=0;lp<localParameters.length;lp++)
			{
				int[] count=(int[]) parameterOccurances.get(localParameters[lp]);
				if(count==null)
				{
					count=new int[]{1};
					parameterOccurances.put(localParameters[lp],count);
				}
				else
				{
					count[0]++;
				}
				
				boolean got=false;
				for(int p=0;p<parameters.size();p++)
				{
					if( ((String) parameters.get(p)).equals(localParameters[lp]))
					{
						got=true;
						break;
					}
				}
				
				if(!got)
				{
					parameters.add(localParameters[lp]);
				}
			}
		}
		final String[] parameterList=(String[]) parameters.toArray(new String[parameters.size()]);
		final String[][] parameterRecords=new String[parameterList.length][2];
		
		for(int p=0;p<parameterList.length;p++)
		{
			parameterRecords[p][0]=parameterList[p];
			int[] count=(int[]) parameterOccurances.get(parameterList[p]);
			String t="true",f="false";
			if(count[0]<templates.size())
			{
				parameterRecords[p][1]=t;
			}
			else
			{
				parameterRecords[p][1]=f;
			}
		
			parameterList[p]=null;
		}
		
		return parameterRecords;
	}

	public int replaceParametersInTemplate(String template,String[][] parameterValues, String[] urlHolder)
	{
		int at=0;
		int replaced=0;
		replacing:
		for(;;)
		{
			if(at>=template.length()){break;}
			
			int dollarBrace=template.indexOf("${",at);
			if(dollarBrace==-1){break;}
			
			int endBrace=template.indexOf("}",dollarBrace+2);
			if(endBrace==-1){break;}
			
			String parameterName=template.substring(dollarBrace+2,endBrace).trim();
			
			for(int p=0;p<parameterValues.length;p++)
			{
				if(parameterName.equals(parameterValues[p][0]))
				{
					if(parameterValues[p][1].trim().length()>0)
					{
						template=template.substring(0,dollarBrace)+parameterValues[p][1]+template.substring(endBrace+1);
						replaced++;
						at=dollarBrace+parameterValues[p][1].length();
						continue replacing;
					}
					else
					{
						// failed to replace - this is not a valid substitution
						urlHolder[0]=null;
						return -1;
					}
				}
			}
			
			at=endBrace+1;
		}
		
		urlHolder[0]=template;
		return replaced;
	}

	public String[] getParametersFromTemplate(String template)
	{
		final ArrayList parameters=new ArrayList(3);
		int at=0;
		for(;;)
		{
			if(at>=template.length()){break;}
			
			int dollarBrace=template.indexOf("${",at);
			if(dollarBrace==-1){break;}
			
			int endBrace=template.indexOf("}",dollarBrace+2);
			if(endBrace==-1){break;}
			
			String parameterName=template.substring(dollarBrace+2,endBrace).trim();
			
			parameters.add(parameterName);
			
			at=endBrace+1;
		}
		
		final String[] parameterList=(String[]) parameters.toArray(new String[parameters.size()]);
		return parameterList;		
	}

	public void detectDrivers()
	{
		synchronized(drivers)
		{
			for(int i=0;i<drivers.size();i++)
			{
				Object[] driverInfo=(Object[]) drivers.get(i);
				try
				{
					Class driverClass=Class.forName((String) driverInfo[DRIVER_CLASS]);
					driverInfo[DRIVER_STATUS]=DRIVER_STATUS_AVAILABLE;
					java.sql.Driver driver=(java.sql.Driver) driverClass.newInstance();
					driverInfo[DRIVER_VERSION]=driver.getMajorVersion()+"."+driver.getMinorVersion();
				}
				catch(UnsupportedClassVersionError ucve)
				{
					System.err.println("WARNING: Unsupported class version for: "+(String) driverInfo[DRIVER_CLASS]+" . Consider using a driver compatible with this version of java "+System.getProperty("java.version"));
					driverInfo[DRIVER_STATUS]=DRIVER_STATUS_INCOMPLETE;
				}
				catch(NoClassDefFoundError ncdfe)
				{
					driverInfo[DRIVER_STATUS]=DRIVER_STATUS_INCOMPLETE;
				}
				catch(ClassNotFoundException ex)
				{
					driverInfo[DRIVER_STATUS]=DRIVER_STATUS_UNAVAILABLE;
				}
				catch(Exception ex)
				{
					System.err.println("WARNING: Could not load driver: "+(String) driverInfo[DRIVER_CLASS]+" : "+ex);
					driverInfo[DRIVER_STATUS]=DRIVER_STATUS_UNAVAILABLE;
				}
			}
		}
	}
	
	/**
	*   Load the list of drivers: {Title, Class, URL template}
	*/
	public void loadDriverList(final BufferedReader br) throws IOException
	{
		if(br==null)
		{
			throw new IOException("Missing jdbcdrivers.txt");
		}
		
		final ArrayList localDrivers=new ArrayList(35);
		int lineCount=0;
		for(;;)
		{
			String line=null, title=null, klass=null;
			final ArrayList templates=new ArrayList(2);
			while( (line=br.readLine())!=null )
			{
				lineCount++;
				line=line.trim();
				// comment
				if(line.length()==0 || line.startsWith("#") || line.startsWith("//"))
				{
					continue;
				}
				break;
			}
			if(line==null){break;}
			
			title=line;
			
			line=null;
			
			while( (line=br.readLine())!=null )
			{
				lineCount++;
				line=line.trim();
				// comment
				if(line.length()==0 || line.startsWith("#") || line.startsWith("//"))
				{
					continue;
				}
				break;
			}
			if(line==null)
			{
				throw new IOException("Malformed driver list: unexpected end of file at line "+lineCount+". Expecting a class for "+title);
			}
			
			klass=line;
			
			line=null;
			
			while( (line=br.readLine())!=null )
			{
				lineCount++;
				line=line.trim();
				// comment
				if(line.length()==0 || line.startsWith("#") || line.startsWith("//"))
				{
					continue;
				}
				break;
			}
			if(line==null)
			{
				throw new IOException("Malformed driver list: unexpected end of file at line "+lineCount+". Expecting an URL template for "+title+" "+klass);
			}
			
			templates.add(line);
			
			line=null;
			
			while( (line=br.readLine())!=null )
			{
				lineCount++;
				line=line.trim();
				if(line.length()==0) {break;}
				// comment
				if(line.startsWith("#") || line.startsWith("//"))
				{
					continue;
				}
				
				templates.add(line);
			}
			
			Object[] driverInfo=new Object[DRIVER_BOUNDS];
			driverInfo[DRIVER_TITLE]=title;
			driverInfo[DRIVER_CLASS]=klass;
			driverInfo[DRIVER_TEMPLATES]=templates;
			driverInfo[DRIVER_STATUS]=DRIVER_STATUS_UNDEFINED;
			localDrivers.add(driverInfo);
		}
		
		synchronized(drivers)
		{
			drivers.clear();
			drivers.addAll(localDrivers);
		}
		
		localDrivers.clear();
		
		try
		{
			br.close();
		}
		catch(IOException ioe)
		{
		}
	}
	
	public BufferedReader findDriverList()
	{
		final String filename="jdbcdrivers.txt";

		final String resource="/"+this.getClass().getName().replace('.','/')+".class";
		final java.net.URL resourceURL=this.getClass().getResource(resource);
		final String url=resourceURL.toString();
		
		if(url.indexOf("!/")!=-1)
		{
			final File jarFile=new File(url.substring("jar:file:".length(),url.indexOf("!/")));

			ZipFile zipRead=null;
			
			try
			{
				zipRead=new ZipFile(jarFile);
			}
			catch(Exception e)
			{
				zipRead=null;
			}

			if(zipRead!=null)
			{
			
				Enumeration en=zipRead.entries();
				while(en.hasMoreElements())
				{
					ZipEntry ze=(ZipEntry) en.nextElement();
					String entryName=ze.getName();
					// ignore jar meta-inf
					if(ze.getName().endsWith(filename)==false){continue;}
					
					try
					{
						InputStream is=zipRead.getInputStream(ze);
						BufferedReader br=new BufferedReader(new InputStreamReader(is));
						return br;
					}
					catch(Exception e)
					{
					}
				}
			}
		}

		// not found in the packaged jar file.
		// ask the system for it
		
		final URL resourceURL2=this.getClass().getClassLoader().getResource(filename);

		if(resourceURL2!=null)
		{
			try
			{
				InputStream is=resourceURL2.openStream();
				BufferedReader br=new BufferedReader(new InputStreamReader(is));
				return br;
			}
			catch(Exception e)
			{
			}
		}
		
		// failed
		return null;
	}
	
}
