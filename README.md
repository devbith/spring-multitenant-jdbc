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
 2. Start two instance PostgreSQL:

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
4. Send HTTP BASIC authentication request.
    ```
    curl -v -u steve@gmail.com:pw http://localhost:8080/customers
    curl -v -u robert@gmail.com:pw http://localhost:8080/customers
   ```

*Here:*
Each user `steve@gmail.com` & `robert@gmaill.com` contains tenant id which is the context returned by `determineCurrentLookupKey()`.
method. When the HTTP BASIC request is being sent then based on the authentication of user `AbstractRoutingDatasource` determines the context   
provides the associated `DataSource`.

### Further experiment
Login to database either of the database
- `PGPASSWORD=pw psql -U user -h localhost -p 5431 user` 
- `PGPASSWORD=pw psql -U user -h localhost -p 5432 user`

then insert or remove the data and send above http basic auth request from step 4.
Doing this we can see that `AbstractRoutingDatasource` is providing difference DataSource as per the context. 
