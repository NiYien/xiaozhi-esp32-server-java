package com.xiaozhi.common.exception;

/**
 * 密码错误异常
 * 
 */

public class UserPasswordNotMatchException extends Exception {
  public UserPasswordNotMatchException() {
  }

  public UserPasswordNotMatchException(String msg) {
    super(msg);
  }
}