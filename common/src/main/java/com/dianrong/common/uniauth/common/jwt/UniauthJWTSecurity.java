package com.dianrong.common.uniauth.common.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.InvalidClaimException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.dianrong.common.uniauth.common.jwt.exp.InvalidJWTExpiredException;
import com.dianrong.common.uniauth.common.jwt.exp.InvalidSecurityKeyException;
import com.dianrong.common.uniauth.common.jwt.exp.JWTVerifierCreateFailedException;
import com.dianrong.common.uniauth.common.jwt.exp.LoginJWTCreateFailedException;
import com.dianrong.common.uniauth.common.jwt.exp.LoginJWTExpiredException;

import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Date;

import lombok.extern.slf4j.Slf4j;

import org.springframework.util.Assert;

/**
 * JWT操作相关的API 涉及加密和解密函数.
 *
 * @author wanglin
 */
@Slf4j
public class UniauthJWTSecurity {

  /**
   * 私钥.
   */
  private final RSAPrivateKey rsaPrivateKey;

  /**
   * 公钥.
   */
  private final RSAPublicKey rsaPublicKey;

  /**
   * JWT的验证对象.
   */
  private final JWTVerifier verifier;

  /**
   * 构造一个UniauthJWTSecurity.
   * 
   * @param rsaPrivateKey 私钥,不能为空.
   * @param rsaPublicKey 公钥,不能为空.
   * 
   * @throws JWTVerifierCreateFailedException 创建对应的JWTVerifier失败.
   * @throws InvalidSecurityKeyException 传入的公钥或私钥不规范.
   */
  public UniauthJWTSecurity(final String rsaPrivateKey, String rsaPublicKey)
      throws JWTVerifierCreateFailedException, NoSuchAlgorithmException,
      InvalidSecurityKeyException {
    Assert.notNull(rsaPrivateKey);
    Assert.notNull(rsaPublicKey);
    try {
      this.rsaPublicKey = RSASecurityKeyHelper.getPublickKey(rsaPublicKey);
    } catch (InvalidKeySpecException e) {
      log.error("PublicKey:{}  is invalid!", rsaPublicKey);
      throw new InvalidSecurityKeyException("PublicKey:" + rsaPublicKey + " is invalid!");
    }
    try {
      this.rsaPrivateKey = RSASecurityKeyHelper.getPrivateKey(rsaPrivateKey);
    } catch (InvalidKeySpecException e) {
      log.error("PrivateKey:{}  is invalid!", rsaPrivateKey);
      throw new InvalidSecurityKeyException("PrivateKey:" + rsaPrivateKey + "  is invalid!");
    }
    try {
      this.verifier = JWT.require(Algorithm.RSA256(this.rsaPublicKey)).build();
    } catch (Exception e) {
      log.error("failed to create JWTVerifier ", e);
      throw new JWTVerifierCreateFailedException("Failed create JWTVerifier ", e);
    }
  }

  /**
   * 创建JWT. 使用私钥加密生成JWT.
   * 
   * @throws LoginJWTCreateFailedException 生成登陆JWT失败.
   */
  public String createJwt(UniauthUserJWTInfo jwtInfo) throws LoginJWTCreateFailedException {
    Assert.notNull(jwtInfo);
    try {
      Date issuedAt = new Date(jwtInfo.getCreateTime());
      Date expiresAt = new Date(jwtInfo.getExpireTime());
      Integer tenancyId = jwtInfo.getTenancyId() == null ? null : jwtInfo.getTenancyId().intValue();
      return JWT.create().withIssuer(jwtInfo.getIssuer()).withIssuedAt(issuedAt)
          .withExpiresAt(expiresAt).withAudience(jwtInfo.getAudience())
          .withSubject(jwtInfo.getSubject())
          .withClaim(JWTConstant.IDENTITY_KEY, jwtInfo.getIdentity())
          .withClaim(JWTConstant.TENANCY_ID_KEY, tenancyId).sign(Algorithm.RSA256(rsaPrivateKey));
    } catch (Exception e) {
      log.error("Failed create login jwt ", e);
      throw new LoginJWTCreateFailedException("Failed create login jwt: " + jwtInfo, e);
    }
  }

  /**
   * 解密JWT,生成登陆用户身份信息.
   * 
   * @throws LoginJWTExpiredException JWT已经过期了.
   * @throws InvalidJWTExpiredException 非法的JWT.
   */
  public UniauthUserJWTInfo getInfoFromJwt(String jwt)
      throws LoginJWTExpiredException, InvalidJWTExpiredException {
    Assert.notNull(jwt);
    try {
      DecodedJWT decodedJwt = this.verifier.verify(jwt);
      String audience = decodedJwt.getAudience() == null ? null
          : decodedJwt.getAudience().isEmpty() ? null : decodedJwt.getAudience().get(0);
      long createTime = decodedJwt.getIssuedAt().getTime();
      long expireTIme = decodedJwt.getExpiresAt().getTime();
      Integer tenancyId = decodedJwt.getClaim(JWTConstant.TENANCY_ID_KEY).asInt();
      return new UniauthUserJWTInfo(decodedJwt.getIssuer(), audience, decodedJwt.getSubject(),
          decodedJwt.getClaim(JWTConstant.IDENTITY_KEY).asString(),
          tenancyId == null ? null : tenancyId.longValue(), createTime, expireTIme);
    } catch (InvalidClaimException invalidException) {
      if (invalidException.getMessage() != null
          && invalidException.getMessage().contains("The Token has expired on")) {
        throw new LoginJWTExpiredException(jwt + " is expired!");
      }
      log.error(jwt + " is expired!", invalidException);
      throw new InvalidJWTExpiredException(jwt + " is a invalid jwt token ", invalidException);
    } catch (Throwable t) {
      log.error(jwt + " is a invalid jwt token ", t);
      throw new InvalidJWTExpiredException(jwt + " is a invalid jwt token ", t);
    }
  }
}
