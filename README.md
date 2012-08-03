dbdrivers
=========

A java package that will return a jdbc connector given a database name, or a driver name and some parameters.
Currently, knows about 55 connectors. Please do add more to the file jdbcdrivers.txt .

Example

Connecting to a database.

~~~

java.sql.Connection connection=null;

try
{
        dbdrivers.DBDrivers dbc=new dbdrivers.DBDrivers();
 
        Properties properties=new Properties();
        properties.put("database",databaseName);
        properties.put("user",databaseUser);
        properties.put("password",databasePassword);
        properties.put("host",databaseHost);

        // databaseType is either the name of a jdbc driver class
        // or name of the driver
        connection=dbc.getConnection(databaseType, properties);
}
catch(Exception ex)
{
         ...
}

...

~~~

Listing the names of the drivers.

~~~

dbdrivers.DBDrivers dbc=new dbdrivers.DBDrivers();
String[] driverNames=dbc.getAvailableDriverNames();

~~~

Listing all drivers, their names, classes, statuses and versions

~~~

dbdrivers.DBDrivers dbc=new dbdrivers.DBDrivers();
ArrayList drivers=dbc.getDriverList();

for(int i=0;i<drivers.size();i++)
{
  Object[] driverInfo=(Object[]) drivers.get(i);
  System.out.println(driverInfo[DBDrivers.DRIVER_TITLE]+" "+driverInfo[DBDrivers.DRIVER_CLASS]+" "+driverInfo[DBDrivers.DRIVER_STATUS]+" "+driverInfo[DBDrivers.DRIVER_VERSION]);
}

~~~