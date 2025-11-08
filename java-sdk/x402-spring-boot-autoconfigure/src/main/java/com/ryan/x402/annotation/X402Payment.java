package com.ryan.x402.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface X402Payment {

  /**
   * "10000" = 0.01 USDC(6 decimals)
   */
  String price();

  String payTo() default "";
}