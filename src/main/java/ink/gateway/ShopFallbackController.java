package ink.gateway;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ShopFallbackController {

  @RequestMapping("/fallback/shop")
  ResponseEntity<GatewayError> unavailable() {
    return ResponseEntity.status(503)
        .body(new GatewayError("SHOP_UNAVAILABLE", "店铺暂时不可用，请稍后再试"));
  }

  record GatewayError(String code, String message) {}
}
