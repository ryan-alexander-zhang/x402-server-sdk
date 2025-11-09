# x402-server-sdk

## 项目简介
x402-server-sdk 是一个基于 Spring Boot 的服务端 SDK，包含通用模块（x402-common）、自动配置模块（x402-spring-boot-autoconfigure）和启动器模块（x402-spring-boot-starter）。外部应用只需引入 starter，即可自动获得 x402 相关功能。

## 快速开始

### 1. 添加依赖
在你的 Spring Boot 项目的 `pom.xml` 中添加如下依赖：
```xml
<dependency>
    <groupId>com.ryan.x402</groupId>
    <artifactId>x402-spring-boot-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. 自动配置说明
- starter 已自动依赖 autoconfigure 模块，无需手动引入。
- autoconfigure 模块通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件声明自动配置类，Spring Boot 会自动加载，无需额外配置 `@ComponentScan`。
- 相关 Bean 会自动注入到你的应用上下文。

## 主要功能
- 支付与结算相关模型和工具类
- 自动化配置，开箱即用
- Spring Boot Starter 方式集成

## 常见问题
- 自动注入失败：请确认已正确引入 starter 依赖，且 autoconfigure 模块的 `AutoConfiguration.imports` 文件存在于 jar 包的 `META-INF/spring/` 目录下。
- 依赖缺失：请确认 pom.xml 中已添加所有必要依赖。

## 联系方式
如有问题或建议，请联系项目维护者。

---
