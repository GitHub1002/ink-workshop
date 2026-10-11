package ink.workshop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PlaceOrderIntegrationTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void resetShop() {
    jdbcTemplate.update("delete from order_lines");
    jdbcTemplate.update("delete from orders");
    jdbcTemplate.update("delete from sessions");
    jdbcTemplate.update("delete from users");
    jdbcTemplate.update("delete from inventory where product_id > 4");
    jdbcTemplate.update("delete from products where id > 4");
    jdbcTemplate.update("update inventory set available = 10");
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

    mockMvc.perform(post("/orders")
            .header("Authorization", bearer(user.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"lines":[{"productId":1,"quantity":2}]}
                """))
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

    mockMvc.perform(post("/orders")
            .header("Authorization", bearer(user.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"lines":[{"productId":1,"quantity":11}]}
                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

    assertThat(orderCount()).isZero();
    assertThat(available(1)).isEqualTo(10);
  }

  @Test
  void rollsBackEarlierLinesWhenALaterLineIsShort() throws Exception {
    LoggedIn user = registerAndLogin("buyer@example.com");

    mockMvc.perform(post("/orders")
            .header("Authorization", bearer(user.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"lines":[{"productId":1,"quantity":1},{"productId":2,"quantity":11}]}
                """))
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

    MvcResult created = mockMvc.perform(post("/orders")
            .header("Authorization", bearer(buyer.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"lines":[{"productId":1,"quantity":1}]}
                """))
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

    mockMvc.perform(post("/orders")
            .header("Authorization", bearer(user.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"lines":[{"productId":%d,"quantity":2}]}
                """.formatted(productId)))
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
    Integer available = jdbcTemplate.queryForObject(
        "select available from inventory where product_id = ?", Integer.class, productId);
    return available == null ? -1 : available;
  }

  private record LoggedIn(long userId, String token) {}
}
