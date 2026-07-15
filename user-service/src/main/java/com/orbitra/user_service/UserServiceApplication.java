/*
  UserServiceApplication.java
  Entry point for User Service - owns profile data (name, contact info,
  preferences) for an Account, keyed by the same Account.id as auth-service.
*/
package com.orbitra.user_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UserServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
	}

}
