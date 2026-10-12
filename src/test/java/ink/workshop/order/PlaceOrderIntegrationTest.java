package ink.workshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import com.jayway.jsonpath.JsonPath;
import ink.catalog.CatalogApplication;
import ink.inventory.InventoryApplication;
import ink.workshop.catalog.CatalogClient;
import ink.workshop.inventory.InventoryClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlaceOrderIntegrationTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private InventoryClient inventoryClient;

  @Autowired
  private CatalogClient catalogClient;

  private ConfigurableApplicationContext inventory;
  private ConfigurableApplicationContext catalog;
  private String inventoryBaseUrl;
  private JdbcTemplate inventoryJdbc;
  private JdbcTemplate catalogJdbc;

  @BeforeAll
  void startOtherServices() throws SQLException {
    createDatabase("ink_catalog");
    createDatabase("ink_inventory");
    catalog = startService(CatalogApplication::start, "ink_catalog");
    inventory = startService(InventoryApplication::start, "ink_inventory");
    catalogClient.useBaseUrl(baseUrl(catalog));
    inventoryBaseUrl = baseUrl(inventory);
    inventoryClient.useBaseUrl(inventoryBaseUrl);
    catalogJdbc = jdbc(databaseUrl("ink_catalog"));
    inventoryJdbc = jdbc(databaseUrl("ink_inventory"));
  }

  @AfterAll
  void stopOtherServices() {
    if (catalog != null) {
      catalog.close();
    }
    if (inventory != null) {
      inventory.close();
    }
  }

  @BeforeEach
  void resetShop() {
    jdbcTemplate.update("delete from order_lines");
    jdbcTemplate.update("delete from orders");
    jdbcTemplate.update("delete from sessions");
    jdbcTemplate.update("delete from users");
    inventoryJdbc.update("delete from stock_deductions");
    inventoryJdbc.update("delete from inventory where product_id > 4");
    inventoryJdbc.update("update inventory set available = 10");
    catalogJdbc.update("delete from products where id > 4");
  }

  @Test
  void listsSeededProducts() throws Exception {
    mockMvc.perform(get("/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4))
        .andExpect(jsonPath("$[0].name").value("松烟墨"))
        .andExpect(jsonPath("$[0].priceCents").value(6800));
  }

  @Test
  void showsProductDetail() throws Exception {
    mockMvc.perform(get("/products/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("松烟墨"))
        .andExpect(jsonPath("$.priceCents").value(6800));
  }

  @Test
  void placesOrderAndDeductsStock() throws Exception {
    LoggedIn user = registerAndLogin("buyer@example.com");

    place(user, "deduct-stock", """
        {"lines":[{"productId":1,"quantity":2}]}
        """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CREATED"))
        .andExpect(jsonPath("$.userId").value(user.userId()))
        .andExpect(jsonPath("$.totalCents").value(13600))
        .andExpect(jsonPath("$.lines[0].productName").value("松烟墨"))
        .andExpect(jsonPath("$.lines[0].unitPriceCents").value(6800))
        .andExpect(jsonPath("$.lines[0].quantity").value(2));

    mockMvc.perform(get("/inventory/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(8));

    assertThat(orderCount()).isEqualTo(1);
  }

  @Test
  void rejectsOrderWhenStockIsShort() throws Exception {
    LoggedIn user = registerAndLogin("buyer@example.com");

    place(user, "short-stock", """
        {"lines":[{"productId":1,"quantity":11}]}
        """)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

    assertThat(orderCount()).isZero();
    assertThat(available(1)).isEqualTo(10);
  }

  @Test
  void rollsBackEarlierLinesWhenALaterLineIsShort() throws Exception {
    LoggedIn user = registerAndLogin("buyer@example.com");

    place(user, "rollback-lines", """
        {"lines":[{"productId":1,"quantity":1},{"productId":2,"quantity":11}]}
        """)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

    assertThat(orderCount()).isZero();
    assertThat(available(1)).isEqualTo(10);
    assertThat(available(2)).isEqualTo(10);
  }

  @Test
  void rejectsOrderWithoutLogin() throws Exception {
    mockMvc.perform(post("/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"lines":[{"productId":1,"quantity":1}]}
                """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void listsOnlyTheCurrentUsersOrders() throws Exception {
    LoggedIn buyer = registerAndLogin("buyer@example.com");
    LoggedIn other = registerAndLogin("other@example.com");

    MvcResult created = place(buyer, "buyer-order", """
        {"lines":[{"productId":1,"quantity":1}]}
        """)
        .andExpect(status().isCreated())
        .andReturn();
    int orderId = JsonPath.read(body(created), "$.id");

    mockMvc.perform(get("/orders").header("Authorization", bearer(buyer.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(orderId));

    mockMvc.perform(get("/orders").header("Authorization", bearer(other.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    mockMvc.perform(get("/orders/" + orderId).header("Authorization", bearer(other.token())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
  }

  @Test
  void rejectsDuplicateEmailAndWrongPassword() throws Exception {
    registerAndLogin("buyer@example.com");

    mockMvc.perform(post("/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"buyer@example.com","password":"secret-pass","name":"另一个"}
                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));

    mockMvc.perform(post("/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"buyer@example.com","password":"wrong-pass"}
                """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void listsAProductAndSetsItsStock() throws Exception {
    LoggedIn user = registerAndLogin("buyer@example.com");

    MvcResult created = mockMvc.perform(post("/products")
            .header("Authorization", bearer(user.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"端砚","priceCents":22000,"description":"实用"}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("端砚"))
        .andReturn();
    int productId = JsonPath.read(body(created), "$.id");

    mockMvc.perform(get("/inventory/" + productId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(0));

    mockMvc.perform(put("/inventory/" + productId)
            .header("Authorization", bearer(user.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"available":3}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(3));

    place(user, "inkstone-" + productId, """
        {"lines":[{"productId":%d,"quantity":2}]}
        """.formatted(productId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.totalCents").value(44000))
        .andExpect(jsonPath("$.lines[0].productName").value("端砚"));

    assertThat(available(productId)).isEqualTo(1);
  }

  @Test
  void rejectsMalformedJson() throws Exception {
    LoggedIn user = registerAndLogin("buyer@example.com");

    mockMvc.perform(post("/orders")
            .header("Authorization", bearer(user.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
  }

  @Test
  void rejectsPasswordThatExceedsTheBcryptByteLimit() throws Exception {
    String tooLong = "密".repeat(25);

    mockMvc.perform(post("/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"long@example.com","password":"%s","name":"墨客"}
                """.formatted(tooLong)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    String accepted = "a".repeat(72);
    mockMvc.perform(post("/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"boundary@example.com","password":"%s","name":"墨客"}
                """.formatted(accepted)))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"boundary@example.com","password":"%s"}
                """.formatted(accepted + "extra")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void retriesTheSameOrderWithoutDeductingTwice() throws Exception {
    LoggedIn user = registerAndLogin("buyer@example.com");
    String body = """
        {"lines":[{"productId":1,"quantity":2}]}
        """;

    MvcResult first = place(user, "same-order", body)
        .andExpect(status().isCreated())
        .andReturn();
    int orderId = JsonPath.read(body(first), "$.id");

    jdbcTemplate.update("delete from order_lines");
    jdbcTemplate.update("delete from orders");

    place(user, "same-order", body)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.totalCents").value(13600));

    assertThat(orderCount()).isEqualTo(1);
    assertThat(available(1)).isEqualTo(8);
    assertThat(orderId).isPositive();
  }

  @Test
  void rejectsAReusedKeyWhenTheOrderContentsDiffer() throws Exception {
    LoggedIn user = registerAndLogin("buyer@example.com");

    place(user, "one-key", """
        {"lines":[{"productId":1,"quantity":1}]}
        """)
        .andExpect(status().isCreated());

    place(user, "one-key", """
        {"lines":[{"productId":1,"quantity":2}]}
        """)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

    assertThat(orderCount()).isEqualTo(1);
    assertThat(available(1)).isEqualTo(9);
  }

  @Test
  void rejectsAnOrderWithoutAnIdempotencyKey() throws Exception {
    LoggedIn user = registerAndLogin("buyer@example.com");

    mockMvc.perform(post("/orders")
            .header("Authorization", bearer(user.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"lines":[{"productId":1,"quantity":1}]}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    assertThat(orderCount()).isZero();
  }

  @Test
  void reportsAnUnknownDeductionWhenInventoryDoesNotAnswer() throws Exception {
    LoggedIn user = registerAndLogin("buyer@example.com");
    Thread acceptor = null;
    try (ServerSocket socket = new ServerSocket(0)) {
      acceptor = new Thread(() -> {
        try (Socket ignored = socket.accept()) {
          Thread.sleep(10_000);
        } catch (Exception ignored) {
          // The request times out and this socket is closed.
        }
      });
      acceptor.setDaemon(true);
      acceptor.start();
      inventoryClient.useBaseUrl("http://127.0.0.1:" + socket.getLocalPort());

      place(user, "timed-out", """
          {"lines":[{"productId":1,"quantity":1}]}
          """)
          .andExpect(status().isGatewayTimeout())
          .andExpect(jsonPath("$.code").value("STOCK_DEDUCTION_UNKNOWN"));
    } finally {
      inventoryClient.useBaseUrl(inventoryBaseUrl);
      if (acceptor != null) {
        acceptor.interrupt();
      }
    }

    assertThat(orderCount()).isZero();
    assertThat(available(1)).isEqualTo(10);
  }

  private ResultActions place(LoggedIn user, String idempotencyKey, String json) throws Exception {
    return mockMvc.perform(post("/orders")
        .header("Authorization", bearer(user.token()))
        .header("Idempotency-Key", idempotencyKey)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json));
  }

  private LoggedIn registerAndLogin(String email) throws Exception {
    MvcResult registered = mockMvc.perform(post("/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s","password":"secret-pass","name":"墨客"}
                """.formatted(email)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist())
        .andReturn();
    Number userId = JsonPath.read(body(registered), "$.id");

    MvcResult session = mockMvc.perform(post("/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s","password":"secret-pass"}
                """.formatted(email)))
        .andExpect(status().isCreated())
        .andReturn();
    String token = JsonPath.read(body(session), "$.token");
    return new LoggedIn(userId.longValue(), token);
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private static String body(MvcResult result) throws Exception {
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private long orderCount() {
    Long count = jdbcTemplate.queryForObject("select count(*) from orders", Long.class);
    return count == null ? 0 : count;
  }

  private int available(long productId) {
    Integer available = inventoryJdbc.queryForObject(
        "select available from inventory where product_id = ?", Integer.class, productId);
    return available == null ? -1 : available;
  }

  private void createDatabase(String name) throws SQLException {
    try (Connection connection = DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement statement = connection.createStatement()) {
      statement.execute("create database " + name);
    } catch (SQLException exception) {
      if (!"42P04".equals(exception.getSQLState())) {
        throw exception;
      }
    }
  }

  private ConfigurableApplicationContext startService(ServiceStarter starter, String database) {
    return starter.start(
        "--server.port=0",
        "--spring.datasource.url=" + databaseUrl(database),
        "--spring.datasource.username=" + postgres.getUsername(),
        "--spring.datasource.password=" + postgres.getPassword());
  }

  private String databaseUrl(String database) {
    return postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + database);
  }

  private static String baseUrl(ConfigurableApplicationContext context) {
    return "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
  }

  private static JdbcTemplate jdbc(String url) {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        url, postgres.getUsername(), postgres.getPassword());
    return new JdbcTemplate(dataSource);
  }

  @FunctionalInterface
  private interface ServiceStarter {
    ConfigurableApplicationContext start(String... args);
  }

  private record LoggedIn(long userId, String token) {}
}
