# Setting Up Apache Solr for Napster Clone

Based on the SolrConfig files in your project, here's a detailed guide for setting up the Apache Solr instances required by your Napster Clone application.

## 1. System Requirements

- Java 11 or higher (required by Solr 9.x)
- Apache Solr 9.8.1 (as referenced in your code)
- Windows environment (your paths use Windows format)
- At least 2GB RAM for each Solr instance

## 2. Installing Apache Solr

### Download Solr

1. Download Apache Solr 9.8.1 from [Apache Solr Downloads](https://solr.apache.org/downloads.html)
2. Download the binary distribution (.zip for Windows)
3. Extract the archive to a location of your choice, e.g., solr-9.8.1

### Set Up Two Solr Instances

Your configuration indicates you need two Solr instances running on different ports:

#### First Instance (Port 8983)

1. Use the default extracted Solr directory at solr-9.8.1

#### Second Instance (Port 8984)

1. Create a copy of the Solr directory at server2
2. This second instance will run on port 8984 as specified in your code

## 3. Starting Solr Instances

### Start First Solr Instance (Port 8983)

```bash
cd D:\solr-9.8.1
bin\solr.cmd start -p 8983 -Dsolr.allowPaths=D:\\solr-9.8.1\\server\\solr\\configsets
```

### Start Second Solr Instance (Port 8984)

```bash
cd D:\solr-9.8.1
bin\solr.cmd start -p 8984 -Dsolr.allowPaths=D:\\solr-9.8.1\\server2\\solr\\configsets
```

## 4. Creating and Configuring the Napster Clone Core

### Method 1: Using Your Java Program

Your CreateNapsterCloneCores.java will handle the complete setup process. To use it:

1. Compile the Java program:

   ```bash
   javac -cp . CreateNapsterCloneCores.java
   ```

2. Run the program:
   ```bash
   java CreateNapsterCloneCores
   ```

This program will:

- Delete any existing napster_clone cores on both Solr instances
- Create new napster_clone cores on both instances
- Apply the schema configuration with all required fields
- Set up replication between the two instances (master-slave configuration)

### Method 2: Manual Setup (Alternative)

If you prefer to set up manually:

#### Create Cores on Each Instance

1. First Instance (8983):

   ```bash
   bin\solr.cmd create -c napster_clone -p 8983
   ```

2. Second Instance (8984):
   ```bash
   bin\solr.cmd create -c napster_clone -p 8984
   ```

#### Configure Schema

Use the schema definition from your CreateNapsterCloneCores.java file to create a schema.json file:

```json
{
  "add-field": {
    "name": "doc_type_s",
    "type": "string",
    "indexed": true,
    "stored": true,
    "multiValued": false
  },
  "add-field": {
    "name": "username_s",
    "type": "string",
    "indexed": true,
    "stored": true,
    "multiValued": false
  },
  "add-field": {
    "name": "ip_s",
    "type": "string",
    "indexed": true,
    "stored": true,
    "multiValued": false
  },
  "add-field": {
    "name": "port_i",
    "type": "pint",
    "indexed": true,
    "stored": true,
    "multiValued": false
  },
  "add-field": {
    "name": "status_s",
    "type": "string",
    "indexed": true,
    "stored": true,
    "multiValued": false
  },
  "add-field": {
    "name": "filename_s",
    "type": "string",
    "indexed": true,
    "stored": true,
    "multiValued": false
  },
  "add-field": {
    "name": "filename_txt_en",
    "type": "text_en",
    "indexed": true,
    "stored": true,
    "multiValued": false
  },
  "add-field": {
    "name": "size_l",
    "type": "plong",
    "indexed": true,
    "stored": true,
    "multiValued": false
  },
  "add-field": {
    "name": "owner_username_s",
    "type": "string",
    "indexed": true,
    "stored": true,
    "multiValued": false
  }
}
```

Upload this schema to both instances:

```bash
curl -X POST -H "Content-Type:application/json" --data-binary @schema.json "http://localhost:8983/solr/napster_clone/schema"
curl -X POST -H "Content-Type:application/json" --data-binary @schema.json "http://localhost:8984/solr/napster_clone/schema"
```

## 5. Configuring Master-Slave Replication

Your program sets up replication automatically, but if you need to do it manually:

1. Configure 8983 as Master:

   ```bash
   curl "http://localhost:8983/solr/napster_clone/replication?command=details&isMaster=true&enableReplication=true"
   ```

2. Configure 8984 as Slave:
   ```bash
   curl "http://localhost:8984/solr/napster_clone/replication?command=details&isSlave=true&masterUrl=http://localhost:8983/solr/napster_clone/replication&pollInterval=00:00:60"
   ```

## 6. Load Balancer Setup (Optional)

Your code includes a LoadBalancer.java that can distribute requests between both Solr instances for improved reliability and performance.

To use it:

1. Compile the LoadBalancer:

   ```bash
   javac LoadBalancer.java
   ```

2. Run it:
   ```bash
   java LoadBalancer
   ```

This will start a load balancer on port 8080 that distributes requests between your two Solr instances.

## 7. Verifying the Setup

1. Access the Solr Admin UI:

   - First instance: http://localhost:8983/solr/
   - Second instance: http://localhost:8984/solr/

2. Verify napster_clone core exists on both instances:

   - Check http://localhost:8983/solr/#/napster_clone
   - Check http://localhost:8984/solr/#/napster_clone

3. Verify schema configuration:

   - Go to http://localhost:8983/solr/#/napster_clone/schema
   - Confirm all fields are properly defined (doc_type_s, username_s, ip_s, etc.)

4. Test replication:
   - Add a document to the master:
     ```bash
     curl -X POST -H "Content-Type:application/json" --data-binary '[{"id":"test1","doc_type_s":"user","username_s":"testuser"}]' "http://localhost:8983/solr/napster_clone/update?commit=true"
     ```
   - Wait a minute and check if it appears on the slave instance:
     ```bash
     curl "http://localhost:8984/solr/napster_clone/select?q=id:test1&wt=json"
     ```

## 8. Configuring the Napster Clone Application

In your JoinForm.java, the application is already set up to use these Solr instances:

```java
private static final String[] SOLR_URLS = {
    "http://localhost:8983/solr/napster_clone",
    "http://localhost:8984/solr/napster_clone"
};
```

## 9. Maintenance and Cleanup

### Deleting Cores

If you need to delete the cores and start fresh, you can use the DeleteNapsterCloneCores.java program:

```bash
javac DeleteNapsterCloneCores.java
java DeleteNapsterCloneCores
```

### Stopping Solr

To stop the Solr instances:

```bash
bin\solr.cmd stop -p 8983
bin\solr.cmd stop -p 8984
```

## 10. Troubleshooting

### Solr Won't Start

- Check Java version (must be Java 11+)
- Ensure ports 8983 and 8984 are not in use
- Check Solr logs in logs and `D:\solr-9.8.1\server2\logs`

### Core Creation Fails

- Ensure you've run Solr with proper permissions: `-Dsolr.allowPaths=D:\\solr-9.8.1\\server\\solr\\configsets`
- Check that the paths in the Java code match your actual Solr installation directory

### Application Can't Connect to Solr

- Verify Solr is running: http://localhost:8983/solr/ and http://localhost:8984/solr/
- Check if cores are created and schema is configured properly
- Ensure firewall is not blocking the connections

This setup provides you with a high-availability Solr configuration for your Napster Clone application with master-slave replication for better reliability.
