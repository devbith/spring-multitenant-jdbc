# Spring Multi Tenant JDBC
An example of multi tenant JBDC application with spring boot.

Uses `AbstractRoutingDatasource` from spring which dynamically determine the actual DataSource 
based on the current context. In the scope of this project, the context is spring security context based on authentication. 

## Usage

Requirement: Java 21, Docker & Gradle.

1. Build the project 
    ```
    ./gradlew build
    ```
 2. Start two instance of PostgreSQL

     - With bash script 
          ```bash
         ./development/start-postgres-db.sh db1 5431 
         ./development/start-postgres-db.sh db1 5432 
          ```
    
     - With gradle task
        ```
         ./gradlew composeUp
        ```
       
3. Run the application
4. Send HTTP BASIC authentication request
    ```
    curl -v -u steve@gmail.com:pw http://localhost:8080/customers
    curl -v -u robert@gmail.com:pw http://localhost:8080/customers
   ```

*Here:*
Each user, identified by their email (steve@gmail.com and robert@gmail.com), has an associated tenant ID, which is determined by the determineCurrentLookupKey() method. During an HTTP BASIC request, the AbstractRoutingDatasource class uses the user's authentication details to determine the context and provide the corresponding DataSource.

### Further experiment
To further test the functionality, log in to either of the databases:
1. `PGPASSWORD=pw psql -U user -h localhost -p 5431 user` 
2. `PGPASSWORD=pw psql -U user -h localhost -p 5432 user`

Insert or remove data from the selected database. Send an HTTP BASIC auth request as described in step 4.
By doing this, we will observe that `AbstractRoutingDatasource` provides different DataSource instances based on the context, confirming its ability to route requests appropriately.
