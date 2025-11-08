package com.ryan.x402.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "x402")
public class X402Configuration {

  /**
   * Whether to enable X402 payment interception
   */
  private boolean enabled = false;

  /**
   * Default payee address, can be overridden by @X402Payment.payTo
   */
  private String defaultPayTo;

  /**
   * Network identifier, e.g. base-sepolia
   */
  private String network = "base-sepolia";

  /**
   * Token symbol, e.g. USDC
   */
  private String asset = "USDC";

  /**
   * Maximum payment waiting time (seconds)
   */
  private int maxTimeoutSeconds = 30;

  /**
   * Facilitator base URL. e.g. https://facilitator.example.com
   */
  private String facilitatorBaseUrl;

  // getter / setter

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getDefaultPayTo() {
    return defaultPayTo;
  }

  public void setDefaultPayTo(String defaultPayTo) {
    this.defaultPayTo = defaultPayTo;
  }

  public String getNetwork() {
    return network;
  }

  public void setNetwork(String network) {
    this.network = network;
  }

  public String getAsset() {
    return asset;
  }

  public void setAsset(String asset) {
    this.asset = asset;
  }

  public int getMaxTimeoutSeconds() {
    return maxTimeoutSeconds;
  }

  public void setMaxTimeoutSeconds(int maxTimeoutSeconds) {
    this.maxTimeoutSeconds = maxTimeoutSeconds;
  }

  public String getFacilitatorBaseUrl() {
    return facilitatorBaseUrl;
  }

  public void setFacilitatorBaseUrl(String facilitatorBaseUrl) {
    this.facilitatorBaseUrl = facilitatorBaseUrl;
  }
}
