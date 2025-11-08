package com.ryan.x402.intereptor;

import static java.math.BigDecimal.TEN;
import static java.math.RoundingMode.DOWN;

import com.ryan.x402.annotation.X402Payment;
import com.ryan.x402.facilitator.FacilitatorClient;
import com.ryan.x402.model.ExactSchemePayload;
import com.ryan.x402.model.PaymentPayload;
import com.ryan.x402.model.PaymentRequiredResponse;
import com.ryan.x402.model.PaymentRequirements;
import com.ryan.x402.model.SettlementResponse;
import com.ryan.x402.model.SettlementResponseHeader;
import com.ryan.x402.model.VerificationResponse;
import com.ryan.x402.util.Json;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class X402Interceptor implements HandlerInterceptor {

  private static final String ATTR_REQUIREMENTS = "x402.payment.requirements";
  private static final String ATTR_HEADER = "x402.payment.header";
  private static final String ATTR_PAYLOAD = "x402.payment.payload";

  private final String defaultPayTo;
  private final String network;            // e.g. "base-sepolia"
  private final String asset;              // e.g. "USDC"
  private final int maxTimeoutSeconds;  // e.g. 30
  private final FacilitatorClient facilitator;

  public X402Interceptor(String defaultPayTo,
      String network,
      String asset,
      int maxTimeoutSeconds,
      FacilitatorClient facilitator) {
    this.defaultPayTo = Objects.requireNonNull(defaultPayTo);
    this.network = Objects.requireNonNull(network);
    this.asset = Objects.requireNonNull(asset);
    this.maxTimeoutSeconds = maxTimeoutSeconds;
    this.facilitator = Objects.requireNonNull(facilitator);
  }

  /* ======================== preHandle: /verify ======================== */

  @Override
  public boolean preHandle(HttpServletRequest request,
      HttpServletResponse response,
      Object handler) throws Exception {

    X402Payment annotation = resolveAnnotation(handler);
    // Non-payment endpoint, skip
    if (annotation == null) {
      return true;
    }

    String path = request.getRequestURI();
    PaymentRequirements requirements = buildRequirements(path, annotation);

    String header = request.getHeader("X-PAYMENT");
    if (!StringUtils.hasText(header)) {
      respond402(response, requirements, null);
      return false;
    }

    PaymentPayload payload;
    VerificationResponse vr;

    try {
      payload = PaymentPayload.fromHeader(header);

      // check resource and path math
      if (!Objects.equals(payload.payload.get("resource"), path)) {
        respond402(response, requirements, "resource mismatch");
        return false;
      }

      vr = facilitator.verify(header, requirements);
    } catch (IllegalArgumentException ex) {
      respond402(response, requirements, "malformed X-PAYMENT header");
      return false;
    } catch (IOException ex) {
      // communication error with facilitator
      respond500(response, "Payment verification failed: " + ex.getMessage());
      return false;
    } catch (Exception ex) {
      respond500(response, "Internal server error during payment verification");
      return false;
    }

    if (!vr.isValid) {
      respond402(response, requirements, vr.invalidReason);
      return false;
    }

    // verify passed, store for afterCompletion
    request.setAttribute(ATTR_REQUIREMENTS, requirements);
    request.setAttribute(ATTR_HEADER, header);
    request.setAttribute(ATTR_PAYLOAD, payload);

    return true;
  }

  /* ======================== afterCompletion: /settle ======================== */

  @Override
  public void afterCompletion(HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      @Nullable Exception ex) throws Exception {

    PaymentRequirements requirements =
        (PaymentRequirements) request.getAttribute(ATTR_REQUIREMENTS);
    String header =
        (String) request.getAttribute(ATTR_HEADER);
    PaymentPayload payload =
        (PaymentPayload) request.getAttribute(ATTR_PAYLOAD);

    // Non-payment endpoint or verification didn't pass, skip
    if (requirements == null || header == null || payload == null) {
      return;
    }

    // If response already indicates an error, skip settlement
    if (response.getStatus() >= 400) {
      return;
    }

    try {
      SettlementResponse sr = facilitator.settle(header, requirements);
      if (sr == null || !sr.success) {
        if (!response.isCommitted()) {
          String errorMsg = (sr != null && sr.error != null)
              ? sr.error
              : "settlement failed";
          respond402(response, requirements, errorMsg);
        }
        return;
      }

      try {
        String payer = extractPayerFromPayload(payload);
        String base64Header = createPaymentResponseHeader(sr, payer);
        response.setHeader("X-PAYMENT-RESPONSE", base64Header);
        response.setHeader("Access-Control-Expose-Headers", "X-PAYMENT-RESPONSE");
      } catch (Exception buildEx) {
        if (!response.isCommitted()) {
          respond500(response, "Failed to create settlement response header");
        }
      }

    } catch (Exception e) {
      if (!response.isCommitted()) {
        respond402(response, requirements, "settlement error: " + e.getMessage());
      }
    }
  }

  /* ======================== Resolve Annotation ======================== */

  @Nullable
  private X402Payment resolveAnnotation(Object handler) {
    if (!(handler instanceof HandlerMethod hm)) {
      return null;
    }

    X402Payment methodAnn = hm.getMethodAnnotation(X402Payment.class);
    if (methodAnn != null) {
      return methodAnn;
    }

    return hm.getBeanType().getAnnotation(X402Payment.class);
  }

  /* ======================== helpers ======================== */

  private PaymentRequirements buildRequirements(String path, X402Payment ann) {
    String priceStr = ann.price();
    if (!StringUtils.hasText(priceStr)) {
      throw new IllegalStateException("@X402Payment.price must not be empty");
    }

    // Validate the number
    BigDecimal priceDecimal = new BigDecimal(priceStr).multiply(TEN.pow(6)).setScale(0, DOWN);

    String payTo = StringUtils.hasText(ann.payTo()) ? ann.payTo() : defaultPayTo;

    PaymentRequirements pr = new PaymentRequirements();
    pr.scheme = "exact";
    pr.network = network;
    pr.maxAmountRequired = priceDecimal.toPlainString();
    pr.asset = asset;
    pr.resource = path;
    pr.mimeType = "application/json";
    pr.payTo = payTo;
    pr.maxTimeoutSeconds = maxTimeoutSeconds;
    return pr;
  }

  private void respond402(HttpServletResponse resp,
      PaymentRequirements requirements,
      String error) throws IOException {

    if (resp.isCommitted()) {
      return;
    }

    resp.resetBuffer();
    resp.setStatus(HttpServletResponse.SC_PAYMENT_REQUIRED);
    resp.setContentType("application/json");

    PaymentRequiredResponse prr = new PaymentRequiredResponse();
    prr.x402Version = 1;
    prr.accepts.add(requirements);
    prr.error = error;

    resp.getWriter().write(Json.MAPPER.writeValueAsString(prr));
    resp.flushBuffer();
  }

  private void respond500(HttpServletResponse resp,
      String message) throws IOException {

    if (resp.isCommitted()) {
      return;
    }

    resp.resetBuffer();
    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    resp.setContentType("application/json");
    resp.getWriter()
        .write("{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
    resp.flushBuffer();
  }

  private String createPaymentResponseHeader(SettlementResponse sr, String payer) throws Exception {
    SettlementResponseHeader settlementHeader = new SettlementResponseHeader(
        true,
        sr.txHash != null ? sr.txHash : "",
        sr.networkId != null ? sr.networkId : "",
        payer
    );

    String jsonString = Json.MAPPER.writeValueAsString(settlementHeader);
    return Base64.getEncoder()
        .encodeToString(jsonString.getBytes(StandardCharsets.UTF_8));
  }

  private String extractPayerFromPayload(PaymentPayload payload) {
    try {
      ExactSchemePayload exactPayload =
          Json.MAPPER.convertValue(payload.payload, ExactSchemePayload.class);
      return exactPayload.authorization != null
          ? exactPayload.authorization.from
          : null;
    } catch (Exception ex) {
      try {
        Object authorization = payload.payload.get("authorization");
        if (authorization instanceof Map<?, ?> map) {
          Object from = map.get("from");
          return from instanceof String ? (String) from : null;
        }
      } catch (Exception ignore) {
      }
      return null;
    }
  }
}
