package org.example;

import static org.springframework.web.servlet.function.RouterFunctions.route;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@SpringBootApplication
public class App {

  public static void main(String[] args) {
    SpringApplication.run(App.class);
  }

  @Bean
  RouterFunction<ServerResponse> routes(JdbcTemplate jdbcTemplate) {
    return route().GET("/customers", request -> {
      var results = jdbcTemplate.query("select * from customer", (rs, rowNum) -> new Customer(rs.getInt("id"), rs.getString("name")));
      return ServerResponse.accepted().body(results);
    }).build();
  }

  record Customer(int id, String name) { }
}

@Configuration
class SecurityConfiguration {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.httpBasic(Customizer.withDefaults())
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .csrf(AbstractHttpConfigurer::disable);
    return http.build();
  }

  @Bean
  UserDetailsService userDetailsService() {
    var steve = createUser("steve@gmail.com", 1);
    var robert = createUser("robert@gmail.com", 2);
    var users = Stream.of(steve, robert).collect(Collectors.toMap(User::getUsername, u -> u));

    return username -> {
      var user = users.getOrDefault(username, null);
      if (user == null) {
        throw new UsernameNotFoundException("Couldn't find username " + username);
      }
      return user;
    };
  }

  public static User createUser(String name, Integer tenantId) {
    return new MultiTenantUser(name, PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("pw"), true, true, true, true, List.of(new SimpleGrantedAuthority("USER")), tenantId);
  }
}

class MultiTenantUser extends User {

  private final Integer tenantId;

  public MultiTenantUser(String username, String password, boolean enabled, boolean accountNonExpired, boolean credentialsNonExpired,
      boolean accountNonLocked, Collection<? extends GrantedAuthority> authorities, Integer tenantId) {
    super(username, password, enabled, accountNonExpired, credentialsNonExpired, accountNonLocked, authorities);
    this.tenantId = tenantId;
  }

  public Integer getTenantId() {
    return tenantId;
  }
}

@Configuration
class DataSourceConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(DataSourceConfiguration.class);

  /**
   * @param dataSourceMap  Spring resolves this dependency. Spring under the hood maintains map-like structure where
   *                       the bean names are the keys and the bean instances are the values of same type, allowing
   *                       efficient bean retrieval and dependency resolution.
   *                       For Example: Here there are two DataSource beans where the keys are ds1 and ds1
   */
  @Bean
  @Primary
  DataSource multiTenantDataSource(Map<String, DataSource> dataSourceMap) {
    var prefix = "ds";
    var map = dataSourceMap.entrySet()
        .stream()
        .filter(e -> e.getKey().startsWith(prefix))
        .collect(Collectors.toMap(
            e -> (Object) Integer.parseInt(e.getKey().substring(prefix.length())),
            e -> (Object) e.getValue()
        ));

    map.forEach((tenantId, ds) -> {
      ClassPathResource sqlSchemaResource = new ClassPathResource("schema.sql");
      ClassPathResource sqlDataResource = new ClassPathResource(prefix + tenantId + "-data.sql");
      var initializer = new ResourceDatabasePopulator(sqlSchemaResource, sqlDataResource);
      initializer.execute((DataSource) ds);
      logger.info("initialized datasource for %s", tenantId);
    });

    MultiTenantDataSource mds = new MultiTenantDataSource();
    mds.setTargetDataSources(map); // Important step
    return mds;
  }

  @Bean
  DataSource ds1() {
    return dataSource(5431);
  }

  @Bean
  DataSource ds2() {
    return dataSource(5432);
  }

  private static DataSource dataSource(int port) {
    var dsp = new DataSourceProperties();
    dsp.setPassword("pw");
    dsp.setUsername("user");
    dsp.setUrl("jdbc:postgresql://localhost:" + port + "/my-db");
    return dsp.initializeDataSourceBuilder()
        .type(HikariDataSource.class)
        .build();
  }
}

class MultiTenantDataSource extends AbstractRoutingDataSource {

  private static final Logger logger = LoggerFactory.getLogger(MultiTenantDataSource.class);

  private final AtomicBoolean initialized = new AtomicBoolean();

  @Override
  protected DataSource determineTargetDataSource() {
    if (this.initialized.compareAndSet(false, true)) {
      this.afterPropertiesSet();
    }
    return super.determineTargetDataSource();
  }

  @Override
  protected Object determineCurrentLookupKey() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication !=null && authentication.getPrincipal() instanceof MultiTenantUser multiTenantUser) {
      var tenantId = multiTenantUser.getTenantId();
      logger.info("The tenant id is: %", tenantId);
      return tenantId;
    }
    return 1;
  }
}
